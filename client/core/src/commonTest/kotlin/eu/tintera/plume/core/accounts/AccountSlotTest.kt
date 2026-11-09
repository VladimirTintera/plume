package eu.tintera.plume.core.accounts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Jména adresářů a tajemství podle slotu. Primární slot musí dávat PŘESNĚ dnešní jména: jinak
 * by aplikace po aktualizaci nenašla databázi ani klíč k ní a uživatel by se musel přihlásit znovu.
 */
class AccountSlotTest {

    @Test
    fun primarniSlotMaDnesniJmena() {
        val slot = AccountSlot.Primary
        assertTrue(slot.isPrimary)
        assertEquals("tdlib", slot.directory("tdlib"))
        assertEquals("tdlib-files", slot.directory("tdlib-files"))
        assertEquals("plume_tdlib_database", slot.secretName("plume_tdlib_database"))
        assertEquals("tdlib-key", slot.secretName("tdlib-key", separator = "-"))
        assertEquals("database-key", slot.secretName("database-key", separator = "-"))
    }

    @Test
    fun dalsiSlotMaVlastniPodadresarAPriponu() {
        val slot = AccountSlot("a1b2")
        assertFalse(slot.isPrimary)
        assertEquals("accounts/a1b2/tdlib", slot.directory("tdlib"))
        assertEquals("accounts/a1b2/tdlib-files", slot.directory("tdlib-files"))
        assertEquals("plume_tdlib_database_a1b2", slot.secretName("plume_tdlib_database"))
        assertEquals("tdlib-key-a1b2", slot.secretName("tdlib-key", separator = "-"))
    }

    @Test
    fun primarniSlotJeHodnota() {
        assertEquals(AccountSlot.Primary, AccountSlot("0"))
        assertTrue(AccountSlot("0").isPrimary)
    }

    /** Id se skládá do cest a aliasů, takže lomítko, tečka ani velké písmeno projít nesmí. */
    @Test
    fun neplatneIdNeprojde() {
        listOf("", "../x", "a.b", "A1", "a b", "a_b").forEach { id ->
            assertFailsWith<IllegalArgumentException>(id) { AccountSlot(id) }
        }
    }

    /** Staré intenty a zástupci slot nenesou a patří primárnímu účtu. */
    @Test
    fun chybejiciSlotJePrimarni() {
        assertEquals(AccountSlot.Primary, AccountSlot.parse(null))
        assertEquals(AccountSlot("a1b2"), AccountSlot.parse("a1b2"))
    }

    /** Extra do exportované aktivity může poslat kdokoli, aplikace kvůli němu nesmí spadnout. */
    @Test
    fun necitelnySlotNeniVyjimka() {
        listOf("", "../x", "A1").forEach { assertEquals(null, AccountSlot.parse(it)) }
    }
}
