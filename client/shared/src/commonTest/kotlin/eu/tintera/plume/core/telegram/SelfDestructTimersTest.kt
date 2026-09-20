package eu.tintera.plume.core.telegram

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SelfDestructTimersTest {

    @Test
    fun `casovac vraci null pred spustenim`() {
        val timers = SelfDestructTimers()
        assertNull(timers.remainingSeconds(123L))
        assertFalse(timers.isConsumed(123L))
    }

    @Test
    fun `spusteni casovace nastavi expiraci a nerestartuje se pri dalsim volani`() {
        val timers = SelfDestructTimers()
        val expiresAt1 = timers.startTimer(123L, 10)
        val expiresAt2 = timers.startTimer(123L, 15)

        assertEquals(expiresAt1, expiresAt2)
        val remaining = timers.remainingSeconds(123L)
        assertNotNull(remaining)
        assertTrue(remaining in 9..10)
        assertFalse(timers.isConsumed(123L))
    }

    @Test
    fun `oznaceni za spotrebovane zneplatni zpravu`() {
        val timers = SelfDestructTimers()
        timers.startTimer(123L, 10)
        assertFalse(timers.isConsumed(123L))

        timers.markConsumed(123L)
        assertTrue(timers.isConsumed(123L))
        assertTrue(123L in timers.consumedIds.value)
    }

    @Test
    fun `view once zprava se po oznaceni stane spotrebovanou`() {
        val timers = SelfDestructTimers()
        assertFalse(timers.isConsumed(456L))
        timers.markConsumed(456L)
        assertTrue(timers.isConsumed(456L))
    }
}
