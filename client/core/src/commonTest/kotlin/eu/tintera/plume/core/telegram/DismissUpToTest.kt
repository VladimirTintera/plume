package eu.tintera.plume.core.telegram

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Po které id se odhrnutá notifikace odebere v TDLib ([dismissUpTo]). */
class DismissUpToTest {

    @Test
    fun nejvyssiIdMeziRadky() {
        assertEquals(42, dismissUpTo(listOf(40, 42, 41)))
    }

    /** Vlastní odpověď a řádky převzaté po restartu id nenesou; počítá se z ostatních. */
    @Test
    fun radkyBezIdSePreskoci() {
        assertEquals(17, dismissUpTo(listOf(null, 17, null)))
    }

    /** Bez jediného id od TDLib se TDLib nic neříká — hádat id by mohlo odebrat i novější. */
    @Test
    fun bezIdNicNeodebira() {
        assertNull(dismissUpTo(listOf(null, null)))
        assertNull(dismissUpTo(emptyList()))
    }
}
