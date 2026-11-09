package eu.tintera.plume.features.videochat

import eu.tintera.plume.core.accounts.AccountSlot
import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.accounts.primaryAccount
import eu.tintera.plume.client.R
import eu.tintera.plume.core.notifications.CALLS_CHANNEL_ID
import eu.tintera.plume.core.notifications.NotificationIntents
import eu.tintera.plume.core.ui.readString
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.video_chat_bar_speaking
import eu.tintera.plume.resources.video_chat_notification_listening
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.core.component.KoinComponent
import org.koin.core.context.GlobalContext
import org.koin.core.component.inject

/**
 * Služba v popředí, dokud je posluchač ve videochatu ([VideoChatSession]) — i se zavřenou
 * obrazovkou videochatu a aplikací na pozadí.
 *
 * - **Bez ní videochat na pozadí neběží**: systém proces zastaví a mikrofon aplikaci mimo
 *   popředí nedá. Typ `phoneCall` (s `MANAGE_OWN_CALLS`, jako hovor ve dvou) celou dobu,
 *   `microphone` jen s mluvením — přidá se s odtlumením, které je vždycky akce uživatele
 *   v aplikaci, tedy z popředí, jak to Android u mikrofonu chce.
 * - **Notifikace je `CallStyle`** s názvem videochatu a „Zavěsit" od systému, které videochat
 *   opustí; ťuknutí otevře skupinu (nad ní je pruh videochatu).
 * - **Skončí sama**, když posluchač odejde, nebo videochat skončí.
 */
class VideoChatService : Service(), KoinComponent {

    override fun getKoin() = GlobalContext.get().primaryAccount()

    private val slot: AccountSlot by inject()

    private val logger = Logger.withTag("VideoChatService")
    private val session: VideoChatSession by inject()
    private val scope = MainScope()

    /** Typ, se kterým služba běží — mikrofon se přidá a ubere s mluvením. */
    private var runningWithMicrophone = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        scope.launch {
            session.active.collect { chat ->
                if (chat == null || !chat.running) {
                    stop()
                    return@collect
                }
                updateMicrophone(chat)
                runCatching { getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(chat)) }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_LEAVE) {
            session.leave()
            return START_NOT_STICKY
        }
        val chat = session.active.value?.takeIf { it.running }
        if (chat == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        val started = runCatching {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, runBlocking { notification(chat) }, foregroundTypes(microphone = false))
        }
        if (started.isFailure) {
            // Spuštění z pozadí nebo chybějící oprávnění. Videochat tím nekončí, jen ho systém
            // na pozadí nemusí udržet — proto jen záznam.
            logger.w(started.exceptionOrNull()) { "Službu videochatu nejde spustit." }
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun foregroundTypes(microphone: Boolean): Int = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
            ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL or (if (microphone) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
        else -> 0
    }

    private suspend fun updateMicrophone(chat: ActiveVideoChat) {
        val microphone = chat.state.micOn && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (microphone == runningWithMicrophone) return
        runCatching { ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(chat), foregroundTypes(microphone)) }
            .onSuccess { runningWithMicrophone = microphone }
            .onFailure { logger.w(it) { "Typ služby videochatu (mikrofon: $microphone) nejde změnit." } }
    }

    private suspend fun notification(chat: ActiveVideoChat): Notification {
        val person = Person.Builder().setName(chat.title).setImportant(true).build()
        return NotificationCompat.Builder(this, CALLS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setStyle(NotificationCompat.CallStyle.forOngoingCall(person, leaveIntent()))
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentText(
                readString(if (chat.state.micOn) Res.string.video_chat_bar_speaking else Res.string.video_chat_notification_listening),
            )
            .setContentIntent(NotificationIntents.openChat(this, slot, NOTIFICATION_ID, chat.chatId))
            .build()
    }

    private fun leaveIntent(): PendingIntent = PendingIntent.getService(
        this,
        NOTIFICATION_ID,
        Intent(this, VideoChatService::class.java).setAction(ACTION_LEAVE),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun stop() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val ACTION_LEAVE = "eu.tintera.plume.action.VIDEO_CHAT_LEAVE"
        const val NOTIFICATION_ID = 7303
    }
}

/**
 * Spouští [VideoChatService], když se posluchač k videochatu připojuje nebo v něm je.
 * Připojení je vždycky ťuknutí na pruh v aplikaci, tedy z popředí.
 */
class VideoChatServiceControls(
    private val context: Context,
    private val session: VideoChatSession,
    private val scope: CoroutineScope,
) {
    private val logger = Logger.withTag("VideoChatService")

    fun start() {
        scope.launch {
            session.active
                .map { chat -> chat?.takeIf { it.running }?.groupCallId }
                .distinctUntilChanged()
                .collect { groupCallId ->
                    if (groupCallId == null) return@collect
                    runCatching { ContextCompat.startForegroundService(context, Intent(context, VideoChatService::class.java)) }
                        .onFailure { failure -> logger.w(failure) { "Službu videochatu nejde spustit." } }
                }
        }
    }
}
