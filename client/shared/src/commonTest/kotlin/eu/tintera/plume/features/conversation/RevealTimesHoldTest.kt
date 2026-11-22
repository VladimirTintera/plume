package eu.tintera.plume.features.conversation

import kotlin.test.Test
import kotlin.test.assertEquals

class RevealTimesHoldTest {

    private val max = 100f

    @Test
    fun countdownStartsWhenTimesAreAlmostOut() {
        assertEquals(HoldAction.None, holdAction(finger = 80f, revealMax = max, holding = false))
        assertEquals(HoldAction.Start, holdAction(finger = 85f, revealMax = max, holding = false))
        assertEquals(HoldAction.Start, holdAction(finger = 140f, revealMax = max, holding = false))
    }

    @Test
    fun fingerJitterDoesNotCancelCountdown() {
        // Prst v klidu cuká o pár pixelů oběma směry; odpočet musí běžet dál.
        listOf(100f, 98f, 101f, 97f, 99f, 86f, 70f).forEach { finger ->
            assertEquals(HoldAction.None, holdAction(finger, revealMax = max, holding = true), "prst na $finger")
        }
    }

    @Test
    fun realPullBackCancelsCountdown() {
        assertEquals(HoldAction.Cancel, holdAction(finger = 59f, revealMax = max, holding = true))
        assertEquals(HoldAction.Cancel, holdAction(finger = 0f, revealMax = max, holding = true))
    }

    @Test
    fun runningCountdownIsNotStartedAgain() {
        assertEquals(HoldAction.None, holdAction(finger = 100f, revealMax = max, holding = true))
    }
}
