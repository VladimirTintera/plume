package eu.tintera.plume.features.chats

import eu.tintera.plume.core.telegram.ChatListState
import eu.tintera.plume.core.telegram.ChatSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Skládání výsledků hledání chatů a co z nich ukáže výběr pro přeposílání a sdílení.
 */
class ChatSearchTest {

    /** Serverové výsledky jdou za lokální a lokální pořadí se nemění. */
    @Test
    fun serveroveZaLokalnimiBezDuplicit() {
        val merged = mergeChatResults(
            local = listOf(chat(3), chat(1)),
            server = listOf(chat(1), chat(7), chat(3), chat(5)),
        )
        assertEquals(listOf(3L, 1L, 7L, 5L), merged.map { it.id })
    }

    /** Bez dotazu je ve výběru celý hlavní seznam, výsledky hledání se nečtou. */
    @Test
    fun bezDotazuHlavniSeznam() {
        val picker = pickerChats(
            query = "  ",
            list = ChatListState.Ready(listOf(chat(1), chat(2)), canLoadMore = false),
            search = ChatSearchState(query = "x", results = listOf(chat(9))),
        )
        assertEquals(listOf(1L, 2L), picker.chats.map { it.id })
        assertFalse(picker.loading)
    }

    @Test
    fun bezDotazuSeSeznamemNaciti() {
        assertTrue(pickerChats("", ChatListState.Loading, ChatSearchState()).loading)
    }

    /** S dotazem výsledky hledání — i chat, který v hlavním seznamu není (archiv). */
    @Test
    fun sDotazemVysledkyHledani() {
        val picker = pickerChats(
            query = "ana ",
            list = ChatListState.Ready(listOf(chat(1)), canLoadMore = false),
            search = ChatSearchState(query = "ana", results = listOf(chat(42))),
        )
        assertEquals(listOf(42L), picker.chats.map { it.id })
        assertFalse(picker.loading)
    }

    /**
     * Během psaní zůstávají výsledky předchozího dotazu — výběr nesmí při každém znaku
     * probliknout načítáním.
     */
    @Test
    fun predchoziVysledkyVydrziDoNovych() {
        val picker = pickerChats(
            query = "anna",
            list = ChatListState.Ready(emptyList(), canLoadMore = false),
            search = ChatSearchState(query = "ann", results = listOf(chat(42))),
        )
        assertEquals(listOf(42L), picker.chats.map { it.id })
        assertFalse(picker.loading)
    }

    /** Prázdný výsledek je „nic nenalezeno", až když hledání pro TENTO dotaz doběhlo. */
    @Test
    fun prazdnyVysledekAzPoDobehnuti() {
        val list = ChatListState.Ready(emptyList(), canLoadMore = false)
        assertTrue(pickerChats("anna", list, ChatSearchState(query = "ann")).loading)
        assertTrue(pickerChats("anna", list, ChatSearchState(query = "anna", searching = true)).loading)
        assertFalse(pickerChats("anna", list, ChatSearchState(query = "anna")).loading)
    }

    private fun chat(id: Long) = ChatSummary(
        id = id,
        title = "Chat $id",
        unreadCount = 0,
        lastMessagePreview = null,
        isPinned = false,
        order = id,
        avatarFileId = null,
        isMuted = false,
        isMarkedAsUnread = false,
        isPrivate = true,
        lastMessageTimestamp = null,
        presence = null,
        activity = null,
        draft = null,
    )
}
