package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.telegram.ChatFolder
import eu.tintera.plume.core.telegram.ChatListState
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.core.telegram.ChatSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update

/**
 * Rychlý přepínač chatů: podržení šipky zpět v konverzaci nabídne chaty, mezi kterými uživatel
 * právě přechází, a ťuknutí přepne bez návratu do seznamu.
 *
 * - **Naposledy otevřené napřed, pak vršek seznamu.** Hned po startu žádná historie není, a i tak
 *   má nabídka co nabídnout: vršek seznamu jsou chaty, ve kterých se naposledy psalo.
 * - **Historie žije jen v paměti procesu.** Nese „s kým si teď píšu", ne dlouhodobý zvyk —
 *   po restartu ji nahradí pořadí v seznamu chatů, které říká skoro totéž.
 * - **Id chatu, který v seznamech není, se přeskočí** (otevřený z odkazu a nikdy nepřidaný, jiný
 *   účet). Bez souhrnu z [ChatRepository] by neměl jméno ani fotku, a jiný účet se tak nemusí
 *   z historie mazat zvlášť.
 */
class ChatSwitcher(private val chats: ChatRepository) {
    private val recent = MutableStateFlow<List<Long>>(emptyList())

    /** Konverzace [chatId] je na obrazovce; i téma nebo vlákno počítá za svůj chat. */
    fun opened(chatId: Long) = recent.update { (listOf(chatId) + (it - chatId)).take(MAX_RECENT) }

    /** Co nabídnout v konverzaci [currentChatId] — ji samu ne. */
    fun candidates(currentChatId: Long): Flow<List<ChatSummary>> = combine(
        recent,
        chats.chats(ChatFolder.Main),
        chats.chats(ChatFolder.Archive),
        chats.savedMessagesChatId,
    ) { recent, main, archive, saved ->
        switcherChats(
            recent = recent,
            chats = (main as? ChatListState.Ready)?.chats.orEmpty(),
            archived = (archive as? ChatListState.Ready)?.chats.orEmpty(),
            currentChatId = currentChatId,
            savedMessagesChatId = saved,
        )
    }.distinctUntilChanged()

    private companion object {
        /** Víc, než se nabízí: otevřený chat a chaty bez souhrnu z nabídky vypadnou. */
        const val MAX_RECENT = 16
    }
}

/**
 * Chaty pro přepínač: naposledy otevřené ([recent], nejčerstvější první), pak hlavní seznam
 * v pořadí od TDLib. Archivovaný chat jen z historie — kdo ho otevřel, chce se k němu vrátit,
 * ale vršek archivu do „aktivních" nepatří.
 *
 * Sponzorovaný chat se nenabízí nikdy (uživatel si ho nevybral) a Uložené zprávy ze seznamu jen
 * připnuté — stejné pravidlo jako v seznamu chatů. Z historie ano: tam je dal uživatel sám.
 */
fun switcherChats(
    recent: List<Long>,
    chats: List<ChatSummary>,
    archived: List<ChatSummary>,
    currentChatId: Long,
    savedMessagesChatId: Long?,
    max: Int = SWITCHER_MAX_CHATS,
): List<ChatSummary> {
    val known = (archived + chats).associateBy { it.id }
    val fromRecent = recent.mapNotNull { known[it] }
    val fromList = chats.filter { it.id != savedMessagesChatId || it.isPinned }
    return (fromRecent + fromList)
        .filter { it.id != currentChatId && it.sponsor == null }
        .distinctBy { it.id }
        .take(max)
}

/** Tolik se vejde do nabídky pod šipkou i na nízkém displeji bez posouvání. */
const val SWITCHER_MAX_CHATS = 6
