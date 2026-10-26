package eu.tintera.plume.core.telegram

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

/**
 * Konverzace zúžená na vlákno komentářů ([rootMessageId] v diskusní skupině).
 *
 * **Obal, ne druhá konverzace.** Vlákno je obyčejná konverzace diskusní skupiny, jen s jiným
 * zdrojem historie a s filtrem nových zpráv; ViewModel konverzace o tom nemusí vědět skoro nic.
 * Přepsané je jen to, co se ve vlákně chová jinak — všechno ostatní jde rovnou do [delegate].
 *
 * - **Odeslání bez citace odpovídá kořeni.** Komentář je v Telegramu odpověď na kopii příspěvku
 *   v diskusní skupině (nebo na jiný komentář); zpráva bez odpovědi by skončila v hlavním chatu
 *   skupiny, ne pod příspěvkem.
 * - **Kořen je nejstarší zpráva vlákna.** `getMessageThreadHistory` ho nevrací, takže se přidá
 *   na konec historie, když už starší komentáře nejsou.
 * - **Koncept a hledání patří vláknu**, ne chatu skupiny (`MessageTopicThread`): koncept skupiny
 *   by se objevil v jejím hlavním chatu a hledání bez tématu prošlo celou skupinu.
 * - **„Píše…", připnuté zprávy, reklamy a ankety se vypínají.** Patří chatu skupiny, ne vláknu.
 *   Počet nepřečtených chatu je za celou skupinu, takže ho nahradí počet a hranice
 *   přečtení VLÁKNA (`getMessageThread`) — otevření pak skočí na první nepřečtený komentář.
 */
class ThreadConversationRepository(
    private val delegate: ConversationRepository,
    private val rootMessageId: Long,
) : ConversationRepository by delegate {

    override suspend fun history(chatId: Long, fromMessageId: Long, limit: Int, offset: Int): Result<List<ChatMessage>> {
        // Pod kořenem už nic není; bez tohohle by se kořen přidával znovu a znovu.
        if (fromMessageId == rootMessageId && offset == 0) return Result.success(emptyList())
        return delegate.threadHistory(chatId, rootMessageId, fromMessageId, limit, offset).map { page ->
            // Prázdná stránka = začátek vlákna, a nad ním stojí příspěvek, ke kterému patří.
            page.ifEmpty { listOfNotNull(delegate.message(chatId, rootMessageId).getOrNull()) }
        }
    }

    override fun newMessages(chatId: Long): Flow<ChatMessage> =
        delegate.newMessages(chatId).filter { it.threadId == rootMessageId }

    override suspend fun chatInfo(chatId: Long): Result<ChatInfo> {
        // Bez údajů o vlákně se nepřečtené neukazují vůbec — hranice skupiny by do vlákna nepatřila.
        val thread = delegate.commentThread(chatId, rootMessageId).getOrNull()
        return delegate.chatInfo(chatId).map { info ->
            info.copy(
                content = info.content.withoutPolls(),
                lastReadInboxMessageId = thread?.let { readBoundary(chatId, it) } ?: 0L,
                unreadCount = thread?.unreadCount ?: 0,
                draftText = thread?.draft?.text,
                draftFormats = thread?.draft?.formats.orEmpty(),
                // Odpověď kořeni je výchozí cíl komentáře, ne citace — nad polem by visela pořád.
                draftReplyToMessageId = thread?.draft?.replyToMessageId?.takeIf { it != rootMessageId },
                draftLinkPreview = thread?.draft?.linkPreview,
                draftReplyQuote = thread?.draft?.quote,
            )
        }
    }

    /**
     * Poslední přečtený komentář, odvozený z POČTU nepřečtených, ne z hranice od serveru.
     *
     * `MessageReplyInfo.lastReadInboxMessageId` u vlákna hranici nenese: po přečtení prvních šesti
     * ze třinácti komentářů vrátil server jako „poslední přečtený" NEJNOVĚJŠÍ komentář, přestože
     * `unreadMessageCount` správně klesl na sedm (ověřeno na emulátoru). Hranice se proto dopočítá
     * z nejnovějších komentářů ([threadReadBoundary]). Vlákno, které uživatel ještě neotevřel,
     * hranici nemá (0) a přečtený je v něm jen kořen — tam se historie číst nemusí.
     */
    private suspend fun readBoundary(chatId: Long, thread: CommentThread): Long? {
        if (thread.unreadCount <= 0) return null
        if (thread.lastReadMessageId == 0L) return rootMessageId
        val loaded = mutableListOf<ChatMessage>()
        repeat(MAX_BOUNDARY_PAGES) {
            val from = loaded.lastOrNull()?.id ?: 0L
            val page = delegate.threadHistory(chatId, rootMessageId, from, BOUNDARY_PAGE_SIZE, 0).getOrNull()
                ?: return null
            val fresh = page.filter { message -> loaded.none { it.id == message.id } }
            loaded += fresh
            val reachedStart = fresh.isEmpty()
            threadReadBoundary(loaded, thread.unreadCount, reachedStart, rootMessageId)?.let { return it }
            if (reachedStart) return null
        }
        return null
    }

    override suspend fun markRead(chatId: Long, messageIds: List<Long>): Result<Unit> =
        delegate.markThreadRead(chatId, messageIds)

    override fun accessUpdates(chatId: Long): Flow<ChatAccess> =
        delegate.accessUpdates(chatId).map { it.copy(content = it.content.withoutPolls()) }

    /**
     * Anketu i seznam úkolů zakládá samostatná obrazovka, která o vláknu neví, takže by skončily
     * v hlavním chatu skupiny. Ve vlákně se proto nenabízejí.
     */
    private fun ContentAccess.withoutPolls() = copy(polls = false, checklists = false)

    override suspend fun saveDraft(
        chatId: Long,
        text: String,
        replyToMessageId: Long?,
        linkPreview: LinkPreviewOptions?,
        quote: ReplyQuote?,
        formats: List<FormatSpan>,
    ): Result<Unit> = delegate.saveThreadDraft(chatId, rootMessageId, text, replyToMessageId, linkPreview, quote, formats)

    override suspend fun searchInChat(
        chatId: Long,
        query: String,
        fromMessageId: Long,
        senderUserId: Long?,
    ): Result<ChatSearchPage> = delegate.searchInThread(chatId, rootMessageId, query, fromMessageId, senderUserId)

    override suspend fun sendTyping(chatId: Long, typing: Boolean): Result<Unit> = Result.success(Unit)

    override suspend fun pinnedMessages(chatId: Long): Result<PinnedMessages> =
        Result.success(PinnedMessages(messages = emptyList(), canUnpin = false))

    override fun pinnedMessageChanges(chatId: Long): Flow<Unit> = emptyFlow()

    override suspend fun sponsoredMessages(chatId: Long): Result<List<SponsoredMessage>> = Result.success(emptyList())

    // Všechna odeslání: bez citace se odpovídá kořeni, jinak komentář nepatří do vlákna.

    override suspend fun send(
        chatId: Long,
        text: String,
        replyToMessageId: Long?,
        linkPreview: LinkPreviewOptions?,
        quote: ReplyQuote?,
        options: SendOptions,
        mentions: List<NameMention>,
        formats: List<FormatSpan>,
    ): Result<ChatMessage> =
        delegate.send(chatId, text, reply(replyToMessageId), linkPreview, quote, options, mentions, formats)

    /**
     * Naplánované zprávy patří celé skupině, ne vláknu — ukazovat je v komentářích by míchalo
     * cizí. Plánovat se ve vlákně proto nenabízí (viz `ConversationState.canSchedule`).
     */
    override fun hasScheduledMessages(chatId: Long): Flow<Boolean> = flowOf(false)

    override suspend fun sendDice(
        chatId: Long,
        emoji: String,
        replyToMessageId: Long?,
        quote: ReplyQuote?,
    ): Result<ChatMessage> = delegate.sendDice(chatId, emoji, reply(replyToMessageId), quote)

    override suspend fun sendSticker(
        chatId: Long,
        sticker: StickerItem,
        replyToMessageId: Long?,
        quote: ReplyQuote?,
        options: SendOptions,
    ): Result<ChatMessage> = delegate.sendSticker(chatId, sticker, reply(replyToMessageId), quote, options)

    override suspend fun sendAnimation(
        chatId: Long,
        animation: OutgoingAnimation,
        replyToMessageId: Long?,
        quote: ReplyQuote?,
    ): Result<ChatMessage> = delegate.sendAnimation(chatId, animation, reply(replyToMessageId), quote)

    override suspend fun sendLocation(
        chatId: Long,
        place: Place,
        replyToMessageId: Long?,
        quote: ReplyQuote?,
    ): Result<ChatMessage> = delegate.sendLocation(chatId, place, reply(replyToMessageId), quote)

    override suspend fun sendLiveLocation(
        chatId: Long,
        fix: LiveFix,
        livePeriod: Int,
        replyToMessageId: Long?,
        quote: ReplyQuote?,
    ): Result<ChatMessage> = delegate.sendLiveLocation(chatId, fix, livePeriod, reply(replyToMessageId), quote)

    override suspend fun sendContact(
        chatId: Long,
        userId: Long,
        replyToMessageId: Long?,
        quote: ReplyQuote?,
    ): Result<ChatMessage> = delegate.sendContact(chatId, userId, reply(replyToMessageId), quote)

    override suspend fun sendGif(
        chatId: Long,
        gif: GifItem,
        replyToMessageId: Long?,
        quote: ReplyQuote?,
        options: SendOptions,
    ): Result<ChatMessage> = delegate.sendGif(chatId, gif, reply(replyToMessageId), quote, options)

    override suspend fun sendInlineResult(
        chatId: Long,
        queryId: Long,
        resultId: String,
        replyToMessageId: Long?,
        quote: ReplyQuote?,
    ): Result<ChatMessage> = delegate.sendInlineResult(chatId, queryId, resultId, reply(replyToMessageId), quote)

    override suspend fun sendVoiceNote(
        chatId: Long,
        path: String,
        durationSeconds: Int,
        waveform: ByteArray,
        replyToMessageId: Long?,
        quote: ReplyQuote?,
        options: SendOptions,
        uploadedFileId: Int?,
    ): Result<ChatMessage> =
        delegate.sendVoiceNote(chatId, path, durationSeconds, waveform, reply(replyToMessageId), quote, options, uploadedFileId)

    override suspend fun sendVideoNote(
        chatId: Long,
        videoNote: OutgoingVideoNote,
        replyToMessageId: Long?,
        quote: ReplyQuote?,
        options: SendOptions,
    ): Result<ChatMessage> = delegate.sendVideoNote(chatId, videoNote, reply(replyToMessageId), quote, options)

    override suspend fun sendAttachments(
        chatId: Long,
        attachments: List<OutgoingAttachment>,
        caption: String,
        replyToMessageId: Long?,
        quote: ReplyQuote?,
        options: SendOptions,
        mentions: List<NameMention>,
        formats: List<FormatSpan>,
    ): Result<List<ChatMessage>> =
        delegate.sendAttachments(chatId, attachments, caption, reply(replyToMessageId), quote, options, mentions, formats)

    override suspend fun sendFiles(
        chatId: Long,
        fileIds: List<Int>,
        replyToMessageId: Long?,
        quote: ReplyQuote?,
        options: SendOptions,
    ): Result<List<ChatMessage>> = delegate.sendFiles(chatId, fileIds, reply(replyToMessageId), quote, options)

    override suspend fun sendPoll(
        chatId: Long,
        poll: PollDraft,
        replyToMessageId: Long?,
        quote: ReplyQuote?,
        options: SendOptions,
    ): Result<ChatMessage> = delegate.sendPoll(chatId, poll, reply(replyToMessageId), quote, options)

    override suspend fun sendKeyboardText(chatId: Long, text: String, replyToMessageId: Long?): Result<ChatMessage> =
        delegate.sendKeyboardText(chatId, text, reply(replyToMessageId))

    override suspend fun shareOwnPhone(chatId: Long, replyToMessageId: Long?): Result<ChatMessage> =
        delegate.shareOwnPhone(chatId, reply(replyToMessageId))

    private fun reply(replyToMessageId: Long?): Long = replyToMessageId ?: rootMessageId
}

/**
 * Hranice přečtení vlákna z [unreadCount] nejnovějších příchozích komentářů ([newestFirst] od
 * nejnovějšího): vrací id těsně pod prvním nepřečteným, takže se nad ním nakreslí předěl
 * a skok míří na něj. Vlastní komentáře se nepočítají — nepřečtené jsou jen cizí.
 *
 * Když historie skončila dřív, než se napočítaly všechny, jsou nepřečtené všechny a hranicí je
 * kořen ([reachedStart]). Jinak `null` = načtené zprávy nestačí.
 */
internal fun threadReadBoundary(
    newestFirst: List<ChatMessage>,
    unreadCount: Int,
    reachedStart: Boolean,
    rootMessageId: Long,
): Long? {
    if (unreadCount <= 0) return null
    var counted = 0
    for (message in newestFirst) {
        // Nad kořenem už žádný komentář není.
        if (message.id == rootMessageId) return rootMessageId
        if (message.isOutgoing) continue
        counted++
        if (counted == unreadCount) return message.id - 1
    }
    return if (reachedStart) rootMessageId else null
}

private const val BOUNDARY_PAGE_SIZE = 100
private const val MAX_BOUNDARY_PAGES = 5
