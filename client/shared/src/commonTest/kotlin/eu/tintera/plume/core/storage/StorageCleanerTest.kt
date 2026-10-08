package eu.tintera.plume.core.storage

import eu.tintera.plume.core.settings.KeepMedia
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StorageCleanerTest {

    private val now = 1_700_000_000L

    @Test
    fun navzdyNeuklidiNikdy() {
        assertFalse(cleanupDue(KeepMedia.Forever, lastRunAt = null, now = now))
        assertFalse(cleanupDue(KeepMedia.Forever, lastRunAt = now - 10 * CLEANUP_INTERVAL_SECONDS, now = now))
    }

    @Test
    fun poprveSeUklidiHned() {
        assertTrue(cleanupDue(KeepMedia.Week, lastRunAt = null, now = now))
    }

    @Test
    fun nejvysJednouDenne() {
        assertFalse(cleanupDue(KeepMedia.ThreeDays, lastRunAt = now - CLEANUP_INTERVAL_SECONDS + 1, now = now))
        assertTrue(cleanupDue(KeepMedia.ThreeDays, lastRunAt = now - CLEANUP_INTERVAL_SECONDS, now = now))
    }

    /** Hodiny posunuté zpátky nesmí úklid zablokovat na dny. */
    @Test
    fun casVBudoucnostiJeDavno() {
        assertTrue(cleanupDue(KeepMedia.Month, lastRunAt = now + 3600, now = now))
    }
}
