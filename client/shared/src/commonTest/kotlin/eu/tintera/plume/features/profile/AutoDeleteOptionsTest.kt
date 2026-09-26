package eu.tintera.plume.features.profile

import kotlin.test.Test
import kotlin.test.assertEquals

/** Nabízené doby automatického mazání. */
class AutoDeleteOptionsTest {

    private val day = 24 * 60 * 60

    @Test
    fun zakladniDoby() {
        assertEquals(listOf(0, day, 7 * day, 31 * day), autoDeleteOptions(0))
        assertEquals(listOf(0, day, 7 * day, 31 * day), autoDeleteOptions(7 * day))
    }

    @Test
    fun jinaPlatnaDobaSeZaradi() {
        // Nastavená jiným klientem nebo botem — musí být vidět, co platí.
        assertEquals(listOf(0, day, 2 * day, 7 * day, 31 * day), autoDeleteOptions(2 * day))
    }
}
