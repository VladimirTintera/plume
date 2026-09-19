package eu.tintera.plume.features.chats

import eu.tintera.plume.core.telegram.ChatListState
import eu.tintera.plume.core.telegram.ChatSummary
import eu.tintera.plume.core.telegram.FoundMessage
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

    /** Zvýraznění bez ohledu na velikost písmen, všechny výskyty, bez překryvů. */
    @Test
    fun zvyrazneniVsechVyskytu() {
        assertEquals(listOf(0..2, 5..7, 10..12), highlightRanges("Ahoj ahoj Aho", " aho "))
        assertEquals(listOf(0..1, 2..3), highlightRanges("aaaa", "aa"))
        assertEquals(emptyList(), highlightRanges("text", "  "))
    }

    /** TDLib hledá bez diakritiky, zvýraznění musí taky — a na pozicích v původním textu. */
    @Test
    fun zvyrazneniBezDiakritiky() {
        assertEquals(listOf(0..6), highlightRanges("Obšlehl jsem", "obslehl"))
        assertEquals(listOf(4..8), highlightRanges("Tak zprav", "zpráv"))
    }

    /** Slovo z konce dlouhé zprávy musí být ve dvouřádkovém výsledku vidět. */
    @Test
    fun ukazkaZacinaPredNalezenymSlovem() {
        val text = "Úvodní věta, která je dost dlouhá, aby se hledané slovo nevešlo, a pak durov na konci."
        val snippet = snippetAround(text, "durov")
        assertTrue(snippet.startsWith("…"))
        assertTrue(snippet.indexOf("durov") in 1..30, snippet)
        // Začíná na začátku slova, ne uprostřed.
        assertTrue(snippet[1] != ' ' && text.contains(" " + snippet.drop(1)), snippet)
        // Krátký text i výskyt u začátku zůstávají celé.
        assertEquals("durov je tu", snippetAround("durov je tu", "durov"))
    }

    /** Hashtag je jedno slovo s mřížkou; samotná mřížka ani věta hashtagem nejsou. */
    @Test
    fun hashtagovyDotaz() {
        assertTrue(isHashtagQuery("#plume"))
        assertTrue(isHashtagQuery(" #plume "))
        assertFalse(isHashtagQuery("#"))
        assertFalse(isHashtagQuery("plume"))
        assertFalse(isHashtagQuery("#plume a dál"))
    }

    /** Další stránka zpráv nesmí zopakovat zprávu z hranice předchozí; stejné id v jiném chatu je jiná zpráva. */
    @Test
    fun strankyZpravBezDuplicit() {
        val merged = mergeFoundMessages(
            loaded = listOf(found(chat = 1, message = 10), found(chat = 1, message = 9)),
            page = listOf(found(chat = 1, message = 9), found(chat = 2, message = 9), found(chat = 1, message = 8)),
        )
        assertEquals(listOf(1L to 10L, 1L to 9L, 2L to 9L, 1L to 8L), merged.map { it.chat.id to it.messageId })
    }

    private fun found(chat: Long, message: Long) =
        FoundMessage(chat = chat(chat), messageId = message, text = "", date = 0, isOutgoing = false)

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
