package eu.tintera.plume.core.telegram

import dev.g000sha256.tdl.dto.MessageSchedulingStateSendAtDate
import dev.g000sha256.tdl.dto.MessageSchedulingStateSendWhenOnline
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SchedulingTest {

    @Test
    fun vychoziOdeslaniNeposilaVolby() {
        // Bez voleb jde `options = null`, jako před plánováním — TDLib nic nemění.
        assertNull(SendOptions().toTdl())
    }

    @Test
    fun bezZvukuJeDisableNotification() {
        val options = SendOptions(silent = true).toTdl()!!
        assertTrue(options.disableNotification)
        assertNull(options.schedulingState)
    }

    @Test
    fun naplanovaniSePrevadiTamIZpet() {
        val at = SendOptions(schedule = MessageSchedule.At(1_790_440_000)).toTdl()!!.schedulingState
        assertEquals(1_790_440_000, assertIs<MessageSchedulingStateSendAtDate>(at).sendDate)
        assertEquals(MessageSchedule.At(1_790_440_000), at.toSchedule())

        val online = MessageSchedule.WhenOnline.toTdl()
        assertIs<MessageSchedulingStateSendWhenOnline>(online)
        assertEquals(MessageSchedule.WhenOnline, online.toSchedule())
    }
}
