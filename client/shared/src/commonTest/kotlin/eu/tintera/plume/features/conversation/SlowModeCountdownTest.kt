package eu.tintera.plume.features.conversation

import kotlin.test.Test
import kotlin.test.assertEquals

class SlowModeCountdownTest {

    @Test
    fun sekundyNahoru() {
        // Zbývá 26,2 s: ukáže se 27, „26" by o kus předběhlo skutečnost.
        assertEquals(27, secondsLeft(until = 26_200, now = 0))
        assertEquals(1, secondsLeft(until = 1, now = 0))
    }

    @Test
    fun poUplynutiNebezCekaniNula() {
        assertEquals(0, secondsLeft(until = 1_000, now = 1_000))
        assertEquals(0, secondsLeft(until = 500, now = 1_000))
        assertEquals(0, secondsLeft(until = null, now = 1_000))
    }

    @Test
    fun popisekJakoCasHlasovky() {
        assertEquals("0:27", slowModeLabel(27))
        assertEquals("4:05", slowModeLabel(245))
    }
}
