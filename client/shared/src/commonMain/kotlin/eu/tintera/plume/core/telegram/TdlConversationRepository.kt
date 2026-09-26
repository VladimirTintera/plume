package eu.tintera.plume.core.telegram

import co.touchlab.kermit.Logger
import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.dto.InputTextQuote
import dev.g000sha256.tdl.dto.InputMessageReplyTo
import dev.g000sha256.tdl.dto.TextEntityTypeBold
import dev.g000sha256.tdl.dto.TextEntityTypeItalic
import dev.g000sha256.tdl.dto.TextEntityTypeUnderline
import dev.g000sha256.tdl.dto.TextEntityTypeStrikethrough
import dev.g000sha256.tdl.dto.TextEntityTypeSpoiler
import dev.g000sha256.tdl.dto.TextEntityTypeCustomEmoji
import dev.g000sha256.tdl.dto.MessageContent
import dev.g000sha256.tdl.dto.MessagePoll
import dev.g000sha256.tdl.dto.MessageContact
import dev.g000sha256.tdl.dto.ChatTypeSecret
import dev.g000sha256.tdl.dto.UserTypeBot
import dev.g000sha256.tdl.dto.OptionValueInteger
import dev.g000sha256.tdl.dto.InputPollTypeRegular
import dev.g000sha256.tdl.dto.InputPollTypeQuiz
import dev.g000sha256.tdl.dto.InputPollMedia
import dev.g000sha256.tdl.dto.InputPollMediaAnimation
import dev.g000sha256.tdl.dto.InputPollMediaLink
import dev.g000sha256.tdl.dto.InputPollMediaLocation
import dev.g000sha256.tdl.dto.InputPollMediaSticker
import dev.g000sha256.tdl.dto.InputPollMediaVenue
import dev.g000sha256.tdl.dto.Location
import dev.g000sha256.tdl.dto.Venue
import dev.g000sha256.tdl.dto.InputPollMediaPhoto
import dev.g000sha256.tdl.dto.InputPollMediaVideo
import dev.g000sha256.tdl.dto.InputPollOption
import dev.g000sha256.tdl.dto.InputMessagePoll
import dev.g000sha256.tdl.dto.MessagePhoto
import dev.g000sha256.tdl.dto.MessageVideo
import dev.g000sha256.tdl.dto.MessageAnimation
import dev.g000sha256.tdl.dto.MessageDocument
import dev.g000sha256.tdl.dto.MessageAudio
import dev.g000sha256.tdl.dto.MessageVoiceNote
import dev.g000sha256.tdl.dto.MessagePaidMedia
import dev.g000sha256.tdl.dto.ReplyMarkupShowKeyboard
import dev.g000sha256.tdl.dto.ReplyMarkupForceReply
import dev.g000sha256.tdl.dto.ReplyMarkupRemoveKeyboard
import dev.g000sha256.tdl.dto.KeyboardButtonTypeText
import dev.g000sha256.tdl.dto.KeyboardButtonTypeRequestPhoneNumber
import dev.g000sha256.tdl.dto.KeyboardButtonTypeRequestLocation
import dev.g000sha256.tdl.dto.KeyboardButtonTypeRequestPoll
import dev.g000sha256.tdl.dto.KeyboardButtonTypeRequestUsers
import dev.g000sha256.tdl.dto.KeyboardButtonTypeRequestChat
import dev.g000sha256.tdl.dto.KeyboardButtonTypeWebApp
import dev.g000sha256.tdl.dto.KeyboardButtonTypeRequestManagedBot
import dev.g000sha256.tdl.dto.ButtonStyle
import dev.g000sha256.tdl.dto.InputMessageContact
import dev.g000sha256.tdl.dto.Contact
import dev.g000sha256.tdl.dto.ReplyMarkupInlineKeyboard
import dev.g000sha256.tdl.dto.InlineKeyboardButtonTypeCallback
import dev.g000sha256.tdl.dto.InlineKeyboardButtonTypeUrl
import dev.g000sha256.tdl.dto.InlineKeyboardButtonTypeCopyText
import dev.g000sha256.tdl.dto.InlineKeyboardButtonTypeUser
import dev.g000sha256.tdl.dto.InlineKeyboardButtonTypeDisabled
import dev.g000sha256.tdl.dto.InlineKeyboardButtonTypeWebApp
import dev.g000sha256.tdl.dto.InlineKeyboardButtonTypeLoginUrl
import dev.g000sha256.tdl.dto.InlineKeyboardButtonTypeSwitchInline
import dev.g000sha256.tdl.dto.InlineKeyboardButtonTypeBuy
import dev.g000sha256.tdl.dto.InlineKeyboardButtonTypeCallbackGame
import dev.g000sha256.tdl.dto.InlineKeyboardButtonTypeCallbackWithPassword
import dev.g000sha256.tdl.dto.ButtonStylePrimary
import dev.g000sha256.tdl.dto.ButtonStyleSuccess
import dev.g000sha256.tdl.dto.ButtonStyleDanger
import dev.g000sha256.tdl.dto.CallbackQueryPayloadData
import dev.g000sha256.tdl.dto.ChatActionCancel
import dev.g000sha256.tdl.dto.ChatActionTyping
import dev.g000sha256.tdl.dto.ChatTypeBasicGroup
import dev.g000sha256.tdl.dto.ChatTypePrivate
import dev.g000sha256.tdl.dto.ChatTypeSupergroup
import dev.g000sha256.tdl.dto.Chat
import dev.g000sha256.tdl.dto.ChatMemberStatus
import dev.g000sha256.tdl.dto.ChatMemberStatusAdministrator
import dev.g000sha256.tdl.dto.ChatMemberStatusBanned
import dev.g000sha256.tdl.dto.ChatMemberStatusCreator
import dev.g000sha256.tdl.dto.ChatMemberStatusLeft
import dev.g000sha256.tdl.dto.ChatMemberStatusMember
import dev.g000sha256.tdl.dto.ChatMemberStatusRestricted
import dev.g000sha256.tdl.dto.ChatPermissions
import dev.g000sha256.tdl.dto.DraftMessage
import dev.g000sha256.tdl.dto.DraftMessageContentText
import dev.g000sha256.tdl.dto.LinkPreviewOptions as TdlLinkPreviewOptions
import dev.g000sha256.tdl.dto.TextEntityTypeTextUrl
import dev.g000sha256.tdl.dto.TextEntityTypeUrl
import dev.g000sha256.tdl.dto.FormattedText
import dev.g000sha256.tdl.dto.InputAnimation
import dev.g000sha256.tdl.dto.InputDocument
import dev.g000sha256.tdl.dto.InputFileId
import dev.g000sha256.tdl.dto.InputMessageVenue
import dev.g000sha256.tdl.dto.InputMessageLocation
import dev.g000sha256.tdl.dto.InputFileLocal
import dev.g000sha256.tdl.dto.InputMessageAnimation
import dev.g000sha256.tdl.dto.InputMessageContent
import dev.g000sha256.tdl.dto.InputMessageDocument
import dev.g000sha256.tdl.dto.InputMessagePhoto
import dev.g000sha256.tdl.dto.InputMessageReplyToMessage
import dev.g000sha256.tdl.dto.InputMessageSticker
import dev.g000sha256.tdl.dto.InputMessageText
import dev.g000sha256.tdl.dto.InputMessageVideo
import dev.g000sha256.tdl.dto.InputMessageVoiceNote
import dev.g000sha256.tdl.dto.InputPhoto
import dev.g000sha256.tdl.dto.InputSticker
import dev.g000sha256.tdl.dto.InputThumbnail
import dev.g000sha256.tdl.dto.InputVideo
import dev.g000sha256.tdl.dto.InputVoiceNote
import dev.g000sha256.tdl.dto.Message
import dev.g000sha256.tdl.dto.MessageInteractionInfo
import dev.g000sha256.tdl.dto.MessageOrigin
import dev.g000sha256.tdl.dto.MessageOriginChannel
import dev.g000sha256.tdl.dto.MessageOriginChat
import dev.g000sha256.tdl.dto.MessageOriginHiddenUser
import dev.g000sha256.tdl.dto.MessageOriginUser
import dev.g000sha256.tdl.dto.MessageSelfDestructType
import dev.g000sha256.tdl.dto.MessageSelfDestructTypeImmediately
import dev.g000sha256.tdl.dto.MessageSelfDestructTypeTimer
import dev.g000sha256.tdl.dto.MessageReadDateMyPrivacyRestricted
import dev.g000sha256.tdl.dto.MessageReadDateRead
import dev.g000sha256.tdl.dto.MessageReadDateTooOld
import dev.g000sha256.tdl.dto.MessageReadDateUnread
import dev.g000sha256.tdl.dto.MessageReadDateUserPrivacyRestricted
import dev.g000sha256.tdl.dto.MessageReplyToMessage
import dev.g000sha256.tdl.dto.MessageTopicThread
import dev.g000sha256.tdl.dto.MessageSender
import dev.g000sha256.tdl.dto.MessageSenderChat
import dev.g000sha256.tdl.dto.MessageSenderUser
import dev.g000sha256.tdl.dto.MessageSendingState
import dev.g000sha256.tdl.dto.MessageSendingStateFailed
import dev.g000sha256.tdl.dto.MessageSendingStatePending
import dev.g000sha256.tdl.dto.MessageSourceChatHistory
import dev.g000sha256.tdl.dto.MessageText
import dev.g000sha256.tdl.dto.ReactionType
import dev.g000sha256.tdl.dto.ReactionTypeCustomEmoji
import dev.g000sha256.tdl.dto.ReactionTypeEmoji
import dev.g000sha256.tdl.dto.ReactionTypePaid
import dev.g000sha256.tdl.dto.SearchMessagesFilter
import dev.g000sha256.tdl.dto.SearchMessagesFilterDocument
import dev.g000sha256.tdl.dto.SearchMessagesFilterPhotoAndVideo
import dev.g000sha256.tdl.dto.SearchMessagesFilterUrl
import dev.g000sha256.tdl.dto.SearchMessagesFilterPinned
import dev.g000sha256.tdl.dto.SearchMessagesFilterUnreadMention
import dev.g000sha256.tdl.dto.SearchMessagesFilterUnreadPollVote
import dev.g000sha256.tdl.dto.SearchMessagesFilterUnreadReaction
import dev.g000sha256.tdl.dto.TextEntity
import dev.g000sha256.tdl.TdlThrowable
import dev.g000sha256.tdl.toResult
import kotlin.time.Clock
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.ceil

/**
 * Konverzace nad TDLib.
 *
 * **Jména odesílatelů se kešují.** Ve skupině pošle padesát zpráv deset lidí; bez keše by to bylo
 * padesát `getUser` volání jen kvůli hlavičkám bublin.
 */
class TdlConversationRepository(
    private val holder: TelegramClientHolder,
) : ConversationRepository {


    private val logger = Logger.withTag("Conversation")

    private val client: TdlClient get() = holder.client

    /**
     * Keš odesílatelů (jméno + avatar).
     *
     * Ve skupině pošle padesát zpráv deset lidí a u reakcí se tytéž osoby opakují napříč emoji;
     * bez keše by to znamenalo desítky `getUser` volání jen kvůli hlavičkám bublin a avatarům.
     */
    private val senders = mutableMapOf<Long, SenderInfo>()
    private val sendersMutex = Mutex()

    /**
     * Klient, pro kterého keš [senders] platí. Po odhlášení a přihlášení jiného účtu může mít
     * tentýž uživatel jiné jméno (kontakty jsou per účet) a profilovky jiná `fileId`.
     */
    private var sendersClient: TdlClient? = null

    /**
     * Id vlastního účtu — a tím i id chatu Uložených zpráv (je to soukromý chat se sebou).
     *
     * Drží se stejně jako keš odesílatelů, tedy jen pro klienta, pro kterého platí: po odhlášení
     * a přihlášení jiného účtu je id jiné. `getMe` je lokální dotaz, takže tohle šetří jen
     * opakování, ne síť.
     */
    private var myId: Long? = null
    private var myIdClient: TdlClient? = null
    private val myIdMutex = Mutex()

    override suspend fun history(
        chatId: Long,
        fromMessageId: Long,
        limit: Int,
        offset: Int,
    ): Result<List<ChatMessage>> {
        val isGroup = isGroup(chatId)

        return client.getChatHistory(
            chatId = chatId,
            fromMessageId = fromMessageId,
            // Nula = od `fromMessageId` čistě do minulosti. Záporný offset přidá tolik zpráv
            // NOVĚJŠÍCH (okno kolem zprávy, stránka směrem k přítomnosti); `limit` pak musí být
            // aspoň `-offset`.
            offset = offset,
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

    override suspend fun commentThread(chatId: Long, messageId: Long): Result<CommentThread> =
        client.getMessageThread(chatId = chatId, messageId = messageId)
            .toResult()
            .map { CommentThread(chatId = it.chatId, rootMessageId = it.messageThreadId) }

    override suspend fun threadHistory(
        chatId: Long,
        threadId: Long,
        fromMessageId: Long,
        limit: Int,
        offset: Int,
    ): Result<List<ChatMessage>> {
        val isGroup = isGroup(chatId)
        return client.getMessageThreadHistory(
            chatId = chatId,
            messageId = threadId,
            fromMessageId = fromMessageId,
            offset = offset,
            limit = limit,
        )
            .toResult()
            .map { messages -> messages.messages.filterNotNull().map { it.toChatMessage(isGroup) } }
    }

    override fun newMessages(chatId: Long): Flow<ChatMessage> =
        holder.updates { newMessageUpdates }
            .filter { it.message.chatId == chatId }
            .map { it.message.toChatMessage(isGroup(chatId)) }

    override fun deletedMessages(chatId: Long): Flow<List<Long>> =
        holder.updates { deleteMessagesUpdates }
            // `isPermanent == false` znamená jen uvolnění z cache, ne smazání pro uživatele —
            // takovou zprávu z obrazovky odstranit nesmíme.
            .filter { it.chatId == chatId && it.isPermanent }
            .map { it.messageIds.toList() }

    override fun contentOpenedUpdates(chatId: Long): Flow<Long> =
        holder.updates { messageContentOpenedUpdates }
            .filter { it.chatId == chatId }
            .map { it.messageId }

    override suspend fun openContent(chatId: Long, messageId: Long): Result<Unit> =
        client.openMessageContent(chatId = chatId, messageId = messageId).toResult().map { }

    override fun editedMessages(chatId: Long): Flow<ChatMessage> =
        merge(
            holder.updates { messageContentUpdates }.filter { it.chatId == chatId }.map { it.messageId },
            // Tlačítka bota se mění i bez změny obsahu — a pak přijde jen tenhle update.
            holder.updates { messageEditedUpdates }.filter { it.chatId == chatId }.map { it.messageId },
        )
            .map { messageId ->
                // Update nese jen nový obsah, ne celou zprávu — zbytek se musí dočíst.
                client.getMessage(chatId = chatId, messageId = messageId)
                    .toResult()
                    .map { it.toChatMessage(isGroup(chatId)) }
            }
            .filter { it.isSuccess }
            .map { it.getOrThrow() }

    override fun interactionUpdates(chatId: Long): Flow<MessageInteractions> =
        holder.updates { messageInteractionInfoUpdates }
            .filter { it.chatId == chatId }
            .map { update ->
                MessageInteractions(
                    messageId = update.messageId,
                    reactions = update.interactionInfo.toReactions(),
                    postStats = update.interactionInfo.toPostStats(),
                )
            }

    /**
     * Každá změna přečte počty znovu z chatu (`getChat` je lokální). Počty chodí čtyřmi updaty —
     * dva nesou jen počet, dva zprávu i počet — a skládat je by znamenalo držet stav, který TDLib
     * drží sám. Odběr stojí před prvním čtením.
     */
    override fun unreadMarks(chatId: Long): Flow<UnreadMarks> =
        merge(
            holder.updates { chatUnreadMentionCountUpdates }.filter { it.chatId == chatId }.map { },
            holder.updates { messageMentionReadUpdates }.filter { it.chatId == chatId }.map { },
            holder.updates { chatUnreadReactionCountUpdates }.filter { it.chatId == chatId }.map { },
            holder.updates { messageUnreadReactionsUpdates }.filter { it.chatId == chatId }.map { },
            holder.updates { chatUnreadPollVoteCountUpdates }.filter { it.chatId == chatId }.map { },
            holder.updates { messageContainsUnreadPollVotesUpdates }.filter { it.chatId == chatId }.map { },
        )
            .onStart { emit(Unit) }
            .mapNotNull {
                client.getChat(chatId = chatId).toResult().getOrNull()?.let { chat ->
                    UnreadMarks(
                        mentions = chat.unreadMentionCount,
                        reactions = chat.unreadReactionCount,
                        pollVotes = chat.unreadPollVoteCount,
                    )
                }
            }
            .distinctUntilChanged()

    override fun unreadMarkChanges(chatId: Long): Flow<UnreadMarkChange> =
        merge(
            holder.updates { messageUnreadReactionsUpdates }
                .filter { it.chatId == chatId }
                .map { UnreadMarkChange(it.messageId, UnreadMarkKind.Reaction, hasUnread = it.unreadReactions.isNotEmpty()) },
            holder.updates { messageContainsUnreadPollVotesUpdates }
                .filter { it.chatId == chatId }
                .map { UnreadMarkChange(it.messageId, UnreadMarkKind.PollVote, hasUnread = it.containsUnreadPollVotes) },
        )

    override suspend fun oldestUnreadMark(chatId: Long, kind: UnreadMarkKind): Result<Long?> =
        client.searchChatMessages(
            chatId = chatId,
            topicId = null,
            query = "",
            senderId = null,
            fromMessageId = 0L,
            offset = 0,
            // Stovka pokryje každý běžný případ; kdyby jich bylo víc, skočí se na nejstarší z nich
            // a po jejím přečtení na další.
            limit = UNREAD_MARKS_PAGE,
            filter = when (kind) {
                UnreadMarkKind.Mention -> SearchMessagesFilterUnreadMention()
                UnreadMarkKind.Reaction -> SearchMessagesFilterUnreadReaction()
                UnreadMarkKind.PollVote -> SearchMessagesFilterUnreadPollVote()
            },
        )
            .toResult()
            .map { found -> oldestOf(found.messages.filterNotNull().map { it.id }) }

    override suspend fun readAllMarks(chatId: Long, kind: UnreadMarkKind): Result<Unit> =
        when (kind) {
            UnreadMarkKind.Mention -> client.readAllChatMentions(chatId = chatId)
            UnreadMarkKind.Reaction -> client.readAllChatReactions(chatId = chatId)
            UnreadMarkKind.PollVote -> client.readAllChatPollVotes(chatId = chatId)
        }
            .toResult()
            .map { }

    override suspend fun setReaction(
        chatId: Long,
        messageId: Long,
        emoji: String,
        add: Boolean,
    ): Result<Unit> {
        val type = emoji.toReactionType()

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
                // Reakce vlastním emoji může chtít Premium (`needsPremium`) — bez něj by
                // ťuknutí skončilo chybou, takže se takové nenabízí. Ptá se jen tehdy, když
                // nějaká taková v nabídce je; `getMe` je lokální.
                val all = (available.topReactions + available.recentReactions + available.popularReactions)
                    .filterNotNull()
                val isPremium = all.none { it.needsPremium } ||
                    client.getMe().toResult().getOrNull()?.isPremium == true

                // Pořadí schválně: nejdřív doporučené, pak nedávno použité, pak populární.
                // Duplicity se odstraní, ale první výskyt si drží pozici.
                all.filter { isPremium || !it.needsPremium }
                    .mapNotNull { it.type.toReactionKey() }
                    .distinct()
            }

    /**
     * Příznaky, co se s konkrétní zprávou smí.
     *
     * `MessageProperties` je samostatný dotaz — TDLib je do `Message` nedává, protože se mění
     * v čase (editovat jde jen chvíli po odeslání). Proto se ptáme až při otevření nabídky.
     *
     * Ze čtyřiceti příznaků se bere jen to, co nabídka opravdu umí; zbytek (přeposlání, editace,
     * připnutí, statistiky) přibude s položkami, které je použijí.
     */
    override suspend fun messageActions(chatId: Long, messageId: Long): Result<MessageActions> =
        client.getMessageProperties(chatId = chatId, messageId = messageId)
            .toResult()
            .map { properties ->
                MessageActions(
                    canReply = properties.canBeReplied,
                    // `canBeSaved` je ochrana obsahu v kanálu — když je false, nesmí se z něj
                    // nic vynášet, tedy ani text do schránky.
                    canCopy = properties.canBeSaved,
                    canDeleteForSelf = properties.canBeDeletedOnlyForSelf,
                    canDeleteForAll = properties.canBeDeletedForAllUsers,
                    canEdit = properties.canBeEdited,
                    canPin = properties.canBePinned,
                    canForward = properties.canBeForwarded,
                    canSave = properties.canBeSaved,
                    canReport = properties.canReportChat,
                )
            }

    override suspend fun deleteMessages(
        chatId: Long,
        messageIds: List<Long>,
        revoke: Boolean,
    ): Result<Unit> =
        client.deleteMessages(
            chatId = chatId,
            messageIds = messageIds.toLongArray(),
            revoke = revoke,
        )
            .toResult()
            .map { }

    /**
     * Připnuté zprávy přes vyhledávání s filtrem, ne `getChatPinnedMessage` — to vrací jen nejnovější.
     *
     * Vyhledávání stránkuje po stovce; víc než [MAX_PINNED_PAGES] stránek připnutých zpráv v chatu
     * nebývá a dál se nečte. Právo odepínat se zjistí z první zprávy (`canBePinned`): v chatu je
     * jedno pro všechny zprávy, takže dotaz na každou by byl zbytečný.
     */
    override suspend fun searchInChat(
        chatId: Long,
        query: String,
        fromMessageId: Long,
        senderUserId: Long?,
    ): Result<ChatSearchPage> =
        client.searchChatMessages(
            chatId = chatId,
            topicId = null,
            query = query,
            senderId = senderUserId?.let { MessageSenderUser(userId = it) },
            fromMessageId = fromMessageId,
            offset = 0,
            limit = IN_CHAT_SEARCH_PAGE,
            filter = null,
        )
            .toResult()
            .map { found ->
                ChatSearchPage(
                    messageIds = found.messages.filterNotNull().map { it.id },
                    // -1 = server odhad nemá.
                    total = found.totalCount.takeIf { it >= 0 },
                    nextFromMessageId = found.nextFromMessageId.takeIf { it != 0L },
                )
            }

    override suspend fun pinnedMessages(chatId: Long): Result<PinnedMessages> {
        val isGroup = isGroup(chatId)
        val found = mutableListOf<Message>()
        var from = 0L
        var pages = 0

        do {
            val page = client.searchChatMessages(
                chatId = chatId,
                topicId = null,
                query = "",
                senderId = null,
                fromMessageId = from,
                offset = 0,
                limit = PINNED_PAGE_SIZE,
                filter = SearchMessagesFilterPinned(),
            ).toResult().getOrElse { return Result.failure(it) }

            found += page.messages.filterNotNull()
            from = page.nextFromMessageId
            pages++
            // Nula = dál už nic není.
        } while (from != 0L && pages < MAX_PINNED_PAGES)

        val canUnpin = found.firstOrNull()
            ?.let { client.getMessageProperties(chatId = chatId, messageId = it.id).toResult().getOrNull() }
            ?.canBePinned == true

        return Result.success(
            PinnedMessages(
                // Vyhledávání vrací od nejnovější; pro jistotu se to neodvozuje z pořadí stránek.
                messages = found.distinctBy { it.id }.sortedByDescending { it.id }.map { it.toChatMessage(isGroup) },
                canUnpin = canUnpin,
            ),
        )
    }

    override suspend fun forward(
        fromChatId: Long,
        messageIds: List<Long>,
        toChatId: Long,
        options: ForwardOptions,
    ): Result<Unit> =
        client.forwardMessages(
            chatId = toChatId,
            topicId = null,
            fromChatId = fromChatId,
            messageIds = messageIds.sorted().toLongArray(),
            options = null,
            // Kopie = bez „Přeposláno od …". Popisky jde vynechat jen u kopie, jinak je TDLib
            // ignoruje — posílat `true` bez kopie by jen předstíralo, že se něco stane.
            sendCopy = options.hideSender,
            removeCaption = options.hideSender && options.hideCaptions,
        )
            .toResult()
            .map { }

    override suspend fun forwardCapabilities(chatId: Long, messageIds: List<Long>): ForwardCapabilities =
        coroutineScope {
            // Zpráva kvůli popisku a příznaky kvůli kopii — pro každou zprávu obojí souběžně.
            val found = messageIds.map { id ->
                async {
                    val message = async { client.getMessage(chatId = chatId, messageId = id).toResult().getOrNull() }
                    val properties = async { client.getMessageProperties(chatId = chatId, messageId = id).toResult().getOrNull() }
                    message.await() to properties.await()
                }
            }.awaitAll()
            if (found.any { (message, properties) -> message == null || properties == null }) {
                return@coroutineScope ForwardCapabilities()
            }
            ForwardCapabilities(
                canHideSender = found.all { (_, properties) -> properties!!.canBeCopied },
                // Popisek = text u přílohy. U typů bez přílohy je text naše vlastní věta
                // („Anketa: …"), ne popisek, který by kopie mohla vynechat.
                hasCaptions = found.any { (message, _) ->
                    message!!.messageMedia() != null && message.captionBody().text.isNotBlank()
                },
            )
        }

    override suspend fun unpin(chatId: Long, messageId: Long): Result<Unit> =
        client.unpinChatMessage(chatId = chatId, messageId = messageId).toResult().map { }

    override suspend fun pin(
        chatId: Long,
        messageId: Long,
        notify: Boolean,
        onlyForSelf: Boolean,
    ): Result<Unit> =
        client.pinChatMessage(
            chatId = chatId,
            messageId = messageId,
            disableNotification = !notify,
            onlyForSelf = onlyForSelf,
        ).toResult().map { }

    override fun pinnedMessageChanges(chatId: Long): Flow<Unit> =
        holder.updates { messageIsPinnedUpdates }
            .filter { it.chatId == chatId }
            .map { }

    /**
     * Fotky a videa přes vyhledávání s filtrem, ne procházením historie — fotek bývá v chatu zlomek
     * zpráv a `getChatHistory` by kvůli pár snímkům tahal stovky textů.
     *
     * Jedno volání vrátí obě strany: záporný `offset` přidá novější zprávy, `limit` zbytek
     * starších. Zpráva [messageId] je ve výsledku sama, takže `limit` je o jednu větší.
     */
    override suspend fun visualMedia(chatId: Long, messageId: Long, older: Int, newer: Int): Result<List<ChatMessage>> {
        val isGroup = isGroup(chatId)
        return client.searchChatMessages(
            chatId = chatId,
            topicId = null,
            query = "",
            senderId = null,
            fromMessageId = messageId,
            offset = -newer,
            limit = older + newer + 1,
            filter = SearchMessagesFilterPhotoAndVideo(),
        )
            .toResult()
            .map { found ->
                found.messages
                    .filterNotNull()
                    .map { it.toChatMessage(isGroup) }
                    .filter { message ->
                        val photo = message.media as? MessageMedia.Photo
                        val video = message.media as? MessageMedia.Video
                        (photo != null || video != null) &&
                            message.selfDestructType == null &&
                            photo?.isSecret != true &&
                            video?.isSecret != true
                    }
                    .sortedBy { it.id }
            }
    }

    /** TDLib počítá od nejnovější; prohlížeč řadí od nejstarší, takže se to obrací. */
    override suspend fun visualMediaPosition(chatId: Long, messageId: Long): Result<PhotoPosition?> {
        val filter = SearchMessagesFilterPhotoAndVideo()
        val fromNewest = client.getChatMessagePosition(chatId = chatId, filter = filter, messageId = messageId)
            .toResult()
            .getOrElse { return Result.failure(it) }
            .count
        val total = client.getChatMessageCount(chatId = chatId, filter = filter, returnLocal = false)
            .toResult()
            .getOrElse { return Result.failure(it) }
            .count
        val number = total - fromNewest + 1
        return Result.success(PhotoPosition(number, total).takeIf { fromNewest > 0 && number in 1..total })
    }

    /**
     * Sdílený obsah profilu: hledání s filtrem, ne procházení historie — fotek, souborů
     * a odkazů bývá zlomek zpráv a `getChatHistory` by kvůli nim tahal stovky textů.
     *
     * Mizející a tajná média se vyřazují stejně jako v prohlížeči: obsah, který má zmizet po
     * zhlédnutí, do trvalého přehledu nepatří.
     */
    override suspend fun sharedMedia(
        chatId: Long,
        kind: SharedMediaKind,
        fromMessageId: Long,
    ): Result<SharedMediaPage> {
        val isGroup = isGroup(chatId)
        return client.searchChatMessages(
            chatId = chatId,
            topicId = null,
            query = "",
            senderId = null,
            fromMessageId = fromMessageId,
            offset = 0,
            limit = SHARED_MEDIA_PAGE,
            filter = kind.toFilter(),
        )
            .toResult()
            .map { found ->
                val messages = found.messages
                    .filterNotNull()
                    .map { it.toChatMessage(isGroup) }
                    .filter { kind != SharedMediaKind.Media || it.isLastingVisualMedia }
                // Konec se pozná JEN podle toho, že server nevrátil nic. Počet po filtrování
                // na to nestačí: stránka samých mizejících fotek vyjde prázdná uprostřed chatu.
                SharedMediaPage(messages = messages, reachedEnd = found.messages.isEmpty())
            }
    }

    /**
     * Počty se ptají naráz, ne jeden po druhém — jsou to tři nezávislé dotazy na server a profil
     * podle nich teprve vykreslí řádky. Druh, na který server neodpoví, má nulu: řádek se
     * neukáže, ale ostatní se tím nezahodí.
     */
    override suspend fun sharedMediaCounts(chatId: Long): Result<SharedMediaCounts> = runCatching {
        coroutineScope {
            val counts = SharedMediaKind.entries
                .map { kind -> async { kind to countOf(chatId, kind) } }
                .awaitAll()
                .toMap()
            SharedMediaCounts(
                media = counts.getValue(SharedMediaKind.Media),
                files = counts.getValue(SharedMediaKind.Files),
                links = counts.getValue(SharedMediaKind.Links),
            )
        }
    }

    private suspend fun countOf(chatId: Long, kind: SharedMediaKind): Int =
        client.getChatMessageCount(chatId = chatId, filter = kind.toFilter(), returnLocal = false)
            .toResult()
            .map { it.count }
            .getOrDefault(0)

    override suspend fun message(chatId: Long, messageId: Long): Result<ChatMessage> {
        val isGroup = isGroup(chatId)
        return client.getMessage(chatId = chatId, messageId = messageId)
            .toResult()
            .map { it.toChatMessage(isGroup) }
    }

    override fun sentMessageUpdates(chatId: Long): Flow<SentMessageUpdate> = merge(
        holder.updates { messageSendSucceededUpdates }
            .filter { it.message.chatId == chatId }
            .map { update ->
                SentMessageUpdate.Succeeded(
                    oldMessageId = update.oldMessageId,
                    message = update.message.toChatMessage(isGroup(chatId)),
                )
            },
        holder.updates { messageSendFailedUpdates }
            .filter { it.message.chatId == chatId }
            .map { update ->
                SentMessageUpdate.Failed(
                    oldMessageId = update.oldMessageId,
                    message = update.message.toChatMessage(isGroup(chatId)),
                )
            },
    )

    override fun readOutboxUpdates(chatId: Long): Flow<Long> =
        holder.updates { chatReadOutboxUpdates }
            .filter { it.chatId == chatId }
            .map { it.lastReadOutboxMessageId }

    /**
     * Odešle zprávu.
     *
     * Entity se **dohledávají před odesláním** (`getTextEntities`). Bez toho by odkaz odešel jako
     * mrtvý text: server si ho sám neoznačí a příjemce by dostal řetězec, na který se nedá
     * kliknout. Když dohledání selže, zpráva stejně odejde — přijít o odkaz je lepší než
     * nepřijmout zprávu vůbec.
     */
    /**
     * Na co odpověď míří: celá zpráva, nebo úryvek z ní ([quote]).
     *
     * Formátování úryvku se bere z PŮVODNÍ zprávy (`getMessage` je lokální — zpráva, na kterou se
     * odpovídá, je načtená). Když se úryvek nenajde nebo zprávu přečíst nejde, odpoví se na celou
     * zprávu: to je pořád odpověď, kterou uživatel chtěl, jen bez zúžení.
     * `pollOptionId` je prázdný řetězec, ne `null` — pole je v TDLib nenullovatelné.
     */
    private suspend fun replyInput(chatId: Long, messageId: Long?, quote: ReplyQuote?): InputMessageReplyTo? {
        messageId ?: return null
        val inputQuote = quote?.let {
            val original = client.getMessage(chatId = chatId, messageId = messageId).toResult()
                .getOrNull()?.content?.quotableText()
            original?.let { text -> quoteInput(text, it) }
                ?: null.also { logger.w { "Citovaný úryvek se ve zprávě $messageId nenašel, odpovídám na celou." } }
        }
        return InputMessageReplyToMessage(
            messageId = messageId,
            quote = inputQuote,
            checklistTaskId = 0,
            pollOptionId = "",
        )
    }

    override suspend fun send(
        chatId: Long,
        text: String,
        replyToMessageId: Long?,
        linkPreview: LinkPreviewOptions?,
        quote: ReplyQuote?,
    ): Result<ChatMessage> {
        val entities = detectEntities(text)

        return client.sendMessage(
            chatId = chatId,
            // Vlákna (fóra) fáze 2 neřeší — null znamená hlavní vlákno chatu.
            topicId = null,
            replyTo = replyInput(chatId, replyToMessageId, quote),
            options = null,
            replyMarkup = null,
            inputMessageContent = InputMessageText(
                text = FormattedText(text = text, entities = entities),
                linkPreviewOptions = linkPreview?.toTdl(),
                clearDraft = true,
            ),
        )
            .toResult()
            // TDLib vrací zprávu okamžitě, s dočasným id a stavem Pending.
            .map { it.toChatMessage(isGroup(chatId)) }
    }

    override suspend fun sendVoiceNote(
        chatId: Long,
        path: String,
        durationSeconds: Int,
        waveform: ByteArray,
        replyToMessageId: Long?,
        quote: ReplyQuote?,
    ): Result<ChatMessage> =
        client.sendMessage(
            chatId = chatId,
            topicId = null,
            replyTo = replyInput(chatId, replyToMessageId, quote),
            options = null,
            replyMarkup = null,
            inputMessageContent = InputMessageVoiceNote(
                voiceNote = InputVoiceNote(
                    voiceNote = InputFileLocal(path = path),
                    duration = durationSeconds,
                    waveform = waveform,
                ),
                caption = null,
                selfDestructType = null,
            ),
        )
            .toResult()
            .map { it.toChatMessage(isGroup(chatId)) }

    override suspend fun sendAttachments(
        chatId: Long,
        attachments: List<OutgoingAttachment>,
        caption: String,
        replyToMessageId: Long?,
        quote: ReplyQuote?,
    ): Result<List<ChatMessage>> {
        require(attachments.isNotEmpty()) { "Není co poslat." }
        val isGroup = isGroup(chatId)
        // Entity popisku se dohledávají jako u textu — jinak by odkaz v popisku odešel jako mrtvý text.
        val formattedCaption = caption.takeIf { it.isNotBlank() }
            ?.let { FormattedText(text = it, entities = detectEntities(it)) }
        val contents = attachments.mapIndexed { index, attachment ->
            attachment.toInputContent(caption = formattedCaption.takeIf { index == 0 })
        }

        if (contents.size == 1) {
            return client.sendMessage(
                chatId = chatId,
                topicId = null,
                replyTo = replyInput(chatId, replyToMessageId, quote),
                options = null,
                replyMarkup = null,
                inputMessageContent = contents.single(),
            )
                .toResult()
                .map { listOf(it.toChatMessage(isGroup)) }
        }

        return client.sendMessageAlbum(
            chatId = chatId,
            topicId = null,
            replyTo = replyInput(chatId, replyToMessageId, quote),
            options = null,
            inputMessageContents = contents.toTypedArray(),
        )
            .toResult()
            .map { sent -> sent.messages.filterNotNull().map { it.toChatMessage(isGroup) } }
    }

    /**
     * Příloha do ankety. Fotka a video se nahrávají podle cesty, jako u zprávy; GIF a samolepka
     * jsou soubory, které server už má (`InputFileId`). Popisek příloha ankety nemá — „without
     * caption" v dokumentaci TDLib.
     */
    private fun PollDraftMedia.toPollMedia(): InputPollMedia? = when (this) {
        is PollDraftMedia.File -> when (val file = attachment) {
            is OutgoingAttachment.Photo -> InputPollMediaPhoto(
                photo = InputPhoto(
                    photo = InputFileLocal(path = file.path),
                    thumbnail = null,
                    video = null,
                    addedStickerFileIds = intArrayOf(),
                    width = file.width,
                    height = file.height,
                ),
            )
            is OutgoingAttachment.Video -> InputPollMediaVideo(
                video = InputVideo(
                    video = InputFileLocal(path = file.path),
                    thumbnail = file.thumbnail?.let {
                        InputThumbnail(thumbnail = InputFileLocal(path = it.path), width = it.width, height = it.height)
                    },
                    cover = null,
                    startTimestamp = 0,
                    addedStickerFileIds = intArrayOf(),
                    duration = file.durationSeconds,
                    width = file.width,
                    height = file.height,
                    supportsStreaming = file.streamable,
                ),
            )
            // Formulář soubor nenabízí; kdyby se sem dostal, anketa odejde bez něj.
            is OutgoingAttachment.Document -> null
        }
        is PollDraftMedia.Gif -> InputPollMediaAnimation(
            animation = InputAnimation(
                animation = InputFileId(id = animation.fileId),
                thumbnail = null,
                addedStickerFileIds = intArrayOf(),
                duration = 0,
                width = animation.width,
                height = animation.height,
            ),
        )
        is PollDraftMedia.Sticker -> InputPollMediaSticker(
            sticker = InputSticker(
                sticker = InputFileId(id = sticker.sendFileId),
                thumbnail = null,
                width = sticker.preview.width,
                height = sticker.preview.height,
            ),
        )
        is PollDraftMedia.Link -> InputPollMediaLink(url = url)
        is PollDraftMedia.Location -> if (place.id.isEmpty() && place.title.isBlank()) {
            InputPollMediaLocation(location = Location(latitude = place.latitude, longitude = place.longitude, horizontalAccuracy = 0.0))
        } else {
            InputPollMediaVenue(
                venue = Venue(
                    location = Location(latitude = place.latitude, longitude = place.longitude, horizontalAccuracy = 0.0),
                    title = place.title,
                    address = place.address,
                    provider = place.provider,
                    id = place.id,
                    type = place.type,
                ),
            )
        }
    }

    private fun OutgoingAttachment.toInputContent(caption: FormattedText?): InputMessageContent {
        val file = InputFileLocal(path = path)
        if (mode == AttachmentMode.AsFile || this is OutgoingAttachment.Document) {
            return InputMessageDocument(
                document = InputDocument(
                    document = file,
                    thumbnail = null,
                    // Soubor zůstane souborem: bez toho by TDLib z odeslaného videa nebo hudby
                    // udělal přehrávatelnou zprávu, a uživatel posílal výslovně „soubor".
                    disableContentTypeDetection = true,
                ),
                caption = caption,
            )
        }
        return when (this) {
            is OutgoingAttachment.Photo -> InputMessagePhoto(
                photo = InputPhoto(
                    photo = file,
                    // Náhled se posílá jen v tajných chatech; jinak ho server vyrobí sám.
                    thumbnail = null,
                    video = null,
                    addedStickerFileIds = intArrayOf(),
                    width = width,
                    height = height,
                ),
                caption = caption,
                showCaptionAboveMedia = false,
                selfDestructType = selfDestructType?.toTdl(),
                hasSpoiler = hasSpoiler,
            )

            is OutgoingAttachment.Video -> InputMessageVideo(
                video = InputVideo(
                    video = file,
                    thumbnail = thumbnail?.let {
                        InputThumbnail(thumbnail = InputFileLocal(path = it.path), width = it.width, height = it.height)
                    },
                    cover = null,
                    startTimestamp = 0,
                    addedStickerFileIds = intArrayOf(),
                    duration = durationSeconds,
                    width = width,
                    height = height,
                    supportsStreaming = streamable,
                ),
                caption = caption,
                showCaptionAboveMedia = false,
                selfDestructType = selfDestructType?.toTdl(),
                hasSpoiler = hasSpoiler,
            )

            is OutgoingAttachment.Document -> InputMessageDocument(
                document = InputDocument(
                    document = file,
                    thumbnail = null,
                    // Soubor zůstane souborem: bez toho by TDLib z odeslaného videa nebo hudby
                    // udělal přehrávatelnou zprávu, a uživatel posílal výslovně „soubor".
                    disableContentTypeDetection = true,
                ),
                caption = caption,
            )
        }
    }

    override suspend fun sendSticker(
        chatId: Long,
        sticker: StickerItem,
        replyToMessageId: Long?,
        quote: ReplyQuote?,
    ): Result<ChatMessage> {
        val file = InputFileId(id = sticker.sendFileId)

        return client.sendMessage(
            chatId = chatId,
            topicId = null,
            replyTo = replyInput(chatId, replyToMessageId, quote),
            options = null,
            replyMarkup = null,
            inputMessageContent = InputMessageSticker(
                sticker = InputSticker(
                    sticker = file,
                    // Náhled se nahrává jen u nových samolepek; tahle už na serveru je.
                    thumbnail = null,
                    width = sticker.preview.width,
                    height = sticker.preview.height,
                ),
                emoji = sticker.preview.emoji,
            ),
        )
            .toResult()
            .map { it.toChatMessage(isGroup(chatId)) }
            .onSuccess {
                // Nedávné se udržují i samy, ale výslovné přidání posune samolepku nahoru hned.
                // Neúspěch nevadí — zpráva už odešla.
                client.addRecentSticker(isAttached = false, sticker = file)
            }
    }

    override suspend fun sendAnimation(
        chatId: Long,
        animation: OutgoingAnimation,
        replyToMessageId: Long?,
        quote: ReplyQuote?,
    ): Result<ChatMessage> =
        client.sendMessage(
            chatId = chatId,
            topicId = null,
            replyTo = replyInput(chatId, replyToMessageId, quote),
            options = null,
            replyMarkup = null,
            inputMessageContent = InputMessageAnimation(
                animation = InputAnimation(
                    animation = InputFileLocal(path = animation.path),
                    thumbnail = animation.thumbnail?.let {
                        InputThumbnail(thumbnail = InputFileLocal(path = it.path), width = it.width, height = it.height)
                    },
                    addedStickerFileIds = intArrayOf(),
                    duration = animation.durationSeconds,
                    width = animation.width,
                    height = animation.height,
                ),
                caption = null,
                showCaptionAboveMedia = false,
                hasSpoiler = false,
            ),
        )
            .toResult()
            .map { it.toChatMessage(isGroup(chatId)) }

    override suspend fun sendLocation(
        chatId: Long,
        place: Place,
        replyToMessageId: Long?,
        quote: ReplyQuote?,
    ): Result<ChatMessage> {
        val location = Location(latitude = place.latitude, longitude = place.longitude, horizontalAccuracy = 0.0)
        val content = if (place.title.isBlank()) {
            InputMessageLocation(location = location)
        } else {
            InputMessageVenue(
                venue = Venue(
                    location = location,
                    title = place.title,
                    address = place.address,
                    provider = place.provider,
                    id = place.id,
                    type = place.type,
                ),
            )
        }
        return client.sendMessage(
            chatId = chatId,
            topicId = null,
            replyTo = replyInput(chatId, replyToMessageId, quote),
            options = null,
            replyMarkup = null,
            inputMessageContent = content,
        )
            .toResult()
            .map { it.toChatMessage(isGroup(chatId)) }
    }

    override suspend fun sendGif(
        chatId: Long,
        gif: GifItem,
        replyToMessageId: Long?,
        quote: ReplyQuote?,
    ): Result<ChatMessage> {
        val file = InputFileId(id = gif.animation.fileId)
        return client.sendMessage(
            chatId = chatId,
            topicId = null,
            replyTo = replyInput(chatId, replyToMessageId, quote),
            options = null,
            replyMarkup = null,
            inputMessageContent = InputMessageAnimation(
                animation = InputAnimation(
                    animation = file,
                    // Soubor na serveru už je i s náhledem; nahrává se jen u nového GIFu.
                    thumbnail = null,
                    addedStickerFileIds = intArrayOf(),
                    duration = 0,
                    width = gif.animation.width,
                    height = gif.animation.height,
                ),
                caption = null,
                showCaptionAboveMedia = false,
                hasSpoiler = false,
            ),
        )
            .toResult()
            .map { it.toChatMessage(isGroup(chatId)) }
            .onSuccess {
                // Neúspěch nevadí — zpráva už odešla. Jen GIF nebude mezi uloženými nahoře.
                client.addSavedAnimation(animation = file)
            }
    }

    override suspend fun sendTyping(chatId: Long, typing: Boolean): Result<Unit> =
        client.sendChatAction(
            chatId = chatId,
            // Vlákna (fóra) zatím neřešíme, stejně jako u odesílání.
            topicId = null,
            // Prázdný řetězec = obyčejný účet, ne firemní připojení. Parametr je nenullovatelný.
            businessConnectionId = "",
            action = if (typing) ChatActionTyping() else ChatActionCancel(),
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

    override suspend fun openChat(chatId: Long): Result<Unit> =
        client.openChat(chatId = chatId)
            .toResult()
            .map { }
            // Chat, který lokální databáze nezná, TDLib otevřít odmítne. Uživateli se tím nic
            // nestane — jen přijde o odložení notifikace — takže se to jen loguje.
            .onFailure { logger.w(it) { "Otevření chatu se nepodařilo ohlásit." } }

    override suspend fun closeChat(chatId: Long): Result<Unit> =
        client.closeChat(chatId = chatId)
            .toResult()
            .map { }
            .onFailure { logger.w(it) { "Zavření chatu se nepodařilo ohlásit." } }

    override suspend fun editText(chatId: Long, messageId: Long, text: String): Result<Unit> =
        client.editMessageText(
            chatId = chatId,
            messageId = messageId,
            replyMarkup = null,
            inputMessageContent = InputMessageText(
                text = FormattedText(text = text, entities = detectEntities(text)),
                linkPreviewOptions = null,
                // Úprava s konceptem chatu nesouvisí; smazat ho by uživateli vzalo rozepsaný text.
                clearDraft = false,
            ),
        )
            .toResult()
            .map { }

    override suspend fun editCaption(chatId: Long, messageId: Long, caption: String): Result<Unit> =
        client.editMessageCaption(
            chatId = chatId,
            messageId = messageId,
            replyMarkup = null,
            caption = FormattedText(text = caption, entities = detectEntities(caption)),
            // Popisek nad médiem aplikace nekreslí ani neposílá, takže ho ani úprava nezavádí.
            showCaptionAboveMedia = false,
        )
            .toResult()
            .map { }

    /**
     * Odkazy, zmínky a spol. v textu, který uživatel napsal.
     *
     * Bez toho by odkaz odešel jako mrtvý text: server si ho sám neoznačí. Když dohledání selže,
     * text odejde bez entit — přijít o odkaz je lepší než neodeslat vůbec.
     */
    private suspend fun detectEntities(text: String): Array<TextEntity> =
        client.getTextEntities(text)
            .toResult()
            .map { it.entities.filterNotNull().toTypedArray() }
            .getOrElse { failure ->
                logger.w(failure) { "Entity v odesílaném textu se nepodařilo dohledat." }
                emptyArray()
            }

    override suspend fun linkPreview(text: String): Result<LinkPreview?> {
        val formatted = FormattedText(text = text, entities = detectEntities(text))
        if (formatted.entities.none { it.type is TextEntityTypeUrl || it.type is TextEntityTypeTextUrl }) {
            return Result.success(null)
        }
        return client.getLinkPreview(text = formatted, linkPreviewOptions = null)
            .toResult()
            .map { it.toDomain() }
            // 404 = „text nemá náhled" (stránka nic nevydala). To je odpověď, ne chyba.
            .recoverCatching { failure ->
                if (failure is TdlThrowable && failure.code == NOT_FOUND) null else throw failure
            }
    }

    override suspend fun saveDraft(
        chatId: Long,
        text: String,
        replyToMessageId: Long?,
        linkPreview: LinkPreviewOptions?,
        quote: ReplyQuote?,
    ): Result<Unit> =
        client.setChatDraftMessage(
            chatId = chatId,
            topicId = null,
            draftMessage = text.takeIf { it.isNotBlank() }?.let {
                DraftMessage(
                    replyTo = replyInput(chatId, replyToMessageId, quote),
                    date = Clock.System.now().epochSeconds.toInt(),
                    // Entity se tu nedohledávají: koncept je rozepsaný text, odkazy se označí
                    // až při odeslání (viz `send`).
                    content = DraftMessageContentText(
                        text = FormattedText(text = it, entities = emptyArray()),
                        linkPreviewOptions = linkPreview?.toTdl(),
                    ),
                    effectId = 0L,
                    suggestedPostInfo = null,
                )
            },
        )
            .toResult()
            .map { }

    override suspend fun chatInfo(chatId: Long): Result<ChatInfo> =
        client.getChat(chatId)
            .toResult()
            .map { chat ->
                val access = chatAccess(chat)
                ChatInfo(
                    id = chat.id,
                    title = chat.title,
                    avatarFileId = chat.photo?.small?.id,
                    lastReadOutboxMessageId = chat.lastReadOutboxMessageId,
                    lastReadInboxMessageId = chat.lastReadInboxMessageId,
                    unreadCount = chat.unreadCount,
                    isGroup = chat.type is ChatTypeBasicGroup || chat.type is ChatTypeSupergroup,
                    isChannel = (chat.type as? ChatTypeSupergroup)?.isChannel == true,
                    // Uložené zprávy jsou soukromý chat s vlastním účtem. `getMe` je lokální,
                    // po přihlášení ho TDLib zná bez dotazu na server.
                    isSavedMessages = (chat.type as? ChatTypePrivate)?.userId
                        ?.let { it == client.getMe().toResult().getOrNull()?.id } == true,
                    draftText = chat.draftMessage?.toChatDraft()?.text,
                    canJoin = access.canJoin,
                    writeAccess = access.writeAccess,
                    content = access.content,
                    draftReplyToMessageId = (chat.draftMessage?.replyTo as? InputMessageReplyToMessage)
                        ?.messageId,
                    draftReplyQuote = (chat.draftMessage?.replyTo as? InputMessageReplyToMessage)
                        ?.quote
                        ?.let { ReplyQuote(text = it.text.text, position = it.position) },
                    draftLinkPreview = (chat.draftMessage?.content as? DraftMessageContentText)
                        ?.linkPreviewOptions?.toDomain(),
                    verification = client.verificationOf(chat),
                    emojiStatus = chat.emojiStatus.statusEmojiId(),
                )
            }


    /**
     * Jestli se do chatu smí psát a jestli se k němu jde přidat.
     *
     * `getSupergroup` / `getBasicGroup` jsou lokální — TDLib skupinu zná, když zná chat. Když
     * postavení zjistit nejde, psaní se NEBLOKUJE: pole navíc skončí nanejvýš chybou po odeslání,
     * chybějící pole by uživatele od chatu odřízlo.
     */
    private suspend fun chatAccess(chat: Chat): ChatAccess {
        val status = client.ownMemberStatus(chat)
        val supergroup = chat.type as? ChatTypeSupergroup
        return ChatAccess(
            canJoin = supergroup != null && status == OwnMemberStatus.Left,
            writeAccess = writeAccess(
                isChannel = supergroup?.isChannel == true,
                status = status,
                membersCanSend = chat.permissions.canSendBasicMessages,
            ),
            content = contentAccess(status = status, groupDefaults = chat.permissions.toContentAccess()).let {
                // Právo skupiny nestačí: do soukromého chatu s člověkem anketa nejde vůbec.
                it.copy(polls = it.polls && pollsAllowedIn(pollChatKind(chat)))
            },
        )
    }

    /** Druh chatu pro [pollsAllowedIn]. Bot a Uložené zprávy se zjišťují lokálně. */
    private suspend fun pollChatKind(chat: Chat): PollChatKind = when (val type = chat.type) {
        is ChatTypeBasicGroup -> PollChatKind.Group
        is ChatTypeSupergroup -> if (type.isChannel) PollChatKind.Channel else PollChatKind.Group
        is ChatTypeSecret -> PollChatKind.Secret
        is ChatTypePrivate -> when {
            type.userId == client.getMe().toResult().getOrNull()?.id -> PollChatKind.SavedMessages
            client.getUser(userId = type.userId).toResult().getOrNull()?.type is UserTypeBot -> PollChatKind.Bot
            else -> PollChatKind.Private
        }
    }

    override fun accessUpdates(chatId: Long): Flow<ChatAccess> = flow {
        val type = client.getChat(chatId).toResult().getOrNull()?.type ?: return@flow
        val supergroupId = (type as? ChatTypeSupergroup)?.supergroupId
        val basicGroupId = (type as? ChatTypeBasicGroup)?.basicGroupId
        if (supergroupId == null && basicGroupId == null) return@flow
        emitAll(
            merge(
                holder.updates { chatPermissionsUpdates }.filter { it.chatId == chatId }.map { },
                holder.updates { supergroupUpdates }.filter { it.supergroup.id == supergroupId }.map { },
                holder.updates { basicGroupUpdates }.filter { it.basicGroup.id == basicGroupId }.map { },
            )
                // Update skupiny chodí i kvůli počtu členů; čte se znovu a pustí dál jen změna.
                .mapNotNull { client.getChat(chatId).toResult().getOrNull()?.let { chatAccess(it) } }
                .distinctUntilChanged(),
        )
    }
    /**
     * Dostupnost protějšku.
     *
     * Nejdřív se jednou přečte aktuální stav, teprve pak se navazují updaty: `updateUserStatus`
     * chodí jen na ZMĚNU, takže bez toho prvního čtení by hlavička zůstala prázdná, dokud se
     * protějšek neodpojí. V soukromém chatu je `chatId` shodné s `userId`.
     */
    override fun presenceUpdates(chatId: Long): Flow<Presence> = flow {
        val initial = client.getUser(chatId).toResult().getOrNull()?.status
        if (initial != null) emit(initial.toPresence())

        holder.updates { userStatusUpdates }
            .filter { it.userId == chatId }
            .collect { emit(it.status.toPresence()) }
    }

    override suspend fun joinChat(chatId: Long): Result<Unit> =
        client.joinChat(chatId = chatId).toResult().map { }

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

    /**
     * Má u zpráv smysl jméno a tvář odesílatele? Jen ve skupině — kanál je pro TDLib taky
     * supergroup (`ChatInfo.isGroup`), jenže píše jediný autor, sám kanál, a jeho jméno nad
     * každým příspěvkem jen opakovalo hlavičku.
     */
    private suspend fun isGroup(chatId: Long): Boolean =
        chatInfo(chatId).map { it.isGroup && !it.isChannel }.getOrDefault(false)

    private suspend fun Message.toChatMessage(isGroup: Boolean): ChatMessage {
        val senderId = senderId.plainId()
        // Text a jeho entity musí pocházet z jednoho rozhodnutí — rozsahy platí jen pro ten
        // řetězec, ze kterého vznikly.
        val body = captionBody()

        /*
         * V Uložených zprávách je odchozí VŠECHNO — je to chat sám se sebou, nikdo jiný do něj
         * psát nemůže.
         *
         * TDLib u přeposlané zprávy hlásí `isOutgoing = false` (napsal ji někdo jiný), takže se
         * kreslila jako příchozí: šedá bublina vlevo a bez odznaku doručení, zatímco vlastní
         * fotky v témž chatu stály vpravo. Ostatní klienti mají v Uložených zprávách všechno
         * na straně odesílatele a odkud zpráva pochází, říká značka „Přeposláno od …".
         */
        val outgoing = isOutgoing || chatId == myId()

        // Jméno a tvář mají smysl jen ve skupině a jen u cizích zpráv — jinde je to šum. Obojí
        // z jednoho dotazu (a z cache), ne ze dvou.
        val sender = if (isGroup && !outgoing) resolveSender(senderId) else null

        return ChatMessage(
            id = id,
            isOutgoing = outgoing,
            senderName = sender?.title,
            senderAvatarFileId = sender?.avatarFileId,
            senderId = senderId,
            text = body.text,
            entities = body.entities,
            media = messageMedia(
                hideSensitive = client.hidesSensitive(this),
                // Profilovka sdíleného kontaktu: `getUser` je lokální, TDLib uživatele zná, když
                // zná zprávu, která ho sdílí.
                contactPhotoFileId = (content as? MessageContact)?.contact?.userId?.takeIf { it != 0L }
                    ?.let { client.getUser(userId = it).toResult().getOrNull()?.profilePhoto?.small?.id },
            ),
            linkPreview = linkPreview(),
            reactions = interactionInfo.toReactions(),
            postStats = interactionInfo.toPostStats(),
            hasUnreadReactions = unreadReactions.isNotEmpty(),
            hasUnreadPollVotes = containsUnreadPollVotes,
            threadId = (topicId as? MessageTopicThread)?.messageThreadId,
            sendState = if (outgoing) sendingState.toSendState() else null,
            // Odpověď mimo tenhle chat nese `externalReply` — v načtené historii citovaná zpráva
            // není, takže se na ni skákat nedá.
            replyToMessageId = (replyTo as? MessageReplyToMessage)
                ?.takeIf { it.chatId == chatId }
                ?.messageId,
            // Citace ČÁSTI zprávy. Automatickou citaci (`isManual == false`, server ji doplní
            // u odpovědi na zprávu z jiného chatu) nekreslíme jinak než celou zprávu.
            replyQuote = (replyTo as? MessageReplyToMessage)
                ?.takeIf { it.chatId == chatId }
                ?.quote
                ?.takeIf { it.isManual }
                ?.text?.text
                ?.takeIf { it.isNotEmpty() },
            isService = content.serviceText() != null,
            timestamp = date,
            isEdited = editDate > 0,
            mediaAlbumId = mediaAlbumId,
            selfDestructType = selfDestructType?.toSelfDestructType(),
            // Zlomky vteřiny nahoru: „0 s" by u běžícího odpočtu znamenalo, že už vypršel.
            selfDestructIn = selfDestructIn.takeIf { it > 0.0 }?.let { ceil(it).toInt() },
            forwardOrigin = forwardInfo?.origin?.toForwardOrigin(),
            keyboard = (replyMarkup as? ReplyMarkupInlineKeyboard)?.toBotKeyboard(),
            poll = (content as? MessagePoll)?.toChatPoll(),
            externalReply = (replyTo as? MessageReplyToMessage)
                ?.takeIf { it.chatId != chatId }
                ?.toExternalReply(),
        )
    }

    /**
     * Zástupná citace zprávy z jiného chatu, složená z toho, co TDLib pošle rovnou v odpovědi.
     *
     * Kdo psal, říká [MessageReplyToMessage.origin]; bez něj (server ho u zprávy ze stejného
     * účtu vynechá) aspoň název chatu. Text je automatický úryvek, který server k odpovědi
     * z jiného chatu doplní sám (`quote`, `isManual == false`), a bez něj popis obsahu —
     * „Fotka" je lepší než prázdná citace.
     */
    private suspend fun MessageReplyToMessage.toExternalReply(): ExternalReply {
        val origin = origin?.toForwardOrigin()
        val sourceChat = chatId.takeIf { it != 0L }
        val name = origin?.name ?: sourceChat?.let { resolveSender(it)?.title }
        val text = quote?.text?.text?.takeIf { it.isNotBlank() }
            ?: content?.previewTextOrNull(isOutgoing = false)
            ?: ""
        val quote = ChatMessage(
            id = messageId,
            isOutgoing = false,
            senderName = name,
            senderAvatarFileId = null,
            // Podle odesílatele se ve skupině barví jméno citace; bez původu je to chat.
            senderId = when (origin) {
                is ForwardOrigin.User -> origin.userId
                is ForwardOrigin.Chat -> origin.chatId
                is ForwardOrigin.Hidden, null -> sourceChat ?: 0L
            },
            text = text,
            entities = emptyList(),
            media = null,
            linkPreview = null,
            reactions = emptyList(),
            sendState = null,
            replyToMessageId = null,
            isService = false,
            timestamp = originSendDate,
            isEdited = false,
        )
        val target = sourceChat?.let {
            ForwardOrigin.Chat(chatId = it, messageId = messageId.takeIf { id -> id != 0L }, name = name)
        }
        return ExternalReply(quote = quote, target = target)
    }

    override fun replyMarkup(chatId: Long): Flow<ChatReplyMarkup?> = flow {
        val updates = holder.updates { chatReplyMarkupUpdates }
            .filter { it.chatId == chatId }
            .map { it.replyMarkupMessage?.toChatReplyMarkup() }
        // Odběr napřed, pak první čtení — změna mezi nimi by se jinak ztratila.
        emitAll(
            updates.onStart {
                val chat = client.getChat(chatId = chatId).toResult().getOrNull()
                val messageId = chat?.replyMarkupMessageId?.takeIf { it != 0L }
                emit(
                    messageId?.let {
                        client.getMessage(chatId = chatId, messageId = it).toResult().getOrNull()?.toChatReplyMarkup()
                    },
                )
            },
        )
    }

    override suspend fun dismissReplyMarkup(chatId: Long, messageId: Long): Result<Unit> =
        client.deleteChatReplyMarkup(chatId = chatId, messageId = messageId).toResult().map { }

    override suspend fun sendKeyboardText(chatId: Long, text: String, replyToMessageId: Long?): Result<ChatMessage> =
        client.sendMessage(
            chatId = chatId,
            topicId = null,
            replyTo = replyInput(chatId, replyToMessageId, quote = null),
            options = null,
            replyMarkup = null,
            inputMessageContent = InputMessageText(
                // Text tlačítka je příkaz pro bota, ne zpráva s odkazy — entity se nedohledávají.
                text = FormattedText(text = text, entities = emptyArray()),
                linkPreviewOptions = null,
                // Rozepsaný koncept zůstává: ťuknutí na tlačítko není odeslání pole.
                clearDraft = false,
            ),
        )
            .toResult()
            .map { it.toChatMessage(isGroup(chatId)) }

    override suspend fun shareOwnPhone(chatId: Long, replyToMessageId: Long?): Result<ChatMessage> = runCatching {
        val me = client.getMe().toResult().getOrThrow()
        client.sendMessage(
            chatId = chatId,
            topicId = null,
            replyTo = replyInput(chatId, replyToMessageId, quote = null),
            options = null,
            replyMarkup = null,
            inputMessageContent = InputMessageContact(
                contact = Contact(
                    // TDLib posílá číslo bez „+"; tak se i vrací.
                    phoneNumber = me.phoneNumber,
                    firstName = me.firstName,
                    lastName = me.lastName,
                    vcard = "",
                    userId = me.id,
                ),
            ),
        ).toResult().getOrThrow().toChatMessage(isGroup(chatId))
    }

    override suspend fun vote(chatId: Long, messageId: Long, answerIndexes: List<Int>): Result<Unit> =
        client.setPollAnswer(chatId = chatId, messageId = messageId, optionIds = answerIndexes.toIntArray())
            .toResult()
            .map { }

    override suspend fun sendPoll(
        chatId: Long,
        poll: PollDraft,
        replyToMessageId: Long?,
        quote: ReplyQuote?,
    ): Result<ChatMessage> = runCatching {
        val isChannel = (client.getChat(chatId).toResult().getOrNull()?.type as? ChatTypeSupergroup)?.isChannel == true
        val correct = poll.correctAnswers
        client.sendMessage(
            chatId = chatId,
            topicId = null,
            replyTo = replyInput(chatId, replyToMessageId, quote),
            options = null,
            replyMarkup = null,
            inputMessageContent = InputMessagePoll(
                question = FormattedText(text = poll.question, entities = emptyArray()),
                options = poll.answers
                    .mapIndexed { index, answer ->
                        InputPollOption(
                            text = FormattedText(text = answer, entities = emptyArray()),
                            media = poll.answerMedia.getOrNull(index)?.toPollMedia(),
                        )
                    }
                    .toTypedArray(),
                // Popis je jako popisek přílohy: odkazy a zmínky se dohledají, jinak by odešly
                // jako mrtvý text.
                description = poll.description.takeIf { it.isNotBlank() }
                    ?.let { FormattedText(text = it, entities = detectEntities(it)) },
                media = poll.media?.toPollMedia(),
                // Neanonymní anketu server do kanálu nepustí.
                isAnonymous = poll.anonymous || isChannel,
                allowsMultipleAnswers = poll.multipleAnswers,
                // Změnit hlas jde u ankety, u kvízu ne — odpověď na kvíz je odpověď, ne názor.
                allowsRevoting = correct == null,
                // Omezení hlasujících jsou „for channel chats only"; jinde by je server odmítl.
                membersOnly = poll.membersOnly && isChannel,
                countryCodes = if (isChannel) poll.countryCodes.toTypedArray() else emptyArray(),
                shuffleOptions = poll.shuffle,
                hideResultsUntilCloses = poll.hideResultsUntilClose && (poll.openPeriodSeconds > 0 || poll.closeDate > 0),
                type = if (correct == null) {
                    InputPollTypeRegular(allowAddingOptions = false)
                } else {
                    InputPollTypeQuiz(
                        // TDLib chce rostoucí seznam.
                        correctOptionIds = correct.sorted().toIntArray(),
                        explanation = FormattedText(text = poll.explanation, entities = emptyArray()),
                        explanationMedia = null,
                    )
                },
                // Doba a čas uzavření jsou dvě podoby téže volby; přednost má výslovný čas.
                openPeriod = if (poll.closeDate > 0) 0 else poll.openPeriodSeconds,
                closeDate = poll.closeDate.toInt(),
                isClosed = false,
            ),
        ).toResult().getOrThrow().toChatMessage(isGroup(chatId))
    }

    override suspend fun pollLimits(): PollLimits {
        suspend fun option(name: String) =
            (client.getOption(name = name).toResult().getOrNull() as? OptionValueInteger)?.value?.toInt()
        val limits = PollLimits(
            maxAnswers = option("poll_answer_count_max")?.takeIf { it >= MIN_POLL_ANSWERS }
                ?: DEFAULT_POLL_ANSWER_COUNT_MAX,
            maxOpenPeriodSeconds = option("poll_open_period_max")?.coerceAtLeast(0) ?: 0,
            maxDescription = option("message_caption_length_max")?.takeIf { it > 0 } ?: DEFAULT_POLL_DESCRIPTION_MAX,
            maxCountries = option("poll_country_count_max")?.coerceAtLeast(0) ?: 0,
        )
        logger.d { "Meze ankety od serveru: $limits" }
        return limits
    }

    override suspend fun stopPoll(chatId: Long, messageId: Long): Result<Unit> =
        client.stopPoll(chatId = chatId, messageId = messageId, replyMarkup = null).toResult().map { }

    override suspend fun pollVoters(
        chatId: Long,
        messageId: Long,
        answerIndex: Int,
        offset: Int,
    ): Result<PollVotersPage> = runCatching {
        val page = client.getPollVoters(
            chatId = chatId,
            messageId = messageId,
            optionId = answerIndex,
            offset = offset,
            limit = POLL_VOTERS_PAGE,
        ).toResult().getOrThrow()
        PollVotersPage(
            // Odesílatel, kterého TDLib nezná, ze seznamu vypadne — prázdný řádek by nic neřekl.
            voters = page.voters.filterNotNull().mapNotNull { resolveSender(it.voterId.plainId()) },
            total = page.totalCount,
        )
    }

    override suspend fun pressBotButton(
        chatId: Long,
        messageId: Long,
        row: Int,
        column: Int,
        text: String,
    ): Result<BotAnswer> = runCatching {
        val message = client.getMessage(chatId = chatId, messageId = messageId).toResult().getOrThrow()
        val button = (message.replyMarkup as? ReplyMarkupInlineKeyboard)
            ?.rows?.getOrNull(row)?.getOrNull(column)
            ?.takeIf { it.text == text }
            ?: error("Tlačítko se mezitím změnilo.")
        val data = (button.type as? InlineKeyboardButtonTypeCallback)?.data
            ?: error("Tlačítko nemá zpětné volání.")
        val answer = client.getCallbackQueryAnswer(
            chatId = chatId,
            messageId = messageId,
            payload = CallbackQueryPayloadData(data = data),
        ).toResult().getOrThrow()
        BotAnswer(text = answer.text.trim(), alert = answer.showAlert, url = answer.url.trim())
    }

    /**
     * Původ přeposlané zprávy. Jméno jde z keše odesílatelů ([resolveSender]) — v kanálu, kam
     * se přeposílá z pár zdrojů pořád dokola, je to jeden dotaz na zdroj, ne na zprávu.
     *
     * `when` je bez `else`: nový druh původu v novější TDLib rozbije překlad, místo aby se zpráva
     * tiše přestala tvářit jako přeposlaná.
     */
    private suspend fun MessageOrigin.toForwardOrigin(): ForwardOrigin = when (this) {
        is MessageOriginUser -> ForwardOrigin.User(senderUserId, resolveSender(senderUserId)?.title)
        is MessageOriginHiddenUser -> ForwardOrigin.Hidden(senderName.ifBlank { null })
        is MessageOriginChat -> ForwardOrigin.Chat(
            chatId = senderChatId,
            messageId = null,
            name = withSignature(resolveSender(senderChatId)?.title, authorSignature),
        )
        is MessageOriginChannel -> ForwardOrigin.Chat(
            chatId = chatId,
            messageId = messageId.takeIf { it != 0L },
            name = withSignature(resolveSender(chatId)?.title, authorSignature),
        )
    }

    /** „Kanál (Jana)" — podpis autora příspěvku, když ho kanál přikládá. */
    private fun withSignature(title: String?, signature: String): String? = when {
        title == null -> signature.ifBlank { null }
        signature.isBlank() -> title
        else -> "$title ($signature)"
    }

    /**
     * Reakce z interaction info. Placená reakce (hvězdičky, `ReactionTypePaid`) se přeskočí —
     * zahodit ji je lepší než u zprávy neukázat žádnou reakci.
     */
    /**
     * Počty příspěvku. Zhlédnutí server počítá u příspěvků kanálu (i přeposlaných jinam),
     * `replyInfo` má jen příspěvek kanálu s připojenou diskusí a zpráva v diskusní skupině.
     */
    private fun MessageInteractionInfo?.toPostStats(): PostStats? {
        this ?: return null
        return PostStats(views = viewCount, forwards = forwardCount, comments = replyInfo?.replyCount)
            .takeUnless { it.isEmpty }
    }

    private suspend fun MessageInteractionInfo?.toReactions(): List<MessageReaction> {
        val raw = this?.reactions?.reactions?.filterNotNull().orEmpty()

        return raw.mapNotNull { reaction ->
            val emoji = reaction.type.toReactionKey() ?: return@mapNotNull null

            // Odesílatelé se tady neřeší vůbec: čip nese jen emoji a počet. Kdo reagoval, se
            // dozví až sheet s detailem (`reactionDetails`), a ten se ptá na vyžádání.
            MessageReaction(
                emoji = emoji,
                count = reaction.totalCount,
                isChosen = reaction.isChosen,
            )
        }
    }

    override suspend fun messageInfo(chatId: Long, messageId: Long): Result<MessageInfo> = coroutineScope {
        // Zpráva kvůli časům a příznaky kvůli právům — dva nezávislé dotazy, takže současně.
        val messageAsync = async { client.getMessage(chatId = chatId, messageId = messageId).toResult() }
        val propertiesAsync = async {
            client.getMessageProperties(chatId = chatId, messageId = messageId).toResult().getOrNull()
        }

        val message = messageAsync.await().getOrElse { return@coroutineScope Result.failure(it) }
        val properties = propertiesAsync.await()

        /*
         * Tři doplňky, každý za vlastním právem, a zase současně.
         *
         * Selhání jednoho ostatní nezahodí — server řekne ne u kdečeho (chráněný obsah, staré
         * zprávy) a prázdný řádek je lepší než prázdný sheet.
         */
        val linkAsync = async {
            if (properties?.canGetLink != true) {
                null
            } else {
                client
                    .getMessageLink(
                        chatId = chatId,
                        messageId = messageId,
                        mediaTimestamp = 0,
                        checklistTaskId = 0,
                        pollOptionId = "",
                        forAlbum = false,
                        inMessageThread = false,
                    )
                    .toResult()
                    .getOrNull()
                    ?.link
            }
        }
        val viewersAsync = async {
            if (properties?.canGetViewers != true) {
                emptyList()
            } else {
                client
                    .getMessageViewers(chatId = chatId, messageId = messageId)
                    .toResult()
                    .getOrNull()
                    ?.viewers
                    ?.mapNotNull { viewer ->
                        val sender = resolveSender(viewer.userId) ?: return@mapNotNull null
                        MessageViewer(sender = sender, viewedAt = viewer.viewDate)
                    }
                    .orEmpty()
            }
        }
        val readAsync = async {
            if (properties?.canGetReadDate != true) {
                MessageReadState.Unavailable
            } else {
                client
                    .getMessageReadDate(chatId = chatId, messageId = messageId)
                    .toResult()
                    .map { date ->
                        when (date) {
                            is MessageReadDateRead -> MessageReadState.Read(date.readDate)
                            is MessageReadDateUnread -> MessageReadState.Unread
                            is MessageReadDateTooOld -> MessageReadState.TooOld
                            is MessageReadDateUserPrivacyRestricted -> MessageReadState.HiddenByOther
                            is MessageReadDateMyPrivacyRestricted -> MessageReadState.HiddenByMe
                        }
                    }
                    .getOrElse { MessageReadState.Unavailable }
            }
        }

        Result.success(
            MessageInfo(
                sentAt = message.date,
                editedAt = message.editDate.takeIf { it > 0 },
                readState = readAsync.await(),
                viewers = viewersAsync.await(),
                link = linkAsync.await(),
            ),
        )
    }

    override suspend fun reactionDetails(chatId: Long, messageId: Long): Result<List<ReactionDetail>> {
        val added = client
            .getMessageAddedReactions(
                chatId = chatId,
                messageId = messageId,
                // Bez filtru na typ: sheet ukazuje všechny reakce zprávy pohromadě, jak je
                // Telegram vrací — od nejnovější.
                reactionType = null,
                offset = "",
                limit = REACTION_DETAIL_LIMIT,
            )
            .toResult()
            .getOrElse { return Result.failure(it) }

        val details = added.reactions.mapNotNull { reaction ->
            val emoji = reaction.type.toReactionKey() ?: return@mapNotNull null
            val sender = resolveSender(reaction.senderId.plainId()) ?: return@mapNotNull null

            ReactionDetail(sender = sender, emoji = emoji, isOwn = reaction.isOutgoing)
        }

        return Result.success(details)
    }

    override suspend fun sender(senderId: Long): Result<SenderInfo> =
        resolveSender(senderId)?.let { Result.success(it) }
            ?: Result.failure(IllegalStateException("Odesílatel $senderId není známý"))

    override suspend fun searchMembers(chatId: Long, query: String): Result<List<SenderInfo>> =
        client.searchChatMembers(chatId = chatId, query = query, limit = MEMBER_SEARCH_LIMIT, filter = null)
            .toResult()
            .map { found ->
                found.members.filterNotNull()
                    .mapNotNull { (it.memberId as? MessageSenderUser)?.userId }
                    .distinct()
                    .mapNotNull { resolveSender(it) }
            }

    private suspend fun myId(): Long? = myIdMutex.withLock {
        if (myIdClient !== client) {
            myId = null
            myIdClient = client
        }
        myId ?: client.getMe().toResult().getOrNull()?.id?.also { myId = it }
    }

    private suspend fun resolveSender(senderId: Long): SenderInfo? {
        if (senderId == 0L) return null

        sendersMutex.withLock {
            if (sendersClient !== client) {
                senders.clear()
                sendersClient = client
            }
            senders[senderId]
        }?.let { return it }

        val user = client.getUser(senderId).toResult().getOrNull()

        val resolved = if (user != null) {
            val name = listOf(user.firstName, user.lastName)
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .ifBlank { null }
                ?: return null

            SenderInfo(
                id = senderId,
                title = name,
                avatarFileId = user.profilePhoto?.small?.id,
            )
        } else {
            // Odesílatel může být kanál (MessageSenderChat), ne uživatel — getUser pak selže.
            val chat = client.getChat(senderId).toResult().getOrNull() ?: return null
            SenderInfo(
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

/** `MessageSender` je buď uživatel, nebo chat; pro naše účely stačí číselné id. */
private fun MessageSender.plainId(): Long = when (this) {
    is MessageSenderUser -> userId
    is MessageSenderChat -> chatId
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

/**
 * Citace úryvku pro TDLib: kde úryvek v původním textu doopravdy začíná a jeho formátování.
 *
 * [ReplyQuote.position] je jen odhad (dohledává se kolem něj, pak kdekoli); úryvek, který
 * v textu není, vrátí `null` — server by ho odmítl a odpověď bez citace je lepší než žádná.
 *
 * **Formátování se MUSÍ přenést.** TDLib: „Only Bold, Italic, Underline, Strikethrough, Spoiler,
 * and CustomEmoji entities are allowed to be kept and must be kept in the quote." Entity se
 * proto oříznou na úryvek a posunou na jeho začátek; ostatní druhy (odkazy, zmínky, kód…)
 * odpadnou.
 */
internal fun quoteInput(original: FormattedText, quote: ReplyQuote): InputTextQuote? {
    if (quote.text.isEmpty()) return null
    val start = original.text.indexOf(quote.text, startIndex = quote.position.coerceAtLeast(0))
        .takeIf { it >= 0 }
        ?: original.text.indexOf(quote.text).takeIf { it >= 0 }
        ?: return null
    val end = start + quote.text.length
    val entities = original.entities.mapNotNull { entity ->
        val kept = when (entity.type) {
            is TextEntityTypeBold, is TextEntityTypeItalic, is TextEntityTypeUnderline,
            is TextEntityTypeStrikethrough, is TextEntityTypeSpoiler, is TextEntityTypeCustomEmoji,
            -> true
            else -> false
        }
        val from = maxOf(entity.offset, start)
        val to = minOf(entity.offset + entity.length, end)
        if (!kept || from >= to) null else TextEntity(offset = from - start, length = to - from, type = entity.type)
    }
    return InputTextQuote(text = FormattedText(text = quote.text, entities = entities.toTypedArray()), position = start)
}

/**
 * Text zprávy, ze kterého jde citovat, i s formátováním — text nebo popisek přílohy. U druhů bez
 * vlastního textu `null`: nabídka tam citaci nenabídne, protože kopie nemá co označit.
 */
private fun MessageContent.quotableText(): FormattedText? = when (this) {
    is MessageText -> text
    is MessagePhoto -> caption
    is MessageVideo -> caption
    is MessageAnimation -> caption
    is MessageDocument -> caption
    is MessageAudio -> caption
    is MessageVoiceNote -> caption
    is MessagePaidMedia -> caption
    else -> null
}

private fun SelfDestructType.toTdl(): MessageSelfDestructType = when (this) {
    is SelfDestructType.Immediately -> MessageSelfDestructTypeImmediately()
    is SelfDestructType.Timer -> MessageSelfDestructTypeTimer(seconds)
}

/** Připnutých zpráv na jeden dotaz. Víc TDLib nedovolí. */
private const val PINNED_PAGE_SIZE = 100

/** Výsledků hledání v chatu na stránku; další se dotahují, až k nim uživatel došipkuje. */
private const val IN_CHAT_SEARCH_PAGE = 50

/** Kolik nepřečtených zmínek nebo reakcí se načte, než se z nich vybere nejstarší. */
private const val UNREAD_MARKS_PAGE = 100

/** Kolik členů vrátí jedno hledání pro filtr podle odesílatele; víc se na obrazovku nevejde. */
private const val MEMBER_SEARCH_LIMIT = 50

/** Strop stránek připnutých zpráv. Pět set připnutých v jednom chatu je víc než dost. */
private const val MAX_PINNED_PAGES = 5

/**
 * Práva na druhy obsahu z `ChatPermissions`. Bere se jen to, co aplikace umí poslat — hudba
 * a video kolečka by tu byla mrtvá pole.
 */
private fun ChatPermissions.toContentAccess(): ContentAccess = ContentAccess(
    photos = canSendPhotos,
    videos = canSendVideos,
    documents = canSendDocuments,
    voiceNotes = canSendVoiceNotes,
    stickers = canSendOtherMessages,
    polls = canSendPolls,
)

/**
 * Vlastní postavení ve skupině nebo kanálu; `null` v soukromém chatu i tam, kde ho zjistit nejde.
 * Oba dotazy jsou lokální — TDLib skupinu zná, když zná chat.
 */
internal suspend fun TdlClient.ownMemberStatus(chat: Chat): OwnMemberStatus? = when (val type = chat.type) {
    is ChatTypeSupergroup -> getSupergroup(supergroupId = type.supergroupId).toResult().getOrNull()?.status?.toOwn()
    // Převedená skupina (`isActive == false`) žije dál jako supergroup; do té staré se nepíše.
    is ChatTypeBasicGroup -> getBasicGroup(basicGroupId = type.basicGroupId).toResult().getOrNull()
        ?.let { group -> if (group.isActive) group.status.toOwn() else OwnMemberStatus.Left }
    else -> null
}

/** Postavení uživatele ve skupině; `when` bez `else`, takže nový stav v TDLib rozbije překlad. */
private fun ChatMemberStatus.toOwn(): OwnMemberStatus = when (this) {
    is ChatMemberStatusCreator -> OwnMemberStatus.Creator(isMember = isMember)
    is ChatMemberStatusAdministrator -> OwnMemberStatus.Admin(
        canPostMessages = rights.canPostMessages,
        canChangeInfo = rights.canChangeInfo,
        canRestrictMembers = rights.canRestrictMembers,
        canPromoteMembers = rights.canPromoteMembers,
        canManageTags = rights.canManageTags,
    )
    is ChatMemberStatusMember -> OwnMemberStatus.Member
    is ChatMemberStatusRestricted -> OwnMemberStatus.Restricted(
        isMember = isMember,
        canSendMessages = permissions.canSendBasicMessages,
        content = permissions.toContentAccess(),
        canChangeInfo = permissions.canChangeInfo,
    )
    is ChatMemberStatusLeft -> OwnMemberStatus.Left
    is ChatMemberStatusBanned -> OwnMemberStatus.Banned
}

/** Zpráv na stránku sdíleného obsahu. Mřížka jich na obrazovku vezme pár desítek. */
private const val SHARED_MEDIA_PAGE = 50

/** Kolik hlasujících se načte naráz; víc TDLib nevydá (strop je 50). */
private const val POLL_VOTERS_PAGE = 50

/** Když server strop počtu odpovědí nepošle; dlouho platná hodnota Telegramu. */
private const val DEFAULT_POLL_ANSWER_COUNT_MAX = 10

/** Filtr `searchChatMessages` pro druh sdíleného obsahu. */
private fun SharedMediaKind.toFilter(): SearchMessagesFilter = when (this) {
    SharedMediaKind.Media -> SearchMessagesFilterPhotoAndVideo()
    SharedMediaKind.Files -> SearchMessagesFilterDocument()
    SharedMediaKind.Links -> SearchMessagesFilterUrl()
}

/**
 * Fotka nebo video, které v chatu zůstávají.
 *
 * Mizející a tajná se vyřazují: přehled sdílených médií je trvalý seznam a obsah, který má po
 * zhlédnutí zmizet, do něj nepatří — stejně jako se nedostane do listování v prohlížeči.
 */
private val ChatMessage.isLastingVisualMedia: Boolean
    get() {
        val photo = media as? MessageMedia.Photo
        val video = media as? MessageMedia.Video
        return (photo != null || video != null) &&
            selfDestructType == null &&
            photo?.isSecret != true &&
            video?.isSecret != true
    }

/** `getLinkPreview` na text, ke kterému server náhled nemá. */
private const val NOT_FOUND = 404

/**
 * Volba náhledu odkazu v podobě pro TDLib; výchozí volba je `null` (rozhoduje server).
 * Vynucená velikost bez [LinkPreviewOptions.url] se odešle taky, TDLib ji pak jen ignoruje.
 */
private fun LinkPreviewOptions.toTdl(): TdlLinkPreviewOptions? =
    takeUnless { it.isDefault }?.let {
        TdlLinkPreviewOptions(
            isDisabled = disabled,
            url = url.orEmpty(),
            forceSmallMedia = largeMedia == false,
            forceLargeMedia = largeMedia == true,
            showAboveText = aboveText,
        )
    }

/** Volba uložená v konceptu zpátky v doménové podobě; adresa se nepřenáší (viz [LinkPreviewOptions.url]). */
internal fun TdlLinkPreviewOptions.toDomain(): LinkPreviewOptions? =
    LinkPreviewOptions(
        disabled = isDisabled,
        largeMedia = when {
            forceLargeMedia -> true
            forceSmallMedia -> false
            else -> null
        },
        aboveText = showAboveText,
    ).takeUnless { it.isDefault }

/**
 * Inline klávesnice bota. Prázdné řádky se zahazují — v klávesnici by po nich zbyla mezera.
 *
 * `when` nad druhem tlačítka je bez `else`: nový druh v novější TDLib má rozbít překlad, ne se
 * tiše nakreslit jako něco jiného.
 */
private fun ReplyMarkupInlineKeyboard.toBotKeyboard(): BotKeyboard? {
    val rows = rows.map { row ->
        row.filterNotNull().map { button ->
            BotButton(
                text = button.text,
                kind = when (val type = button.type) {
                    is InlineKeyboardButtonTypeCallback -> BotButtonKind.Callback
                    is InlineKeyboardButtonTypeUrl -> BotButtonKind.Url(type.url)
                    is InlineKeyboardButtonTypeCopyText -> BotButtonKind.CopyText(type.text)
                    is InlineKeyboardButtonTypeUser -> BotButtonKind.User(type.userId)
                    is InlineKeyboardButtonTypeDisabled -> BotButtonKind.Disabled
                    is InlineKeyboardButtonTypeWebApp -> BotButtonKind.Unsupported(UnsupportedBotButton.WebApp)
                    is InlineKeyboardButtonTypeLoginUrl -> BotButtonKind.Unsupported(UnsupportedBotButton.LoginUrl)
                    is InlineKeyboardButtonTypeSwitchInline -> BotButtonKind.Unsupported(UnsupportedBotButton.SwitchInline)
                    is InlineKeyboardButtonTypeBuy -> BotButtonKind.Unsupported(UnsupportedBotButton.Buy)
                    is InlineKeyboardButtonTypeCallbackGame -> BotButtonKind.Unsupported(UnsupportedBotButton.Game)
                    is InlineKeyboardButtonTypeCallbackWithPassword -> BotButtonKind.Unsupported(UnsupportedBotButton.Password)
                },
                style = button.style.toBotButtonStyle(),
                // Nula = tlačítko ikonu nemá.
                iconCustomEmojiId = button.iconCustomEmojiId.takeIf { it != 0L },
            )
        }
    }.filter { it.isNotEmpty() }
    return BotKeyboard(rows).takeIf { rows.isNotEmpty() }
}

/**
 * Vlastní klávesnice nebo vynucená odpověď ze zprávy bota. Inline klávesnice sem nepatří (ta je
 * u zprávy, viz [toBotKeyboard]) a „odebrat klávesnici" znamená žádnou.
 */
private fun Message.toChatReplyMarkup(): ChatReplyMarkup? = when (val markup = replyMarkup) {
    is ReplyMarkupShowKeyboard -> {
        val rows = markup.rows.map { row ->
            row.filterNotNull().map { button ->
                ReplyKeyboardButton(
                    text = button.text,
                    kind = when (val type = button.type) {
                        is KeyboardButtonTypeText -> ReplyKeyboardButtonKind.Text
                        is KeyboardButtonTypeRequestPhoneNumber -> ReplyKeyboardButtonKind.PhoneNumber
                        is KeyboardButtonTypeRequestLocation -> ReplyKeyboardButtonKind.Unsupported(UnsupportedKeyboardButton.Location)
                        is KeyboardButtonTypeRequestPoll -> ReplyKeyboardButtonKind.Poll(
                            when {
                                type.forceQuiz -> PollComposerMode.QuizOnly
                                type.forceRegular -> PollComposerMode.RegularOnly
                                else -> PollComposerMode.Any
                            },
                        )
                        is KeyboardButtonTypeRequestUsers -> ReplyKeyboardButtonKind.Unsupported(UnsupportedKeyboardButton.Users)
                        is KeyboardButtonTypeRequestChat -> ReplyKeyboardButtonKind.Unsupported(UnsupportedKeyboardButton.Chat)
                        is KeyboardButtonTypeWebApp -> ReplyKeyboardButtonKind.Unsupported(UnsupportedKeyboardButton.WebApp)
                        is KeyboardButtonTypeRequestManagedBot -> ReplyKeyboardButtonKind.Unsupported(UnsupportedKeyboardButton.ManagedBot)
                    },
                    style = button.style.toBotButtonStyle(),
                    iconCustomEmojiId = button.iconCustomEmojiId.takeIf { it != 0L },
                )
            }
        }.filter { it.isNotEmpty() }
        if (rows.isEmpty()) {
            null
        } else {
            ChatReplyMarkup.Keyboard(
                messageId = id,
                placeholder = markup.inputFieldPlaceholder.trim(),
                rows = rows,
                oneTime = markup.oneTime,
            )
        }
    }
    is ReplyMarkupForceReply -> ChatReplyMarkup.ForceReply(messageId = id, placeholder = markup.inputFieldPlaceholder.trim())
    is ReplyMarkupRemoveKeyboard, is ReplyMarkupInlineKeyboard, null -> null
}

private fun ButtonStyle?.toBotButtonStyle(): BotButtonStyle = when (this) {
    is ButtonStylePrimary -> BotButtonStyle.Primary
    is ButtonStyleSuccess -> BotButtonStyle.Success
    is ButtonStyleDanger -> BotButtonStyle.Danger
    else -> BotButtonStyle.Default
}

/**
 * Klíč reakce (viz [customEmojiReaction]). Placená reakce (hvězdičky) klíč nemá — neumíme ji
 * ukázat ani dát, a `when` bez `else` zařídí, že nový druh reakce v novější TDLib rozbije
 * překlad, místo aby tiše zmizel.
 */
private fun ReactionType.toReactionKey(): String? = when (this) {
    is ReactionTypeEmoji -> emoji
    is ReactionTypeCustomEmoji -> customEmojiReaction(customEmojiId)
    is ReactionTypePaid -> null
}

private fun String.toReactionType(): ReactionType =
    customEmojiIdOf(this)?.let { ReactionTypeCustomEmoji(customEmojiId = it) } ?: ReactionTypeEmoji(emoji = this)
