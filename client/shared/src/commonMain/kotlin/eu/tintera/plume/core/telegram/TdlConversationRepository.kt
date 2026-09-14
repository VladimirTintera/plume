package eu.tintera.plume.core.telegram

import co.touchlab.kermit.Logger
import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.dto.ChatTypeBasicGroup
import dev.g000sha256.tdl.dto.ChatTypeSupergroup
import dev.g000sha256.tdl.dto.FormattedText
import dev.g000sha256.tdl.dto.InputMessageText
import dev.g000sha256.tdl.dto.Message
import dev.g000sha256.tdl.dto.MessageSenderChat
import dev.g000sha256.tdl.dto.MessageSenderUser
import dev.g000sha256.tdl.dto.MessageSourceChatHistory
import dev.g000sha256.tdl.dto.TextEntity
import dev.g000sha256.tdl.toResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Konverzace nad TDLib.
 *
 * **Jména odesílatelů se kešují.** Ve skupině pošle padesát zpráv deset lidí; bez keše by to bylo
 * padesát `getUser` volání jen kvůli hlavičkám bublin.
 */
class TdlConversationRepository(
    holder: TelegramClientHolder,
) : ConversationRepository {

    private val logger = Logger.withTag("Conversation")

    private val client: TdlClient = holder.client

    private val senderNames = mutableMapOf<Long, String>()
    private val namesMutex = Mutex()

    override suspend fun history(
        chatId: Long,
        fromMessageId: Long,
        limit: Int,
    ): Result<List<ChatMessage>> {
        val isGroup = isGroup(chatId)

        return client.getChatHistory(
            chatId = chatId,
            fromMessageId = fromMessageId,
            // Kladný offset by vracel i zprávy NOVĚJŠÍ než fromMessageId. Nula = čistě do minulosti.
            offset = 0,
            limit = limit,
            // `false` = smí sáhnout na server. S `true` by se po doscrollování na konec lokální
            // cache vrátil prázdný seznam a vypadalo by to jako konec historie.
            onlyLocal = false,
        )
            .toResult()
            // Prvky pole jsou v Kotlin metadatech nullable, i když je Java signatura má jako
            // `Message[]` — TDLib do díry po nedostupné zprávě vloží null.
            .map { messages -> messages.messages.filterNotNull().map { it.toChatMessage(isGroup) } }
    }

    override fun newMessages(chatId: Long): Flow<ChatMessage> =
        client.newMessageUpdates
            .filter { it.message.chatId == chatId }
            .map { it.message.toChatMessage(isGroup(chatId)) }

    override fun deletedMessages(chatId: Long): Flow<List<Long>> =
        client.deleteMessagesUpdates
            // `isPermanent == false` znamená jen uvolnění z cache, ne smazání pro uživatele —
            // takovou zprávu z obrazovky odstranit nesmíme.
            .filter { it.chatId == chatId && it.isPermanent }
            .map { it.messageIds.toList() }

    override fun editedMessages(chatId: Long): Flow<ChatMessage> =
        client.messageContentUpdates
            .filter { it.chatId == chatId }
            .map { update ->
                // Update nese jen nový obsah, ne celou zprávu — zbytek se musí dočíst.
                client.getMessage(chatId = chatId, messageId = update.messageId)
                    .toResult()
                    .map { it.toChatMessage(isGroup(chatId)) }
            }
            .filter { it.isSuccess }
            .map { it.getOrThrow() }

    override suspend fun send(chatId: Long, text: String): Result<Unit> =
        client.sendMessage(
            chatId = chatId,
            // Vlákna (fóra) fáze 2 neřeší — null znamená hlavní vlákno chatu.
            topicId = null,
            replyTo = null,
            options = null,
            replyMarkup = null,
            inputMessageContent = InputMessageText(
                text = FormattedText(text = text, entities = emptyArray<TextEntity>()),
                linkPreviewOptions = null,
                clearDraft = true,
            ),
        )
            .toResult()
            .map { }

    override suspend fun markRead(chatId: Long, messageIds: List<Long>): Result<Unit> =
        client.viewMessages(
            chatId = chatId,
            messageIds = messageIds.toLongArray(),
            source = MessageSourceChatHistory(),
            // `forceRead = true`: bez toho TDLib označí za přečtené jen to, o čem si myslí,
            // že to uživatel opravdu viděl na obrazovce. My mu to tvrdíme sami.
            forceRead = true,
        )
            .toResult()
            .map { }

    override suspend fun chatInfo(chatId: Long): Result<ChatInfo> =
        client.getChat(chatId)
            .toResult()
            .map { chat ->
                ChatInfo(
                    id = chat.id,
                    title = chat.title,
                    isGroup = chat.type is ChatTypeBasicGroup || chat.type is ChatTypeSupergroup,
                )
            }

    private suspend fun isGroup(chatId: Long): Boolean =
        chatInfo(chatId).map { it.isGroup }.getOrDefault(false)

    private suspend fun Message.toChatMessage(isGroup: Boolean): ChatMessage {
        val senderId = when (val sender = senderId) {
            is MessageSenderUser -> sender.userId
            is MessageSenderChat -> sender.chatId
            else -> 0L
        }

        return ChatMessage(
            id = id,
            isOutgoing = isOutgoing,
            // Jméno má smysl jen ve skupině a jen u cizích zpráv — jinde je to šum.
            senderName = if (isGroup && !isOutgoing) resolveSenderName(senderId) else null,
            senderId = senderId,
            text = previewText(),
            timestamp = date,
            isEdited = editDate > 0,
        )
    }

    private suspend fun resolveSenderName(senderId: Long): String? {
        if (senderId == 0L) return null

        namesMutex.withLock { senderNames[senderId] }?.let { return it }

        val name = when (val user = client.getUser(senderId).toResult().getOrNull()) {
            null -> {
                // Odesílatel může být kanál (MessageSenderChat), ne uživatel — getUser pak selže.
                client.getChat(senderId).toResult().getOrNull()?.title
            }

            else -> listOf(user.firstName, user.lastName)
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .ifBlank { null }
        } ?: return null

        namesMutex.withLock { senderNames[senderId] = name }
        return name
    }
}
