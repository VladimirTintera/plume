package eu.tintera.plume.core.calls

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.accounts.AccountSlot
import eu.tintera.plume.core.accounts.ServiceOwner
import eu.tintera.plume.client.R
import eu.tintera.plume.core.notifications.CALLS_CHANNEL_ID
import eu.tintera.plume.core.notifications.NotificationIntents
import eu.tintera.plume.core.telegram.ActiveCall
import eu.tintera.plume.core.telegram.CallPhase
import eu.tintera.plume.core.telegram.CallRepository
import eu.tintera.plume.core.telegram.ChatRepository
import org.jetbrains.compose.resources.getString
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.call_bar_calling
import eu.tintera.plume.resources.call_bar_ringing
import eu.tintera.plume.resources.call_bar_connecting
import eu.tintera.plume.resources.call_notification_ongoing
import eu.tintera.plume.resources.call_waiting_for_network
import eu.tintera.plume.resources.notification_call_unknown
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Služba v popředí po dobu hovoru (od vytáčení / přijetí po zavěšení).
 *
 * - **Bez ní hovor na pozadí neběží**: zavřená aplikace přijde o mikrofon (Android od 11 dává
 *   mikrofon na pozadí jen službě v popředí, která vznikla, když byla aplikace vidět) a proces
 *   může systém zastavit. Typ `phoneCall` (s `MANAGE_OWN_CALLS`) a `microphone`.
 * - **Notifikace je `CallStyle`**: jméno, čas hovoru a „Zavěsit" od systému; ťuknutí vrátí do
 *   aplikace na obrazovku hovoru.
 * - **U ucha zhasne obrazovku senzor přiblížení**, dokud hovor jde do sluchátka — jinak by se
 *   tvář dotýkala tlačítek.
 * - **Skončí sama**, když hovor skončí (`CallRepository.active` je prázdný nebo konec).
 */
class CallService : Service(), KoinComponent {

    /** Účet, který službu spustil (viz [ServiceOwner]); nastaví ho [CallServiceControls]. */
    object Owner : ServiceOwner()

    override fun getKoin() = Owner.koin()

    private val logger = Logger.withTag("CallService")
    private val calls: CallRepository by inject()
    private val chats: ChatRepository by inject()
    private val scope = MainScope()
    private var proximity: PowerManager.WakeLock? = null
    private var callerName: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        scope.launch {
            calls.active.collect { call ->
                if (call == null || call.phase == CallPhase.Ended || call.phase == CallPhase.Failed || call.phase == CallPhase.Ringing) {
                    stop()
                    return@collect
                }
                updateProximity(call)
                updateCamera(call)
                runCatching { getSystemService(android.app.NotificationManager::class.java).notify(NOTIFICATION_ID, notification(call)) }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_HANG_UP) {
            scope.launch { calls.hangUp() }
            return START_NOT_STICKY
        }
        val call = calls.active.value
        if (call == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        val started = runCatching {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, runBlocking { notification(call) }, foregroundTypes())
        }
        if (started.isFailure) {
            // Spuštění z pozadí, nebo chybějící oprávnění. Hovor tím nekončí, jen ho systém
            // na pozadí nemusí udržet — proto jen záznam, ne zavěšení.
            logger.w(started.exceptionOrNull()) { "Službu hovoru nejde spustit." }
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun foregroundTypes(camera: Boolean = false, screen: Boolean = false): Int = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
            ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                (if (camera) ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA else 0) or
                (if (screen) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION else 0)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
        else -> 0
    }

    /** Typ služby, se kterým běží — kamera a obrazovka se přidají a uberou s hovorem. */
    private var runningWithCamera = false
    private var runningWithScreen = false

    /**
     * Kamera na pozadí: Android ji aplikaci mimo popředí odebere, ledaže běží služba typu
     * `camera`. Typ se přidá se zapnutím kamery (to je vždycky akce uživatele v aplikaci,
     * tedy z popředí, jak Android chce) a ubere s vypnutím.
     */
    private suspend fun updateCamera(call: ActiveCall) {
        val camera = call.camera && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        // Sdílení obrazovky: typ `mediaProjection` musí běžet dřív, než knihovna zavolá
        // `getMediaProjection` (Android 14+), proto se teprve po něm hlásí `ScreenCapture.serviceReady`.
        val screen = call.screenSharing && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        if (camera == runningWithCamera && screen == runningWithScreen) return
        runCatching { ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(call), foregroundTypes(camera = camera, screen = screen)) }
            .onSuccess {
                runningWithCamera = camera
                runningWithScreen = screen
            }
            .onFailure { logger.w(it) { "Typ služby hovoru (kamera: $camera, obrazovka: $screen) nejde změnit." } }
        ScreenCapture.serviceReady.value = runningWithScreen
    }

    private suspend fun notification(call: ActiveCall): Notification {
        val name = callerName
            ?: chats.summaries(listOf(call.userId)).firstOrNull()?.title?.also { callerName = it }
            ?: getString(Res.string.notification_call_unknown)
        val person = Person.Builder().setName(name).setImportant(true).build()
        val builder = NotificationCompat.Builder(this, CALLS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setStyle(NotificationCompat.CallStyle.forOngoingCall(person, hangUpIntent()))
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(NotificationIntents.showCall(this))
        val connectedAt = call.connectedAt
        if (call.networkLost) {
            builder.setContentText(getString(Res.string.call_waiting_for_network))
        } else if (connectedAt != null) {
            builder.setUsesChronometer(true).setWhen(connectedAt).setShowWhen(true)
                .setContentText(getString(Res.string.call_notification_ongoing))
        } else {
            builder.setContentText(
                getString(
                    when {
                        call.phase != CallPhase.Calling -> Res.string.call_bar_connecting
                        call.received -> Res.string.call_bar_ringing
                        else -> Res.string.call_bar_calling
                    },
                ),
            )
        }
        return builder.build()
    }

    private fun hangUpIntent(): PendingIntent = PendingIntent.getService(
        this,
        NOTIFICATION_ID,
        Intent(this, CallService::class.java).setAction(ACTION_HANG_UP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Zhasnutí u ucha jen ve sluchátku a jen při běžícím hovoru. */
    private fun updateProximity(call: ActiveCall) {
        // S obrazem se telefon drží před sebou, ne u ucha.
        val wanted = call.phase == CallPhase.Active && call.output.kind == CallAudioOutput.Kind.Earpiece &&
            !call.camera && !call.remoteVideo
        val lock = proximity ?: getSystemService(PowerManager::class.java)
            ?.takeIf { it.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK) }
            ?.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "plume:call-proximity")
            ?.also { proximity = it }
            ?: return
        if (wanted && !lock.isHeld) lock.acquire(MAX_CALL_MS)
        if (!wanted && lock.isHeld) lock.release()
    }

    private fun stop() {
        ScreenCapture.serviceReady.value = false
        proximity?.takeIf { it.isHeld }?.release()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        ScreenCapture.serviceReady.value = false
        proximity?.takeIf { it.isHeld }?.release()
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val ACTION_HANG_UP = "eu.tintera.plume.action.CALL_HANG_UP"
        const val NOTIFICATION_ID = 7302

        /** Pojistka zámku senzoru: i kdyby se hovor neukončil, po čtyřech hodinách se pustí. */
        const val MAX_CALL_MS = 4 * 60 * 60 * 1000L
    }
}

/**
 * Spouští [CallService], když hovor vytáčí, spojuje se, nebo běží. Spuštění přijde vždycky
 * z akce uživatele v aplikaci (Zavolat, Přijmout), tedy z popředí — tak to Android u služby
 * s mikrofonem chce.
 */
class CallServiceControls(
    private val slot: AccountSlot,
    private val context: Context,
    private val calls: CallRepository,
    private val scope: CoroutineScope,
) {
    private val logger = Logger.withTag("CallServiceControls")

    fun start() {
        scope.launch {
            calls.active
                .map { call -> call?.takeIf { it.phase in ONGOING }?.id }
                .distinctUntilChanged()
                .collect { callId ->
                    if (callId == null) return@collect
                    CallService.Owner.slot = slot
                    runCatching { ContextCompat.startForegroundService(context, Intent(context, CallService::class.java)) }
                        .onFailure { failure -> logger.w(failure) { "Službu hovoru nejde spustit." } }
                }
        }
    }

    private companion object {
        val ONGOING = setOf(CallPhase.Calling, CallPhase.Connecting, CallPhase.Active)
    }
}
