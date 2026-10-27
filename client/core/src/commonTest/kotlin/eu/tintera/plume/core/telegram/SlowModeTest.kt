package eu.tintera.plume.core.telegram

import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.error_slowmode_wait_minutes
import eu.tintera.plume.resources.error_slowmode_wait_seconds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SlowModeTest {

    @Test
    fun zbyvajiciSekundyNaOkamzik() {
        assertEquals(1_000_000L + 27_500L, slowModeUntil(27.5, now = 1_000_000L))
    }

    @Test
    fun nulaZnamenaNecekat() {
        assertNull(slowModeUntil(0.0, now = 1_000_000L))
    }

    @Test
    fun hlaskaPodDveMinutyVSekundachJinakMinutyNahoru() {
        // Vedle odpočtu „1:01" se „za 2 min" četlo jako rozpor; pod dvě minuty proto sekundy.
        assertEquals(UiText(Res.string.error_slowmode_wait_seconds, 61), slowModeWaitText(61))
        // 121 s je „za 3 min", ne „za 2 min" — to by tvrdilo, že za dvě minuty už jde psát.
        assertEquals(UiText(Res.string.error_slowmode_wait_minutes, 3), slowModeWaitText(121))
    }
}
