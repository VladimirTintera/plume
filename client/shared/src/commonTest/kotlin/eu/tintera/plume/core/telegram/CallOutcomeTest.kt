package eu.tintera.plume.core.telegram

import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.call_missed
import eu.tintera.plume.resources.group_call_active
import eu.tintera.plume.resources.group_call_missed
import eu.tintera.plume.resources.group_video_call_cancelled
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

    @Test
    fun skupinovyHovorMaVlastniNazev() {
        // Dřív bublina „Skupinový hovor" bez směru a zmeškání; teď jako hovor ve dvou.
        assertEquals(Res.string.call_missed, CallOutcome.Missed.title(isVideo = false))
        assertEquals(Res.string.group_call_missed, CallOutcome.Missed.title(isVideo = false, isGroup = true))
        assertEquals(Res.string.group_video_call_cancelled, CallOutcome.Cancelled.title(isVideo = true, isGroup = true))
    }

    @Test
    fun probihajiciSkupinovyHovorNehlasiVysledek() {
        assertEquals(Res.string.group_call_active, CallOutcome.Incoming.title(isVideo = false, isGroup = true, isActive = true))
    }
}
