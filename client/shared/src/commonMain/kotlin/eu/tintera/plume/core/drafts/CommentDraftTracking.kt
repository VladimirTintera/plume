package eu.tintera.plume.core.drafts

import eu.tintera.plume.core.telegram.ConversationRepository
import eu.tintera.plume.core.telegram.FormatSpan
import eu.tintera.plume.core.telegram.LinkPreviewOptions
import eu.tintera.plume.core.telegram.ReplyQuote

/**
 * Konverzace vlákna komentářů [threadId], která každé uložení konceptu ohlásí evidenci
 * [CommentDrafts] — jen tak se přehled konceptů o vláknu dozví (TDLib to nehlásí). Obal nad
 * repository, ne zásah do ViewModelu konverzace: koncept ukládá několik míst (psaní, odeslání
 * příloh) a všechna jdou přes [saveDraft].
 */
class CommentDraftTracking(
    private val delegate: ConversationRepository,
    private val threadId: Long,
    private val drafts: CommentDrafts,
) : ConversationRepository by delegate {

    override suspend fun saveDraft(
        chatId: Long,
        text: String,
        replyToMessageId: Long?,
        linkPreview: LinkPreviewOptions?,
        quote: ReplyQuote?,
        formats: List<FormatSpan>,
    ): Result<Unit> = delegate.saveDraft(chatId, text, replyToMessageId, linkPreview, quote, formats)
        .onSuccess { drafts.note(chatId, threadId, hasDraft = text.isNotBlank()) }
}
