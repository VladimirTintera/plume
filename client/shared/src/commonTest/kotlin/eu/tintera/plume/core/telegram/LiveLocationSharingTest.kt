package eu.tintera.plume.core.telegram

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LiveLocationSharingTest {

    private val prague = LiveFix(latitude = 50.0875, longitude = 14.4208)

    /** Zhruba 0,001° zeměpisné šířky je 111 m. */
    private fun north(meters: Double) = prague.copy(latitude = prague.latitude + meters / 111_195.0)

    @Test
    fun prvniPolohaJdeVzdycky() {
        assertTrue(shouldSendLiveFix(last = null, fix = prague, nowMs = 0))
    }

    @Test
    fun staniNaMisteSePosilaJenJednouZaCas() {
        val last = prague to 0L
        assertFalse(shouldSendLiveFix(last, prague, nowMs = 60_000))
        assertTrue(shouldSendLiveFix(last, prague, nowMs = 2 * 60_000))
    }

    @Test
    fun pohybSePosilaAleNeCastejiNezPoDesetiVterinach() {
        val last = prague to 0L
        // Kus, který na mapě vidět je, ale hned po předchozím: server by dostal úpravu na každý krok.
        assertFalse(shouldSendLiveFix(last, north(100.0), nowMs = 5_000))
        assertTrue(shouldSendLiveFix(last, north(100.0), nowMs = 10_000))
        // Pár metrů je šum GPS.
        assertFalse(shouldSendLiveFix(last, north(10.0), nowMs = 30_000))
    }

    @Test
    fun kdyzSeNekdoDivaPosilaSeHned() {
        // TDLib po `UpdateMessageLiveLocationViewed` čeká polohu hned.
        assertTrue(shouldSendLiveFix(prague to 0L, prague, nowMs = 1_000, viewed = true))
    }

    @Test
    fun zmenaSmeruSePosilaJenZnamy() {
        val last = prague.copy(heading = 90) to 0L
        assertTrue(shouldSendLiveFix(last, prague.copy(heading = 180), nowMs = 20_000))
        // Ztracený směr (stání) sám o sobě úpravu nevyvolá.
        assertFalse(shouldSendLiveFix(last, prague.copy(heading = 0), nowMs = 20_000))
    }

    @Test
    fun severJe360NeNeznamy() {
        assertEquals(360, headingOf(0f))
        assertEquals(360, headingOf(360f))
        assertEquals(90, headingOf(90.2f))
        assertEquals(0, headingOf(null))
        assertEquals(1, headingOf(0.6f))
    }

    @Test
    fun vzdalenostSedi() {
        assertEquals(100.0, distanceMeters(prague, north(100.0)), 0.5)
    }
}
