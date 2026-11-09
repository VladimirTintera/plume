package eu.tintera.plume.core.share

import eu.tintera.plume.core.accounts.AccountSlot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ShortcutIdsTest {

    private val other = AccountSlot("a1b2")

    /** Jiné id primárního účtu by smazalo připnutí na ploše a nastavené konverzační kanály. */
    @Test
    fun primarniUcetMaDnesniId() {
        assertEquals("chat_42", shortcutId(AccountSlot.Primary, 42))
        assertEquals("chat_-1001", shortcutId(AccountSlot.Primary, -1001))
    }

    @Test
    fun dalsiUcetMaSlotVId() {
        assertEquals("chat_a1b2_42", shortcutId(other, 42))
    }

    @Test
    fun idSePrecteZpatky() {
        listOf(AccountSlot.Primary, other).forEach { slot ->
            listOf(42L, -1001L).forEach { chatId ->
                assertEquals(ShortcutTarget(slot, chatId), shortcutChat(shortcutId(slot, chatId)))
            }
        }
    }

    @Test
    fun ciziIdNicNeotevre() {
        assertNull(shortcutChat(null))
        assertNull(shortcutChat("neco_42"))
        assertNull(shortcutChat("chat_"))
        assertNull(shortcutChat("chat_abc"))
        assertNull(shortcutChat("chat_ZLY_42"))
        assertNull(shortcutChat("chat_a1b2_abc"))
    }
}
