package eu.tintera.plume.core.telegram

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.notifications.NotificationIntents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Zobrazuje notifikace v Androidu.
 *
 * **Id notifikace se skládá z TDLib `notificationId`**, ne z chatId — TDLib posílá zrušení podle
 * něj (`removedNotificationIds`) a bez shodného klíče by po přečtení jinde notifikace zůstaly viset.
 */
actual class NotificationPresenter(
    private val context: Context,
    private val scope: CoroutineScope,
) {

    private val logger = Logger.withTag("Notifications")

    private val manager = NotificationManagerCompat.from(context)

    actual fun start(repository: NotificationRepository) {
        createChannel()
        logger.d { "Kanál vytvořen, odebírám notifikace." }

        scope.launch {
            repository.updates.collect { update ->
                when (update) {
                    is NotificationUpdate.Added -> update.notifications.forEach(::show)
                    is NotificationUpdate.Removed -> remove(update)
                }
            }
        }
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Zprávy",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Nové zprávy z Telegramu"
        }
        manager.createNotificationChannel(channel)
    }

    private fun show(notification: ChatNotification) {
        // Od Androidu 13 je POST_NOTIFICATIONS běhové oprávnění. Bez kontroly by `notify`
        // tiše selhalo a nebylo by poznat proč.
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED

        if (!granted) {
            logger.d { "Notifikace se nezobrazí — chybí oprávnění POST_NOTIFICATIONS." }
            return
        }

        val title = notification.senderName
            ?.let { "$it · ${notification.chatTitle}" }
            ?: notification.chatTitle

        val group = GROUP_PREFIX + notification.chatId

        val built = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(title)
            .setContentText(notification.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notification.text))
            // Seskupení podle chatu — jinak by deset zpráv z jedné skupiny udělalo deset
            // samostatných notifikací v liště.
            .setGroup(group)
            .setSilent(notification.isSilent)
            .setAutoCancel(true)
            .setContentIntent(
                NotificationIntents.openChat(
                    context = context,
                    notificationId = notification.notificationId,
                    chatId = notification.chatId,
                ),
            )
            .build()

        logger.d { "Zobrazuji notifikaci ${notification.notificationId} z chatu ${notification.chatId}." }
        manager.notify(notification.notificationId, built)

        // Až po dítěti: se summary napřed by lišta na okamžik ukázala prázdnou skupinu.
        showSummary(notification, group)
    }

    /**
     * Hlavička skupiny.
     *
     * Bez ní Android sbalené notifikace vykreslí jako hromádku bez popisu — `setGroup` sám o sobě
     * říká jen „patří k sobě", ne co ta skupina je. Posílá se při každé zprávě znovu; se stejným
     * id se jen přepíše, takže to nic nemnoží.
     */
    private fun showSummary(notification: ChatNotification, group: String) {
        val summary = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(notification.chatTitle)
            .setGroup(group)
            .setGroupSummary(true)
            // Zvuk dělají děti, ne hlavička — jinak by každá zpráva pípla dvakrát.
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setAutoCancel(true)
            .setContentIntent(
                NotificationIntents.openChat(
                    context = context,
                    notificationId = summaryId(notification.groupId),
                    chatId = notification.chatId,
                ),
            )
            .build()

        manager.notify(summaryId(notification.groupId), summary)
    }

    private fun remove(update: NotificationUpdate.Removed) {
        update.notificationIds.forEach { manager.cancel(it) }
        cancelEmptySummary(update)
    }

    /**
     * Hlavička bez dětí je prázdný řádek v liště, který nejde odkliknout na nic rozumného.
     *
     * Zbylé děti se počítají z `activeNotifications` MINUS to, co jsme právě zrušili — `cancel`
     * je asynchronní a čerstvě zrušené notifikace se ve výpisu ještě chvíli objevují.
     */
    private fun cancelEmptySummary(update: NotificationUpdate.Removed) {
        val summaryId = summaryId(update.groupId)
        val removed = update.notificationIds.toSet()

        val active = manager.activeNotifications.filterNot { it.id in removed }
        val summary = active.firstOrNull { it.id == summaryId } ?: return

        // Skupinu si čteme z hlavičky samotné: `Removed` nese jen groupId od TDLib, ne chatId,
        // takže klíč skupiny bychom jinak nesestavili.
        val group = summary.notification.group ?: return
        val hasChildren = active.any { it.id != summaryId && it.notification.group == group }

        if (!hasChildren) {
            logger.d { "Skupina $group zůstala prázdná, ruším hlavičku." }
            manager.cancel(summaryId)
        }
    }

    /**
     * Id hlavičky skupiny.
     *
     * `inv()` překlopí kladné `groupId` do záporného čísla a je prosté — TDLib vydává `groupId`
     * i `notificationId` z oddělených čítačů, takže obojí může být třeba 1. Se syrovým `groupId`
     * by si hlavička a některá zpráva sáhly na totéž systémové id a jedna by druhou přepsala.
     */
    private fun summaryId(groupId: Int): Int = groupId.inv()

    private companion object {
        const val CHANNEL_ID = "messages"
        const val GROUP_PREFIX = "chat_"
    }
}
