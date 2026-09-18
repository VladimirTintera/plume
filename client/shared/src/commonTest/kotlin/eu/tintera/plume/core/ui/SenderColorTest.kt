package eu.tintera.plume.core.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SenderColorTest {

    @Test
    fun stejnyOdesilatelMaVzdyStejnouBarvu() {
        assertEquals(senderColorIndex(123456789L), senderColorIndex(123456789L))
    }

    @Test
    fun indexJeVzdyVRozsahu() {
        listOf(0L, -1L, Long.MIN_VALUE, Long.MAX_VALUE, 42L).forEach {
            assertTrue(senderColorIndex(it) in 0 until SENDER_COLOR_COUNT, "id $it")
        }
    }

    @Test
    fun odstinyJsouRozlozenePoKruhuOdPrimary() {
        assertEquals(250.0, senderHue(250.0, 0))
        assertEquals(295.0, senderHue(250.0, 1))
        // Přes 360 se přetočí zpátky.
        assertEquals(25.0, senderHue(250.0, 3))
    }
}
