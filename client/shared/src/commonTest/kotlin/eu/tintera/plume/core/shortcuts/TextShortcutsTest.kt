package eu.tintera.plume.core.shortcuts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TextShortcutsTest {

    private val address = TextShortcut("1", "adresa", "Dlouhá 5, Praha", order = 1)
    private val account = TextShortcut("2", "ucet", "123456789/0100", order = 0)
    private val ad = TextShortcut("3", "ad", "Ahoj, dík!", order = 2)

    @Test
    fun dotazNaZacatkuAPoMezere() {
        assertEquals(ShortcutQuery(0, "adr"), shortcutQueryAt("/adr", 4))
        assertEquals(ShortcutQuery(5, ""), shortcutQueryAt("ahoj /", 6))
        assertEquals(ShortcutQuery(5, "uc"), shortcutQueryAt("ahoj /uc ještě", 8))
    }

    @Test
    fun cestaAniTextBezLomitkaDotazNejsou() {
        assertNull(shortcutQueryAt("a/b", 3))
        assertNull(shortcutQueryAt("ahoj", 4))
        assertNull(shortcutQueryAt("/adr esa", 8))
        assertNull(shortcutQueryAt("/", 0))
    }

    @Test
    fun shodaZacatkuPresnaNapred() {
        val all = listOf(address, account, ad)
        assertEquals(listOf(ad, address), matchingShortcuts(all, "AD"))
        assertEquals(listOf(account, address, ad), matchingShortcuts(all, ""))
        assertEquals(emptyList(), matchingShortcuts(all, "x"))
    }

    @Test
    fun vlozeniNahradiJenZkratku() {
        val text = "Posílám /adr na tebe"
        val query = shortcutQueryAt(text, 12)!!
        assertEquals("Posílám Dlouhá 5, Praha na tebe" to 23, shortcutInsertion(text, 12, query, address))
    }

    @Test
    fun nazevJenPovoleneZnaky() {
        assertEquals("moje_adresa2", shortcutName("/moje_Adresa2"))
        assertEquals("mojeadresa2", shortcutName(" /Moje Adresa-2 "))
        assertEquals(MAX_SHORTCUT_NAME, shortcutName("a".repeat(50)).length)
    }
}
