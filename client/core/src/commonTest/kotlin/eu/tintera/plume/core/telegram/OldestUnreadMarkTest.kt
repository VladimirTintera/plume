package eu.tintera.plume.core.telegram

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Kam skočit na nepřečtenou zmínku nebo reakci. */
class OldestUnreadMarkTest {

    @Test
    fun skaceSeNaNejstarsi() {
        // TDLib vrací od nejnovější, skáče se ale od nejstarší — jako u nepřečtených zpráv.
        assertEquals(10L, oldestOf(listOf(30L, 20L, 10L)))
    }

    @Test
    fun bezVysledkuNeniKamSkocit() {
        assertNull(oldestOf(emptyList()))
    }
}
