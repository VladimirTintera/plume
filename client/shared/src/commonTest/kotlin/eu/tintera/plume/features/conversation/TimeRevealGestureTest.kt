package eu.tintera.plume.features.conversation

import kotlin.test.Test
import kotlin.test.assertEquals

/** Kdy si tah nad seznamem vezme odkrývání časů a kdy ho nechá scrollu. */
class TimeRevealGestureTest {

    private val slop = 16f

    @Test
    fun kratkyPohybSeJesteNerozhoduje() {
        assertEquals(TimeRevealDecision.Undecided, timeRevealDecision(dx = -12f, dy = 0f, slop = slop))
    }

    @Test
    fun cistyTahDolevaOdkryvaCasy() {
        assertEquals(TimeRevealDecision.Reveal, timeRevealDecision(dx = -20f, dy = 3f, slop = slop))
    }

    @Test
    fun sikmyTahDolevaPatriScrollu() {
        // Palec levé ruky: nahoru a přitom znatelně doleva. Dřív tohle vysouvalo časy.
        assertEquals(TimeRevealDecision.Ignore, timeRevealDecision(dx = -14f, dy = -12f, slop = slop))
        assertEquals(TimeRevealDecision.Ignore, timeRevealDecision(dx = -16f, dy = 10f, slop = slop))
    }

    @Test
    fun tahDopravaNeodkryvaNic() {
        assertEquals(TimeRevealDecision.Ignore, timeRevealDecision(dx = 20f, dy = 0f, slop = slop))
    }
}
