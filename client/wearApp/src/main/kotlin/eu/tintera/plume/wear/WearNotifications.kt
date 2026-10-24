package eu.tintera.plume.wear

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.ChatNotification
import eu.tintera.plume.core.telegram.NotificationRepository
import eu.tintera.plume.core.telegram.NotificationUpdate
import eu.tintera.plume.core.ui.readString
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.wear_notification_channel
import eu.tintera.plume.resources.wear_notification_mark_read
import eu.tintera.plume.resources.wear_notification_me
import eu.tintera.plume.resources.wear_notification_not_sent
import eu.tintera.plume.resources.wear_quick_reply_1
import eu.tintera.plume.resources.wear_quick_reply_2
import eu.tintera.plume.resources.wear_quick_reply_3
import eu.tintera.plume.resources.wear_quick_reply_4
import eu.tintera.plume.resources.wear_quick_reply_5
import eu.tintera.plume.resources.wear_reply
import eu.tintera.plume.resources.wear_reply_label
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val logger = Logger.withTag("WearNotifications")

private const val CHANNEL_ID = "messages"

/** Kolik zpráv drží notifikace jednoho chatu — na hodinkách se víc nečte. */
private const val MAX_LINES = 6

/**
 * Notifikace na hodinkách z notifikačního API TDLib: jedna na chat (`MessagingStyle`, id
 * systémové notifikace = TDLib `notificationGroupId`), ťuknutí otevře aplikaci. Zmizí, když
 * TDLib skupinu stáhne (přečteno jinde). Telefonní presenter (`shared`) sem nejde.
 *
 * Akce **Odpovědět** (systémové zadávání s rychlými odpověďmi, `RemoteInput`) a **Přečteno**
 * vyřizuje [WearNotificationReceiver]; tenhle presenter jen kreslí a po odpovědi připíše vlastní
 * řádek ([showReply]) nebo selhání ([showReplyFailed]).
 */
class WearNotifications(private val context: Context, private val scope: CoroutineScope) {

    private class Line(
        val notificationId: Int?,
        val messageId: Long?,
        val sender: String?,
        val text: String,
        val timestampMs: Long,
        /** Vlastní odpověď z notifikace: TDLib ji nezná, takže notifikaci naživu nedrží. */
        val own: Boolean = false,
    )

    private class Group(val chatId: Long, val chatTitle: String, val lines: MutableList<Line>)

    /** Stav jen z jednovláknového [dispatcher]u, takže bez zámku. */
    private val groups = mutableMapOf<Int, Group>()

    private val dispatcher = Dispatchers.Default.limitedParallelism(1)

    fun start(repository: NotificationRepository) {
        scope.launch(dispatcher) {
            createChannel()
            repository.updates.collect { update ->
                when (update) {
                    is NotificationUpdate.Added -> add(update.notifications)
                    is NotificationUpdate.Removed -> remove(update.groupId, update.notificationIds)
                    NotificationUpdate.Cleared -> clear()
                    // Hovory hodinky nemají.
                    is NotificationUpdate.Call -> Unit
                }
            }
        }
    }

    /** Odpověď z notifikace: hned vlastní řádek, ať systém přestane čekat na překreslení. */
    suspend fun showReply(groupId: Int, text: String) = withContext(dispatcher) {
        val group = groups[groupId] ?: return@withContext NotificationManagerCompat.from(context).cancel(groupId)
        group.lines += Line(null, null, null, text, System.currentTimeMillis(), own = true)
        show(groupId, group, silent = true)
    }

    /** Odpověď neodešla: řádek se zvukem — selhání uživatel zaregistrovat musí. */
    suspend fun showReplyFailed(groupId: Int, text: String) = withContext(dispatcher) {
        val group = groups[groupId] ?: return@withContext
        group.lines.removeAll { it.own && it.text == text }
        group.lines += Line(null, null, null, readString(Res.string.wear_notification_not_sent, text), System.currentTimeMillis(), own = true)
        show(groupId, group, silent = false)
    }

    private suspend fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, readString(Res.string.wear_notification_channel), NotificationManager.IMPORTANCE_HIGH)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private suspend fun add(notifications: List<ChatNotification>) {
        notifications.groupBy { it.groupId }.forEach { (groupId, batch) ->
            val newest = batch.last()
            val group = groups.getOrPut(groupId) { Group(newest.chatId, newest.chatTitle, mutableListOf()) }
            batch.filter { line -> group.lines.none { it.notificationId == line.notificationId } }
                .forEach { group.lines += Line(it.notificationId, it.messageId, it.senderName, it.text, it.timestamp * 1000L) }
            while (group.lines.size > MAX_LINES) group.lines.removeAt(0)
            show(groupId, group, silent = batch.all { it.isSilent })
        }
    }

    private suspend fun remove(groupId: Int, ids: List<Int>) {
        val group = groups[groupId] ?: return
        group.lines.removeAll { it.notificationId in ids }
        // Vlastní řádky notifikaci naživu nedrží — TDLib je nezná, takže by je nikdy nestáhl.
        if (group.lines.all { it.own }) {
            groups -= groupId
            NotificationManagerCompat.from(context).cancel(groupId)
        } else {
            show(groupId, group, silent = true)
        }
    }

    private fun clear() {
        groups.clear()
        NotificationManagerCompat.from(context).cancelAll()
    }

    private suspend fun show(groupId: Int, group: Group, silent: Boolean) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            logger.d { "Notifikace na hodinkách nejsou povolené — skupina $groupId se nevydá." }
            return
        }
        val me = Person.Builder().setName(readString(Res.string.wear_notification_me)).build()
        val chat = Person.Builder().setName(group.chatTitle).build()
        // Název konverzace jen ve skupině (řádky mají odesílatele): v soukromém chatu je odesílatel
        // totéž co název a titulek by zněl „Notifikator: Notifikator".
        val isGroup = group.lines.any { it.sender != null }
        val style = NotificationCompat.MessagingStyle(me)
            .setConversationTitle(group.chatTitle.takeIf { isGroup })
            .setGroupConversation(isGroup)
        group.lines.forEach { line ->
            // `null` osoba = uživatel sám (vlastní odpověď z notifikace).
            val sender = if (line.own) null else line.sender?.let { Person.Builder().setName(it).build() } ?: chat
            style.addMessage(line.text, line.timestampMs, sender)
        }
        // Čte se po nejnovější zprávu NOTIFIKACE, ne chatu: co přišlo mezitím, uživatel neviděl.
        val newestMessageId = group.lines.lastOrNull { it.messageId != null }?.messageId ?: 0L
        // Ťuknutí otevře konverzaci (jako chat v dlaždici). Request code podle skupiny, aby se
        // intenty různých chatů neslily; běžící aktivitě (`singleTop`) id doručí `onNewIntent`.
        val open = PendingIntent.getActivity(
            context,
            groupId,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(MainActivity.EXTRA_CHAT_ID, group.chatId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setStyle(style)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setSilent(silent)
            .addAction(replyAction(groupId, group.chatId, newestMessageId))
            .addAction(readAction(groupId, group.chatId, newestMessageId))
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(groupId, notification) }
            .onFailure { logger.w(it) { "Notifikace skupiny $groupId se na hodinkách nevydala." } }
    }

    private suspend fun replyAction(groupId: Int, chatId: Long, newestMessageId: Long): NotificationCompat.Action {
        val input = RemoteInput.Builder(WearNotificationReceiver.EXTRA_REPLY)
            .setLabel(readString(Res.string.wear_reply_label))
            .setChoices(
                arrayOf(
                    readString(Res.string.wear_quick_reply_1),
                    readString(Res.string.wear_quick_reply_2),
                    readString(Res.string.wear_quick_reply_3),
                    readString(Res.string.wear_quick_reply_4),
                    readString(Res.string.wear_quick_reply_5),
                ),
            )
            .build()
        // MUTABLE: systém do intentu vkládá napsaný text; s IMMUTABLE by RemoteInput vrátil null.
        val intent = PendingIntent.getBroadcast(
            context,
            groupId,
            WearNotificationReceiver.intent(context, WearNotificationReceiver.ACTION_REPLY, groupId, chatId, newestMessageId),
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Action.Builder(R.drawable.ic_notification, readString(Res.string.wear_reply), intent)
            .addRemoteInput(input)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            // Hodinky umí samy navrhnout odpovědi podle zprávy.
            .setAllowGeneratedReplies(true)
            .setShowsUserInterface(false)
            .build()
    }

    private suspend fun readAction(groupId: Int, chatId: Long, newestMessageId: Long): NotificationCompat.Action {
        val intent = PendingIntent.getBroadcast(
            context,
            groupId,
            WearNotificationReceiver.intent(context, WearNotificationReceiver.ACTION_READ, groupId, chatId, newestMessageId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Action.Builder(R.drawable.ic_notification, readString(Res.string.wear_notification_mark_read), intent)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
            .setShowsUserInterface(false)
            .build()
    }
}
