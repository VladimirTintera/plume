package eu.tintera.plume.core.telegram

import co.touchlab.kermit.Logger
import dev.g000sha256.tdl.TdlClient
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
import dev.g000sha256.tdl.dto.FormattedText
import dev.g000sha256.tdl.dto.InputAnimation
import dev.g000sha256.tdl.dto.InputDocument
import dev.g000sha256.tdl.dto.InputFileId
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
import dev.g000sha256.tdl.dto.MessageSender
import dev.g000sha256.tdl.dto.MessageSenderChat
import dev.g000sha256.tdl.dto.MessageSenderUser
import dev.g000sha256.tdl.dto.MessageSendingState
import dev.g000sha256.tdl.dto.MessageSendingStateFailed
import dev.g000sha256.tdl.dto.MessageSendingStatePending
import dev.g000sha256.tdl.dto.MessageSourceChatHistory
import dev.g000sha256.tdl.dto.MessageText
import dev.g000sha256.tdl.dto.ReactionTypeEmoji
import dev.g000sha256.tdl.dto.SearchMessagesFilter
import dev.g000sha256.tdl.dto.SearchMessagesFilterDocument
import dev.g000sha256.tdl.dto.SearchMessagesFilterPhotoAndVideo
import dev.g000sha256.tdl.dto.SearchMessagesFilterUrl
import dev.g000sha256.tdl.dto.SearchMessagesFilterPinned
import dev.g000sha256.tdl.dto.TextEntity
import dev.g000sha256.tdl.toResult
import kotlin.time.Clock
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.awaitAll
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
                    canForward = properties.canBeForwarded,
                    canSave = properties.canBeSaved,
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

    override suspend fun sendVoiceNote(
        chatId: Long,
        path: String,
        durationSeconds: Int,
        waveform: ByteArray,
        replyToMessageId: Long?,
    ): Result<ChatMessage> =
        client.sendMessage(
            chatId = chatId,
            topicId = null,
            replyTo = replyToMessageId?.let(::replyTo),
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
                replyTo = replyToMessageId?.let(::replyTo),
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
            replyTo = replyToMessageId?.let(::replyTo),
            options = null,
            inputMessageContents = contents.toTypedArray(),
        )
            .toResult()
            .map { sent -> sent.messages.filterNotNull().map { it.toChatMessage(isGroup) } }
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

    override suspend fun sendAnimation(
        chatId: Long,
        animation: OutgoingAnimation,
        replyToMessageId: Long?,
    ): Result<ChatMessage> =
        client.sendMessage(
            chatId = chatId,
            topicId = null,
            replyTo = replyToMessageId?.let(::replyTo),
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

    override suspend fun sendGif(chatId: Long, gif: GifItem, replyToMessageId: Long?): Result<ChatMessage> {
        val file = InputFileId(id = gif.animation.fileId)
        return client.sendMessage(
            chatId = chatId,
            topicId = null,
            replyTo = replyToMessageId?.let(::replyTo),
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
            content = contentAccess(status = status, groupDefaults = chat.permissions.toContentAccess()),
        )
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
            selfDestructType = selfDestructType?.toSelfDestructType(),
            // Zlomky vteřiny nahoru: „0 s" by u běžícího odpočtu znamenalo, že už vypršel.
            selfDestructIn = selfDestructIn.takeIf { it > 0.0 }?.let { ceil(it).toInt() },
            forwardOrigin = forwardInfo?.origin?.toForwardOrigin(),
        )
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
     * Reakce z interaction info. Vlastní (placená) emoji se přeskočí — jsou to samolepky
     * a vykreslení řeší jiná vrstva; zahodit je je lepší než u zprávy neukázat žádnou reakci.
     */
    private suspend fun MessageInteractionInfo?.toReactions(): List<MessageReaction> {
        val raw = this?.reactions?.reactions?.filterNotNull().orEmpty()

        return raw.mapNotNull { reaction ->
            val emoji = (reaction.type as? ReactionTypeEmoji)?.emoji ?: return@mapNotNull null

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
            val emoji = (reaction.type as? ReactionTypeEmoji)?.emoji ?: return@mapNotNull null
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

private fun SelfDestructType.toTdl(): MessageSelfDestructType = when (this) {
    is SelfDestructType.Immediately -> MessageSelfDestructTypeImmediately()
    is SelfDestructType.Timer -> MessageSelfDestructTypeTimer(seconds)
}

/** Připnutých zpráv na jeden dotaz. Víc TDLib nedovolí. */
private const val PINNED_PAGE_SIZE = 100

/** Výsledků hledání v chatu na stránku; další se dotahují, až k nim uživatel došipkuje. */
private const val IN_CHAT_SEARCH_PAGE = 50

/** Kolik členů vrátí jedno hledání pro filtr podle odesílatele; víc se na obrazovku nevejde. */
private const val MEMBER_SEARCH_LIMIT = 50

/** Strop stránek připnutých zpráv. Pět set připnutých v jednom chatu je víc než dost. */
private const val MAX_PINNED_PAGES = 5

/**
 * Práva na druhy obsahu z `ChatPermissions`. Bere se jen to, co aplikace umí poslat — hudba,
 * video kolečka a ankety by tu byly mrtvá pole.
 */
private fun ChatPermissions.toContentAccess(): ContentAccess = ContentAccess(
    photos = canSendPhotos,
    videos = canSendVideos,
    documents = canSendDocuments,
    voiceNotes = canSendVoiceNotes,
    stickers = canSendOtherMessages,
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
