package eu.tintera.plume.core.telegram

import co.touchlab.kermit.Logger
import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.dto.ChatTypeBasicGroup
import dev.g000sha256.tdl.dto.ChatTypeSupergroup
import dev.g000sha256.tdl.dto.FormattedText
import dev.g000sha256.tdl.dto.InputMessageReplyToMessage
import dev.g000sha256.tdl.dto.InputMessageText
import dev.g000sha256.tdl.dto.Message
import dev.g000sha256.tdl.dto.MessageSender
import dev.g000sha256.tdl.dto.MessageSenderChat
import dev.g000sha256.tdl.dto.MessageSenderUser
import dev.g000sha256.tdl.dto.MessageInteractionInfo
import dev.g000sha256.tdl.dto.MessageReplyToMessage
import dev.g000sha256.tdl.dto.MessageSendingState
import dev.g000sha256.tdl.dto.MessageSendingStateFailed
import dev.g000sha256.tdl.dto.MessageSendingStatePending
import dev.g000sha256.tdl.dto.MessageSourceChatHistory
import dev.g000sha256.tdl.dto.MessageText
import dev.g000sha256.tdl.dto.ReactionTypeEmoji
import dev.g000sha256.tdl.dto.TextEntity
import dev.g000sha256.tdl.toResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
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

    /**
     * Keš odesílatelů (jméno + avatar).
     *
     * Ve skupině pošle padesát zpráv deset lidí a u reakcí se tytéž osoby opakují napříč emoji;
     * bez keše by to znamenalo desítky `getUser` volání jen kvůli hlavičkám bublin a avatarům.
     */
    private val senders = mutableMapOf<Long, ReactionSender>()
    private val sendersMutex = Mutex()

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

    override fun reactionUpdates(chatId: Long): Flow<MessageReactions> =
        client.messageInteractionInfoUpdates
            .filter { it.chatId == chatId }
            .map { update ->
                MessageReactions(
                    messageId = update.messageId,
                    reactions = update.interactionInfo.toReactions(),
                )
            }

    override suspend fun setReaction(
        chatId: Long,
        messageId: Long,
        emoji: String,
        add: Boolean,
    ): Result<Unit> {
        val type = ReactionTypeEmoji(emoji = emoji)

        return if (add) {
            client.addMessageReaction(
                chatId = chatId,
                messageId = messageId,
                reactionType = type,
                // `isBig` je ta velká animace přes celou obrazovku. Držíme ji na false —
                // patří k vědomému gestu, ne ke každému ťuknutí na čip.
                isBig = false,
                // Ať se emoji propíše do „naposledy použité" a nabízí se příště nahoře.
                updateRecentReactions = true,
            )
        } else {
            client.removeMessageReaction(chatId = chatId, messageId = messageId, reactionType = type)
        }
            .toResult()
            .map { }
    }

    override suspend fun availableReactions(chatId: Long, messageId: Long): Result<List<String>> =
        client.getMessageAvailableReactions(
            chatId = chatId,
            messageId = messageId,
            // Počet na řádek; TDLib podle toho krátí `topReactions`.
            rowSize = REACTION_ROW_SIZE,
        )
            .toResult()
            .map { available ->
                // Pořadí schválně: nejdřív doporučené, pak nedávno použité, pak populární.
                // Duplicity se odstraní, ale první výskyt si drží pozici.
                (available.topReactions + available.recentReactions + available.popularReactions)
                    .filterNotNull()
                    .mapNotNull { (it.type as? ReactionTypeEmoji)?.emoji }
                    .distinct()
            }

    override suspend fun message(chatId: Long, messageId: Long): Result<ChatMessage> {
        val isGroup = isGroup(chatId)
        return client.getMessage(chatId = chatId, messageId = messageId)
            .toResult()
            .map { it.toChatMessage(isGroup) }
    }

    override fun sentMessageUpdates(chatId: Long): Flow<SentMessageUpdate> = merge(
        client.messageSendSucceededUpdates
            .filter { it.message.chatId == chatId }
            .map { update ->
                SentMessageUpdate.Succeeded(
                    oldMessageId = update.oldMessageId,
                    message = update.message.toChatMessage(isGroup(chatId)),
                )
            },
        client.messageSendFailedUpdates
            .filter { it.message.chatId == chatId }
            .map { update ->
                SentMessageUpdate.Failed(
                    oldMessageId = update.oldMessageId,
                    message = update.message.toChatMessage(isGroup(chatId)),
                )
            },
    )

    override fun readOutboxUpdates(chatId: Long): Flow<Long> =
        client.chatReadOutboxUpdates
            .filter { it.chatId == chatId }
            .map { it.lastReadOutboxMessageId }

    override suspend fun send(chatId: Long, text: String, replyToMessageId: Long?): Result<ChatMessage> =
        client.sendMessage(
            chatId = chatId,
            // Vlákna (fóra) fáze 2 neřeší — null znamená hlavní vlákno chatu.
            topicId = null,
            replyTo = replyToMessageId?.let { id ->
                InputMessageReplyToMessage(
                    messageId = id,
                    // Citace části textu (označený úryvek) zatím neřešíme — odpovídá se na celou zprávu.
                    quote = null,
                    checklistTaskId = 0,
                    // Prázdný řetězec, ne null — tohle pole je v TDLib nenullovatelné
                    // a používá se jen u odpovědí na anketu.
                    pollOptionId = "",
                )
            },
            options = null,
            replyMarkup = null,
            inputMessageContent = InputMessageText(
                text = FormattedText(text = text, entities = emptyArray<TextEntity>()),
                linkPreviewOptions = null,
                clearDraft = true,
            ),
        )
            .toResult()
            // TDLib vrací zprávu okamžitě, s dočasným id a stavem Pending.
            .map { it.toChatMessage(isGroup(chatId)) }

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
                    avatarFileId = chat.photo?.small?.id,
                    lastReadOutboxMessageId = chat.lastReadOutboxMessageId,
                    isGroup = chat.type is ChatTypeBasicGroup || chat.type is ChatTypeSupergroup,
                )
            }

    override suspend fun sponsoredMessages(chatId: Long): Result<List<SponsoredMessage>> =
        client.getChatSponsoredMessages(chatId = chatId)
            .toResult()
            .map { sponsored ->
                sponsored.messages.filterNotNull().map { message ->
                    SponsoredMessage(
                        messageId = message.messageId,
                        title = message.title,
                        // Obsah je MessageContent jako u běžné zprávy; reklamy jsou v praxi text.
                        text = (message.content as? MessageText)?.text?.text.orEmpty(),
                        buttonText = message.buttonText,
                        url = message.sponsor.url,
                        isRecommended = message.isRecommended,
                        additionalInfo = message.additionalInfo.takeIf { it.isNotBlank() },
                    )
                }
            }

    override suspend fun reportSponsoredView(chatId: Long, messageId: Long): Result<Unit> =
        // TDLib nemá pro sponzorované zprávy zvláštní funkci — jejich id se předává do
        // `viewMessages` stejně jako u běžných.
        client.viewMessages(
            chatId = chatId,
            messageIds = longArrayOf(messageId),
            source = MessageSourceChatHistory(),
            forceRead = false,
        )
            .toResult()
            .map { }

    override suspend fun reportSponsoredClick(chatId: Long, messageId: Long): Result<Unit> =
        client.clickChatSponsoredMessage(
            chatId = chatId,
            messageId = messageId,
            // Klikáme na tlačítko, ne na médium, a ne z celoobrazovkového režimu.
            isMediaClick = false,
            fromFullscreen = false,
        )
            .toResult()
            .map { }

    private suspend fun isGroup(chatId: Long): Boolean =
        chatInfo(chatId).map { it.isGroup }.getOrDefault(false)

    private suspend fun Message.toChatMessage(isGroup: Boolean): ChatMessage {
        val senderId = senderId.plainId()

        return ChatMessage(
            id = id,
            isOutgoing = isOutgoing,
            // Jméno má smysl jen ve skupině a jen u cizích zpráv — jinde je to šum.
            senderName = if (isGroup && !isOutgoing) resolveSenderName(senderId) else null,
            senderId = senderId,
            text = captionText(),
            media = messageMedia(),
            reactions = interactionInfo.toReactions(),
            sendState = if (isOutgoing) sendingState.toSendState() else null,
            // Odpovědi mimo tenhle chat (citace z jiné konverzace) se zatím nevykreslují —
            // náhled se skládá z načtené historie, která je vždy jen z jednoho chatu.
            replyToMessageId = (replyTo as? MessageReplyToMessage)
                ?.takeIf { it.chatId == chatId }
                ?.messageId,
            isService = content.serviceText() != null,
            timestamp = date,
            isEdited = editDate > 0,
        )
    }

    /**
     * Reakce z interaction info. Vlastní (placená) emoji se přeskočí — jsou to samolepky
     * a vykreslení řeší jiná vrstva; zahodit je je lepší než u zprávy neukázat žádnou reakci.
     */
    private suspend fun MessageInteractionInfo?.toReactions(): List<MessageReaction> {
        val raw = this?.reactions?.reactions?.filterNotNull().orEmpty()

        return raw.mapNotNull { reaction ->
            val emoji = (reaction.type as? ReactionTypeEmoji)?.emoji ?: return@mapNotNull null

            // Odesílatele řešíme jen tam, kde se reálně vykreslí. U příspěvku se stovkou reakcí
            // by to byla hromada dotazů na data, která stejně skončí schovaná za počtem.
            val senders = if (reaction.totalCount <= MAX_SENDERS_SHOWN) {
                reaction.recentSenderIds
                    .filterNotNull()
                    .mapNotNull { resolveSender(it.plainId()) }
            } else {
                emptyList()
            }

            MessageReaction(
                emoji = emoji,
                count = reaction.totalCount,
                isChosen = reaction.isChosen,
                recentSenders = senders,
            )
        }
    }

    private suspend fun resolveSenderName(senderId: Long): String? = resolveSender(senderId)?.title

    private suspend fun resolveSender(senderId: Long): ReactionSender? {
        if (senderId == 0L) return null

        sendersMutex.withLock { senders[senderId] }?.let { return it }

        val user = client.getUser(senderId).toResult().getOrNull()

        val resolved = if (user != null) {
            val name = listOf(user.firstName, user.lastName)
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .ifBlank { null }
                ?: return null

            ReactionSender(
                id = senderId,
                title = name,
                avatarFileId = user.profilePhoto?.small?.id,
            )
        } else {
            // Odesílatel může být kanál (MessageSenderChat), ne uživatel — getUser pak selže.
            val chat = client.getChat(senderId).toResult().getOrNull() ?: return null
            ReactionSender(
                id = senderId,
                title = chat.title,
                avatarFileId = chat.photo?.small?.id,
            )
        }

        sendersMutex.withLock { senders[senderId] = resolved }
        return resolved
    }
}

/** Kolik emoji nabídnout na řádek ve výběru reakcí. */
private const val REACTION_ROW_SIZE = 8

/**
 * Do kolika reakcí se ještě vyplatí zjišťovat, kdo je poslal.
 *
 * Musí sedět se stropem, který používá UI pro zobrazení avatarů — kdyby byl tenhle nižší,
 * UI by chtělo avatary, které nikdo nenačetl.
 */
private const val MAX_SENDERS_SHOWN = 3

/** `MessageSender` je buď uživatel, nebo chat; pro naše účely stačí číselné id. */
private fun MessageSender.plainId(): Long = when (this) {
    is MessageSenderUser -> userId
    is MessageSenderChat -> chatId
    else -> 0L
}

/**
 * Převod stavu odeslání.
 *
 * `null` znamená, že zpráva je na serveru — TDLib pole vyplňuje jen dokud je rozepsaná
 * nebo selhala.
 */
private fun MessageSendingState?.toSendState(): SendState = when (this) {
    is MessageSendingStatePending -> SendState.Sending
    is MessageSendingStateFailed -> SendState.Failed
    else -> SendState.Sent
}
