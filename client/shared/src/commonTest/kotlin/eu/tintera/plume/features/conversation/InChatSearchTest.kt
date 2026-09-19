package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.telegram.ChatSearchPage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InChatSearchTest {

    private fun page(vararg ids: Long, total: Int? = null, next: Long? = null) =
        ChatSearchPage(messageIds = ids.toList(), total = total, nextFromMessageId = next)

    @Test
    fun prvniStrankaUkazeNejnovejsi() {
        val search = InChatSearch(query = "kocka").withFirstPage("kocka", page(30, 20, 10, total = 7, next = 10))
        assertEquals(30L, search.current)
        assertEquals(7, search.count)
        assertTrue(search.hasOlder)
        assertFalse(search.hasNewer)
    }

    @Test
    fun prazdnyVysledekJeNicNenalezeno() {
        val search = InChatSearch(query = "xyz ").withFirstPage("xyz", page())
        assertNull(search.current)
        assertTrue(search.nothingFound)
        assertFalse(search.hasOlder)
    }

    @Test
    fun behemPsaniDalsihoDotazuNeniNicNenalezeno() {
        val search = InChatSearch(query = "xyzw").withFirstPage("xyz", page())
        assertFalse(search.nothingFound)
    }

    @Test
    fun sipkyNeprekroceziHranice() {
        val search = InChatSearch(query = "a").withFirstPage("a", page(3, 2, 1))
        assertNull(search.newer())
        val oldest = search.older()!!.older()!!
        assertEquals(1L, oldest.current)
        assertNull(oldest.older())
        assertEquals(2L, oldest.newer()!!.current)
    }

    @Test
    fun naKonciStrankyJeTrebaDotahnout() {
        val search = InChatSearch(query = "a").withFirstPage("a", page(3, 2, next = 2)).older()!!
        assertNull(search.older())
        assertTrue(search.hasOlder)
        val loaded = search.withNextPage("a", page(2, 1, next = null))
        assertEquals(listOf(3L, 2L, 1L), loaded.results)
        assertEquals(1L, loaded.older()!!.current)
        assertFalse(loaded.older()!!.hasOlder)
    }

    @Test
    fun strankaStarehoDotazuSeZahodi() {
        val search = InChatSearch(query = "b").withFirstPage("b", page(9))
        assertEquals(search, search.withNextPage("a", page(1)))
    }

    @Test
    fun pocetNeniMensiNezNactene() {
        val search = InChatSearch(query = "a").withFirstPage("a", page(3, 2, 1, total = null))
        assertEquals(3, search.count)
    }
}
