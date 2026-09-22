package eu.tintera.plume.features.chats

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArchiveSwipeGestureTest {

    private val slop = 36f

    @Test
    fun kratkyPohybNicNerozhoduje() {
        assertEquals(ArchiveSwipeDecision.Undecided, archiveSwipeDecision(dx = 20f, dy = 0f, slop = slop))
        assertEquals(ArchiveSwipeDecision.Undecided, archiveSwipeDecision(dx = -20f, dy = 10f, slop = slop))
    }

    @Test
    fun vyrazneVodorovnyTahSiVezmeArchivace() {
        assertEquals(ArchiveSwipeDecision.Swipe, archiveSwipeDecision(dx = 40f, dy = 5f, slop = slop))
        assertEquals(ArchiveSwipeDecision.Swipe, archiveSwipeDecision(dx = -40f, dy = -8f, slop = slop))
    }

    /** Palec jde na začátku scrollu obloukem do strany — a právě tenhle tah patří seznamu. */
    @Test
    fun sikmyTahPatriSeznamu() {
        assertEquals(ArchiveSwipeDecision.Ignore, archiveSwipeDecision(dx = 30f, dy = 25f, slop = slop))
        assertEquals(ArchiveSwipeDecision.Ignore, archiveSwipeDecision(dx = -30f, dy = 20f, slop = slop))
        assertEquals(ArchiveSwipeDecision.Ignore, archiveSwipeDecision(dx = 5f, dy = 40f, slop = slop))
    }

    @Test
    fun prahJePodilSirky() {
        assertEquals(400f, archiveSwipeThreshold(width = 1000f, cap = 500f))
    }

    @Test
    fun sirokaPolozkaMaPrahZastropovany() {
        assertEquals(500f, archiveSwipeThreshold(width = 3000f, cap = 500f))
    }

    /** Než je položka změřená, nesmí puštění archivovat nic. */
    @Test
    fun nezmerenaPolozkaPrahNema() {
        assertTrue(archiveSwipeThreshold(width = 0f, cap = 500f).isInfinite())
        assertFalse(9999f >= archiveSwipeThreshold(width = 0f, cap = 500f))
    }
}
