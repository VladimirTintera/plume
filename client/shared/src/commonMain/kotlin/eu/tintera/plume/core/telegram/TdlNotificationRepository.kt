package eu.tintera.plume.core.telegram

import co.touchlab.kermit.Logger
import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.dto.File
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

            // Chat se načte JEDNOU — je z něj potřeba název i fotka, a dvě volání kvůli jedné
            // notifikaci by byla zbytečná.
            val chat = client.getChat(update.chatId).toResult().getOrNull()
            val chatTitle = chat?.title.orEmpty()

            val notifications = added.mapNotNull { notification ->
                // Jiné typy (kalendářní pozvánky, hovory) zatím nezobrazujeme — přeskočit je
                // je lepší než ukázat notifikaci bez obsahu.
                val type = notification.type as? NotificationTypeNewMessage
                    ?: return@mapNotNull null

                val sender = resolveSender(type)

                ChatNotification(
                    groupId = update.notificationGroupId,
                    notificationId = notification.id,
                    chatId = update.chatId,
                    chatTitle = chatTitle,
                    senderName = sender?.name?.takeIf { it != chatTitle },
                    // `showPreview = false` znamená, že si uživatel nepřeje vidět obsah
                    // na zamykací obrazovce. Musíme to respektovat.
                    text = if (type.showPreview) type.message.previewText() else "Nová zpráva",
                    isSilent = notification.isSilent,
                    timestamp = notification.date,
                    // Ve skupině patří k řádku profilovka toho, kdo psal; v soukromém chatu
                    // je to totéž jako fotka chatu. Fotka chatu je záloha pro kanály a boty,
                    // kde odesílatel vlastní profilovku nemá.
                    avatarPath = downloadedPath(sender?.photo ?: chat?.photo?.small),
                    chatAvatarPath = downloadedPath(chat?.photo?.small),
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

    /**
     * Odesílatel zprávy.
     *
     * Jméno i profilovka se berou z jednoho dotazu. Volající si pak rozhodne, co s nimi:
     * jméno se v soukromém chatu zahazuje (opakovalo by název chatu), profilovka ne — tam
     * naopak je tou správnou ikonou notifikace.
     */
    private suspend fun resolveSender(type: NotificationTypeNewMessage): Sender? {
        val senderId = when (val sender = type.message.senderId) {
            is MessageSenderUser -> sender.userId
            is MessageSenderChat -> sender.chatId
            else -> return null
        }

        val user = client.getUser(senderId).toResult().getOrNull() ?: return null
        val name = listOf(user.firstName, user.lastName)
            .filter { it.isNotBlank() }
            .joinToString(" ")

        return Sender(name = name.takeIf { it.isNotBlank() }, photo = user.profilePhoto?.small)
    }

    /**
     * Cesta k souboru na disku, v případě potřeby ho nejdřív stáhne.
     *
     * **Tady se stahuje synchronně**, na rozdíl od obrázků v UI. Notifikace se skládá naráz
     * a ikonu na ni nejde doplnit dodatečně — bez čekání by prostě nebyla. Profilovka je
     * pár kilobajtů a většinou už na disku je, protože ji ukazuje seznam chatů.
     */
    private suspend fun downloadedPath(file: File?): String? {
        if (file == null) return null
        if (file.local.isDownloadingCompleted && file.local.path.isNotEmpty()) return file.local.path

        val downloaded = client.downloadFile(
            fileId = file.id,
            priority = AVATAR_PRIORITY,
            offset = 0,
            limit = 0,
            synchronous = true,
        )
            .toResult()
            .getOrNull()
            ?: return null

        return downloaded.local.path.takeIf {
            downloaded.local.isDownloadingCompleted && it.isNotEmpty()
        }
    }

    private class Sender(val name: String?, val photo: File?)

    private companion object {
        /** Nejvyšší priorita: notifikace čeká, takže má smysl ji vyřídit dřív než zbytek fronty. */
        const val AVATAR_PRIORITY = 32
    }
}
