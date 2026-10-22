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

    /** Odznak na šipce zpět v konverzaci [currentChatId], viz [unreadElsewhere]. */
    fun unreadElsewhere(currentChatId: Long): Flow<Int> = combine(
        chats.mainUnread,
        chats.chats(ChatFolder.Main),
    ) { unread, main ->
        unreadElsewhere(
            unmutedUnreadChats = unread.unmutedChats,
            chats = (main as? ChatListState.Ready)?.chats.orEmpty(),
            currentChatId = currentChatId,
        )
    }.distinctUntilChanged()

    private companion object {
        /** Víc, než se nabízí: otevřený chat a chaty bez souhrnu z nabídky vypadnou. */
        const val MAX_RECENT = 16
    }
}

/**
 * Chaty pro přepínač: naposledy otevřený chat ([recent], nejčerstvější první), pak nepřečtené
 * chaty z hlavního seznamu, zbytek historie a nakonec hlavní seznam v pořadí od TDLib.
 * Archivovaný chat jen z historie — kdo ho otevřel, chce se k němu vrátit, ale vršek archivu do
 * „aktivních" nepatří.
 *
 * - **Nepřečtené hned za posledním chatem**, protože je slibuje odznak na šipce
 *   ([unreadElsewhere], počítá tytéž). Dřív chodily až v pořadí seznamu a nepřečtený chat
 *   na osmém místě se do nabídky nevešel: šipka hlásila „1" a nabídka ho neukázala. Poslední
 *   chat před nimi zůstává, protože přepínání sem a tam mezi dvěma chaty je hlavní použití.
 * - Sponzorovaný chat se nenabízí nikdy (uživatel si ho nevybral) a Uložené zprávy ze seznamu
 *   jen připnuté — stejné pravidlo jako v seznamu chatů. Z historie ano: tam je dal uživatel sám.
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
    val fromRecent = recent.mapNotNull { known[it] }.filter { it.id != currentChatId }
    val fromList = chats.filter { it.id != savedMessagesChatId || it.isPinned }
    val unread = fromList.filter { it.countsAsUnread() }
    return (fromRecent.take(1) + unread + fromRecent.drop(1) + fromList)
        .filter { it.id != currentChatId && it.sponsor == null }
        .distinctBy { it.id }
        .take(max)
}

/**
 * Kolik nepřečtených chatů čeká mimo otevřenou konverzaci: počet od TDLib
 * ([unmutedUnreadChats], hlavní seznam) bez chatu [currentChatId], pokud se do něj počítá.
 *
 * - **Jen neztlumené.** Odznak na šipce zve zpátky do seznamu; ztlumený chat si o to neříká,
 *   stejně jako v seznamu chatů a u složek.
 * - **Otevřený chat se odečítá podle stejného pravidla, jakým počítá TDLib** (nepřečtené zprávy
 *   nebo ruční příznak, ne ztlumený): dokud se v něm zprávy neoznačí přečtené, je v počtu taky,
 *   a „1" na šipce by mířila na chat, který je právě na obrazovce. Archiv v počtu není (TDLib
 *   ho počítá zvlášť), takže archivovaný otevřený chat se neodečítá.
 */
fun unreadElsewhere(unmutedUnreadChats: Int, chats: List<ChatSummary>, currentChatId: Long): Int {
    val currentCounted = chats.firstOrNull { it.id == currentChatId }?.countsAsUnread() == true
    return (unmutedUnreadChats - if (currentCounted) 1 else 0).coerceAtLeast(0)
}

/** Pravidlo `unreadUnmutedCount` z TDLib: nepřečtené zprávy nebo ruční příznak, ne ztlumený. */
private fun ChatSummary.countsAsUnread(): Boolean = !isMuted && (unreadCount > 0 || isMarkedAsUnread)

/** Tolik se vejde do nabídky pod šipkou i na nízkém displeji bez posouvání. */
const val SWITCHER_MAX_CHATS = 6
