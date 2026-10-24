package eu.tintera.plume.core.telegram

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LiveLocationTest {

    @Test
    fun konecSeRatiOdOdeslaniZpravy() {
        // Hodinu sdílená poloha odeslaná v 1 000 000 končí o hodinu později, ne hodinu od teď.
        assertEquals(1_003_600L, liveLocationUntil(sentAt = 1_000_000, livePeriod = 3_600))
    }

    @Test
    fun sdileniBezKonce() {
        assertEquals(Long.MAX_VALUE, liveLocationUntil(sentAt = 1_000_000, livePeriod = 0x7FFFFFFF))
    }

    @Test
    fun obycejnaPolohaNeniZiva() {
        assertFalse(MessageMedia.Location(50.0, 14.0).isLive)
        assertTrue(MessageMedia.Location(50.0, 14.0, liveUntil = 1L).isLive)
    }
}
