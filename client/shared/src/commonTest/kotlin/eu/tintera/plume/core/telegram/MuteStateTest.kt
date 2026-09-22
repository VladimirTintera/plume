package eu.tintera.plume.core.telegram

import kotlin.test.Test
import kotlin.test.assertEquals

class MuteStateTest {

    private val now = 1_800_000_000L

    @Test
    fun nulaZnamenaZapnuto() {
        assertEquals(MuteState.Off, muteState(muteForSeconds = 0, nowEpochSeconds = now))
    }

    @Test
    fun docasneZtlumeniKonciVBudoucnu() {
        assertEquals(MuteState.Until(now + 3_600), muteState(muteForSeconds = 3_600, nowEpochSeconds = now))
    }

    /** Navždy zapisujeme `Int.MAX_VALUE`, jiní klienti i „jen" roky — obojí je navždy. */
    @Test
    fun viceNezRokJeNavzdy() {
        assertEquals(MuteState.Forever, muteState(muteForSeconds = Int.MAX_VALUE, nowEpochSeconds = now))
        assertEquals(MuteState.Forever, muteState(muteForSeconds = 5 * 365 * 24 * 3_600, nowEpochSeconds = now))
        assertEquals(MuteState.Until(now + 2 * 24 * 3_600), muteState(muteForSeconds = 2 * 24 * 3_600, nowEpochSeconds = now))
    }
}
