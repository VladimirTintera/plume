package eu.tintera.plume.core.settings

import eu.tintera.plume.core.telegram.NotificationChannelKind
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QuietHoursTest {

    private fun at(hour: Int, minute: Int = 0) = hour * 60 + minute

    /** Obvyklý případ: úsek přes půlnoc. */
    @Test
    fun usekPresPulnoc() {
        val night = QuietHours(enabled = true, start = at(22), end = at(7))
        assertTrue(night.contains(at(22)))
        assertTrue(night.contains(at(23, 59)))
        assertTrue(night.contains(at(0)))
        assertTrue(night.contains(at(6, 59)))
        assertFalse(night.contains(at(7)))
        assertFalse(night.contains(at(12)))
        assertFalse(night.contains(at(21, 59)))
    }

    @Test
    fun usekVeDne() {
        val work = QuietHours(enabled = true, start = at(9), end = at(17))
        assertTrue(work.contains(at(9)))
        assertTrue(work.contains(at(16, 59)))
        assertFalse(work.contains(at(17)))
        assertFalse(work.contains(at(8, 59)))
    }

    @Test
    fun stejnyZacatekAKonecJePrazdny() {
        assertFalse(QuietHours(enabled = true, start = at(8), end = at(8)).contains(at(8)))
    }

    @Test
    fun zminkyZvoniVzdy() {
        val night = QuietHours(enabled = true)
        assertFalse(night.silences(NotificationChannelKind.Mentions, at(23)))
        assertTrue(night.silences(NotificationChannelKind.Private, at(23)))
        assertTrue(night.silences(NotificationChannelKind.Groups, at(23)))
        assertTrue(night.silences(NotificationChannelKind.Channels, at(23)))
    }

    @Test
    fun vypnuteNeztisiNic() {
        assertFalse(QuietHours(enabled = false).silences(NotificationChannelKind.Private, at(23)))
    }

    @Test
    fun mimoUsekNeztisi() {
        assertFalse(QuietHours(enabled = true).silences(NotificationChannelKind.Private, at(12)))
    }
}
