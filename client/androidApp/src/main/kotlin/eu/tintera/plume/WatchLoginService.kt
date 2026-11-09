package eu.tintera.plume

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import co.touchlab.kermit.Logger
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import eu.tintera.plume.core.accounts.primaryAccount
import eu.tintera.plume.client.R
import eu.tintera.plume.core.notifications.WATCH_LOGIN_CHANNEL_ID
import eu.tintera.plume.core.notifications.createWatchLoginChannel
import eu.tintera.plume.core.telegram.AuthStage
import eu.tintera.plume.core.telegram.DeviceRepository
import eu.tintera.plume.core.telegram.TelegramSession
import eu.tintera.plume.core.ui.readString
import eu.tintera.plume.core.wear.WatchLogin
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.watch_login_confirm
import eu.tintera.plume.resources.watch_login_decline
import eu.tintera.plume.resources.watch_login_done
import eu.tintera.plume.resources.watch_login_failed
import eu.tintera.plume.resources.watch_login_text
import eu.tintera.plume.resources.watch_login_text_unnamed
import eu.tintera.plume.resources.watch_login_title
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.context.GlobalContext
import kotlin.time.Duration.Companion.seconds

private val logger = Logger.withTag("WatchLogin")

/** Jak dlouho se po probuzení procesu čeká, než TDLib ohlásí, jestli je telefon přihlášený. */
private val READY_TIMEOUT = 10.seconds

/**
 * Žádost hodinek o přihlášení ([WatchLogin]): hodinky pošlou odkaz ze svého QR kódu, telefon se
 * zeptá notifikací a po potvrzení je přihlásí, jako by kód naskenoval.
 *
 * - **Nikdy bez potvrzení.** Odkaz přichází jen od spárovaných hodinek se stejnou aplikací
 *   a podpisem, ale přihlášení nového zařízení k účtu je citlivé — rozhoduje člověk u telefonu.
 * - **Notifikace jen na telefonu** (`setLocalOnly`). Nepřihlášené hodinky zrcadlí notifikace
 *   z telefonu, takže by se žádost ukázala i na nich a „Přihlásit" by šlo ťuknout na hodinkách.
 * - **Každý nový odkaz notifikaci tiše přepíše** (stejné id, `setOnlyAlertOnce`): TDLib kód
 *   obnovuje zhruba po půl minutě a hodinky ho při čekání posílají znovu, takže potvrzení míří
 *   vždycky na platný kód.
 * - **Probuzený proces:** zpráva umí spustit mrtvou aplikaci, TDLib se teprve rozbíhá — na stav
 *   přihlášení se proto čeká ([READY_TIMEOUT]). `onMessageReceived` běží mimo hlavní vlákno,
 *   blokující čekání tu nevadí.
 */
class WatchLoginService : WearableListenerService() {

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != WatchLogin.PATH_REQUEST) return
        val link = event.data.decodeToString().trim()
        if (!WatchLogin.isLoginLink(link)) {
            logger.w { "Od hodinek ${event.sourceNodeId} nepřišel přihlašovací odkaz." }
            return
        }
        val koin = GlobalContext.getOrNull()?.primaryAccount() ?: return
        val session = koin.get<TelegramSession>()
        val stage = runBlocking { withTimeoutOrNull(READY_TIMEOUT) { session.authStage.first { it != AuthStage.Connecting } } }
        when (stage) {
            AuthStage.Ready -> runBlocking {
                WatchLoginNotification.ask(applicationContext, link, event.sourceNodeId, nodeName(event.sourceNodeId))
            }
            null -> replyToWatch(applicationContext, event.sourceNodeId, WatchLogin.Result.Failed)
            else -> replyToWatch(applicationContext, event.sourceNodeId, WatchLogin.Result.NotSignedIn)
        }
    }

    /** Jméno hodinek, jak ho ukazuje systém („Pixel Watch 3"); `null`, když ho nezjistí. */
    private fun nodeName(nodeId: String): String? = runCatching {
        Tasks.await(Wearable.getNodeClient(this).connectedNodes).firstOrNull { it.id == nodeId }?.displayName
    }.getOrNull()?.takeIf { it.isNotBlank() }
}

/** Potvrzení nebo zamítnutí z notifikace [WatchLoginNotification]. */
class WatchLoginReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onReceive(context: Context, intent: Intent) {
        val link = intent.getStringExtra(EXTRA_LINK) ?: return
        val nodeId = intent.getStringExtra(EXTRA_NODE_ID) ?: return
        val app = context.applicationContext
        val pending = goAsync()
        scope.launch {
            try {
                when (intent.action) {
                    ACTION_CONFIRM -> confirm(app, link, nodeId)
                    ACTION_DECLINE -> {
                        NotificationManagerCompat.from(app).cancel(WatchLoginNotification.ID)
                        replyToWatch(app, nodeId, WatchLogin.Result.Declined)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun confirm(context: Context, link: String, nodeId: String) {
        val koin = GlobalContext.getOrNull()?.primaryAccount() ?: return
        val session = koin.get<TelegramSession>()
        val ready = withTimeoutOrNull(READY_TIMEOUT) { session.authStage.first { it == AuthStage.Ready } } != null
        val result = if (ready) koin.get<DeviceRepository>().confirmQrLogin(link) else Result.failure(IllegalStateException("TDLib není přihlášený."))
        result
            .onSuccess {
                logger.i { "Hodinky přihlášené z telefonu (${it.applicationName})." }
                WatchLoginNotification.finished(context, success = true)
                replyToWatch(context, nodeId, WatchLogin.Result.Confirmed)
            }
            .onFailure {
                logger.w(it) { "Přihlášení hodinek z telefonu selhalo." }
                WatchLoginNotification.finished(context, success = false)
                replyToWatch(context, nodeId, WatchLogin.Result.Failed)
            }
    }

    companion object {
        const val ACTION_CONFIRM = "eu.tintera.plume.action.WATCH_LOGIN_CONFIRM"
        const val ACTION_DECLINE = "eu.tintera.plume.action.WATCH_LOGIN_DECLINE"
        const val EXTRA_LINK = "eu.tintera.plume.extra.WATCH_LOGIN_LINK"
        const val EXTRA_NODE_ID = "eu.tintera.plume.extra.WATCH_LOGIN_NODE"
    }
}

/** Notifikace „Přihlásit hodinky?" a její výsledek. */
internal object WatchLoginNotification {

    /** Jedna žádost naráz; nový odkaz i výsledek přepisují tutéž notifikaci. */
    const val ID = 0x57A7C410

    /** Výsledek po potvrzení zmizí sám — je to jen ohlášení, nic se s ním nedělá. */
    private const val RESULT_TIMEOUT_MS = 6_000L

    /** Kód stejně po pár minutách vyprší; neodbytá žádost nemá viset v liště donekonečna. */
    private const val ASK_TIMEOUT_MS = 5 * 60_000L

    suspend fun ask(context: Context, link: String, nodeId: String, nodeName: String?) {
        if (!canNotify(context)) {
            logger.w { "Žádost hodinek o přihlášení se neukáže — chybí oprávnění k notifikacím." }
            replyToWatch(context, nodeId, WatchLogin.Result.Failed)
            return
        }
        createWatchLoginChannel(context)
        val confirm = action(context, WatchLoginReceiver.ACTION_CONFIRM, link, nodeId)
        val decline = action(context, WatchLoginReceiver.ACTION_DECLINE, link, nodeId)
        val text = nodeName?.let { readString(Res.string.watch_login_text, it) } ?: readString(Res.string.watch_login_text_unnamed)
        val notification = NotificationCompat.Builder(context, WATCH_LOGIN_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(readString(Res.string.watch_login_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setOnlyAlertOnce(true)
            .setLocalOnly(true)
            .setTimeoutAfter(ASK_TIMEOUT_MS)
            // Odhrnutí je zamítnutí: hodinky se to dozví a nečekají dál.
            .setDeleteIntent(decline)
            .addAction(0, readString(Res.string.watch_login_decline), decline)
            .addAction(0, readString(Res.string.watch_login_confirm), confirm)
            .build()
        @Suppress("MissingPermission") // ověřeno v canNotify
        NotificationManagerCompat.from(context).notify(ID, notification)
    }

    suspend fun finished(context: Context, success: Boolean) {
        if (!canNotify(context)) return
        val notification = NotificationCompat.Builder(context, WATCH_LOGIN_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(readString(if (success) Res.string.watch_login_done else Res.string.watch_login_failed))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setLocalOnly(true)
            .setAutoCancel(true)
            .setTimeoutAfter(RESULT_TIMEOUT_MS)
            .build()
        @Suppress("MissingPermission") // ověřeno v canNotify
        NotificationManagerCompat.from(context).notify(ID, notification)
    }

    private fun action(context: Context, action: String, link: String, nodeId: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            action.hashCode(),
            Intent(context, WatchLoginReceiver::class.java)
                .setAction(action)
                .putExtra(WatchLoginReceiver.EXTRA_LINK, link)
                .putExtra(WatchLoginReceiver.EXTRA_NODE_ID, nodeId),
            // UPDATE_CURRENT: nový odkaz od hodinek musí přepsat ten v už vydané akci.
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    // `areNotificationsEnabled` pokrývá běhové oprávnění od Androidu 13 i vypnuté notifikace
    // aplikace na starších verzích, kde POST_NOTIFICATIONS neexistuje.
    private fun canNotify(context: Context): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()
}

/** Výsledek hodinkám; když se nedoručí, hodinky dál ukazují „potvrďte v telefonu". */
private fun replyToWatch(context: Context, nodeId: String, result: WatchLogin.Result) {
    Wearable.getMessageClient(context).sendMessage(nodeId, WatchLogin.PATH_RESULT, result.wire.encodeToByteArray())
        .addOnFailureListener { logger.w(it) { "Výsledek přihlášení se hodinkám $nodeId neposlal." } }
}
