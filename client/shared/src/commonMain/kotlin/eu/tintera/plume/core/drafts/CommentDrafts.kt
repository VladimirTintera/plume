package eu.tintera.plume.core.drafts

import co.touchlab.kermit.Logger
import eu.tintera.plume.core.settings.AppSettings
import eu.tintera.plume.core.telegram.ChatDraft
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.core.telegram.ChatSummary
import eu.tintera.plume.core.telegram.DraftRestore
import eu.tintera.plume.core.telegram.ThreadDrafts
import eu.tintera.plume.core.ui.UiText
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.serialization.Serializable

/** Vlákno komentářů, ve kterém účet [accountId] psal koncept. Klíč evidence [CommentDrafts]. */
@Serializable
data class CommentDraftThread(val accountId: Long, val chatId: Long, val threadId: Long)

/** Rozepsaný komentář — položka přehledu konceptů. [chat] je diskusní skupina vlákna. */
data class CommentDraft(val chat: ChatSummary, val threadId: Long, val post: UiText?, val draft: ChatDraft)

/**
 * **Koncepty ve vláknech komentářů**, které TDLib nehlásí žádným updatem ([ThreadDrafts]). Plume
 * si proto pamatuje vlákna, ve kterých koncept psalo ([note], volá ho konverzace vlákna při
 * každém uložení konceptu), a přehled je při čtení ověří u TDLib: koncept se vezme aktuální
 * (i upravený na jiném zařízení) a vlákno, kde už žádný není, se z evidence vyhodí.
 *
 * - **Koncept psaný jen na jiném zařízení ve vlákně, které Plume neotevřelo, v přehledu není** —
 *   nejde se o něm dozvědět jinak než projitím všech vláken všech kanálů.
 * - **Ověřuje se, když se seznam čte a když se evidence změní**, a na [refresh] (otevření
 *   přehledu). Mezi tím může přehled ukazovat koncept, který mezitím odešel z jiného zařízení.
 * - **Evidence je po účtech** (id Uložených zpráv = id účtu, jako záložky) a přežije restart
 *   ([AppSettings.commentDraftThreads]).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CommentDrafts(
    private val settings: AppSettings,
    private val chats: ChatRepository,
    private val threads: ThreadDrafts,
) {
    private val logger = Logger.withTag("Drafts")

    private val refreshes = MutableStateFlow(0)

    /** Koncepty ve vláknech přihlášeného účtu, nejčerstvější první. */
    val drafts: Flow<List<CommentDraft>> = combine(
        settings.commentDraftThreads,
        chats.savedMessagesChatId,
        refreshes,
    ) { list, account, _ -> if (account == null) emptyList() else list.filter { it.accountId == account } }
        .mapLatest { refs -> read(refs) }
        .distinctUntilChanged()

    /** Znovu ověří evidovaná vlákna (otevření přehledu: koncepty se mohly změnit jinde). */
    fun refresh() {
        refreshes.value++
    }

    /** Konverzace vlákna uložila koncept: s textem si vlákno zapamatuje, bez něj ho zapomene. */
    fun note(chatId: Long, threadId: Long, hasDraft: Boolean) {
        val account = chats.savedMessagesChatId.value ?: return
        val thread = CommentDraftThread(account, chatId, threadId)
        val current = settings.commentDraftThreads.value
        val updated = if (hasDraft) (current - thread) + thread else current - thread
        if (updated != current) settings.setCommentDraftThreads(updated)
    }

    /** Smaže koncept vlákna; vrácení ho vrátí i do evidence. */
    suspend fun delete(chatId: Long, threadId: Long): Result<DraftRestore?> =
        threads.delete(chatId, threadId).map { restore ->
            note(chatId, threadId, hasDraft = false)
            restore?.let {
                DraftRestore {
                    restore.restore().onSuccess { note(chatId, threadId, hasDraft = true) }
                }
            }
        }

    private suspend fun read(refs: List<CommentDraftThread>): List<CommentDraft> {
        if (refs.isEmpty()) return emptyList()
        val found = refs.mapNotNull { ref ->
            threads.read(ref.chatId, ref.threadId)
                .onFailure { logger.w(it) { "Koncept vlákna ${ref.threadId} v chatu ${ref.chatId} se nepřečetl." } }
                // Chyba (bez sítě, vlákno nedostupné) z evidence nemaže — příště se zkusí znovu.
                .getOrElse { return@mapNotNull null }
                ?: run {
                    note(ref.chatId, ref.threadId, hasDraft = false)
                    null
                }
        }
        val summaries = chats.summaries(found.map { it.chatId }.distinct()).associateBy { it.id }
        return found.mapNotNull { draft ->
            summaries[draft.chatId]?.let { CommentDraft(it, draft.threadId, draft.post, draft.draft) }
        }.sortedByDescending { it.draft.timestamp }
    }
}
