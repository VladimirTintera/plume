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
                    is NotificationUpdate.Removed ->
                        update.notificationIds.forEach { manager.cancel(it) }
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

        val built = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(title)
            .setContentText(notification.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notification.text))
            // Seskupení podle chatu — jinak by deset zpráv z jedné skupiny udělalo deset
            // samostatných notifikací v liště.
            .setGroup(GROUP_PREFIX + notification.chatId)
            .setSilent(notification.isSilent)
            .setAutoCancel(true)
            .setContentIntent(NotificationIntents.openChat(context, notification.notificationId, notification.chatId))
            .build()

        logger.d { "Zobrazuji notifikaci ${notification.notificationId} z chatu ${notification.chatId}." }
        manager.notify(notification.notificationId, built)
    }

    private companion object {
        const val CHANNEL_ID = "messages"
        const val GROUP_PREFIX = "chat_"
    }
}
