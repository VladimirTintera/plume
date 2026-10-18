package eu.tintera.plume.core.telegram

import dev.g000sha256.tdl.dto.MessageSuggestBirthdate
import dev.g000sha256.tdl.dto.PhotoSize
import dev.g000sha256.tdl.dto.ChatPhoto
import dev.g000sha256.tdl.dto.InputChatPhotoStatic
import dev.g000sha256.tdl.dto.InputChatPhotoAnimation
import dev.g000sha256.tdl.dto.MessageSuggestProfilePhoto
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.conversation_send_failed_unknown
import co.touchlab.kermit.Logger
import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.dto.MessageLiveLocation
import dev.g000sha256.tdl.dto.UnreadReaction
import dev.g000sha256.tdl.dto.InputTextQuote
import dev.g000sha256.tdl.dto.InputMessageReplyTo
import dev.g000sha256.tdl.dto.TextEntityTypeBold
import dev.g000sha256.tdl.dto.TextEntityTypeItalic
import dev.g000sha256.tdl.dto.TextEntityTypeUnderline
import dev.g000sha256.tdl.dto.TextEntityTypeStrikethrough
import dev.g000sha256.tdl.dto.TextEntityTypeSpoiler
import dev.g000sha256.tdl.dto.TextEntityTypeCustomEmoji
import dev.g000sha256.tdl.dto.TextEntityType
import dev.g000sha256.tdl.dto.TextEntityTypeCode
import dev.g000sha256.tdl.dto.TextEntityTypePre
import dev.g000sha256.tdl.dto.TextEntityTypeBlockQuote
import dev.g000sha256.tdl.dto.TextEntityTypeExpandableBlockQuote
import dev.g000sha256.tdl.dto.TextEntityTypePreCode
import dev.g000sha256.tdl.dto.MessageRichMessage
import dev.g000sha256.tdl.dto.RichMessage
import dev.g000sha256.tdl.dto.MessageContent
import dev.g000sha256.tdl.dto.MessagePoll
import dev.g000sha256.tdl.dto.MessageChecklist
import dev.g000sha256.tdl.dto.Checklist
import dev.g000sha256.tdl.dto.InputChecklist
import dev.g000sha256.tdl.dto.InputChecklistTask
import dev.g000sha256.tdl.dto.InputMessageChecklist
import dev.g000sha256.tdl.dto.MessageContact
import dev.g000sha256.tdl.dto.ChatTypeSecret
import dev.g000sha256.tdl.dto.SecretChatStatePending
import dev.g000sha256.tdl.dto.SecretChatStateClosed
import dev.g000sha256.tdl.dto.UserTypeBot
import dev.g000sha256.tdl.dto.UserTypeRegular
import dev.g000sha256.tdl.dto.ImportedContact
import dev.g000sha256.tdl.dto.Usernames
import dev.g000sha256.tdl.dto.OptionValueInteger
import dev.g000sha256.tdl.dto.InputPollTypeRegular
import dev.g000sha256.tdl.dto.InputPollTypeQuiz
import dev.g000sha256.tdl.dto.InputPollMedia
import dev.g000sha256.tdl.dto.InputPollMediaAnimation
import dev.g000sha256.tdl.dto.InputPollMediaLink
import dev.g000sha256.tdl.dto.InputPollMediaLocation
import dev.g000sha256.tdl.dto.InputPollMediaSticker
import dev.g000sha256.tdl.dto.InputPollMediaVenue
import dev.g000sha256.tdl.dto.LiveLocation
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
import dev.g000sha256.tdl.dto.KeyboardButtonSourceMessage
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
import dev.g000sha256.tdl.dto.LoginUrlInfoRequestConfirmation
import dev.g000sha256.tdl.dto.LoginUrlInfoOpen
import dev.g000sha256.tdl.dto.InlineKeyboardButtonTypeSwitchInline
import dev.g000sha256.tdl.dto.TargetChatChosen
import dev.g000sha256.tdl.dto.TargetChatCurrent
import dev.g000sha256.tdl.dto.TargetChatInternalLink
import dev.g000sha256.tdl.dto.InlineKeyboardButtonTypeBuy
import dev.g000sha256.tdl.dto.InlineKeyboardButtonTypeCallbackGame
import dev.g000sha256.tdl.dto.InlineKeyboardButtonTypeCallbackWithPassword
import dev.g000sha256.tdl.dto.ButtonStylePrimary
import dev.g000sha256.tdl.dto.ButtonStyleSuccess
import dev.g000sha256.tdl.dto.ButtonStyleDanger
import dev.g000sha256.tdl.dto.CallbackQueryPayloadData
import dev.g000sha256.tdl.dto.ChatActionCancel
import dev.g000sha256.tdl.dto.ChatActionTyping
import dev.g000sha256.tdl.dto.ChatActionWatchingAnimations
import dev.g000sha256.tdl.dto.StickerFormatTgs
import dev.g000sha256.tdl.dto.BotCommands
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
import dev.g000sha256.tdl.dto.InputMessageLiveLocation
import dev.g000sha256.tdl.dto.InputMessageLocation
import dev.g000sha256.tdl.dto.InputFileLocal
import dev.g000sha256.tdl.dto.InputMessageAnimation
import dev.g000sha256.tdl.dto.InputMessageContent
import dev.g000sha256.tdl.dto.InputMessageDice
import dev.g000sha256.tdl.dto.InputMessageDocument
import dev.g000sha256.tdl.dto.InputAudio
import dev.g000sha256.tdl.dto.InputMessageAudio
import dev.g000sha256.tdl.dto.InputMessagePhoto
import dev.g000sha256.tdl.dto.InputMessageReplyToMessage
import dev.g000sha256.tdl.dto.InputMessageSticker
import dev.g000sha256.tdl.dto.InputMessageText
import dev.g000sha256.tdl.dto.InputMessageVideo
import dev.g000sha256.tdl.dto.InputMessageVideoNote
import dev.g000sha256.tdl.dto.InputMessageVoiceNote
import dev.g000sha256.tdl.dto.InputPhoto
import dev.g000sha256.tdl.dto.InputSticker
import dev.g000sha256.tdl.dto.InputThumbnail
import dev.g000sha256.tdl.dto.InputVideo
import dev.g000sha256.tdl.dto.InputVideoNote
import dev.g000sha256.tdl.dto.InputVoiceNote
import dev.g000sha256.tdl.dto.InternalLinkTypeWebApp
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
import dev.g000sha256.tdl.dto.MessageReplyTo
import dev.g000sha256.tdl.dto.MessageReplyToMessage
import dev.g000sha256.tdl.dto.MessageReplyToStory
import dev.g000sha256.tdl.dto.MessageStory
import dev.g000sha256.tdl.dto.MessageTopicThread
import dev.g000sha256.tdl.dto.MessageTopic
import dev.g000sha256.tdl.dto.MessageTopicForum
import dev.g000sha256.tdl.dto.SuggestedPostPriceGram
import dev.g000sha256.tdl.dto.SuggestedPostPriceStar
import dev.g000sha256.tdl.dto.SuggestedPostStateDeclined
import dev.g000sha256.tdl.dto.SuggestedPostStateApproved
import dev.g000sha256.tdl.dto.SuggestedPostStatePending
import dev.g000sha256.tdl.dto.SuggestedPostInfo
import dev.g000sha256.tdl.dto.MessageTopicSavedMessages
import dev.g000sha256.tdl.dto.MessageTopicDirectMessages
import dev.g000sha256.tdl.dto.MessageSourceDirectMessagesChatTopicHistory
import dev.g000sha256.tdl.dto.DirectMessagesChatTopic
import dev.g000sha256.tdl.dto.MessageSourceForumTopicHistory
import dev.g000sha256.tdl.dto.ForumTopic as TdlForumTopic
import kotlinx.coroutines.flow.flowOf
import dev.g000sha256.tdl.dto.MessageSender
import dev.g000sha256.tdl.dto.MessageSenderChat
import dev.g000sha256.tdl.dto.MessageSenderUser
import dev.g000sha256.tdl.dto.MessageSendingState
import dev.g000sha256.tdl.dto.MessageSendingStateFailed
import dev.g000sha256.tdl.dto.MessageSendingStatePending
import dev.g000sha256.tdl.dto.MessageSourceChatHistory
import dev.g000sha256.tdl.dto.MessageSourceMessageThreadHistory
import dev.g000sha256.tdl.dto.MessageThreadInfo
import dev.g000sha256.tdl.dto.MessageText
import dev.g000sha256.tdl.dto.ReactionType
import dev.g000sha256.tdl.dto.ReactionTypeCustomEmoji
import dev.g000sha256.tdl.dto.ReactionTypeEmoji
import dev.g000sha256.tdl.dto.ReactionTypePaid
import dev.g000sha256.tdl.dto.SearchMessagesFilter
import dev.g000sha256.tdl.dto.SearchMessagesFilterAudio
import dev.g000sha256.tdl.dto.SearchMessagesFilterDocument
import dev.g000sha256.tdl.dto.SearchMessagesFilterPhotoAndVideo
import dev.g000sha256.tdl.dto.SearchMessagesFilterUrl
import dev.g000sha256.tdl.dto.SearchMessagesFilterVoiceNote
import dev.g000sha256.tdl.dto.SearchMessagesFilterVideoNote
import dev.g000sha256.tdl.dto.SearchMessagesFilterAnimation
import dev.g000sha256.tdl.dto.SearchMessagesFilterPinned
import dev.g000sha256.tdl.dto.SearchMessagesFilterUnreadMention
import dev.g000sha256.tdl.dto.SearchMessagesFilterUnreadPollVote
import dev.g000sha256.tdl.dto.SearchMessagesFilterUnreadReaction
import dev.g000sha256.tdl.dto.Story
import dev.g000sha256.tdl.dto.TextEntity
import dev.g000sha256.tdl.TdlThrowable
import dev.g000sha256.tdl.toResult
import dev.g000sha256.tdl.dto.ChatMembersFilterMention
import dev.g000sha256.tdl.dto.TextEntityTypeMentionName
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.withTimeoutOrNull
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
import eu.tintera.plume.core.location.GeoPoint

/**
 * Konverzace nad TDLib.
 *
 * **Jména odesílatelů se kešují.** Ve skupině pošle padesát zpráv deset lidí; bez keše by to bylo
 * padesát `getUser` volání jen kvůli hlavičkám bublin.
 */
class TdlConversationRepository(
    private val holder: TelegramClientHolder,
    /**
     * Téma skupiny s tématy, na které je konverzace zúžená (viz [inForumTopic]), nebo `null`.
     *
     * **Zúžení je parametr, ne obal** jako u vlákna komentářů: téma se TDLib předává v každém
     * odeslání, konceptu, „píše…" i hledání (`topicId`), a obal by musel přepsat přes dvacet
     * metod jen kvůli jednomu argumentu.
     */
    private val forumTopicId: Int? = null,
    /** Téma Uložených zpráv (zprávy uložené z jednoho chatu), viz [inSavedTopic]; `null` = všechny. */
    private val savedTopicId: Long? = null,
    /** Odkud vzít hlavičku tématu Uložených zpráv — TDLib jedno téma dotazem nevydá. */
    private val savedTopics: SavedTopicsRepository? = null,
    /** Vlákno přímých zpráv kanálu (správce s jedním odběratelem), viz [inDirectTopic]. */
    private val directTopicId: Long? = null,
    /** Odkud vzít vlákno přímých zpráv — jméno odesílatele, koncept, nepřečtené. */
    private val directTopics: DirectTopicsRepository? = null,
) : ConversationRepository {

    /**
     * Téma pro ODESLÁNÍ, koncept, „píše…" a zmínky; `null` = celý chat. Téma Uložených zpráv sem
     * nepatří: TDLib do něj odeslat nedovolí a zpráva bez tématu padne do Mých poznámek.
     */
    private val topic: MessageTopic? = forumTopicId?.let { MessageTopicForum(forumTopicId = it) }
        ?: directTopicId?.let { MessageTopicDirectMessages(directMessagesChatTopicId = it) }

    /** Téma pro ČTENÍ — hledání, připnuté, nepřečtené značky. Tady téma Uložených zpráv platí. */
    private val readTopic: MessageTopic? =
        topic ?: savedTopicId?.let { MessageTopicSavedMessages(savedMessagesTopicId = it) }

    /**
     * Skok na datum: `getChatMessageByDate` vrací POSLEDNÍ zprávu před okamžikem, ne první po
     * něm. Cíl je proto zpráva hned za ní (`getChatHistory` o jednu novější). Když před
     * okamžikem nic není (404 — den je starší než chat), vezme se nejstarší zpráva chatu:
     * historie od nejmenšího id serveru směrem k novějším.
     */
    override suspend fun firstMessageFrom(chatId: Long, epochSeconds: Int): Result<Long?> = runCatching {
        val before = client.getChatMessageByDate(chatId = chatId, date = epochSeconds - 1).toResult()
            .map<Long?, Message> { it.id }
            .recoverCatching { failure -> if (failure is TdlThrowable && failure.code == NOT_FOUND) null else throw failure }
            .getOrThrow()
        val from = before ?: FIRST_SERVER_MESSAGE_ID
        // TDLib vrací napřed, co má v lokální databázi — u staré historie s oknem jedné novější
        // přišla zpráva samotná a jedna STARŠÍ, novější ne (ověřeno na kanálu). S oknem pěti
        // novějších dorazily hned; pro jistotu se dotaz opakuje, jak radí dokumentace
        // `getChatHistory`, dokud novější nepřijde.
        var target: Long? = null
        repeat(NEWER_ATTEMPTS) {
            val ids = client.getChatHistory(chatId = chatId, fromMessageId = from, offset = -NEWER_WINDOW, limit = NEWER_WINDOW + 1, onlyLocal = false)
                .toResult().getOrThrow().messages.filterNotNull().map { it.id }
            target = firstMessageAfter(before, ids)
            if (target != null) return@runCatching target
        }
        target
    }.onFailure { logger.w(it) { "Zprávu podle data se nepodařilo najít." } }

    override fun inForumTopic(topicId: Int): ConversationRepository =
        TdlConversationRepository(holder = holder, forumTopicId = topicId, savedTopics = savedTopics, directTopics = directTopics)

    override fun inSavedTopic(topicId: Long): ConversationRepository =
        TdlConversationRepository(holder = holder, savedTopicId = topicId, savedTopics = savedTopics, directTopics = directTopics)

    override fun inDirectTopic(topicId: Long): ConversationRepository =
        TdlConversationRepository(holder = holder, directTopicId = topicId, savedTopics = savedTopics, directTopics = directTopics)

    /**
     * Téma Uložených zpráv, jak ho zná seznam témat. Konverzace obnovená po zabití procesu ho
     * mít nemusí — pak se témata napřed dotáhnou.
     */
    private suspend fun savedTopic(): SavedTopic? {
        val id = savedTopicId ?: return null
        val repository = savedTopics ?: return null
        repository.topics.value?.firstOrNull { it.id == id }?.let { return it }
        repository.load()
        return repository.topics.value?.firstOrNull { it.id == id }
    }

    /** Vlákno přímých zpráv, jak ho TDLib zná teď — koncept a hranice přečtení jsou jeho. */
    private suspend fun directTopic(chatId: Long): DirectMessagesChatTopic? = directTopicId?.let {
        client.getDirectMessagesChatTopic(chatId = chatId, topicId = it).toResult().getOrNull()
    }

    /** Téma, jak ho TDLib zná teď — počty, hranice přečtení a koncept jsou jeho, ne skupiny. */
    private suspend fun forumTopic(chatId: Long): TdlForumTopic? = forumTopicId?.let {
        client.getForumTopic(chatId = chatId, forumTopicId = it).toResult().getOrNull()
    }

    /** Patří zpráva do konverzace? V tématu jen zprávy tématu, jinak všechny. */
    private fun inScope(message: Message): Boolean = when {
        forumTopicId != null -> (message.topicId as? MessageTopicForum)?.forumTopicId == forumTopicId
        savedTopicId != null -> (message.topicId as? MessageTopicSavedMessages)?.savedMessagesTopicId == savedTopicId
        directTopicId != null ->
            (message.topicId as? MessageTopicDirectMessages)?.directMessagesChatTopicId == directTopicId
        else -> true
    }


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

        if (forumTopicId != null) {
            return client.getForumTopicHistory(
                chatId = chatId,
                forumTopicId = forumTopicId,
                fromMessageId = fromMessageId,
                offset = offset,
                limit = limit,
            )
                .toResult()
                .map { messages -> messages.messages.filterNotNull().map { it.toChatMessage(isGroup) } }
        }

        if (directTopicId != null) {
            /*
             * Od nejnovější (`fromMessageId = 0`) čekal TDLib na server přes dvacet vteřin, i když
             * zprávy vlákna měl v lokální databázi (změřeno na emulátoru: 23,7 s proti 0,14 s).
             * Začíná se proto od poslední zprávy vlákna, kterou TDLib zná, s `offset = -1`:
             * `fromMessageId` sám ve výsledku není, o jednu novější ho tam vrátí.
             */
            val last = if (fromMessageId == 0L) directTopic(chatId)?.lastMessage?.id else null
            return client.getDirectMessagesChatTopicHistory(
                chatId = chatId,
                topicId = directTopicId,
                fromMessageId = last ?: fromMessageId,
                offset = if (last != null) offset - 1 else offset,
                limit = if (last != null) maxOf(limit, 1 - offset) else limit,
            )
                .toResult()
                .map { messages -> messages.messages.filterNotNull().map { it.toChatMessage(isGroup) } }
        }

        if (savedTopicId != null) {
            return client.getSavedMessagesTopicHistory(
                savedMessagesTopicId = savedTopicId,
                fromMessageId = fromMessageId,
                offset = offset,
                limit = limit,
            )
                .toResult()
                .map { messages -> messages.messages.filterNotNull().map { it.toChatMessage(isGroup) } }
        }

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
            .map { it.toCommentThread() }

    private fun MessageThreadInfo.toCommentThread() = CommentThread(
        chatId = chatId,
        rootMessageId = messageThreadId,
        lastReadMessageId = replyInfo?.lastReadInboxMessageId ?: 0L,
        unreadCount = unreadMessageCount,
        draft = draftMessage?.toSavedDraft(),
    )

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
            // Naplánovaná zpráva do historie nepatří; až odejde, přijde znovu jako běžná.
            .filter { it.message.chatId == chatId && it.message.schedulingState == null && inScope(it.message) }
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

    override suspend fun recognizeSpeech(chatId: Long, messageId: Long): Result<Unit> =
        client.recognizeSpeech(chatId = chatId, messageId = messageId).toResult().map { }

    override suspend fun clickAnimatedEmoji(chatId: Long, messageId: Long): Result<Int?> =
        client.clickAnimatedEmojiMessage(chatId = chatId, messageId = messageId)
            .toResult()
            // Jiný formát než `.tgs` přehrát neumíme; emoji pak odpoví jen samo, jako bez efektu.
            .map { sticker -> sticker.takeIf { it.format is StickerFormatTgs }?.sticker?.id }
            // 404 = emoji vlastní efekt nemá, není to chyba.
            .recoverCatching { failure -> if (failure is TdlThrowable && failure.code == NOT_FOUND) null else throw failure }

    override fun emojiInteractions(chatId: Long): Flow<EmojiInteraction> =
        holder.updates { animatedEmojiMessageClickedUpdates }
            .filter { it.chatId == chatId && it.sticker.format is StickerFormatTgs }
            .map { EmojiInteraction(messageId = it.messageId, animationFileId = it.sticker.sticker.id) }

    override suspend fun sendWatchingAnimation(chatId: Long, emoji: String): Result<Unit> =
        client.sendChatAction(
            chatId = chatId,
            topicId = topic,
            businessConnectionId = "",
            action = ChatActionWatchingAnimations(emoji = emoji),
        )
            .toResult()
            .map { }

    override suspend fun approveSuggestedPost(chatId: Long, messageId: Long): Result<Unit> =
        // 0 = termín, který si návrh nese; bez něj se zveřejní hned.
        client.approveSuggestedPost(chatId = chatId, messageId = messageId, sendDate = 0).toResult().map { }

    override suspend fun declineSuggestedPost(chatId: Long, messageId: Long, comment: String): Result<Unit> =
        client.declineSuggestedPost(chatId = chatId, messageId = messageId, comment = comment.take(DECLINE_COMMENT_MAX))
            .toResult().map { }

    override suspend fun showOriginalContent(chatId: Long, messageId: Long): Result<Unit> =
        client.deleteMessageEphemeralContent(chatId = chatId, messageId = messageId).toResult().map { }

    override fun editedMessages(chatId: Long): Flow<ChatMessage> =
        merge(
            holder.updates { messageContentUpdates }.filter { it.chatId == chatId }.map { it.messageId },
            // Tlačítka bota se mění i bez změny obsahu — a pak přijde jen tenhle update.
            holder.updates { messageEditedUpdates }.filter { it.chatId == chatId }.map { it.messageId },
            // Kontrolu faktů server přidává (a mění) až po zveřejnění — a přijde jen tenhle update.
            holder.updates { messageFactCheckUpdates }.filter { it.chatId == chatId }.map { it.messageId },
            // Schválený nebo odmítnutý návrh příspěvku: mění se jen stav, obsah ne.
            holder.updates { messageSuggestedPostInfoUpdates }.filter { it.chatId == chatId }.map { it.messageId },
            // Bot nahradil obsah zprávy mizejícím (nebo ho uživatel vrátil) — obsah sám se nezměnil.
            holder.updates { messageEphemeralContentUpdates }.filter { it.chatId == chatId }.map { it.messageId },
        )
            .map { messageId ->
                // Update nese jen nový obsah, ne celou zprávu — zbytek se musí dočíst.
                client.getMessage(chatId = chatId, messageId = messageId)
                    .toResult()
                    .map { it.toChatMessage(isGroup(chatId)) }
            }
            // Přeplánování je úprava zprávy — naplánovaná by se tudy vmíchala do historie.
            .filter { it.isSuccess && it.getOrNull()?.schedule == null }
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
            holder.updates { forumTopicUpdates }.filter { it.chatId == chatId && it.forumTopicId == forumTopicId }.map { },
        )
            .onStart { emit(Unit) }
            .mapNotNull {
                if (forumTopicId != null) {
                    return@mapNotNull forumTopic(chatId)?.let { topic ->
                        UnreadMarks(
                            mentions = topic.unreadMentionCount,
                            reactions = topic.unreadReactionCount,
                            pollVotes = topic.unreadPollVoteCount,
                        )
                    }
                }
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
                .map {
                    UnreadMarkChange(
                        messageId = it.messageId,
                        kind = UnreadMarkKind.Reaction,
                        hasUnread = it.unreadReactions.isNotEmpty(),
                        reactions = it.unreadReactions.toNewReactions(),
                    )
                },
            holder.updates { messageContainsUnreadPollVotesUpdates }
                .filter { it.chatId == chatId }
                .map { UnreadMarkChange(it.messageId, UnreadMarkKind.PollVote, hasUnread = it.containsUnreadPollVotes) },
        )

    override suspend fun oldestUnreadMark(chatId: Long, kind: UnreadMarkKind): Result<Long?> =
        client.searchChatMessages(
            chatId = chatId,
            topicId = readTopic,
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

    override suspend fun unreadMentions(chatId: Long, limit: Int): Result<List<ChatMessage>> {
        val isGroup = isGroup(chatId)
        return client.searchChatMessages(
            chatId = chatId,
            topicId = readTopic,
            query = "",
            senderId = null,
            fromMessageId = 0L,
            offset = 0,
            limit = limit,
            filter = SearchMessagesFilterUnreadMention(),
        )
            .toResult()
            .map { found -> found.messages.filterNotNull().map { it.toChatMessage(isGroup) } }
    }

    override suspend fun readAllMarks(chatId: Long, kind: UnreadMarkKind): Result<Unit> =
        if (directTopicId != null && kind == UnreadMarkKind.Reaction) {
            client.readAllDirectMessagesChatTopicReactions(chatId = chatId, topicId = directTopicId).toResult().map { }
        } else if (forumTopicId != null) {
            when (kind) {
                UnreadMarkKind.Mention -> client.readAllForumTopicMentions(chatId = chatId, forumTopicId = forumTopicId)
                UnreadMarkKind.Reaction -> client.readAllForumTopicReactions(chatId = chatId, forumTopicId = forumTopicId)
                UnreadMarkKind.PollVote -> client.readAllForumTopicPollVotes(chatId = chatId, forumTopicId = forumTopicId)
            }
                .toResult()
                .map { }
        } else when (kind) {
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
                    canEditMedia = properties.canEditMedia,
                )
            }

    /**
     * `quote` jde `null`: jinou citaci chce server jen u `needAnotherReplyQuote`, a taková zpráva
     * se k opakování nenabízí ([toSendFailure]). Hvězdy taky ne — placené zprávy Plume neumí.
     * Zpráva, kterou TDLib znovu poslat nedokázal, má ve výsledku `null`; když je to každá, je to
     * neúspěch, jinak by ťuknutí nic neudělalo a nikdo by se nedozvěděl proč.
     */
    override suspend fun resend(chatId: Long, messageIds: List<Long>): Result<Unit> =
        client.resendMessages(
            chatId = chatId,
            messageIds = messageIds.sorted().toLongArray(),
            quote = null,
            paidMessageStarCount = 0,
        )
            .toResult()
            .mapCatching { result ->
                check(result.messages.any { it != null }) { "TDLib nic znovu neposlal: $messageIds" }
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

    override suspend fun senderModeration(chatId: Long, messageIds: List<Long>): Result<SenderModeration?> =
        runCatching {
            val chat = client.getChat(chatId = chatId).toResult().getOrThrow()
            // Smazat všechno od autora i nahlásit spam umí TDLib jen v supergroupě; v kanálu nikdo nepíše.
            val type = chat.type as? ChatTypeSupergroup
            if (type == null || type.isChannel || messageIds.isEmpty()) return@runCatching null
            val messages = client.getMessages(chatId = chatId, messageIds = messageIds.toLongArray())
                .toResult().getOrThrow().messages.filterNotNull()
            if (messages.size != messageIds.size || messages.any { it.isOutgoing }) return@runCatching null
            // Volby míří na JEDNOHO člověka; výběr zpráv od víc lidí je jen mazání.
            val userId = messages.map { (it.senderId as? MessageSenderUser)?.userId }.distinct().singleOrNull()
                ?: return@runCatching null
            val user = client.getUser(userId = userId).toResult().getOrThrow()
            val status = client.getChatMember(chatId = chatId, memberId = MessageSenderUser(userId = userId))
                .toResult().getOrThrow().status
            val spamReportable = messages.all { message ->
                client.getMessageProperties(chatId = chatId, messageId = message.id).toResult().getOrNull()
                    ?.canReportSupergroupSpam == true
            }
            val rights = senderModerationRights(
                own = client.ownMemberStatus(chat),
                target = TargetMember(
                    role = status.role(),
                    canBeEdited = (status as? ChatMemberStatusAdministrator)?.canBeEdited == true,
                    isSelf = userId == client.getMe().toResult().getOrNull()?.id,
                    isBot = user.type is UserTypeBot,
                ),
                spamReportable = spamReportable,
            )
            val name = listOf(user.firstName, user.lastName).filter { it.isNotBlank() }.joinToString(" ")
            rights.takeIf { it.any && name.isNotBlank() }?.let { SenderModeration(userId = userId, name = name, rights = it) }
        }

    override suspend fun deleteAsModerator(
        chatId: Long,
        messageIds: List<Long>,
        userId: Long,
        choice: ModerationChoice,
    ): Result<Unit> {
        val sender = MessageSenderUser(userId = userId)
        val failures = mutableListOf<Throwable>()
        fun Result<*>.collect() = onFailure { failures += it }

        if (choice.reportSpam) {
            val supergroupId = (client.getChat(chatId = chatId).toResult().getOrNull()?.type as? ChatTypeSupergroup)?.supergroupId
            if (supergroupId != null) {
                client.reportSupergroupSpam(supergroupId = supergroupId, messageIds = messageIds.toLongArray()).toResult().collect()
            }
        }
        if (choice.deleteReactions) {
            // Ještě před vyhozením, dokud je autor členem: dokumentace neříká, jestli server
            // maže reakce i někomu, kdo už ve skupině není.
            client.deleteAllRecentMessageReactionsFromSender(chatId = chatId, senderId = sender).toResult().collect()
        }
        if (choice.ban) {
            // Navždy a bez mazání: zprávy řeší krok níž podle toho, co si správce zaškrtl.
            client.banChatMember(chatId = chatId, memberId = sender, bannedUntilDate = FOREVER, revokeMessages = false)
                .toResult().collect()
        }
        if (choice.deleteAll) {
            client.deleteChatMessagesBySender(chatId = chatId, senderId = sender).toResult().collect()
        } else {
            client.deleteMessages(chatId = chatId, messageIds = messageIds.toLongArray(), revoke = true).toResult().collect()
        }
        return failures.firstOrNull()?.let { Result.failure(it) } ?: Result.success(Unit)
    }

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
    ): Result<ChatSearchPage> = search(chatId, readTopic, query, fromMessageId, senderUserId)

    override suspend fun searchInThread(
        chatId: Long,
        threadId: Long,
        query: String,
        fromMessageId: Long,
        senderUserId: Long?,
    ): Result<ChatSearchPage> =
        search(chatId, MessageTopicThread(messageThreadId = threadId), query, fromMessageId, senderUserId)

    private suspend fun search(
        chatId: Long,
        topic: MessageTopic?,
        query: String,
        fromMessageId: Long,
        senderUserId: Long?,
    ): Result<ChatSearchPage> =
        client.searchChatMessages(
            chatId = chatId,
            topicId = topic,
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
                topicId = readTopic,
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
            options = SendOptions(schedule = options.schedule).toTdl(),
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

    override suspend fun unpinAll(chatId: Long): Result<Unit> {
        val result = if (forumTopicId != null) {
            client.unpinAllForumTopicMessages(chatId = chatId, forumTopicId = forumTopicId)
        } else {
            client.unpinAllChatMessages(chatId = chatId)
        }
        return result.toResult().map { }
    }

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
        query: String,
        offset: Int,
    ): Result<SharedMediaPage> {
        val isGroup = isGroup(chatId)
        return client.searchChatMessages(
            chatId = chatId,
            topicId = null,
            query = query,
            senderId = null,
            fromMessageId = fromMessageId,
            offset = offset,
            // Limit musí být větší než -offset, jinak by TDLib nevrátil nic staršího.
            limit = maxOf(SHARED_MEDIA_PAGE, 1 - offset),
            filter = kind.toFilter(),
        )
            .toResult()
            .map { found ->
                val messages = found.messages
                    .filterNotNull()
                    .map { it.toChatMessage(isGroup) }
                    // Článek s fotkami a videi patří do Médií taky — TDLib ho hledáním vrací
                    // a mřížka ho rozbalí na dlaždice (`sharedMediaTiles`).
                    .filter { kind != SharedMediaKind.Media || it.isLastingVisualMedia || it.hasArticleVisualMedia }
                    // Mizející kolečko a hlasovka po přehrání zmizí, do trvalého přehledu nepatří.
                    .filter { (kind != SharedMediaKind.VideoNotes && kind != SharedMediaKind.Voice) || it.selfDestructType == null }
                // Konec se pozná JEN podle toho, že server nevrátil nic. Počet po filtrování
                // na to nestačí: stránka samých mizejících fotek vyjde prázdná uprostřed chatu.
                SharedMediaPage(
                    messages = messages,
                    reachedEnd = found.messages.isEmpty(),
                    nextFromMessageId = found.messages.lastOrNull()?.id ?: 0,
                )
            }
    }

    override suspend fun sharedMediaPositions(chatId: Long, kind: SharedMediaKind): Result<SharedMediaPositions> =
        client.getChatSparseMessagePositions(
            chatId = chatId,
            filter = kind.toFilter(),
            // Od nejnovější zprávy; pozice jsou počítané od ní.
            fromMessageId = 0,
            limit = SPARSE_POSITIONS_LIMIT,
            savedMessagesTopicId = 0,
        )
            .toResult()
            .map { found ->
                SharedMediaPositions(
                    totalCount = found.totalCount,
                    positions = found.positions
                        .map { MediaPosition(position = it.position, messageId = it.messageId, timestamp = it.date) }
                        .sortedBy { it.position },
                )
            }

    override suspend fun mediaCalendar(chatId: Long, fromMessageId: Long): Result<MediaCalendarPage> {
        val isGroup = isGroup(chatId)
        return client.getChatMessageCalendar(
            chatId = chatId,
            topicId = null,
            filter = SharedMediaKind.Media.toFilter(),
            fromMessageId = fromMessageId,
        )
            .toResult()
            .map { calendar ->
                val days = calendar.days.toList()
                MediaCalendarPage(
                    days = days.map { MediaCalendarDay(count = it.totalCount, message = it.message.toChatMessage(isGroup)) },
                    // Dny jdou od nejnovějšího a každý nese svou PRVNÍ zprávu, takže nejstarší
                    // zpráva stránky je u posledního dne — od ní se pokračuje k starším.
                    nextFromMessageId = days.lastOrNull()?.message?.id ?: 0,
                    reachedEnd = days.isEmpty(),
                )
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
                music = counts.getValue(SharedMediaKind.Music),
                gifs = counts.getValue(SharedMediaKind.Gifs),
                videoNotes = counts.getValue(SharedMediaKind.VideoNotes),
                voice = counts.getValue(SharedMediaKind.Voice),
            )
        }
    }

    private suspend fun countOf(chatId: Long, kind: SharedMediaKind): Int =
        client.getChatMessageCount(chatId = chatId, filter = kind.toFilter(), returnLocal = false)
            .toResult()
            .map { it.count }
            .getOrDefault(0)

    /**
     * Hledáním s filtrem, jako [visualMedia]: jedno volání vrátí skladbu, novější (záporný
     * `offset`) i starší. Hlasovky filtr nebere, ty jsou `VoiceNote`.
     *
     * **`offset = -1` NOVĚJŠÍ zprávu nepřinese**, jen tu samotnou — ověřeno logem: od téže
     * zprávy dal `-1` [skladba, starší, starší] a `-2` [novější, skladba, starší, starší].
     * Proto `-2`; zpráva sama se ve výsledku odfiltruje podle id.
     */
    override suspend fun musicNeighbors(chatId: Long, messageId: Long): Result<MusicNeighbors> {
        val isGroup = isGroup(chatId)
        return client.searchChatMessages(
            chatId = chatId,
            topicId = null,
            query = "",
            senderId = null,
            fromMessageId = messageId,
            offset = -2,
            limit = 4,
            filter = SearchMessagesFilterAudio(),
        )
            .toResult()
            .map { found ->
                val tracks = found.messages.filterNotNull()
                    .map { it.toChatMessage(isGroup) }
                    .filter { it.media is MessageMedia.Audio }
                MusicNeighbors(
                    previous = tracks.filter { it.id < messageId }.maxByOrNull { it.id },
                    next = tracks.filter { it.id > messageId }.minByOrNull { it.id },
                )
            }
    }

    override suspend fun message(chatId: Long, messageId: Long): Result<ChatMessage> {
        val isGroup = isGroup(chatId)
        return client.getMessage(chatId = chatId, messageId = messageId)
            .toResult()
            .map { it.toChatMessage(isGroup) }
    }

    override suspend fun recentLiveLocations(chatId: Long): Result<List<ChatMessage>> {
        val isGroup = isGroup(chatId)
        return client.searchChatRecentLocationMessages(chatId = chatId, limit = RECENT_LOCATIONS_LIMIT)
            .toResult()
            .map { messages ->
                messages.messages.filterNotNull()
                    .filter { it.content is MessageLiveLocation && inScope(it) }
                    .map { it.toChatMessage(isGroup) }
            }
    }

    override fun sentMessageUpdates(chatId: Long): Flow<SentMessageUpdate> = merge(
        holder.updates { messageSendSucceededUpdates }
            // Potvrzení NAPLÁNOVANÉ zprávy (odešla na server, ale čeká na svůj čas) do historie
            // nepatří — `withSentMessage` by ji do seznamu vložil.
            .filter { it.message.chatId == chatId && it.message.schedulingState == null }
            .map { update ->
                SentMessageUpdate.Succeeded(
                    oldMessageId = update.oldMessageId,
                    message = update.message.toChatMessage(isGroup(chatId)),
                )
            },
        holder.updates { messageSendFailedUpdates }
            .filter { it.message.chatId == chatId && it.message.schedulingState == null }
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
        options: SendOptions,
        mentions: List<NameMention>,
        formats: List<FormatSpan>,
    ): Result<ChatMessage> {
        val entities = detectEntities(text, mentions, formats)
        val content = InputMessageText(
            text = FormattedText(text = text, entities = entities),
            linkPreviewOptions = linkPreview?.toTdl(),
            clearDraft = true,
        )
        val replyTo = replyInput(chatId, replyToMessageId, quote)

        sendEphemeralText(chatId, text, replyToMessageId, replyTo, content)?.let { return it }

        return client.sendMessage(
            chatId = chatId,
            // Vlákna (fóra) fáze 2 neřeší — null znamená hlavní vlákno chatu.
            topicId = topic,
            replyTo = replyTo,
            options = options.toTdl(),
            replyMarkup = null,
            inputMessageContent = content,
        )
            .toResult()
            // TDLib vrací zprávu okamžitě, s dočasným id a stavem Pending.
            .map { it.toChatMessage(isGroup(chatId)) }
    }

    override suspend fun sendDice(
        chatId: Long,
        emoji: String,
        replyToMessageId: Long?,
        quote: ReplyQuote?,
    ): Result<ChatMessage> =
        client.sendMessage(
            chatId = chatId,
            topicId = topic,
            replyTo = replyInput(chatId, replyToMessageId, quote),
            options = null,
            replyMarkup = null,
            // Hod jde z pole jako text, takže koncept maže stejně jako odeslaný text.
            inputMessageContent = InputMessageDice(emoji = emoji, clearDraft = true),
        )
            .toResult()
            .map { it.toChatMessage(isGroup(chatId)) }

    override suspend fun sendVoiceNote(
        chatId: Long,
        path: String,
        durationSeconds: Int,
        waveform: ByteArray,
        replyToMessageId: Long?,
        quote: ReplyQuote?,
        options: SendOptions,
    ): Result<ChatMessage> =
        client.sendMessage(
            chatId = chatId,
            topicId = topic,
            replyTo = replyInput(chatId, replyToMessageId, quote),
            options = options.toTdl(),
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

    override suspend fun sendVideoNote(
        chatId: Long,
        videoNote: OutgoingVideoNote,
        replyToMessageId: Long?,
        quote: ReplyQuote?,
        options: SendOptions,
    ): Result<ChatMessage> =
        client.sendMessage(
            chatId = chatId,
            topicId = topic,
            replyTo = replyInput(chatId, replyToMessageId, quote),
            options = options.toTdl(),
            replyMarkup = null,
            inputMessageContent = InputMessageVideoNote(
                videoNote = InputVideoNote(
                    videoNote = InputFileLocal(path = videoNote.path),
                    thumbnail = videoNote.thumbnail?.let {
                        InputThumbnail(thumbnail = InputFileLocal(path = it.path), width = it.width, height = it.height)
                    },
                    duration = videoNote.durationSeconds,
                    length = videoNote.length,
                ),
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
        options: SendOptions,
        mentions: List<NameMention>,
        formats: List<FormatSpan>,
    ): Result<List<ChatMessage>> {
        require(attachments.isNotEmpty()) { "Není co poslat." }
        val isGroup = isGroup(chatId)
        // Entity popisku se dohledávají jako u textu — jinak by odkaz v popisku odešel jako mrtvý text.
        val formattedCaption = caption.takeIf { it.isNotBlank() }
            ?.let { FormattedText(text = it, entities = detectEntities(it, mentions, formats)) }
        val contents = sendableTogether(attachments).mapIndexed { index, attachment ->
            attachment.toInputContent(caption = formattedCaption.takeIf { index == 0 })
        }

        if (contents.size == 1) {
            return client.sendMessage(
                chatId = chatId,
                topicId = topic,
                replyTo = replyInput(chatId, replyToMessageId, quote),
                options = options.toTdl(),
                replyMarkup = null,
                inputMessageContent = contents.single(),
            )
                .toResult()
                .map { listOf(it.toChatMessage(isGroup)) }
        }

        return client.sendMessageAlbum(
            chatId = chatId,
            topicId = topic,
            replyTo = replyInput(chatId, replyToMessageId, quote),
            options = options.toTdl(),
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
            is OutgoingAttachment.Document, is OutgoingAttachment.Audio -> null
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

            is OutgoingAttachment.Audio -> InputMessageAudio(
                audio = InputAudio(
                    audio = file,
                    albumCoverThumbnail = cover?.let {
                        InputThumbnail(thumbnail = InputFileLocal(path = it.path), width = it.width, height = it.height)
                    },
                    duration = durationSeconds,
                    title = title.orEmpty(),
                    performer = performer.orEmpty(),
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
        options: SendOptions,
    ): Result<ChatMessage> {
        val file = InputFileId(id = sticker.sendFileId)

        return client.sendMessage(
            chatId = chatId,
            topicId = topic,
            replyTo = replyInput(chatId, replyToMessageId, quote),
            options = options.toTdl(),
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
            topicId = topic,
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

    /** Uživatel za sdíleným kontaktem: profilovka a jestli ho nabídnout k přidání do kontaktů. */
    private suspend fun sharedContactUser(content: MessageContent): SharedContactUser? {
        val userId = (content as? MessageContact)?.contact?.userId?.takeIf { it != 0L } ?: return null
        val user = client.getUser(userId = userId).toResult().getOrNull() ?: return null
        return SharedContactUser(
            photoFileId = user.profilePhoto?.small?.id,
            canBeAdded = !user.isContact && user.type is UserTypeRegular && userId != myId(),
        )
    }

    override suspend fun addSharedContact(chatId: Long, messageId: Long): Result<Long> = runCatching {
        val message = client.getMessage(chatId = chatId, messageId = messageId).toResult().getOrThrow()
        val contact = (message.content as? MessageContact)?.contact ?: error("Zpráva $messageId nenese kontakt.")
        check(contact.userId != 0L) { "Kontakt ve zprávě $messageId není na Telegramu." }
        client.addContact(
            userId = contact.userId,
            contact = ImportedContact(
                phoneNumber = contact.phoneNumber,
                // TDLib chce aspoň jeden znak křestního jména; bez něj vezmeme příjmení.
                firstName = contact.firstName.ifBlank { contact.lastName },
                lastName = if (contact.firstName.isBlank()) "" else contact.lastName,
                note = null,
            ),
            sharePhoneNumber = false,
        ).toResult().getOrThrow()
        contact.userId
    }

    override suspend fun acceptSuggestedBirthdate(chatId: Long, messageId: Long): Result<Unit> = runCatching {
        val message = client.getMessage(chatId = chatId, messageId = messageId).toResult().getOrThrow()
        val suggested = message.content as? MessageSuggestBirthdate ?: error("Zpráva $messageId nenese návrh data narození.")
        client.setBirthdate(birthdate = suggested.birthdate).toResult().getOrThrow()
    }

    override suspend fun acceptSuggestedPhoto(chatId: Long, messageId: Long): Result<Unit> = runCatching {
        val message = client.getMessage(chatId = chatId, messageId = messageId).toResult().getOrThrow()
        val photo = (message.content as? MessageSuggestProfilePhoto)?.photo
            ?: error("Zpráva $messageId nenese návrh profilové fotky.")
        // Animovaná profilovka se nahraje jako video se snímkem pro statickou podobu, jinak
        // největší velikost fotky. Stahuje se synchronně — bez souboru není co nahrát.
        val animation = photo.animation
        val input = if (animation != null) {
            InputChatPhotoAnimation(
                animation = InputFileLocal(path = downloadedPath(animation.file.id)),
                mainFrameTimestamp = animation.mainFrameTimestamp,
            )
        } else {
            val biggest = photo.sizes.maxByOrNull { it.width * it.height } ?: error("Navržená fotka nemá velikost.")
            InputChatPhotoStatic(photo = InputFileLocal(path = downloadedPath(biggest.photo.id)))
        }
        client.setProfilePhoto(photo = input, isPublic = false).toResult().getOrThrow()
    }

    private suspend fun downloadedPath(fileId: Int): String =
        client.downloadFile(fileId = fileId, priority = 32, offset = 0, limit = 0, synchronous = true)
            .toResult().getOrThrow().local.path.takeIf { it.isNotBlank() }
            ?: error("Soubor $fileId se nestáhl.")

    override suspend fun contactCard(userId: Long): Result<MessageMedia.Contact> = runCatching {
        val user = client.getUser(userId = userId).toResult().getOrThrow()
        MessageMedia.Contact(
            name = listOf(user.firstName, user.lastName).filter { it.isNotBlank() }.joinToString(" "),
            phone = user.phoneNumber.takeIf { it.isNotBlank() }?.let(::contactPhone).orEmpty(),
            userId = userId,
            photoFileId = user.profilePhoto?.small?.id,
        )
    }

    override suspend fun sendContact(
        chatId: Long,
        userId: Long,
        replyToMessageId: Long?,
        quote: ReplyQuote?,
    ): Result<ChatMessage> {
        val user = client.getUser(userId = userId).toResult().getOrElse { return Result.failure(it) }
        return client.sendMessage(
            chatId = chatId,
            topicId = topic,
            replyTo = replyInput(chatId, replyToMessageId, quote),
            options = null,
            replyMarkup = null,
            inputMessageContent = InputMessageContact(
                contact = Contact(
                    phoneNumber = user.phoneNumber,
                    firstName = user.firstName,
                    lastName = user.lastName,
                    vcard = "",
                    userId = userId,
                ),
            ),
        )
            .toResult()
            .map { it.toChatMessage(isGroup(chatId)) }
    }

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
            topicId = topic,
            replyTo = replyInput(chatId, replyToMessageId, quote),
            options = null,
            replyMarkup = null,
            inputMessageContent = content,
        )
            .toResult()
            .map { it.toChatMessage(isGroup(chatId)) }
    }

    override suspend fun sendLiveLocation(
        chatId: Long,
        fix: LiveFix,
        livePeriod: Int,
        replyToMessageId: Long?,
        quote: ReplyQuote?,
    ): Result<ChatMessage> =
        client.sendMessage(
            chatId = chatId,
            topicId = topic,
            replyTo = replyInput(chatId, replyToMessageId, quote),
            options = null,
            replyMarkup = null,
            inputMessageContent = InputMessageLiveLocation(
                location = LiveLocation(
                    location = Location(
                        latitude = fix.latitude,
                        longitude = fix.longitude,
                        horizontalAccuracy = fix.accuracyMeters.coerceIn(0.0, 1500.0),
                    ),
                    livePeriod = livePeriod,
                    heading = fix.heading,
                    proximityAlertRadius = 0,
                ),
            ),
        )
            .toResult()
            .map { it.toChatMessage(isGroup(chatId)) }

    override suspend fun sendGif(
        chatId: Long,
        gif: GifItem,
        replyToMessageId: Long?,
        quote: ReplyQuote?,
        options: SendOptions,
    ): Result<ChatMessage> {
        val file = InputFileId(id = gif.animation.fileId)
        return client.sendMessage(
            chatId = chatId,
            topicId = topic,
            replyTo = replyInput(chatId, replyToMessageId, quote),
            options = options.toTdl(),
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

    /**
     * Inline boti podle jména, pro JEDNU instanci klienta (po odhlášení je nový účet jiný).
     * Dotaz jde při každé pauze v psaní a `searchPublicChat` na neznámé jméno jde na server;
     * bez keše by se tentýž bot hledal znovu s každým písmenem dotazu.
     */
    private var inlineBots: Pair<TdlClient, MutableMap<String, InlineBotInfo?>>? = null

    override suspend fun inlineBot(username: String): Result<InlineBotInfo?> {
        val current = client
        val cache = inlineBots?.takeIf { it.first === current }?.second
            ?: mutableMapOf<String, InlineBotInfo?>().also { inlineBots = current to it }
        val key = username.lowercase()
        if (key in cache) return Result.success(cache[key])
        return current.searchPublicChat(username = username)
            .toResult()
            .mapCatching { chat ->
                val userId = (chat.type as? ChatTypePrivate)?.userId ?: return@mapCatching null
                val user = current.getUser(userId = userId).toResult().getOrThrow()
                val bot = user.type as? UserTypeBot ?: return@mapCatching null
                InlineBotInfo(
                    userId = userId,
                    username = user.usernames?.publicName() ?: username,
                    placeholder = bot.inlineQueryPlaceholder,
                    needLocation = bot.needLocation,
                )
            }
            // Neexistující jméno je taky odpověď („bot to není") a pamatuje se stejně.
            .recover { failure ->
                logger.i { "@$username se jako bot nenašel: ${failure.message}" }
                null
            }
            .onSuccess { cache[key] = it }
    }

    override suspend fun inlineResults(
        botUserId: Long,
        chatId: Long,
        query: String,
        offset: String,
        location: GeoPoint?,
    ): Result<InlineResultsPage> =
        client.getInlineQueryResults(
            botUserId = botUserId,
            chatId = chatId,
            userLocation = location?.let { Location(latitude = it.latitude, longitude = it.longitude, horizontalAccuracy = 0.0) },
            query = query,
            offset = offset,
        )
            .toResult()
            .map { it.toPage() }

    override suspend fun sendInlineResult(
        chatId: Long,
        queryId: Long,
        resultId: String,
        replyToMessageId: Long?,
        quote: ReplyQuote?,
    ): Result<ChatMessage> =
        client.sendInlineQueryResultMessage(
            chatId = chatId,
            topicId = topic,
            replyTo = replyInput(chatId, replyToMessageId, quote),
            options = null,
            queryId = queryId,
            resultId = resultId,
            // „přes @bot" u zprávy nechává i oficiální klient; skrýt ho jde jen u pár botů od Telegramu.
            hideViaBot = false,
        )
            .toResult()
            .map { it.toChatMessage(isGroup(chatId)) }

    override suspend fun sendTyping(chatId: Long, typing: Boolean): Result<Unit> =
        client.sendChatAction(
            chatId = chatId,
            // Vlákna (fóra) zatím neřešíme, stejně jako u odesílání.
            topicId = topic,
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
            // V tématu se čte téma; se zdrojem historie chatu by se přečtení tématu nepohnulo
            // (tatáž past jako u vlákna komentářů).
            source = when {
                forumTopicId != null -> MessageSourceForumTopicHistory()
                directTopicId != null -> MessageSourceDirectMessagesChatTopicHistory()
                else -> MessageSourceChatHistory()
            },
            // `forceRead = true`: bez toho TDLib označí za přečtené jen to, o čem si myslí,
            // že to uživatel opravdu viděl na obrazovce. My mu to tvrdíme sami.
            forceRead = true,
        )
            .toResult()
            .map { }

    override suspend fun markThreadRead(chatId: Long, messageIds: List<Long>): Result<Unit> =
        client.viewMessages(
            chatId = chatId,
            messageIds = messageIds.toLongArray(),
            source = MessageSourceMessageThreadHistory(),
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

    override suspend fun editText(
        chatId: Long,
        messageId: Long,
        text: String,
        linkPreview: LinkPreviewOptions?,
        formats: List<FormatSpan>,
    ): Result<Unit> =
        client.editMessageText(
            chatId = chatId,
            messageId = messageId,
            replyMarkup = null,
            inputMessageContent = InputMessageText(
                text = FormattedText(text = text, entities = detectEntities(text, formats = formats)),
                // Volbu přebírá úprava z původní zprávy (`forEdit`). S `null` platí výchozí volba
                // serveru, takže úprava zprávy poslané BEZ náhledu by náhled vrátila (ověřeno Bot API).
                linkPreviewOptions = linkPreview?.toTdl(),
                // Úprava s konceptem chatu nesouvisí; smazat ho by uživateli vzalo rozepsaný text.
                clearDraft = false,
            ),
        )
            .toResult()
            .map { }

    override suspend fun editCaption(
        chatId: Long,
        messageId: Long,
        caption: String,
        formats: List<FormatSpan>,
    ): Result<Unit> =
        client.editMessageCaption(
            chatId = chatId,
            messageId = messageId,
            replyMarkup = null,
            caption = FormattedText(text = caption, entities = detectEntities(caption, formats = formats)),
            // Popisek nad médiem aplikace nekreslí, jen ho přenáší — nastavit ho mohl jiný klient
            // a úprava popisku by ho jinak tiše vrátila pod médium.
            showCaptionAboveMedia = currentContent(chatId, messageId).showsCaptionAboveMedia(),
        )
            .toResult()
            .map { }

    override suspend fun editMedia(
        chatId: Long,
        messageId: Long,
        replacement: MediaReplacement,
        caption: String,
        formats: List<FormatSpan>,
    ): Result<Unit> {
        val formatted = caption.takeIf { it.isNotBlank() }
            ?.let { FormattedText(text = it, entities = detectEntities(it, formats = formats)) }
        val content = when (replacement) {
            is MediaReplacement.File -> replacement.attachment.toInputContent(caption = formatted)
            // GIF z výběru na serveru už je i s náhledem, jako při odeslání (`sendGif`).
            is MediaReplacement.Gif -> InputMessageAnimation(
                animation = InputAnimation(
                    animation = InputFileId(id = replacement.gif.animation.fileId),
                    thumbnail = null,
                    addedStickerFileIds = intArrayOf(),
                    duration = 0,
                    width = replacement.gif.animation.width,
                    height = replacement.gif.animation.height,
                ),
                caption = formatted,
                showCaptionAboveMedia = false,
                hasSpoiler = false,
            )
        }
        return client.editMessageMedia(
            chatId = chatId,
            messageId = messageId,
            replyMarkup = null,
            inputMessageContent = content,
        )
            .toResult()
            .map { }
    }

    /** Obsah zprávy, jak ho zná lokální databáze; `null`, když ji TDLib nevydá. */
    private suspend fun currentContent(chatId: Long, messageId: Long): MessageContent? =
        client.getMessage(chatId = chatId, messageId = messageId).toResult().getOrNull()?.content

    /**
     * Odkazy, zmínky a spol. v textu, který uživatel napsal, a k nim ruční formátování z pole.
     *
     * Bez toho by odkaz odešel jako mrtvý text: server si ho sám neoznačí. Když dohledání selže,
     * text odejde bez nich — přijít o odkaz je lepší než neodeslat vůbec. Ruční formátování
     * ([formats]) na dohledání nezávisí a odejde vždycky.
     *
     * **V kódu se nic nedohledává.** Kód nesmí obsahovat jiné entity (TDLib je z něj vyhodí)
     * a adresa napsaná do kódu má zůstat kódem, ne odkazem. **V odkazu s popiskem taky ne**:
     * adresa napsaná jako popisek by se jinak stala druhým odkazem přes tentýž text.
     */
    private suspend fun detectEntities(
        text: String,
        mentions: List<NameMention> = emptyList(),
        formats: List<FormatSpan> = emptyList(),
    ): Array<TextEntity> {
        // Úseky, do kterých dohledané entity nesmí: kód a odkaz s popiskem.
        val sealedRanges = formats.filter { it.format.family == TextFormat.Code || it.format == TextFormat.Link }
            .map { it.start until it.end }
        fun inSealed(offset: Int, length: Int) = sealedRanges.any { offset < it.last + 1 && offset + length > it.first }
        val detected = client.getTextEntities(text)
            .toResult()
            .map { it.entities.filterNotNull() }
            .getOrElse { failure ->
                logger.w(failure) { "Entity v odesílaném textu se nepodařilo dohledat." }
                emptyList()
            }
            .filterNot { inSealed(it.offset, it.length) }
        // Zmínky jménem pole nenese — pamatuje si je ViewModel a tady se dohledají v textu.
        val named = if (mentions.isEmpty()) emptyList() else nameMentionEntities(
            text = text,
            mentions = mentions,
            occupied = detected.map { it.offset until it.offset + it.length },
        )
            .filterNot { inSealed(it.offset, it.length) }
            .map { TextEntity(offset = it.offset, length = it.length, type = TextEntityTypeMentionName(userId = it.userId)) }
        return (detected + named + formats.toTdl(text)).sortedBy { it.offset }.toTypedArray()
    }

    override fun autoDeleteTime(chatId: Long): Flow<Int> =
        holder.updates { chatMessageAutoDeleteTimeUpdates }
            .filter { it.chatId == chatId }
            .map { it.messageAutoDeleteTime }
            // Odběr stojí dřív než první čtení, aby se změna mezi nimi neztratila.
            .onStart {
                client.getChat(chatId = chatId).toResult().getOrNull()?.let { emit(it.messageAutoDeleteTime) }
            }
            .distinctUntilChanged()

    override fun messageSender(chatId: Long): Flow<Long?> =
        holder.updates { chatMessageSenderUpdates }
            .filter { it.chatId == chatId }
            .map { it.messageSenderId?.plainId() }
            // Odběr stojí dřív než první čtení, aby se změna mezi nimi neztratila.
            .onStart {
                client.getChat(chatId = chatId).toResult().getOrNull()?.let { emit(it.messageSenderId?.plainId()) }
            }
            .distinctUntilChanged()

    override suspend fun availableMessageSenders(chatId: Long): Result<List<MessageSenderOption>> = runCatching {
        val senders = client.getChatAvailableMessageSenders(chatId = chatId).toResult().getOrThrow().senders.filterNotNull()
        // Na Premium se ptá, jen když je co zamknout — `getMe` je lokální, předplatné se ale mění.
        val isPremium = senders.none { it.needsPremium } || client.getMe().toResult().getOrNull()?.isPremium == true
        senders.mapNotNull { option ->
            val locked = option.needsPremium && !isPremium
            when (val sender = option.sender) {
                is MessageSenderUser -> {
                    val user = client.getUser(userId = sender.userId).toResult().getOrNull() ?: return@mapNotNull null
                    MessageSenderOption(
                        id = sender.userId,
                        title = listOf(user.firstName, user.lastName).filter { it.isNotBlank() }.joinToString(" "),
                        avatarFileId = user.profilePhoto?.small?.id,
                        kind = ChatKind.Private,
                        locked = locked,
                    )
                }
                is MessageSenderChat -> {
                    val chat = client.getChat(chatId = sender.chatId).toResult().getOrNull() ?: return@mapNotNull null
                    MessageSenderOption(
                        id = sender.chatId,
                        title = chat.title,
                        avatarFileId = chat.photo?.small?.id,
                        kind = chat.type.chatKind(),
                        locked = locked,
                        isChat = sender.chatId == chatId,
                    )
                }
            }
        }
    }

    override suspend fun setMessageSender(chatId: Long, senderId: Long): Result<Unit> =
        client.setChatMessageSender(chatId = chatId, messageSenderId = senderId.toMessageSender()).toResult().map { }

    override fun hasScheduledMessages(chatId: Long): Flow<Boolean> =
        // Naplánované zprávy patří celé skupině; v tématu by se míchaly cizí (jako ve vlákně).
        if (forumTopicId != null) flowOf(false) else holder.updates { chatHasScheduledMessagesUpdates }
            .filter { it.chatId == chatId }
            .map { it.hasScheduledMessages }
            // Odběr stojí dřív než první čtení, aby se změna mezi nimi neztratila.
            .onStart {
                client.getChat(chatId = chatId).toResult().getOrNull()?.let { emit(it.hasScheduledMessages) }
            }
            .distinctUntilChanged()

    override suspend fun scheduledMessages(chatId: Long): Result<List<ChatMessage>> {
        val isGroup = isGroup(chatId)
        return client.getChatScheduledMessages(chatId = chatId)
            .toResult()
            .map { messages ->
                messages.messages.filterNotNull()
                    .map { it.toChatMessage(isGroup) }
                    // „Až bude online" nemá čas, patří na začátek — odejde nejdřív, jak to jde.
                    .sortedBy { (it.schedule as? MessageSchedule.At)?.epochSeconds ?: 0L }
            }
    }

    override suspend fun reschedule(chatId: Long, messageId: Long, schedule: MessageSchedule?): Result<Unit> =
        client.editMessageSchedulingState(
            chatId = chatId,
            messageId = messageId,
            schedulingState = schedule?.toTdl(),
        ).toResult().map { }

    override suspend fun linkPreview(text: String, url: String?): Result<TextLinkPreview> {
        val formatted = FormattedText(text = text, entities = detectEntities(text))
        val links = formatted.entities.mapNotNull { entity ->
            when (val type = entity.type) {
                is TextEntityTypeUrl -> text.substring(entity.offset, entity.offset + entity.length)
                is TextEntityTypeTextUrl -> type.url
                else -> null
            }
        }.distinct()
        if (links.isEmpty()) return Result.success(TextLinkPreview(links = emptyList(), preview = null))
        // Odkaz, který v textu už není, by dal náhled něčeho, co zpráva neobsahuje.
        val chosen = url?.takeIf { it in links }
        return client.getLinkPreview(
            text = formatted,
            linkPreviewOptions = chosen?.let { LinkPreviewOptions(url = it).toTdl() },
        )
            .toResult()
            .map { it.toDomain() }
            // 404 = „text nemá náhled" (stránka nic nevydala). To je odpověď, ne chyba.
            .recoverCatching { failure ->
                if (failure is TdlThrowable && failure.code == NOT_FOUND) null else throw failure
            }
            .map { TextLinkPreview(links = links, preview = it) }
    }

    override suspend fun saveDraft(
        chatId: Long,
        text: String,
        replyToMessageId: Long?,
        linkPreview: LinkPreviewOptions?,
        quote: ReplyQuote?,
        formats: List<FormatSpan>,
    ): Result<Unit> = setDraft(chatId, topic, text, replyToMessageId, linkPreview, quote, formats)

    override suspend fun saveThreadDraft(
        chatId: Long,
        threadId: Long,
        text: String,
        replyToMessageId: Long?,
        linkPreview: LinkPreviewOptions?,
        quote: ReplyQuote?,
        formats: List<FormatSpan>,
    ): Result<Unit> = setDraft(
        chatId = chatId,
        topic = MessageTopicThread(messageThreadId = threadId),
        text = text,
        replyToMessageId = replyToMessageId,
        linkPreview = linkPreview,
        quote = quote,
        formats = formats,
    )

    private suspend fun setDraft(
        chatId: Long,
        topic: MessageTopic?,
        text: String,
        replyToMessageId: Long?,
        linkPreview: LinkPreviewOptions?,
        quote: ReplyQuote?,
        formats: List<FormatSpan>,
    ): Result<Unit> =
        client.setChatDraftMessage(
            chatId = chatId,
            topicId = topic,
            draftMessage = text.takeIf { it.isNotBlank() }?.let {
                DraftMessage(
                    replyTo = replyInput(chatId, replyToMessageId, quote),
                    date = Clock.System.now().epochSeconds.toInt(),
                    // Odkazy a zmínky se tu nedohledávají: koncept je rozepsaný text, označí se
                    // až při odeslání (viz `send`). Ruční formátování ale z textu poznat nejde,
                    // takže ho koncept nese — jinak by se po návratu do chatu ztratilo.
                    content = DraftMessageContentText(
                        text = FormattedText(text = it, entities = formats.toTdl(it).toTypedArray()),
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
                val forumTopic = forumTopic(chat.id)
                val savedTopic = savedTopic()
                val directTopic = directTopic(chat.id)
                val access = chatAccess(chat, forumTopic)
                val restriction = client.restrictionInfoOf(chat)
                val supergroup = (chat.type as? ChatTypeSupergroup)
                    ?.let { client.getSupergroup(supergroupId = it.supergroupId).toResult().getOrNull() }
                // V tématu koncept tématu; koncept skupiny do něj nepatří.
                val draft = when {
                    forumTopicId != null -> forumTopic?.draftMessage
                    directTopicId != null -> directTopic?.draftMessage
                    else -> chat.draftMessage
                }?.toSavedDraft()
                val calls = callTarget(chat)
                ChatInfo(
                    id = chat.id,
                    title = chat.title,
                    avatarFileId = chat.photo?.small?.id,
                    lastReadOutboxMessageId = chat.lastReadOutboxMessageId,
                    lastReadInboxMessageId = forumTopic?.lastReadInboxMessageId
                        ?: directTopic?.lastReadInboxMessageId
                        ?: chat.lastReadInboxMessageId,
                    unreadCount = forumTopic?.unreadCount ?: directTopic?.unreadCount?.toInt() ?: chat.unreadCount,
                    isGroup = chat.type is ChatTypeBasicGroup || chat.type is ChatTypeSupergroup,
                    isChannel = (chat.type as? ChatTypeSupergroup)?.isChannel == true,
                    // Uložené zprávy jsou soukromý chat s vlastním účtem. `getMe` je lokální,
                    // po přihlášení ho TDLib zná bez dotazu na server.
                    isSavedMessages = (chat.type as? ChatTypePrivate)?.userId
                        ?.let { it == client.getMe().toResult().getOrNull()?.id } == true,
                    draftText = draft?.text,
                    draftFormats = draft?.formats.orEmpty(),
                    canJoin = access.canJoin,
                    upgradedToChatId = upgradedTo(chat),
                    // Do tématu uloženého z jiného chatu TDLib odeslat nedovolí; Moje poznámky
                    // jsou Uložené zprávy bez tématu, tam se píše jako dřív.
                    writeAccess = if (savedTopicId != null && savedTopic?.kind != SavedTopicKind.MyNotes) {
                        WriteAccess.SavedTopic
                    } else {
                        access.writeAccess
                    },
                    content = access.content,
                    draftReplyToMessageId = draft?.replyToMessageId,
                    draftReplyQuote = draft?.quote,
                    draftLinkPreview = draft?.linkPreview,
                    verification = client.verificationOf(chat),
                    isSecret = chat.type is ChatTypeSecret,
                    avatarColorId = (chat.type as? ChatTypeSecret)?.userId ?: chat.id,
                    emojiStatus = chat.emojiStatus.statusEmojiId(),
                    restriction = restriction?.reasonText,
                    sensitive = client.hidesSensitive(restriction),
                    protectsContent = chat.hasProtectedContent,
                    topic = forumTopic?.info?.let {
                        TopicHeader(
                            id = it.forumTopicId,
                            name = it.name,
                            icon = it.icon.toDomain(),
                            isGeneral = it.isGeneral,
                            isClosed = it.isClosed,
                        )
                    },
                    savedTopic = savedTopic?.header,
                    callUserId = calls?.first,
                    callSupportsVideo = calls?.second == true,
                    isForum = supergroup?.isForum == true,
                    directTopic = directTopic?.let { topic -> directTopics?.topic(chat.id, topic.id)?.header },
                    directMessagesChatId = supergroup
                        ?.takeIf { it.isChannel && it.hasDirectMessagesGroup }
                        ?.let { client.getSupergroupFullInfo(supergroupId = it.id).toResult().getOrNull() }
                        ?.directMessagesChatId
                        ?.takeIf { it != 0L },
                    isDirectMessagesAdmin = supergroup?.isAdministeredDirectMessagesGroup == true,
                    isDirectMessages = supergroup?.isDirectMessagesGroup == true,
                )
            }

    /**
     * Komu z chatu zavolat a jestli i s videem: jen člověk v soukromém nebo tajném chatu, který
     * hovory přijímá. Nedostupné podrobnosti (bez sítě) hovor nezakazují — rozhodne server.
     */
    private suspend fun callTarget(chat: Chat): Pair<Long, Boolean>? {
        val userId = when (val type = chat.type) {
            is ChatTypePrivate -> type.userId
            is ChatTypeSecret -> type.userId
            else -> return null
        }
        if (userId == myId()) return null
        val user = client.getUser(userId = userId).toResult().getOrNull() ?: return null
        if (user.type !is UserTypeRegular) return null
        val full = client.getUserFullInfo(userId = userId).toResult().getOrNull()
        if (full?.canBeCalled == false) return null
        return userId to (full?.supportsVideoCalls != false)
    }


    /** Koncept, jak ho čte konverzace: chat, téma i vlákno komentářů ho nesou stejně. */
    private fun DraftMessage.toSavedDraft(): SavedDraft? {
        val text = toChatDraft()?.text ?: return null
        val reply = replyTo as? InputMessageReplyToMessage
        val content = content as? DraftMessageContentText
        return SavedDraft(
            text = text,
            formats = content?.text
                ?.let { draft -> draft.toBody().let { draftFormatsOf(it.text, it.entities) } }
                .orEmpty(),
            replyToMessageId = reply?.messageId,
            quote = reply?.quote?.let { ReplyQuote(text = it.text.text, position = it.position) },
            linkPreview = content?.linkPreviewOptions?.toDomain(),
        )
    }

    override suspend fun botIntro(chatId: Long): Result<BotIntro?> = runCatching {
        val chat = client.getChat(chatId = chatId).toResult().getOrThrow()
        val userId = (chat.type as? ChatTypePrivate)?.userId ?: return@runCatching null
        val user = client.getUser(userId = userId).toResult().getOrThrow()
        if (user.type !is UserTypeBot) return@runCatching null
        val info = client.getUserFullInfo(userId = userId).toResult().getOrNull()?.botInfo
        // Popis bot nastavuje jako holý text; odkazy, zmínky a příkazy v něm dohledá TDLib
        // lokálně (`getTextEntities`), stejně jako u popisu skupiny v profilu.
        val description = info?.description.orEmpty()
        val entities = description.takeIf { it.isNotBlank() }
            ?.let { client.getTextEntities(text = it).toResult().getOrNull()?.entities }
            ?: emptyArray()
        val body = FormattedText(text = description, entities = entities).toBody()
        BotIntro(
            description = body.text,
            descriptionEntities = body.entities,
            photo = info?.photo?.toIntroPicture(),
            animation = info?.animation?.toMedia(),
        )
    }

    override suspend fun startBot(chatId: Long, parameter: String): Result<ChatMessage> =
        // Soukromý chat s botem má id rovné id bota.
        client.sendBotStartMessage(botUserId = chatId, chatId = chatId, parameter = parameter)
            .toResult()
            .map { it.toChatMessage(isGroup = false) }

    override suspend fun botCommands(chatId: Long): Result<List<BotCommandItem>> = runCatching {
        val chat = client.getChat(chatId = chatId).toResult().getOrThrow()
        // Bez `else`: nový druh chatu v novější TDLib má rozbít překlad, ne tiše přijít o příkazy.
        when (val type = chat.type) {
            is ChatTypePrivate -> client.getUserFullInfo(userId = type.userId).toResult().getOrNull()
                ?.botInfo?.commands?.filterNotNull().orEmpty()
                .map { BotCommandItem(it.command, it.description, botCommandText(it.command, null, inGroup = false)) }
            // Tajný chat příkazy bota nemá — s botem tajný chat založit nejde.
            is ChatTypeSecret -> emptyList()
            is ChatTypeBasicGroup -> groupCommands(
                client.getBasicGroupFullInfo(basicGroupId = type.basicGroupId).toResult().getOrNull()?.botCommands,
            )
            is ChatTypeSupergroup -> if (type.isChannel) {
                emptyList()
            } else {
                groupCommands(
                    client.getSupergroupFullInfo(supergroupId = type.supergroupId).toResult().getOrNull()?.botCommands,
                )
            }
        }
    }

    /**
     * Tlačítko nabídky z plných údajů bota (po prvním dotazu lokální, jako příkazy).
     *
     * **Odkaz typu `t.me/bot/app` se otevírá jako odkaz** ([BotMenuApp.isLink]). TDLib u tlačítka
     * předepisuje: když je adresa interní odkaz na mini aplikaci (`InternalLinkTypeWebApp`), musí
     * se zpracovat jako ten odkaz (`getWebAppLinkUrl`), ne poslat do `openWebApp`.
     */
    override suspend fun botMenuApp(chatId: Long): Result<BotMenuApp?> = runCatching {
        val chat = client.getChat(chatId = chatId).toResult().getOrThrow()
        val userId = (chat.type as? ChatTypePrivate)?.userId ?: return@runCatching null
        val button = client.getUserFullInfo(userId = userId).toResult().getOrNull()?.botInfo?.menuButton
            ?: return@runCatching null
        if (button.url.isBlank() || button.text.isBlank()) return@runCatching null
        val linkType = client.getInternalLinkType(link = button.url).toResult().getOrNull()
        BotMenuApp(text = button.text, url = button.url, isLink = linkType is InternalLinkTypeWebApp)
    }

    override suspend fun shareUsersWithBot(
        chatId: Long,
        messageId: Long,
        buttonId: Int,
        userIds: List<Long>,
        onlyCheck: Boolean,
    ): Result<Unit> =
        client.shareUsersWithBot(
            source = KeyboardButtonSourceMessage(chatId = chatId, messageId = messageId),
            buttonId = buttonId,
            sharedUserIds = userIds.toLongArray(),
            onlyCheck = onlyCheck,
        ).toResult().map { }

    override suspend fun shareChatWithBot(
        chatId: Long,
        messageId: Long,
        buttonId: Int,
        sharedChatId: Long,
        onlyCheck: Boolean,
    ): Result<Unit> =
        client.shareChatWithBot(
            source = KeyboardButtonSourceMessage(chatId = chatId, messageId = messageId),
            buttonId = buttonId,
            sharedChatId = sharedChatId,
            onlyCheck = onlyCheck,
        ).toResult().map { }

    /**
     * Bot, kterému má zpráva jít jako mizející, nebo `null` (běžná zpráva). Jen ve skupině:
     * odpověď na mizející zprávu od bota jde zpátky jemu, příkaz s `isEphemeral` botovi, který
     * ho nabízí ([ephemeralCommandBot]). Plné údaje skupiny jsou po prvním dotazu lokální.
     */
    private suspend fun ephemeralReceiver(chatId: Long, text: String, replyToMessageId: Long?): Long? {
        if (replyToMessageId != null) {
            val replied = client.getMessage(chatId = chatId, messageId = replyToMessageId).toResult().getOrNull()
            if (replied != null && replied.receiverId != null && !replied.isOutgoing) {
                return (replied.senderId as? MessageSenderUser)?.userId
            }
        }
        if (!text.trimStart().startsWith("/")) return null
        val chat = client.getChat(chatId = chatId).toResult().getOrNull() ?: return null
        val bots = when (val type = chat.type) {
            is ChatTypeBasicGroup ->
                client.getBasicGroupFullInfo(basicGroupId = type.basicGroupId).toResult().getOrNull()?.botCommands
            is ChatTypeSupergroup -> if (type.isChannel) null else
                client.getSupergroupFullInfo(supergroupId = type.supergroupId).toResult().getOrNull()?.botCommands
            else -> null
        } ?: return null
        val usernames = bots.associate { bot ->
            bot.botUserId to client.getUser(userId = bot.botUserId).toResult().getOrNull()?.usernames?.editableUsername
        }
        return ephemeralCommandBot(text, bots.toList(), usernames::get)
    }

    /** Příkazy všech botů ve skupině, každý s jménem svého bota (viz [botCommandText]). */
    private suspend fun groupCommands(bots: Array<BotCommands>?): List<BotCommandItem> =
        bots.orEmpty().flatMap { bot ->
            val username = client.getUser(userId = bot.botUserId).toResult().getOrNull()?.usernames?.editableUsername
            bot.commands.filterNotNull().map {
                BotCommandItem(it.command, it.description, botCommandText(it.command, username, inGroup = true))
            }
        }

    /**
     * Jestli se do chatu smí psát a jestli se k němu jde přidat.
     *
     * `getSupergroup` / `getBasicGroup` jsou lokální — TDLib skupinu zná, když zná chat. Když
     * postavení zjistit nejde, psaní se NEBLOKUJE: pole navíc skončí nanejvýš chybou po odeslání,
     * chybějící pole by uživatele od chatu odřízlo.
     */
    private suspend fun chatAccess(chat: Chat, forumTopic: TdlForumTopic? = null): ChatAccess {
        val status = client.ownMemberStatus(chat)
        val supergroup = chat.type as? ChatTypeSupergroup
        // Diskusní skupina kanálu smí dovolit psaní i bez vstupu (`joinToSendMessages`). Dotaz je
        // lokální a ptá se jen ten, kdo členem není.
        val nonMembersCanSend = supergroup != null && !supergroup.isChannel &&
            status == OwnMemberStatus.Left &&
            client.getSupergroup(supergroupId = supergroup.supergroupId).toResult().getOrNull()
                ?.joinToSendMessages == false
        val chatWrite = writeAccess(
            isChannel = supergroup?.isChannel == true,
            status = status,
            membersCanSend = chat.permissions.canSendBasicMessages,
            nonMembersCanSend = nonMembersCanSend,
        )
        // Tajný chat: psát jde jen v dohodnutém stavu. Dotaz je lokální, stav drží TDLib.
        val secret = (chat.type as? ChatTypeSecret)?.let { type ->
            when (client.getSecretChat(secretChatId = type.secretChatId).toResult().getOrNull()?.state) {
                is SecretChatStatePending -> WriteAccess.SecretPending
                is SecretChatStateClosed -> WriteAccess.SecretClosed
                else -> null
            }
        }
        return ChatAccess(
            // Kdo smí psát bez vstupu, dostane pole, ne „Přidat se" — jako u oficiálních klientů.
            canJoin = supergroup != null && status == OwnMemberStatus.Left && !nonMembersCanSend,
            writeAccess = secret ?: topicWriteAccess(chatWrite, status, topicClosed = forumTopic?.info?.isClosed == true),
            content = contentAccess(status = status, groupDefaults = chat.permissions.toContentAccess()).let {
                // Právo skupiny nestačí: do soukromého chatu s člověkem anketa nejde vůbec,
                // seznam úkolů zase ne do kanálu. V tématu ani jedno: zakládá je obrazovka,
                // která o tématu neví.
                val kind = pollChatKind(chat)
                it.copy(
                    polls = it.polls && forumTopicId == null && pollsAllowedIn(kind),
                    checklists = it.checklists && forumTopicId == null && checklistsAllowedIn(kind),
                )
            },
        )
    }

    /** Nová skupina převedené základní skupiny ([ChatInfo.upgradedToChatId]); lokální dotaz. */
    private suspend fun upgradedTo(chat: Chat): Long? {
        val basic = chat.type as? ChatTypeBasicGroup ?: return null
        return client.getBasicGroup(basicGroupId = basic.basicGroupId).toResult().getOrNull()
            ?.upgradedToSupergroupId?.takeIf { it != 0L }?.let(::supergroupChatId)
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
        val secretChatId = (type as? ChatTypeSecret)?.secretChatId
        if (supergroupId == null && basicGroupId == null && secretChatId == null) return@flow
        emitAll(
            merge(
                // Tajný chat: protějšek se připojil, nebo chat ukončil.
                holder.updates { secretChatUpdates }.filter { it.secretChat.id == secretChatId }.map { },
                holder.updates { chatPermissionsUpdates }.filter { it.chatId == chatId }.map { },
                holder.updates { supergroupUpdates }.filter { it.supergroup.id == supergroupId }.map { },
                holder.updates { basicGroupUpdates }.filter { it.basicGroup.id == basicGroupId }.map { },
                // Uzavření a otevření tématu.
                holder.updates { forumTopicInfoUpdates }
                    .filter { it.info.chatId == chatId && it.info.forumTopicId == forumTopicId }
                    .map { },
            )
                // Update skupiny chodí i kvůli počtu členů; čte se znovu a pustí dál jen změna.
                .mapNotNull { client.getChat(chatId).toResult().getOrNull()?.let { chatAccess(it, forumTopic(chatId)) } }
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
        // Tajný chat má vlastní id; protějšek je uživatel z typu chatu.
        val userId = (client.getChat(chatId).toResult().getOrNull()?.type as? ChatTypeSecret)?.userId ?: chatId
        val initial = client.getUser(userId).toResult().getOrNull()?.status
        if (initial != null) emit(initial.toPresence())

        holder.updates { userStatusUpdates }
            .filter { it.userId == userId }
            .collect { emit(it.status.toPresence()) }
    }

    override suspend fun joinChat(chatId: Long): Result<Unit> =
        client.joinChat(chatId = chatId).toResult().map { }

    override suspend fun sponsoredMessages(chatId: Long): Result<List<SponsoredMessage>> =
        if (forumTopicId != null) Result.success(emptyList()) else client.getChatSponsoredMessages(chatId = chatId)
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
     * každým příspěvkem jen opakovalo hlavičku. **Přímé zprávy kanálu taky ne**: jsou to
     * supergroupy, ale baví se v nich dva — odběratel a kanál — jako v soukromém chatu.
     *
     * Volá se u každé stránky historie i nové zprávy, proto jen lokální `getChat`
     * a `getSupergroup`, ne celé [chatInfo].
     */
    private suspend fun isGroup(chatId: Long): Boolean {
        val chat = client.getChat(chatId = chatId).toResult().getOrNull() ?: return false
        return when (val type = chat.type) {
            is ChatTypeBasicGroup -> true
            is ChatTypeSupergroup -> !type.isChannel &&
                client.getSupergroup(supergroupId = type.supergroupId).toResult().getOrNull()?.isDirectMessagesGroup != true
            else -> false
        }
    }

    /**
     * Zpráva pro konverzaci, i s mizejícím obsahem ([Ephemeral]).
     *
     * Obsah, který bot nahradil jen pro uživatele (`ephemeralContent`), se kreslí MÍSTO běžného
     * — tak to TDLib předepisuje. Samostatná funkce nad [toChatMessageContent], ne větev v ní:
     * ta je obří suspend funkce, kterou další větev se suspend voláním už jednou rozbila v ART
     * (viz [externalReply]).
     */
    private suspend fun Message.toChatMessage(isGroup: Boolean): ChatMessage {
        val overlay = ephemeralContent
        val message = (if (overlay != null) withEphemeralContent(overlay) else this).toChatMessageContent(isGroup)
        val receiver = receiverId?.plainId()
        if (overlay == null && receiver == null) return message
        return message.copy(ephemeral = ephemeralOf(receiver, replacesOriginal = overlay != null))
    }

    /** Komu mizející zpráva patří: `receiverName` jen u cizího příjemce (vlastní příkaz botovi). */
    private suspend fun ephemeralOf(receiver: Long?, replacesOriginal: Boolean): Ephemeral {
        val other = receiver?.takeIf { it != myId() }
        return Ephemeral(
            receiverName = other?.let { resolveSender(it)?.title.orEmpty() },
            replacesOriginal = replacesOriginal,
        )
    }

    private suspend fun Message.toChatMessageContent(isGroup: Boolean): ChatMessage {
        val senderId = senderId.plainId()
        val hideSensitive = client.hidesSensitive(this)
        // Text a jeho entity musí pocházet z jednoho rozhodnutí — rozsahy platí jen pro ten
        // řetězec, ze kterého vznikly.
        val body = captionBody(fullArticle = cachedFullArticle(), hideSensitive = hideSensitive)

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
            senderVerification = sender?.verification,
            senderId = senderId,
            text = body.text,
            entities = body.entities,
            // Zpráva o členech se jmény („Pixel 5 opustil(a) skupinu"); bez jmen zůstane
            // bezejmenná věta z `serviceText`.
            // Totéž u úkolů v seznamu („Jana odškrtl(a) „Mléko"").
            description = namedMemberServiceText(myUserId = { myId() }) { resolveSender(it)?.title }
                ?: namedChecklistServiceText(
                    myUserId = { myId() },
                    nameOf = { resolveSender(it)?.title },
                    taskTexts = { checklistMessageId -> client.checklistTaskTexts(chatId, checklistMessageId) },
                )
                // A u ostatních oznámení, která někdo udělal („Jana změnil(a) název…").
                ?: namedActorServiceText(myUserId = { myId() }) { resolveSender(it)?.title }
                // A u sdílení s botem („Sdílel(a) jsi s botem „Výlet"").
                ?: content.namedSharedWithBotText { resolveSender(it)?.title }
                // A u připnutí („Jana připnul(a) „Sraz v 8"").
                ?: namedPinServiceText(
                    myUserId = { myId() },
                    nameOf = { resolveSender(it)?.title },
                    pinnedText = { pinnedId -> client.localMessageText(chatId, pinnedId) },
                )
                ?: body.description,
            media = messageMedia(
                hideSensitive = hideSensitive,
                // Uživatel sdíleného kontaktu: `getUser` je lokální, TDLib uživatele zná, když zná
                // zprávu, která ho sdílí. Samostatná funkce, ne výraz tady — viz [externalReply].
                contactUser = sharedContactUser(content),
                // Kanály soutěže a výherci z keše odesílatelů — ostatní zprávy na ni nesáhnou.
                senderName = { id -> resolveSender(id)?.title },
                story = ::sharedStory,
            ),
            linkPreview = linkPreview(hideSensitive),
            article = body.article,
            linkPreviewOptions = (content as? MessageText)?.linkPreviewOptions?.toDomain(),
            reactions = interactionInfo.toReactions(),
            postStats = interactionInfo.toPostStats(),
            hasUnreadReactions = unreadReactions.isNotEmpty(),
            newReactions = unreadReactions.toNewReactions(),
            hasUnreadPollVotes = containsUnreadPollVotes,
            threadId = (topicId as? MessageTopicThread)?.messageThreadId,
            schedule = schedulingState?.toSchedule(),
            hasTimestampedMedia = hasTimestampedMedia,
            sendState = if (outgoing) sendingState.toSendState() else null,
            sendFailure = (sendingState as? MessageSendingStateFailed)?.toSendFailure(),
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
            isService = content.serviceText(isChannelPost) != null,
            upgradeLink = content.upgradeLink(),
            serviceTargetMessageId = content.serviceTargetMessageId(),
            serviceOpen = content.serviceOpenTarget(),
            suggestedBirthdate = (content as? MessageSuggestBirthdate)?.takeIf { !outgoing }?.birthdate?.toDomain(),
            suggestedPhoto = (content as? MessageSuggestProfilePhoto)?.photo?.previewSize()
                ?.let { SuggestedPhoto(fileId = it.photo.id, canSet = !outgoing) },
            timestamp = date,
            isEdited = editDate > 0,
            mediaAlbumId = mediaAlbumId,
            selfDestructType = selfDestructType?.toSelfDestructType(),
            // Zlomky vteřiny nahoru: „0 s" by u běžícího odpočtu znamenalo, že už vypršel.
            selfDestructIn = selfDestructIn.takeIf { it > 0.0 }?.let { ceil(it).toInt() },
            forwardOrigin = forwardInfo?.origin?.toForwardOrigin(),
            autoDeleteAt = autoDeleteIn.takeIf { it > 0.0 }
                ?.let { Clock.System.now().epochSeconds + ceil(it).toLong() },
            viaBot = viaBotUserId.takeIf { it != 0L }?.let { botUsername(it) },
            // Ve skupině patří podpis (anonymní správce) k JMÉNU jako štítek; mimo skupinu je to
            // podpis příspěvku kanálu. Soukromý chat podpis nenese.
            authorSignature = authorSignature.ifBlank { null }?.takeIf { !isGroup },
            senderTag = (senderTag.ifBlank { null } ?: authorSignature.ifBlank { null })
                ?.takeIf { sender != null },
            senderBoostCount = senderBoostCount.takeIf { sender != null } ?: 0,
            effect = effectId.takeIf { it != 0L }?.let { messageEffect(it) },
            summaryLanguage = summaryLanguageCode.ifBlank { null },
            suggestedPost = suggestedPostInfo?.toDomain(),
            factCheck = factCheck?.let { check ->
                val body = check.text.toBody()
                FactCheck(body.text, body.entities, check.countryCode.ifBlank { null })
                    .takeIf { it.text.isNotBlank() }
            },
            keyboard = (replyMarkup as? ReplyMarkupInlineKeyboard)?.let { markup ->
                // Inline dotaz jde na bota, „přes" kterého zpráva přišla, jinak na odesílatele.
                // Dohledává se, jen když klávesnice takové tlačítko má.
                val switchesInline = markup.rows.any { row -> row.any { it?.type is InlineKeyboardButtonTypeSwitchInline } }
                val inlineBot = if (!switchesInline) null else viaBotUserId.takeIf { it != 0L }
                    // `senderId` je tu už číslo odesílatele (`plainId`): kladné = uživatel.
                    ?: senderId.takeIf { it > 0 }
                markup.toBotKeyboard(inlineBot?.let { botUsername(it) })
            },
            poll = (content as? MessagePoll)?.let { poll ->
                poll.toChatPoll().let { chatPoll ->
                    chatPoll.copy(
                        recentVoters = poll.poll.recentVoterIds.take(MAX_RECENT_VOTERS)
                            .mapNotNull { resolveSender(it.plainId()) },
                        options = if (chatPoll.isClosed) chatPoll.options else chatPoll.options.map { answer ->
                            answer.copy(canDelete = pollOptionDeletable(chatId, id, answer.id))
                        },
                    )
                }
            },
            checklist = (content as? MessageChecklist)?.toChatChecklist()?.let { checklist ->
                checklist.copy(
                    tasks = checklist.tasks.map { task ->
                        if (task.doneById == 0L) task else task.copy(doneBy = resolveSender(task.doneById))
                    },
                )
            },
            externalReply = externalReply(replyTo, chatId),
        ).let { message -> restrictionText?.let(message::restricted) ?: message }
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
        // Obsah z jiného chatu nese jen `origin`; příspěvek kanálu se pozná z něj.
        val description = if (text == null) {
            content?.previewTextOrNull(isOutgoing = false, isChannelPost = this.origin is MessageOriginChannel)
        } else {
            null
        }
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
            text = text.orEmpty(),
            entities = emptyList(),
            description = description,
            media = content?.replyMedia(),
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

    /**
     * Citace z jiného místa: zpráva z jiného chatu, nebo příběh. Odpověď na zprávu v tomtéž
     * chatu se kreslí z načtené historie (`replyToMessageId`), sem nepatří.
     *
     * Samostatná funkce, ne `when` přímo v [toChatMessage]: ta je obří suspend funkce a další
     * větev se suspend voláním v ní rozhodila typy registrů — ART ji odmítl při ověření
     * (`VerifyError … copy-reference … type=Conflict`) a aplikace spadla hned při startu.
     */
    private suspend fun externalReply(replyTo: MessageReplyTo?, chatId: Long): ExternalReply? =
        when (replyTo) {
            is MessageReplyToMessage -> replyTo.takeIf { it.chatId != chatId }?.toExternalReply()
            is MessageReplyToStory -> replyTo.toExternalReply()
            null -> null
        }

    /**
     * Odpověď na příběh (`MessageReplyToStory`) jako citace: „Příběh" s náhledem a jménem
     * autora. Obsah dočte [sharedStory] (s keší), takže se v chatu plném odpovědí na tentýž
     * příběh ptá jednou. Kam ťuknutí vede, rozhoduje konverzace podle [MessageMedia.Story] —
     * ven do chatu autora ne: otevře se příběh sám.
     */
    private suspend fun MessageReplyToStory.toExternalReply(): ExternalReply {
        val story = story(storyPosterChatId, storyId)
        val quote = ChatMessage(
            id = 0,
            isOutgoing = false,
            senderName = story.posterName,
            senderAvatarFileId = null,
            senderId = storyPosterChatId,
            text = "",
            entities = emptyList(),
            media = story,
            linkPreview = null,
            reactions = emptyList(),
            sendState = null,
            replyToMessageId = null,
            isService = false,
            timestamp = story.postedAt,
            isEdited = false,
        )
        return ExternalReply(quote = quote, target = null)
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

    override suspend fun sendKeyboardText(chatId: Long, text: String, replyToMessageId: Long?): Result<ChatMessage> {
        val replyTo = replyInput(chatId, replyToMessageId, quote = null)
        val content = InputMessageText(
            // Text tlačítka je příkaz pro bota, ne zpráva s odkazy — entity se nedohledávají.
            text = FormattedText(text = text, entities = emptyArray()),
            linkPreviewOptions = null,
            // Rozepsaný koncept zůstává: ťuknutí na tlačítko není odeslání pole.
            clearDraft = false,
        )
        // Příkaz ze seznamu příkazů jde tudy, ne přes [send] — i on může být mizející.
        sendEphemeralText(chatId, text, replyToMessageId, replyTo, content)?.let { return it }
        return client.sendMessage(
            chatId = chatId,
            topicId = topic,
            replyTo = replyTo,
            options = null,
            replyMarkup = null,
            inputMessageContent = content,
        )
            .toResult()
            .map { it.toChatMessage(isGroup(chatId)) }
    }

    /**
     * Mizející příkaz bota a odpověď na mizející zprávu bota smí jít JEN mizející cestou — běžná
     * zpráva by příkaz ukázala celé skupině a odpověď TDLib odmítne. `null` = běžná zpráva.
     */
    private suspend fun sendEphemeralText(
        chatId: Long,
        text: String,
        replyToMessageId: Long?,
        replyTo: InputMessageReplyTo?,
        content: InputMessageText,
    ): Result<ChatMessage>? {
        val botUserId = ephemeralReceiver(chatId, text, replyToMessageId) ?: return null
        return client.sendEphemeralMessage(
            chatId = chatId,
            topicId = topic,
            receiverUserId = botUserId,
            callbackQueryId = 0,
            replaceCallbackQueryMessage = false,
            replyTo = replyTo,
            protectContent = false,
            sendingId = 0,
            onlyPreview = false,
            replyMarkup = null,
            /*
             * Koncept se maže až po odeslání, ne přes `clearDraft`: mizející cesta ho smaže jen
             * v TDLib, na serveru zůstane a po restartu (i na jiném zařízení) se vrátí do pole.
             * Smazat ho potom ručně nejde — TDLib ho už nemá, změnu nevidí a server se nic
             * nedozví. S `clearDraft = false` koncept v TDLib ještě je a smazání dojde i na server.
             */
            inputMessageContent = InputMessageText(text = content.text, linkPreviewOptions = content.linkPreviewOptions, clearDraft = false),
        )
            .toResult()
            .onSuccess {
                if (content.clearDraft) client.setChatDraftMessage(chatId = chatId, topicId = topic, draftMessage = null)
            }
            .map { it.toChatMessage(isGroup(chatId)) }
    }

    override suspend fun shareOwnPhone(chatId: Long, replyToMessageId: Long?): Result<ChatMessage> = runCatching {
        val me = client.getMe().toResult().getOrThrow()
        client.sendMessage(
            chatId = chatId,
            topicId = topic,
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
        options: SendOptions,
    ): Result<ChatMessage> = runCatching {
        val isChannel = (client.getChat(chatId).toResult().getOrNull()?.type as? ChatTypeSupergroup)?.isChannel == true
        val correct = poll.correctAnswers
        client.sendMessage(
            chatId = chatId,
            topicId = topic,
            replyTo = replyInput(chatId, replyToMessageId, quote),
            options = options.toTdl(),
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
                    // Přidávat odpovědi jde jen do neanonymní ankety mimo kanál („not supported in
                    // channel chats and for anonymous polls").
                    InputPollTypeRegular(allowAddingOptions = poll.allowAddingOptions && !poll.anonymous && !isChannel)
                } else {
                    InputPollTypeQuiz(
                        // TDLib chce rostoucí seznam.
                        correctOptionIds = correct.sorted().toIntArray(),
                        explanation = FormattedText(text = poll.explanation, entities = emptyArray()),
                        explanationMedia = poll.explanationMedia?.toPollMedia(),
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

    /**
     * Smí uživatel odpověď smazat? `getPollOptionProperties` je offline dotaz, takže se ptá
     * u každé odpovědi otevřené ankety při převodu zprávy. Odpověď bez id (server ho ještě
     * nepřidělil) smazat nejde.
     */
    private suspend fun pollOptionDeletable(chatId: Long, messageId: Long, optionId: String): Boolean =
        optionId.isNotEmpty() &&
            client.getPollOptionProperties(chatId = chatId, messageId = messageId, pollOptionId = optionId)
                .toResult().getOrNull()?.canBeDeleted == true

    override suspend fun deletePollOption(chatId: Long, messageId: Long, optionId: String): Result<Unit> =
        client.deletePollOption(chatId = chatId, messageId = messageId, optionId = optionId).toResult().map { }

    override suspend fun stopPoll(chatId: Long, messageId: Long): Result<Unit> =
        client.stopPoll(chatId = chatId, messageId = messageId, replyMarkup = null).toResult().map { }

    override suspend fun addPollOption(chatId: Long, messageId: Long, text: String, media: PollDraftMedia?): Result<Unit> =
        client.addPollOption(
            chatId = chatId,
            messageId = messageId,
            option = InputPollOption(text = FormattedText(text = text, entities = emptyArray()), media = media?.toPollMedia()),
        ).toResult().map { }

    override suspend fun markChecklistTasks(chatId: Long, messageId: Long, done: List<Int>, notDone: List<Int>): Result<Unit> =
        client.markChecklistTasksAsDone(
            chatId = chatId,
            messageId = messageId,
            markedAsDoneTaskIds = done.toIntArray(),
            markedAsNotDoneTaskIds = notDone.toIntArray(),
        ).toResult().map { }

    override suspend fun addChecklistTasks(chatId: Long, messageId: Long, tasks: List<String>): Result<Unit> = runCatching {
        // Id nového úkolu volí klient a musí být v seznamu jedinečné; bere se za nejvyšším.
        val message = client.getMessage(chatId = chatId, messageId = messageId).toResult().getOrThrow()
        val existing = (message.content as? MessageChecklist)?.list?.tasks?.maxOfOrNull { it.id } ?: 0
        client.addChecklistTasks(
            chatId = chatId,
            messageId = messageId,
            tasks = tasks.mapIndexed { index, text -> checklistTask(existing + index + 1, text) }.toTypedArray(),
        ).toResult().getOrThrow()
    }

    override suspend fun sendChecklist(
        chatId: Long,
        checklist: ChecklistDraft,
        replyToMessageId: Long?,
        quote: ReplyQuote?,
        options: SendOptions,
    ): Result<ChatMessage> = runCatching {
        client.sendMessage(
            chatId = chatId,
            topicId = topic,
            replyTo = replyInput(chatId, replyToMessageId, quote),
            options = options.toTdl(),
            replyMarkup = null,
            inputMessageContent = InputMessageChecklist(checklist = checklist.toInput()),
        ).toResult().getOrThrow().toChatMessage(isGroup(chatId))
    }

    override suspend fun editChecklist(chatId: Long, messageId: Long, checklist: ChecklistDraft): Result<Unit> = runCatching {
        // Formulář nese jen holý text. Co uživatel nezměnil, si podrží formátování ze zprávy —
        // úprava jednoho úkolu nesmí potichu smazat tučné písmo u ostatních.
        val current = (client.getMessage(chatId = chatId, messageId = messageId).toResult().getOrNull()?.content as? MessageChecklist)?.list
        client.editMessageChecklist(
            chatId = chatId,
            messageId = messageId,
            replyMarkup = null,
            checklist = checklist.toInput(current),
        ).toResult().getOrThrow()
    }

    /**
     * Id úkolů volí klient: kladná a v seznamu jedinečná ([resolvedTaskIds]). Text, který se
     * oproti [current] nezměnil, jde i s jeho entitami.
     */
    private fun ChecklistDraft.toInput(current: Checklist? = null): InputChecklist {
        val ids = resolvedTaskIds()
        val currentTasks = current?.tasks?.associateBy { it.id }.orEmpty()
        return InputChecklist(
            title = current?.title?.takeIf { it.text == title } ?: FormattedText(text = title, entities = emptyArray()),
            tasks = tasks.mapIndexed { index, text ->
                val kept = currentTasks[ids[index]]?.text?.takeIf { it.text == text }
                if (kept != null) InputChecklistTask(id = ids[index], text = kept) else checklistTask(ids[index], text)
            }.toTypedArray(),
            othersCanAddTasks = othersCanAddTasks,
            othersCanMarkTasksAsDone = othersCanMarkTasksAsDone,
        )
    }

    private fun checklistTask(id: Int, text: String) =
        InputChecklistTask(id = id, text = FormattedText(text = text, entities = emptyArray()))

    override suspend fun checklistLimits(): ChecklistLimits {
        suspend fun option(name: String) =
            (client.getOption(name = name).toResult().getOrNull() as? OptionValueInteger)?.value?.toInt()?.takeIf { it > 0 }
        val limits = ChecklistLimits(
            maxTitle = option("checklist_title_length_max") ?: DEFAULT_CHECKLIST_TITLE_MAX,
            maxTasks = option("checklist_task_count_max") ?: DEFAULT_CHECKLIST_TASK_COUNT_MAX,
            maxTaskText = option("checklist_task_text_length_max") ?: DEFAULT_CHECKLIST_TASK_TEXT_MAX,
        )
        logger.d { "Meze seznamu úkolů od serveru: $limits" }
        return limits
    }

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

    /** `when` bez `else`: nový druh odpovědi v novější TDLib má rozbít překlad. */
    override suspend fun loginUrlInfo(chatId: Long, messageId: Long, buttonId: Long): Result<LoginUrlPrompt> = runCatching {
        when (val info = client.getLoginUrlInfo(chatId = chatId, messageId = messageId, buttonId = buttonId).toResult().getOrThrow()) {
            is LoginUrlInfoOpen -> LoginUrlPrompt.Open(url = info.url, skipConfirmation = info.skipConfirmation)
            is LoginUrlInfoRequestConfirmation -> LoginUrlPrompt.Confirm(
                url = info.url,
                domain = info.domain,
                botName = client.getUser(userId = info.botUserId).toResult().getOrNull()
                    ?.let { user ->
                        user.usernames?.publicName()?.let { "@$it" }
                            ?: listOf(user.firstName, user.lastName).filter { it.isNotBlank() }.joinToString(" ")
                    }
                    .orEmpty(),
                requestWriteAccess = info.requestWriteAccess,
            )
        }
    }.onFailure { logger.w(it) { "Přihlášení z tlačítka ve zprávě $messageId nejde zjistit." } }

    override suspend fun loginUrl(chatId: Long, messageId: Long, buttonId: Long, allowWriteAccess: Boolean): Result<String> =
        client.getLoginUrl(chatId = chatId, messageId = messageId, buttonId = buttonId, allowWriteAccess = allowWriteAccess)
            .toResult()
            .map { it.url }
            .onFailure { logger.w(it) { "Přihlašovací adresu z tlačítka ve zprávě $messageId nejde získat." } }

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
    /**
     * Veřejné jméno inline bota u zprávy „přes @bot". Lokální dotaz, ale v historii se tentýž
     * bot opakuje u každé zprávy, takže se pamatuje — pro jednu instanci klienta, jako odesílatelé.
     * Bot bez veřejného jména (nemělo by nastat) = `null`, a značka se neukáže.
     */
    private suspend fun botUsername(userId: Long): String? = botUsernamesMutex.withLock {
        val current = client
        val cache = botUsernames?.takeIf { it.first === current }?.second
            ?: mutableMapOf<Long, String?>().also { botUsernames = current to it }
        if (userId in cache) return@withLock cache[userId]
        val username = current.getUser(userId = userId).toResult().getOrNull()
            ?.usernames?.publicName()
        cache[userId] = username
        username
    }

    /** Jméno, pod kterým bot vystupuje: upravitelné, jinak první aktivní (sběratelské). */
    private fun Usernames.publicName(): String? =
        editableUsername.ifBlank { null } ?: activeUsernames.firstOrNull { !it.isNullOrBlank() }

    override suspend fun story(posterChatId: Long, storyId: Int): MessageMedia.Story =
        MessageStory(storyPosterChatId = posterChatId, storyId = storyId, viaMention = false)
            .toMedia(sharedStory(posterChatId, storyId)) { id -> resolveSender(id)?.title }

    override suspend fun fullArticle(chatId: Long, messageId: Long): Result<ChatMessage> {
        val message = client.getMessage(chatId = chatId, messageId = messageId).toResult()
            .getOrElse { return Result.failure(it) }
        val content = message.content
        if (content is MessageRichMessage && !content.message.isFull && message.cachedFullArticle() == null) {
            val full = client.getFullRichMessage(chatId = chatId, messageId = messageId).toResult()
                .getOrElse { return Result.failure(it) }
            fullArticlesMutex.withLock { fullArticleCache()[message.articleKey()] = full }
        }
        return Result.success(message.toChatMessage(isGroup(chatId)))
    }

    /**
     * Dočtený celý článek k téhle zprávě, nebo `null`. Klíčem je i čas úpravy — upravený článek
     * je jiný a starý celý text by ho přebil.
     */
    private suspend fun Message.cachedFullArticle(): RichMessage? {
        val content = content as? MessageRichMessage ?: return null
        if (content.message.isFull) return null
        return fullArticlesMutex.withLock { fullArticleCache()[articleKey()] }
    }

    private fun Message.articleKey() = ArticleKey(chatId, id, editDate)

    /** Volá se pod [fullArticlesMutex]. Pro jednu instanci klienta, jako ostatní keše. */
    private fun fullArticleCache(): MutableMap<ArticleKey, RichMessage> {
        val current = client
        return fullArticles?.takeIf { it.first === current }?.second
            ?: mutableMapOf<ArticleKey, RichMessage>().also { fullArticles = current to it }
    }

    private data class ArticleKey(val chatId: Long, val messageId: Long, val editDate: Int)

    /**
     * Celé články dočtené přes `getFullRichMessage`. Dlouhý článek posílá TDLib useknutý
     * (`RichMessage.isFull`); dočtený se pamatuje, aby se při každém novém převodu zprávy
     * (přečtení, reakce, návrat do chatu) nevrátil začátek.
     */
    private var fullArticles: Pair<TdlClient, MutableMap<ArticleKey, RichMessage>>? = null
    private val fullArticlesMutex = Mutex()

    private var botUsernames: Pair<TdlClient, MutableMap<Long, String?>>? = null
    private val botUsernamesMutex = Mutex()

    /**
     * Efekt zprávy podle id. Efektů je pár desítek a v chatu se opakují, takže se pamatují —
     * pro jednu instanci klienta: `fileId` animace platí jen v databázi, ze které přišel.
     * Efekt, který server nevydá (zrušený, nedostupný), je `null` a zpráva se ukáže bez něj.
     */
    private suspend fun messageEffect(effectId: Long): MessageEffect? = effectsMutex.withLock {
        val current = client
        val cache = effects?.takeIf { it.first === current }?.second
            ?: mutableMapOf<Long, MessageEffect?>().also { effects = current to it }
        if (effectId in cache) return@withLock cache[effectId]
        val effect = current.getMessageEffect(effectId = effectId).toResult()
            .onFailure { logger.w(it) { "Efekt zprávy $effectId se nepodařilo načíst" } }
            .getOrNull()
            ?.toMessageEffect()
        cache[effectId] = effect
        effect
    }

    override suspend fun sendableEffects(ids: List<Long>): List<MessageEffect> {
        val all = ids.mapNotNull { messageEffect(it) }
        // Na Premium se ptá, jen když je co odfiltrovat — a pokaždé znovu, předplatné se mění.
        val isPremium = all.none { it.needsPremium } ||
            client.getMe().toResult().getOrNull()?.isPremium == true
        return all.filter { isPremium || !it.needsPremium }
    }

    private var effects: Pair<TdlClient, MutableMap<Long, MessageEffect?>>? = null
    private val effectsMutex = Mutex()

    /**
     * Sdílený příběh pro kartu ve zprávě. Napřed lokálně (TDLib příběh ze zprávy obvykle zná),
     * pak ze serveru. Pamatuje se pro instanci klienta jako efekt — zpráva se převádí znovu při
     * každém přečtení a reakci, a dotaz na server kvůli tomu by byl zbytečný. **Pamatuje se
     * i „není"**, ale jen když to řekl server (chyba 4xx — vypršelý příběh je 404, autor, který
     * příběhy nikdy neměl, 400 `STORIES_NEVER_CREATED`): výpadek spojení příběh nezruší a příště
     * se zkusí znovu.
     *
     * **Dotaz na server má strop** ([STORY_FETCH_TIMEOUT]). Převod zprávy je součást načítání
     * historie a TDLib bez spojení na síť čeká — bez stropu by jeden sdílený příběh zdržel celou
     * konverzaci. Nedočkaný příběh se ukáže jako nedostupný a s další úpravou zprávy se zkusí znovu.
     */
    private suspend fun sharedStory(posterChatId: Long, storyId: Int): Story? = storiesMutex.withLock {
        val current = client
        val cache = stories?.takeIf { it.first === current }?.second
            ?: mutableMapOf<Pair<Long, Int>, Story?>().also { stories = current to it }
        val key = posterChatId to storyId
        if (key in cache) return@withLock cache[key]
        current.getStory(storyPosterChatId = posterChatId, storyId = storyId, onlyLocal = true).toResult().getOrNull()
            ?.let { return@withLock it.also { story -> cache[key] = story } }
        val remote = withTimeoutOrNull(STORY_FETCH_TIMEOUT) {
            current.getStory(storyPosterChatId = posterChatId, storyId = storyId, onlyLocal = false).toResult()
        } ?: return@withLock null
        remote
            .onSuccess { cache[key] = it }
            .onFailure { failure ->
                if (failure is TdlThrowable && failure.code in CLIENT_ERRORS) {
                    cache[key] = null
                } else {
                    logger.w(failure) { "Příběh $posterChatId/$storyId se nepodařilo načíst" }
                }
            }
            .getOrNull()
    }

    private var stories: Pair<TdlClient, MutableMap<Pair<Long, Int>, Story?>>? = null
    private val storiesMutex = Mutex()

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
    private suspend fun MessageInteractionInfo?.toPostStats(): PostStats? {
        this ?: return null
        return PostStats(
            views = viewCount,
            forwards = forwardCount,
            comments = replyInfo?.replyCount,
            commenters = replyInfo?.recentReplierIds.orEmpty().take(MAX_RECENT_COMMENTERS)
                .mapNotNull { resolveSender(it.plainId()) },
        ).takeUnless { it.isEmpty }
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

    override suspend fun reactionDetails(chatId: Long, messageId: Long, reaction: String?): Result<List<ReactionDetail>> {
        val added = client
            .getMessageAddedReactions(
                chatId = chatId,
                messageId = messageId,
                // Filtruje SERVER: detail je jedna stránka, takže filtr nad ní by u příspěvku
                // s tisíci reakcemi ukázal jen zlomek těch s vybraným emoji. Bez filtru všechny
                // reakce pohromadě, od nejnovější.
                reactionType = reaction?.toReactionType(),
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

    override suspend fun giveawayStatus(chatId: Long, messageId: Long): Result<GiveawayStatus> =
        client.getGiveawayInfo(chatId = chatId, messageId = messageId).toResult().map { it.toStatus() }

    override suspend fun summarizeMessage(chatId: Long, messageId: Long, languageCode: String?): Result<MessageBody> =
        client.summarizeMessage(
            chatId = chatId,
            messageId = messageId,
            translateToLanguageCode = languageCode.orEmpty(),
            // Prázdný = výchozí tón; volbu tónu aplikace nenabízí, stejně jako u překladu.
            tone = "",
        )
            .toResult()
            .map { it.toBody() }

    override suspend fun translateMessage(chatId: Long, messageId: Long, languageCode: String): Result<MessageBody> =
        client.translateMessageText(
            chatId = chatId,
            messageId = messageId,
            toLanguageCode = languageCode,
            // Prázdný = výchozí tón překladu; volbu tónu aplikace nenabízí.
            tone = "",
        )
            .toResult()
            .map { it.toBody() }

    override suspend fun mentionCandidates(chatId: Long, query: String): Result<List<MentionCandidate>> {
        val me = myId()
        return client.searchChatMembers(
            chatId = chatId,
            query = query,
            limit = MENTION_LIMIT,
            // Jen ti, které jde v chatu zmínit; bez tématu (fóra zatím nejsou).
            filter = ChatMembersFilterMention(topicId = topic),
        )
            .toResult()
            .map { found ->
                found.members.filterNotNull()
                    .mapNotNull { (it.memberId as? MessageSenderUser)?.userId }
                    .distinct()
                    .filter { it != me }
                    .mapNotNull { userId -> mentionCandidate(userId) }
            }
    }

    override suspend fun recentInlineBots(): Result<List<MentionCandidate>> =
        client.getRecentInlineBots()
            .toResult()
            .map { users -> users.userIds.toList().mapNotNull { userId -> mentionCandidate(userId) }.filter { it.username != null } }

    override suspend fun recentHashtags(prefix: String): Result<List<String>> =
        client.searchHashtags(prefix = prefix, limit = HASHTAG_LIMIT)
            .toResult()
            // Bez `#` jako prefix, a pro jistotu i ve výsledku — vkládá se s ním obrazovka.
            .map { found -> found.hashtags.map { it.removePrefix("#") }.filter { it.isNotEmpty() }.distinct() }

    override suspend fun removeRecentHashtag(hashtag: String): Result<Unit> =
        client.removeRecentHashtag(hashtag = hashtag.removePrefix("#")).toResult().map { }

    /** Uživatel jako kandidát zmínky; smazaný účet (bez jména) `null`. */
    private suspend fun mentionCandidate(userId: Long): MentionCandidate? {
        val user = client.getUser(userId = userId).toResult().getOrNull() ?: return null
        val name = listOf(user.firstName, user.lastName).filter { it.isNotBlank() }.joinToString(" ")
        // Smazaný účet jméno nemá a zmínit ho nemá smysl.
        if (name.isBlank()) return null
        return MentionCandidate(
            userId = userId,
            name = name,
            firstName = user.firstName.ifBlank { name },
            username = user.usernames?.publicName(),
            avatarFileId = user.profilePhoto?.small?.id,
        )
    }

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

    /**
     * Nepřečtené reakce v doménové podobě, s odesílatelem z keše. Placená reakce klíč nemá
     * (`toReactionKey`) a vypadne, stejně jako v čipech.
     */
    private suspend fun Array<UnreadReaction>.toNewReactions(): List<NewReaction> = mapNotNull { reaction ->
        reaction.type.toReactionKey()?.let { key -> NewReaction(key, resolveSender(reaction.senderId.plainId())) }
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
                verification = user.verificationStatus.toVerification(),
            )
        } else {
            // Odesílatel může být kanál (MessageSenderChat), ne uživatel — getUser pak selže.
            val chat = client.getChat(senderId).toResult().getOrNull() ?: return null
            SenderInfo(
                id = senderId,
                title = chat.title,
                avatarFileId = chat.photo?.small?.id,
                verification = client.verificationOf(chat),
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
/**
 * Texty úkolů ze zprávy se seznamem podle id, jen z lokální databáze (`getMessageLocally`):
 * servisní věta se skládá při převodu každé zprávy a kvůli ní se nemá čekat na server. Když
 * seznam lokálně není, věta úkol jen spočítá.
 */
internal suspend fun TdlClient.checklistTaskTexts(chatId: Long, messageId: Long): Map<Int, String>? =
    (getMessageLocally(chatId = chatId, messageId = messageId).toResult().getOrNull()?.content as? MessageChecklist)
        ?.list?.tasks?.associate { it.id to it.text.text }

internal fun MessageSender.plainId(): Long = when (this) {
    is MessageSenderUser -> userId
    is MessageSenderChat -> chatId
}

/**
 * Převod stavu odeslání.
 *
 * `null` znamená, že zpráva je na serveru — TDLib pole vyplňuje jen dokud je rozepsaná
 * nebo selhala.
 */
/**
 * Proč zpráva neodešla. `needAnotherSender` a `needDropReply` opakování nebrání — TDLib při
 * něm sám vezme výchozího odesílatele, resp. zahodí odpověď (`MessagesManager::resend_messages`).
 */
internal fun MessageSendingStateFailed.toSendFailure(): SendFailure = SendFailure(
    canRetry = canRetry && !needAnotherReplyQuote && requiredPaidMessageStarCount == 0L,
    reason = errorText(error.message, Res.string.conversation_send_failed_unknown),
)

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
internal fun MessageContent.quotableText(): FormattedText? = when (this) {
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

/** Živých poloh v chatu na jeden dotaz — jedna na člověka, takže sto stačí i velké skupině. */
private const val RECENT_LOCATIONS_LIMIT = 100

/** Výsledků hledání v chatu na stránku; další se dotahují, až k nim uživatel došipkuje. */
private const val IN_CHAT_SEARCH_PAGE = 50

/** Kolik nepřečtených zmínek nebo reakcí se načte, než se z nich vybere nejstarší. */
private const val UNREAD_MARKS_PAGE = 100

/** Kolik členů vrátí jedno hledání pro filtr podle odesílatele; víc se na obrazovku nevejde. */
private const val MEMBER_SEARCH_LIMIT = 50

/** Kolik členů napovídá zmínka. Víc se do panelu nad polem stejně nevejde, dotaz se zpřesní psaním. */
private const val MENTION_LIMIT = 20

/** Strop stránek připnutých zpráv. Pět set připnutých v jednom chatu je víc než dost. */
private const val MAX_PINNED_PAGES = 5

/** Práva na druhy obsahu z `ChatPermissions`. Bere se jen to, co aplikace umí poslat. */
private fun ChatPermissions.toContentAccess(): ContentAccess = ContentAccess(
    photos = canSendPhotos,
    videos = canSendVideos,
    documents = canSendDocuments,
    audios = canSendAudios,
    voiceNotes = canSendVoiceNotes,
    videoNotes = canSendVideoNotes,
    stickers = canSendOtherMessages,
    polls = canSendPolls,
    // „True, if the user can send polls and checklists" — jedno právo pro obojí.
    checklists = canSendPolls,
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
        canInviteUsers = rights.canInviteUsers,
        canManageTopics = rights.canManageTopics,
        canDeleteMessages = rights.canDeleteMessages,
    )
    is ChatMemberStatusMember -> OwnMemberStatus.Member
    is ChatMemberStatusRestricted -> OwnMemberStatus.Restricted(
        isMember = isMember,
        canSendMessages = permissions.canSendBasicMessages,
        content = permissions.toContentAccess(),
        canChangeInfo = permissions.canChangeInfo,
        canInviteUsers = permissions.canInviteUsers,
        canCreateTopics = permissions.canCreateTopics,
    )
    is ChatMemberStatusLeft -> OwnMemberStatus.Left
    is ChatMemberStatusBanned -> OwnMemberStatus.Banned
}

/** Zpráv na stránku sdíleného obsahu. Mřížka jich na obrazovku vezme pár desítek. */
/** Kolik řídkých pozic chtít; víc server nevydá (`getChatSparseMessagePositions`: 50–2000). */
private const val SPARSE_POSITIONS_LIMIT = 2000

private const val SHARED_MEDIA_PAGE = 50

/** Kolik hlasujících se načte naráz; víc TDLib nevydá (strop je 50). */
private const val POLL_VOTERS_PAGE = 50

/** Když server strop počtu odpovědí nepošle; dlouho platná hodnota Telegramu. */
private const val DEFAULT_POLL_ANSWER_COUNT_MAX = 10

/** Filtr `searchChatMessages` pro druh sdíleného obsahu. */
private fun SharedMediaKind.toFilter(): SearchMessagesFilter = when (this) {
    SharedMediaKind.Media -> SearchMessagesFilterPhotoAndVideo()
    SharedMediaKind.Files -> SearchMessagesFilterDocument()
    SharedMediaKind.Music -> SearchMessagesFilterAudio()
    SharedMediaKind.Links -> SearchMessagesFilterUrl()
    SharedMediaKind.Gifs -> SearchMessagesFilterAnimation()
    SharedMediaKind.VideoNotes -> SearchMessagesFilterVideoNote()
    SharedMediaKind.Voice -> SearchMessagesFilterVoiceNote()
}

/**
 * Fotka nebo video, které v chatu zůstávají.
 *
 * Mizející a tajná se vyřazují: přehled sdílených médií je trvalý seznam a obsah, který má po
 * zhlédnutí zmizet, do něj nepatří — stejně jako se nedostane do listování v prohlížeči.
 */
private val ChatMessage.hasArticleVisualMedia: Boolean
    get() = article?.media.orEmpty().any { it.media is MessageMedia.Photo || it.media is MessageMedia.Video }

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
/** Kolik dříve použitých hashtagů se napoví; víc se nad polem stejně nevejde. */
private const val HASHTAG_LIMIT = 20

private const val NOT_FOUND = 404

/** Odpovědi serveru, které říkají „takový příběh není" — na rozdíl od výpadku spojení. */
private val CLIENT_ERRORS = 400..499

/** Jak dlouho smí převod zprávy čekat na sdílený příběh ze serveru. */
private val STORY_FETCH_TIMEOUT = 3.seconds

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

/**
 * Volba uložená v konceptu zpátky v doménové podobě. Adresa vybraného odkazu se přenáší taky;
 * jestli v textu pořád stojí, ověří až první náhled ([LinkPreviewOptions.url]).
 */
internal fun TdlLinkPreviewOptions.toDomain(): LinkPreviewOptions? =
    LinkPreviewOptions(
        disabled = isDisabled,
        url = url.takeIf { it.isNotEmpty() },
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
private fun ReplyMarkupInlineKeyboard.toBotKeyboard(inlineBot: String?): BotKeyboard? {
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
                    is InlineKeyboardButtonTypeWebApp -> BotButtonKind.WebApp(type.url)
                    is InlineKeyboardButtonTypeLoginUrl -> BotButtonKind.LoginUrl(type.url, type.id)
                    is InlineKeyboardButtonTypeSwitchInline -> type.toSwitchInline(inlineBot)
                        ?: BotButtonKind.Unsupported(UnsupportedBotButton.SwitchInline)
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
 * Tlačítko s inline dotazem. `null` = Plume ho neumí: bot bez jména (dotaz by nešlo napsat)
 * a cíl daný odkazem (`TargetChatInternalLink`), který by chtěl odkaz nejdřív otevřít.
 */
private fun InlineKeyboardButtonTypeSwitchInline.toSwitchInline(bot: String?): BotButtonKind.SwitchInline? {
    bot ?: return null
    val target = when (val chat = targetChat) {
        is TargetChatCurrent -> SwitchInlineTarget.Current
        is TargetChatChosen -> SwitchInlineTarget.Chosen(
            users = chat.types.allowUserChats,
            bots = chat.types.allowBotChats,
            groups = chat.types.allowGroupChats,
            channels = chat.types.allowChannelChats,
        )
        is TargetChatInternalLink -> return null
    }
    return BotButtonKind.SwitchInline(bot = bot, query = query, target = target)
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
                        is KeyboardButtonTypeRequestLocation -> ReplyKeyboardButtonKind.Location
                        is KeyboardButtonTypeRequestPoll -> ReplyKeyboardButtonKind.Poll(
                            when {
                                type.forceQuiz -> PollComposerMode.QuizOnly
                                type.forceRegular -> PollComposerMode.RegularOnly
                                else -> PollComposerMode.Any
                            },
                        )
                        is KeyboardButtonTypeRequestUsers -> ReplyKeyboardButtonKind.Users(
                            BotUsersRequest(
                                buttonId = type.id,
                                bots = type.userIsBot.takeIf { type.restrictUserIsBot },
                                maxQuantity = type.maxQuantity.coerceAtLeast(1),
                            ),
                        )
                        is KeyboardButtonTypeRequestChat -> ReplyKeyboardButtonKind.Chat(
                            BotChatRequest(
                                buttonId = type.id,
                                channel = type.chatIsChannel,
                                forum = type.chatIsForum.takeIf { type.restrictChatIsForum },
                                botIsMember = type.botIsMember,
                                botAdministrator = type.botAdministratorRights != null,
                            ),
                        )
                        is KeyboardButtonTypeWebApp -> ReplyKeyboardButtonKind.WebApp(type.url)
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
internal fun ReactionType.toReactionKey(): String? = when (this) {
    is ReactionTypeEmoji -> emoji
    is ReactionTypeCustomEmoji -> customEmojiReaction(customEmojiId)
    is ReactionTypePaid -> null
}

internal fun String.toReactionType(): ReactionType =
    customEmojiIdOf(this)?.let { ReactionTypeCustomEmoji(customEmojiId = it) } ?: ReactionTypeEmoji(emoji = this)

/** Stojí popisek téhle přílohy nad médiem? Jen fotka, video, GIF a placené médium to umí. */
internal fun MessageContent?.showsCaptionAboveMedia(): Boolean = when (this) {
    is MessagePhoto -> showCaptionAboveMedia
    is MessageVideo -> showCaptionAboveMedia
    is MessageAnimation -> showCaptionAboveMedia
    is MessagePaidMedia -> showCaptionAboveMedia
    else -> false
}

/**
 * Ruční formátování z pole jako entity TDLib. Rozsahy mimo text se ořežou — server by celou
 * zprávu odmítl kvůli jednomu špatnému offsetu.
 */
internal fun List<FormatSpan>.toTdl(text: String): List<TextEntity> = mapNotNull { span ->
    // Odkaz bez adresy by server odmítl i s celou zprávou.
    if (span.format == TextFormat.Link && span.url.isNullOrEmpty()) return@mapNotNull null
    if (span.format == TextFormat.CustomEmoji && span.customEmojiId == null) return@mapNotNull null
    val start = span.start.coerceIn(0, text.length)
    val end = span.end.coerceIn(start, text.length)
    if (end > start) TextEntity(offset = start, length = end - start, type = span.toTdl()) else null
}

private fun FormatSpan.toTdl(): TextEntityType = when (format) {
    TextFormat.Bold -> TextEntityTypeBold()
    TextFormat.Italic -> TextEntityTypeItalic()
    TextFormat.Underline -> TextEntityTypeUnderline()
    TextFormat.Strikethrough -> TextEntityTypeStrikethrough()
    TextFormat.Spoiler -> TextEntityTypeSpoiler()
    TextFormat.Code -> TextEntityTypeCode()
    // Blok s jazykem je `preCode` — příjemce podle něj kód obarví.
    TextFormat.CodeBlock -> language?.takeIf { it.isNotEmpty() }?.let { TextEntityTypePreCode(language = it) } ?: TextEntityTypePre()
    TextFormat.Quote -> TextEntityTypeBlockQuote()
    TextFormat.ExpandableQuote -> TextEntityTypeExpandableBlockQuote()
    TextFormat.Link -> TextEntityTypeTextUrl(url = url.orEmpty())
    TextFormat.CustomEmoji -> TextEntityTypeCustomEmoji(customEmojiId = customEmojiId ?: 0)
}

/**
 * Cíl skoku na datum z poslední zprávy PŘED dnem ([before], `null` = žádná) a pár zpráv za ní
 * ([newer], v libovolném pořadí): nejstarší zpráva novější než [before]. `null`, když za ní nic
 * není — vybraný den je novější než poslední zpráva chatu.
 */
internal fun firstMessageAfter(before: Long?, newer: List<Long>): Long? =
    newer.filter { before == null || it > before }.minOrNull()

/** Kolik novějších zpráv se při skoku na datum chce a kolikrát se o ně TDLib požádá. */
private const val NEWER_WINDOW = 5
private const val NEWER_ATTEMPTS = 3

/** Id první zprávy na serveru (`ServerMessageId(1) << 20`) — od něj se čte historie od začátku. */
private const val FIRST_SERVER_MESSAGE_ID = 1L shl 20

/**
 * Velikost navržené fotky na ukázku v konverzaci: nejmenší aspoň [SUGGESTED_PHOTO_PREVIEW_PX]
 * široká (ostrá i v kruhu 96 dp), jinak největší, kterou fotka má.
 */
private fun ChatPhoto.previewSize(): PhotoSize? =
    sizes.filter { it.width >= SUGGESTED_PHOTO_PREVIEW_PX }.minByOrNull { it.width } ?: sizes.maxByOrNull { it.width }

private const val SUGGESTED_PHOTO_PREVIEW_PX = 320

/** `when` bez `else`: nový stav návrhu v novější TDLib rozbije překlad, místo aby tiše čekal. */
private fun SuggestedPostInfo.toDomain() = SuggestedPost(
    state = when (state) {
        is SuggestedPostStatePending -> SuggestedPostState.Pending
        is SuggestedPostStateApproved -> SuggestedPostState.Approved
        is SuggestedPostStateDeclined -> SuggestedPostState.Declined
    },
    stars = (price as? SuggestedPostPriceStar)?.starCount,
    tonCents = (price as? SuggestedPostPriceGram)?.gramCentCount,
    sendDate = sendDate,
    canApprove = canBeApproved,
    canDecline = canBeDeclined,
)

/** Strop komentáře k odmítnutí (`declineSuggestedPost`). */
private const val DECLINE_COMMENT_MAX = 128
