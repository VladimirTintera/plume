package eu.tintera.plume.core.telegram

import co.touchlab.kermit.Logger
import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.dto.DeviceToken
import dev.g000sha256.tdl.dto.DeviceTokenApplePush
import dev.g000sha256.tdl.dto.DeviceTokenFirebaseCloudMessaging
import dev.g000sha256.tdl.dto.MessageSenderChat
import dev.g000sha256.tdl.dto.MessageSenderUser
import dev.g000sha256.tdl.dto.NotificationTypeNewMessage
import dev.g000sha256.tdl.toResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/** Notifikace nad TDLib. */
class TdlNotificationRepository(
    holder: TelegramClientHolder,
) : NotificationRepository {

    private val logger = Logger.withTag("Notifications")

    private val client: TdlClient = holder.client

    override val updates: Flow<NotificationUpdate> = flow {
        client.notificationGroupUpdates.collect { update ->
            // Jeden update může naráz přidávat i odebírat; pořadí je podstatné, aby po přečtení
            // jinde nezůstala viset stará notifikace, kterou právě nahradila novější.
            val removed = update.removedNotificationIds.toList()
            if (removed.isNotEmpty()) {
                emit(NotificationUpdate.Removed(update.notificationGroupId, removed))
            }

            val added = update.addedNotifications.filterNotNull()
            if (added.isEmpty()) return@collect

            val chatTitle = chatTitle(update.chatId)

            val notifications = added.mapNotNull { notification ->
                // Jiné typy (kalendářní pozvánky, hovory) zatím nezobrazujeme — přeskočit je
                // je lepší než ukázat notifikaci bez obsahu.
                val type = notification.type as? NotificationTypeNewMessage
                    ?: return@mapNotNull null

                ChatNotification(
                    groupId = update.notificationGroupId,
                    notificationId = notification.id,
                    chatId = update.chatId,
                    chatTitle = chatTitle,
                    senderName = senderName(type, chatTitle),
                    // `showPreview = false` znamená, že si uživatel nepřeje vidět obsah
                    // na zamykací obrazovce. Musíme to respektovat.
                    text = if (type.showPreview) type.message.previewText() else "Nová zpráva",
                    isSilent = notification.isSilent,
                    timestamp = notification.date,
                )
            }

            if (notifications.isNotEmpty()) emit(NotificationUpdate.Added(notifications))
        }
    }

    override suspend fun registerDevice(token: PushToken): Result<Unit> {
        val deviceToken: DeviceToken = when (token) {
            is PushToken.Firebase ->
                DeviceTokenFirebaseCloudMessaging(token = token.token, encrypt = token.encrypt)

            is PushToken.ApplePush ->
                DeviceTokenApplePush(deviceToken = token.token, isAppSandbox = token.sandbox)
        }

        return client.registerDevice(
            deviceToken = deviceToken,
            // Ostatní účty na zařízení (multi-account) zatím nepodporujeme.
            otherUserIds = LongArray(0),
        )
            .toResult()
            .map { }
            .onFailure { logger.e(it) { "registerDevice selhalo." } }
    }

    override suspend fun processPush(payload: String): Result<Unit> =
        client.processPushNotification(payload = payload)
            .toResult()
            .map { }
            .onFailure { logger.e(it) { "processPushNotification selhalo." } }

    private suspend fun chatTitle(chatId: Long): String =
        client.getChat(chatId).toResult().getOrNull()?.title.orEmpty()

    /**
     * Jméno odesílatele. V soukromém chatu je shodné s názvem chatu, takže by se v notifikaci
     * opakovalo dvakrát — tam se vrací `null`.
     */
    private suspend fun senderName(type: NotificationTypeNewMessage, chatTitle: String): String? {
        val senderId = when (val sender = type.message.senderId) {
            is MessageSenderUser -> sender.userId
            is MessageSenderChat -> sender.chatId
            else -> return null
        }

        val user = client.getUser(senderId).toResult().getOrNull() ?: return null
        val name = listOf(user.firstName, user.lastName)
            .filter { it.isNotBlank() }
            .joinToString(" ")

        return name.takeIf { it.isNotBlank() && it != chatTitle }
    }
}
