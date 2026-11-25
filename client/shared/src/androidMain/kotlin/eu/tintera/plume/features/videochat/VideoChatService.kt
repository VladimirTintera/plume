package eu.tintera.plume.features.videochat

import eu.tintera.plume.resources.locked_chat_notification_title
import eu.tintera.plume.core.telegram.ChatRepository
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
import eu.tintera.plume.client.R
import eu.tintera.plume.core.notifications.CALLS_CHANNEL_ID
import eu.tintera.plume.core.notifications.NotificationIntents
import org.jetbrains.compose.resources.getString
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
 * - **Nepatří žádnému účtu** ([VideoChats]): běží nejvýš jeden videochat přes všechny účty,
 *   a připojení v jiném účtu tak jde touž službou. Dokud ji vlastnil účet, který ji spustil,
 *   skončila by s jeho videochatem i pod videochatem druhého účtu.
 */
class VideoChatService : Service(), KoinComponent {

    private val logger = Logger.withTag("VideoChatService")
    private val videoChats: VideoChats by inject()
    private val scope = MainScope()

    /** Typ, se kterým služba běží — mikrofon se přidá a ubere s mluvením. */
    private var runningWithMicrophone = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        scope.launch {
            videoChats.running.collect { running ->
                if (running == null) {
                    stop()
                    return@collect
                }
                updateMicrophone(running)
                runCatching { getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(running)) }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_LEAVE) {
            videoChats.running.value?.session?.leave()
            return START_NOT_STICKY
        }
        val chat = videoChats.running.value
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

    private suspend fun updateMicrophone(running: AccountVideoChat) {
        val microphone = running.chat.state.micOn && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (microphone == runningWithMicrophone) return
        runCatching { ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(running), foregroundTypes(microphone)) }
            .onSuccess { runningWithMicrophone = microphone }
            .onFailure { logger.w(it) { "Typ služby videochatu (mikrofon: $microphone) nejde změnit." } }
    }

    private suspend fun notification(running: AccountVideoChat): Notification {
        val chat = running.chat
        // Videochat zamčené skupiny (`LockedChats`) notifikace nejmenuje, jako zprávy a hovory z ní.
        val locked = chat.chatId in runCatching { running.account.get<ChatRepository>().lockedChatIds.value }.getOrDefault(emptySet())
        val title = if (locked) getString(Res.string.locked_chat_notification_title) else chat.title
        val person = Person.Builder().setName(title).setImportant(true).build()
        return NotificationCompat.Builder(this, CALLS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setStyle(NotificationCompat.CallStyle.forOngoingCall(person, leaveIntent()))
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            // Live Update (Android 16), jako hovor ve dvou.
            .setRequestPromotedOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentText(
                getString(if (chat.state.micOn) Res.string.video_chat_bar_speaking else Res.string.video_chat_notification_listening),
            )
            .setContentIntent(NotificationIntents.openChat(this, running.account.slot, NOTIFICATION_ID, chat.chatId))
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
 * Spouští [VideoChatService], když se posluchač k videochatu připojuje nebo v něm je — v kterémkoli
 * účtu ([VideoChats]). Připojení je vždycky ťuknutí na pruh v aplikaci, tedy z popředí.
 */
class VideoChatServiceControls(
    private val context: Context,
    private val videoChats: VideoChats,
    private val scope: CoroutineScope,
) {
    private val logger = Logger.withTag("VideoChatService")

    fun start() {
        scope.launch {
            videoChats.running
                .map { it?.chat?.groupCallId }
                .distinctUntilChanged()
                .collect { groupCallId ->
                    if (groupCallId == null) return@collect
                    runCatching { ContextCompat.startForegroundService(context, Intent(context, VideoChatService::class.java)) }
                        .onFailure { failure -> logger.w(failure) { "Službu videochatu nejde spustit." } }
                }
        }
    }
}
