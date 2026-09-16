package eu.tintera.plume.core.telegram

import co.touchlab.kermit.Logger
import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.dto.ChatActionCancel
import dev.g000sha256.tdl.dto.ChatActionTyping
import dev.g000sha256.tdl.dto.ChatTypeBasicGroup
import dev.g000sha256.tdl.dto.ChatTypePrivate
import dev.g000sha256.tdl.dto.ChatTypeSupergroup
import dev.g000sha256.tdl.dto.DraftMessage
import dev.g000sha256.tdl.dto.DraftMessageContentText
import dev.g000sha256.tdl.dto.FormattedText
import dev.g000sha256.tdl.dto.InputFileId
import dev.g000sha256.tdl.dto.InputMessageSticker
import dev.g000sha256.tdl.dto.InputSticker
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
import dev.g000sha256.tdl.dto.SearchMessagesFilterPhoto
import dev.g000sha256.tdl.dto.SearchMessagesFilterPinned
import dev.g000sha256.tdl.dto.TextEntity
import dev.g000sha256.tdl.toResult
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
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
    private val senders = mutableMapOf<Long, ReactionSender>()
    private val sendersMutex = Mutex()

    /**
     * Klient, pro kterého keš [senders] platí. Po odhlášení a přihlášení jiného účtu může mít
     * tentýž uživatel jiné jméno (kontakty jsou per účet) a profilovky jiná `fileId`.
     */
    private var sendersClient: TdlClient? = null

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
        holder.updates { newMessageUpdates }
            .filter { it.message.chatId == chatId }
            .map { it.message.toChatMessage(isGroup(chatId)) }

    override fun deletedMessages(chatId: Long): Flow<List<Long>> =
        holder.updates { deleteMessagesUpdates }
            // `isPermanent == false` znamená jen uvolnění z cache, ne smazání pro uživatele —
            // takovou zprávu z obrazovky odstranit nesmíme.
            .filter { it.chatId == chatId && it.isPermanent }
            .map { it.messageIds.toList() }

    override fun editedMessages(chatId: Long): Flow<ChatMessage> =
        holder.updates { messageContentUpdates }
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
        holder.updates { messageInteractionInfoUpdates }
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
     * Fotky přes vyhledávání s filtrem, ne procházením historie — fotek bývá v chatu zlomek
     * zpráv a `getChatHistory` by kvůli pár snímkům tahal stovky textů.
     *
     * Jedno volání vrátí obě strany: záporný `offset` přidá novější zprávy, `limit` zbytek
     * starších. Zpráva [messageId] je ve výsledku sama, takže `limit` je o jednu větší.
     */
    override suspend fun photos(chatId: Long, messageId: Long, older: Int, newer: Int): Result<List<ChatMessage>> {
        val isGroup = isGroup(chatId)
        return client.searchChatMessages(
            chatId = chatId,
            topicId = null,
            query = "",
            senderId = null,
            fromMessageId = messageId,
            offset = -newer,
            limit = older + newer + 1,
            filter = SearchMessagesFilterPhoto(),
        )
            .toResult()
            .map { found ->
                found.messages
                    .filterNotNull()
                    .map { it.toChatMessage(isGroup) }
                    .filter { it.media is MessageMedia.Photo }
                    .sortedBy { it.id }
            }
    }

    /** TDLib počítá od nejnovější; prohlížeč řadí od nejstarší, takže se to obrací. */
    override suspend fun photoPosition(chatId: Long, messageId: Long): Result<PhotoPosition?> {
        val filter = SearchMessagesFilterPhoto()
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
    override suspend fun send(chatId: Long, text: String, replyToMessageId: Long?): Result<ChatMessage> {
        val entities = detectEntities(text)

        return client.sendMessage(
            chatId = chatId,
            // Vlákna (fóra) fáze 2 neřeší — null znamená hlavní vlákno chatu.
            topicId = null,
            replyTo = replyToMessageId?.let(::replyTo),
            options = null,
            replyMarkup = null,
            inputMessageContent = InputMessageText(
                text = FormattedText(text = text, entities = entities),
                linkPreviewOptions = null,
                clearDraft = true,
            ),
        )
            .toResult()
            // TDLib vrací zprávu okamžitě, s dočasným id a stavem Pending.
            .map { it.toChatMessage(isGroup(chatId)) }
    }

    override suspend fun sendSticker(
        chatId: Long,
        sticker: StickerItem,
        replyToMessageId: Long?,
    ): Result<ChatMessage> {
        val file = InputFileId(id = sticker.sendFileId)

        return client.sendMessage(
            chatId = chatId,
            topicId = null,
            replyTo = replyToMessageId?.let(::replyTo),
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

    override suspend fun saveDraft(chatId: Long, text: String, replyToMessageId: Long?): Result<Unit> =
        client.setChatDraftMessage(
            chatId = chatId,
            topicId = null,
            draftMessage = text.takeIf { it.isNotBlank() }?.let {
                DraftMessage(
                    replyTo = replyToMessageId?.let(::replyTo),
                    date = Clock.System.now().epochSeconds.toInt(),
                    // Entity se tu nedohledávají: koncept je rozepsaný text, odkazy se označí
                    // až při odeslání (viz `send`).
                    content = DraftMessageContentText(
                        text = FormattedText(text = it, entities = emptyArray()),
                        linkPreviewOptions = null,
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
                    draftReplyToMessageId = (chat.draftMessage?.replyTo as? InputMessageReplyToMessage)
                        ?.messageId,
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
        // Text a jeho entity musí pocházet z jednoho rozhodnutí — rozsahy platí jen pro ten
        // řetězec, ze kterého vznikly.
        val body = captionBody()

        // Jméno a tvář mají smysl jen ve skupině a jen u cizích zpráv — jinde je to šum. Obojí
        // z jednoho dotazu (a z cache), ne ze dvou.
        val sender = if (isGroup && !isOutgoing) resolveSender(senderId) else null

        return ChatMessage(
            id = id,
            isOutgoing = isOutgoing,
            senderName = sender?.title,
            senderAvatarFileId = sender?.avatarFileId,
            senderId = senderId,
            text = body.text,
            entities = body.entities,
            media = messageMedia(),
            linkPreview = linkPreview(),
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
            mediaAlbumId = mediaAlbumId,
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

    private suspend fun resolveSender(senderId: Long): ReactionSender? {
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

/**
 * Odpověď na celou zprávu v tomhle chatu.
 *
 * Citace části textu (označený úryvek) zatím neřešíme. `pollOptionId` je prázdný řetězec, ne
 * `null` — pole je v TDLib nenullovatelné a používá se jen u odpovědí na anketu.
 */
private fun replyTo(messageId: Long) = InputMessageReplyToMessage(
    messageId = messageId,
    quote = null,
    checklistTaskId = 0,
    pollOptionId = "",
)

/** Připnutých zpráv na jeden dotaz. Víc TDLib nedovolí. */
private const val PINNED_PAGE_SIZE = 100

/** Strop stránek připnutých zpráv. Pět set připnutých v jednom chatu je víc než dost. */
private const val MAX_PINNED_PAGES = 5
