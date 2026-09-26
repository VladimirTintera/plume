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
 * - **Koncept, „píše…", připnuté zprávy, reklamy a ankety se vypínají.** Všechno z toho patří chatu
 *   skupiny, ne vláknu: koncept by se objevil v hlavním chatu skupiny a „píše…" by viděli všichni
 *   v ní. Nepřečtené se taky nepočítají — počet chatu je za celou skupinu.
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

    override suspend fun chatInfo(chatId: Long): Result<ChatInfo> =
        delegate.chatInfo(chatId).map { info ->
            info.copy(
                content = info.content.withoutPolls(),
                unreadCount = 0,
                draftText = null,
                draftReplyToMessageId = null,
                draftLinkPreview = null,
                draftReplyQuote = null,
            )
        }

    override fun accessUpdates(chatId: Long): Flow<ChatAccess> =
        delegate.accessUpdates(chatId).map { it.copy(content = it.content.withoutPolls()) }

    /**
     * Anketu zakládá samostatná obrazovka, která o vláknu neví, takže by skončila v hlavním chatu
     * skupiny. Ve vlákně se proto nenabízí.
     */
    private fun ContentAccess.withoutPolls() = copy(polls = false)

    override suspend fun saveDraft(
        chatId: Long,
        text: String,
        replyToMessageId: Long?,
        linkPreview: LinkPreviewOptions?,
        quote: ReplyQuote?,
    ): Result<Unit> = Result.success(Unit)

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
    ): Result<ChatMessage> = delegate.send(chatId, text, reply(replyToMessageId), linkPreview, quote, options)

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
    ): Result<ChatMessage> = delegate.sendSticker(chatId, sticker, reply(replyToMessageId), quote)

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

    override suspend fun sendGif(
        chatId: Long,
        gif: GifItem,
        replyToMessageId: Long?,
        quote: ReplyQuote?,
    ): Result<ChatMessage> = delegate.sendGif(chatId, gif, reply(replyToMessageId), quote)

    override suspend fun sendVoiceNote(
        chatId: Long,
        path: String,
        durationSeconds: Int,
        waveform: ByteArray,
        replyToMessageId: Long?,
        quote: ReplyQuote?,
    ): Result<ChatMessage> =
        delegate.sendVoiceNote(chatId, path, durationSeconds, waveform, reply(replyToMessageId), quote)

    override suspend fun sendAttachments(
        chatId: Long,
        attachments: List<OutgoingAttachment>,
        caption: String,
        replyToMessageId: Long?,
        quote: ReplyQuote?,
        options: SendOptions,
    ): Result<List<ChatMessage>> =
        delegate.sendAttachments(chatId, attachments, caption, reply(replyToMessageId), quote, options)

    override suspend fun sendPoll(
        chatId: Long,
        poll: PollDraft,
        replyToMessageId: Long?,
        quote: ReplyQuote?,
    ): Result<ChatMessage> = delegate.sendPoll(chatId, poll, reply(replyToMessageId), quote)

    override suspend fun sendKeyboardText(chatId: Long, text: String, replyToMessageId: Long?): Result<ChatMessage> =
        delegate.sendKeyboardText(chatId, text, reply(replyToMessageId))

    override suspend fun shareOwnPhone(chatId: Long, replyToMessageId: Long?): Result<ChatMessage> =
        delegate.shareOwnPhone(chatId, reply(replyToMessageId))

    private fun reply(replyToMessageId: Long?): Long = replyToMessageId ?: rootMessageId
}
