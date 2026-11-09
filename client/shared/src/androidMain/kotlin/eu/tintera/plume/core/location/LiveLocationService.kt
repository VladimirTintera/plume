package eu.tintera.plume.core.location

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.accounts.primaryAccount
import eu.tintera.plume.client.R
import eu.tintera.plume.core.connectivity.AppForeground
import eu.tintera.plume.core.notifications.NotificationIntents
import eu.tintera.plume.core.telegram.ActiveLiveLocation
import eu.tintera.plume.core.telegram.LiveFix
import eu.tintera.plume.core.telegram.LiveLocationRepository
import eu.tintera.plume.core.telegram.headingOf
import eu.tintera.plume.core.telegram.shouldSendLiveFix
import eu.tintera.plume.core.ui.readPluralString
import eu.tintera.plume.core.ui.readString
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.live_location_notification_chats
import eu.tintera.plume.resources.live_location_notification_stop
import eu.tintera.plume.resources.live_location_notification_title
import eu.tintera.plume.resources.notification_channel_live_location
import eu.tintera.plume.resources.notification_channel_live_location_description
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.core.component.KoinComponent
import org.koin.core.context.GlobalContext
import org.koin.core.component.inject

/**
 * Služba v popředí (`foregroundServiceType="location"`), která posílá polohu do všech zpráv
 * se živou polohou ([LiveLocationRepository.active]). Bez ní by aplikace na pozadí do pár
 * vteřin zamrzla (viz „Notifikace" v docs/poznatky/notifikace.md) a poloha by se přestala posouvat.
 *
 * **Oprávnění k poloze NA POZADÍ není potřeba** a o ně se nežádá: služba typu `location`
 * spuštěná, když je aplikace v popředí, si přístup „při používání" ponese dál. Proto ji
 * [LiveLocationControls] spouští jen z popředí — z pozadí by ji Android 14 nepustil.
 *
 * Notifikace služby říká, kolika chatům se poloha sdílí, a má „Zastavit" (zastaví sdílení
 * všude). Když seznam aktivních zpráv zprázdní (vypršely, zastavené z bubliny nebo jinde),
 * služba skončí sama.
 */
class LiveLocationService : Service(), KoinComponent {

    override fun getKoin() = GlobalContext.get().primaryAccount()

    private val logger = Logger.withTag("LiveLocationService")
    private val repository: LiveLocationRepository by inject()
    private val scope = MainScope()
    private var listener: LocationListener? = null
    private var lastFix: LiveFix? = null
    private var lastSent: Pair<LiveFix, Long>? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        scope.launch {
            repository.active.collect { active ->
                if (active.isEmpty()) {
                    logger.i { "Žádná živá poloha, služba končí." }
                    stop()
                } else {
                    val updated = notification(active)
                    runCatching { getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, updated) }
                }
            }
        }
        // Někdo se dívá: TDLib chce polohu hned, ne až po intervalu.
        scope.launch {
            repository.viewed.collect { lastFix?.let { send(it, viewed = true) } }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            scope.launch { repository.stopAll() }
            return START_NOT_STICKY
        }
        val active = repository.active.value
        val notification = runBlocking { notification(active) }
        val started = runCatching {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0,
            )
        }
        if (started.isFailure) {
            // Oprávnění vzaté zpátky, nebo spuštění z pozadí. Poloha se dál posouvat nedá
            // a stojící „živá" poloha by klamala — sdílení se proto zastaví.
            logger.w(started.exceptionOrNull()) { "Službu polohy nejde spustit, sdílení končí." }
            scope.launch { repository.stopAll() }
            stopSelf()
            return START_NOT_STICKY
        }
        startUpdates()
        return START_NOT_STICKY
    }

    @SuppressLint("MissingPermission") // Kontroluje se hned na začátku.
    private fun startUpdates() {
        if (listener != null) return
        val manager = getSystemService(LocationManager::class.java) ?: return
        val fine = granted(Manifest.permission.ACCESS_FINE_LOCATION)
        if (!fine && !granted(Manifest.permission.ACCESS_COARSE_LOCATION)) return
        val provider = listOfNotNull(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) LocationManager.FUSED_PROVIDER else null,
            if (fine) LocationManager.GPS_PROVIDER else null,
            LocationManager.NETWORK_PROVIDER,
        ).firstOrNull { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) } ?: run {
            logger.w { "Žádný zapnutý poskytovatel polohy." }
            return
        }
        val created = LocationListener { location -> onLocation(location) }
        listener = created
        manager.requestLocationUpdates(provider, UPDATE_INTERVAL_MS, 0f, created, Looper.getMainLooper())
        logger.i { "Sleduji polohu přes $provider." }
    }

    private fun onLocation(location: Location) {
        val fix = LiveFix(
            latitude = location.latitude,
            longitude = location.longitude,
            accuracyMeters = if (location.hasAccuracy()) location.accuracy.toDouble() else 0.0,
            heading = headingOf(if (location.hasBearing() && location.hasSpeed() && location.speed > MOVING_SPEED) location.bearing else null),
        )
        lastFix = fix
        send(fix, viewed = false)
    }

    private fun send(fix: LiveFix, viewed: Boolean) {
        val now = System.currentTimeMillis()
        if (!shouldSendLiveFix(lastSent, fix, now, viewed)) return
        lastSent = fix to now
        scope.launch { repository.update(fix) }
    }

    private suspend fun notification(active: List<ActiveLiveLocation>) =
        NotificationCompat.Builder(this, ensureChannel())
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(readString(Res.string.live_location_notification_title))
            .setContentText(readPluralString(Res.plurals.live_location_notification_chats, active.size, active.size))
            .setCategory(NotificationCompat.CATEGORY_LOCATION_SHARING)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            // Bez toho by Android 12+ notifikaci služby ukázal až po deseti vteřinách — a o tom,
            // že se sdílí poloha, má uživatel vědět hned.
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openChat(active.firstOrNull()?.chatId))
            .addAction(0, readString(Res.string.live_location_notification_stop), stopIntent())
            .build()

    private suspend fun ensureChannel(): String {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                LIVE_LOCATION_CHANNEL_ID,
                readString(Res.string.notification_channel_live_location),
                // Ne LOW: tichá notifikace se schová do sbalené skupiny „Tiché" a o tom, že se
                // sdílí poloha, má uživatel vědět. Zvuk nedělá — notifikace je `setSilent`.
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = readString(Res.string.notification_channel_live_location_description) },
        )
        return LIVE_LOCATION_CHANNEL_ID
    }

    private fun openChat(chatId: Long?): PendingIntent? {
        val intent = chatId?.let { NotificationIntents.chatIntent(this, it) } ?: return null
        return PendingIntent.getActivity(this, NOTIFICATION_ID, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun stopIntent(): PendingIntent = PendingIntent.getService(
        this,
        NOTIFICATION_ID,
        Intent(this, LiveLocationService::class.java).setAction(ACTION_STOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun stop() {
        listener?.let { getSystemService(LocationManager::class.java)?.removeUpdates(it) }
        listener = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        listener?.let { getSystemService(LocationManager::class.java)?.removeUpdates(it) }
        listener = null
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val ACTION_STOP = "eu.tintera.plume.action.LIVE_LOCATION_STOP"
        const val NOTIFICATION_ID = 7301

        /** Jak často si o polohu říkat; posílá se jen to, co projde [shouldSendLiveFix]. */
        const val UPDATE_INTERVAL_MS = 5_000L

        /** Pod tuhle rychlost (m/s, chůze je ~1,4) je směr od GPS šum a neposílá se. */
        const val MOVING_SPEED = 0.5f
    }
}

/**
 * Spouští [LiveLocationService], když je co sdílet a aplikace je v popředí. Běží od startu
 * procesu: seznam živých poloh přežije restart aplikace (drží ho TDLib), a po novém spuštění
 * musí poloha jít dál, aniž by uživatel otevřel chat.
 *
 * **Jen z popředí** — tak to Android 14 u služby typu `location` chce. Proces probuzený pushem
 * na pozadí počká, až uživatel aplikaci otevře; do té doby poloha stojí.
 */
class LiveLocationControls(
    private val context: Context,
    private val repository: LiveLocationRepository,
    private val foreground: AppForeground,
    private val scope: CoroutineScope,
) {
    private val logger = Logger.withTag("LiveLocationControls")

    fun start() {
        scope.launch {
            combine(repository.active.map { it.isNotEmpty() }, foreground.isForeground) { sharing, visible -> sharing && visible }
                .distinctUntilChanged()
                .collect { start ->
                    if (!start) return@collect
                    runCatching { ContextCompat.startForegroundService(context, Intent(context, LiveLocationService::class.java)) }
                        .onFailure { failure -> logger.w(failure) { "Službu polohy nejde spustit." } }
                }
        }
    }
}

/** Kanál notifikace sdílení polohy. **Nesmí se měnit**, jako ostatní id kanálů. */
private const val LIVE_LOCATION_CHANNEL_ID = "live-location"
