package eu.tintera.plume.features.chats

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArchiveSwipeGestureTest {

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
