package eu.tintera.plume.core.telegram

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Jak se zpráva o hovoru pojmenuje podle směru a konce. */
class CallOutcomeTest {

    @Test
    fun odchoziNevzatyIOdmitnutyJeZruseny() {
        // Volající se nedovolal; jestli protějšek nechal zvonit, nebo to típl, se neprozrazuje.
        assertEquals(CallOutcome.Cancelled, callOutcome(isOutgoing = true, end = CallEnd.Missed))
        assertEquals(CallOutcome.Cancelled, callOutcome(isOutgoing = true, end = CallEnd.Declined))
    }

    @Test
    fun prichoziRozlisujeZmeskanyAOdmitnuty() {
        assertEquals(CallOutcome.Missed, callOutcome(isOutgoing = false, end = CallEnd.Missed))
        assertEquals(CallOutcome.Declined, callOutcome(isOutgoing = false, end = CallEnd.Declined))
    }

    @Test
    fun probehlyHovorNeseJenSmer() {
        for (end in listOf(CallEnd.HungUp, CallEnd.Disconnected, CallEnd.Unknown)) {
            assertEquals(CallOutcome.Outgoing, callOutcome(isOutgoing = true, end = end))
            assertEquals(CallOutcome.Incoming, callOutcome(isOutgoing = false, end = end))
        }
    }

    @Test
    fun chybovaBarvaJenUHovoruKteryChceOdpoved() {
        assertTrue(CallOutcome.Missed.isFailure)
        assertTrue(CallOutcome.Declined.isFailure)
        assertFalse(CallOutcome.Cancelled.isFailure)
        assertFalse(CallOutcome.Outgoing.isFailure)
        assertFalse(CallOutcome.Incoming.isFailure)
    }
}
