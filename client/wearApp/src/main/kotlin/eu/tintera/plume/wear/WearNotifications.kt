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
import androidx.core.content.ContextCompat
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.ChatNotification
import eu.tintera.plume.core.telegram.NotificationRepository
import eu.tintera.plume.core.telegram.NotificationUpdate
import eu.tintera.plume.core.ui.readString
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.wear_notification_channel
import eu.tintera.plume.resources.wear_notification_me
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private val logger = Logger.withTag("WearNotifications")

private const val CHANNEL_ID = "messages"

/** Kolik zpráv drží notifikace jednoho chatu — na hodinkách se víc nečte. */
private const val MAX_LINES = 6

/**
 * Notifikace na hodinkách z notifikačního API TDLib: jedna na chat (`MessagingStyle`, id
 * systémové notifikace = TDLib `notificationGroupId`), ťuknutí otevře aplikaci. Zmizí, když
 * TDLib skupinu stáhne (přečteno jinde). Telefonní presenter (`shared`) sem nejde; tenhle umí jen
 * to, co hodinky potřebují — odpověď z notifikace a hovory zatím ne.
 */
class WearNotifications(private val context: Context, private val scope: CoroutineScope) {

    private class Line(val notificationId: Int, val sender: String?, val text: String, val timestampMs: Long)

    private class Group(val chatTitle: String, val lines: MutableList<Line>)

    /** Stav jen z jedné korutiny ([start]), takže bez zámku. */
    private val groups = mutableMapOf<Int, Group>()

    fun start(repository: NotificationRepository) {
        scope.launch {
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

    private suspend fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, readString(Res.string.wear_notification_channel), NotificationManager.IMPORTANCE_HIGH)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private suspend fun add(notifications: List<ChatNotification>) {
        notifications.groupBy { it.groupId }.forEach { (groupId, batch) ->
            val group = groups.getOrPut(groupId) { Group(batch.last().chatTitle, mutableListOf()) }
            batch.filter { line -> group.lines.none { it.notificationId == line.notificationId } }
                .forEach { group.lines += Line(it.notificationId, it.senderName, it.text, it.timestamp * 1000L) }
            while (group.lines.size > MAX_LINES) group.lines.removeAt(0)
            show(groupId, group, silent = batch.all { it.isSilent })
        }
    }

    private suspend fun remove(groupId: Int, ids: List<Int>) {
        val group = groups[groupId] ?: return
        group.lines.removeAll { it.notificationId in ids }
        if (group.lines.isEmpty()) {
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
            val sender = line.sender?.let { Person.Builder().setName(it).build() } ?: chat
            style.addMessage(line.text, line.timestampMs, sender)
        }
        val open = PendingIntent.getActivity(
            context,
            groupId,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setStyle(style)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .setSilent(silent)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(groupId, notification) }
            .onFailure { logger.w(it) { "Notifikace skupiny $groupId se na hodinkách nevydala." } }
    }
}
