package eu.tintera.plume.features.webapp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

class WebAppGuardsTest {

    @Test
    fun bezGestaNicNeprojde() {
        assertFalse(WebAppGestureGate(TestTimeSource()).consume())
    }

    @Test
    fun gestoPlatiDesetSekundAJenJednou() {
        val time = TestTimeSource()
        val gate = WebAppGestureGate(time)
        gate.gesture()
        time += 9.seconds
        assertTrue(gate.consume())
        // Jedno ťuknutí = jedna akce: druhý odkaz ve smyčce už neprojde.
        assertFalse(gate.consume())
    }

    @Test
    fun stareGestoNeplati() {
        val time = TestTimeSource()
        val gate = WebAppGestureGate(time)
        gate.gesture()
        time += GESTURE_WINDOW + 1.milliseconds
        assertFalse(gate.consume())
    }

    @Test
    fun dialogyOtevreneVeSmyckeDostanouPauzu() {
        val time = TestTimeSource()
        val throttle = WebAppDialogThrottle(time)
        assertEquals(Duration.ZERO, throttle.wait())
        // Stránka dialog po každém zavření hned otevře znovu.
        repeat(2) {
            throttle.closed()
            time += 50.milliseconds
            assertEquals(Duration.ZERO, throttle.wait())
        }
        throttle.closed()
        time += 50.milliseconds
        assertEquals(DIALOG_COOLDOWN, throttle.wait())
        // V pauze nic, ani bez dalšího zavření; po ní zase ano.
        time += 1.seconds
        assertEquals(DIALOG_COOLDOWN - 1.seconds, throttle.wait())
        time += DIALOG_COOLDOWN
        assertEquals(Duration.ZERO, throttle.wait())
    }

    @Test
    fun dialogPoChvilceSePocitadloNuluje() {
        val time = TestTimeSource()
        val throttle = WebAppDialogThrottle(time)
        repeat(5) {
            throttle.closed()
            // Člověk, který dialog zavře a za chvíli udělá něco dalšího, smyčka není.
            time += 1.seconds
            assertEquals(Duration.ZERO, throttle.wait())
        }
    }
}
