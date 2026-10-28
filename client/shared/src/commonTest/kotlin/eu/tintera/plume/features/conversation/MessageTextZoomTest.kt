package eu.tintera.plume.features.conversation

import androidx.compose.ui.input.key.Key
import eu.tintera.plume.core.settings.UnimplementedAppSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MessageTextZoomTest {

    private class Settings : UnimplementedAppSettings() {
        val saved = mutableListOf<Int>()
        override fun setMessageTextSize(size: Int) {
            saved += size
        }
    }

    @Test
    fun stipnutiOCtvrtinuJdeNaKrajRozsahu() {
        assertEquals(20, pinchedTextSize(16, 1.25f))
        assertEquals(12, pinchedTextSize(16, 0.75f))
        assertEquals(17, pinchedTextSize(16, 1.06f))
        assertEquals(20, pinchedTextSize(16, 3f), "dál než rozsah nastavení nejde")
        assertEquals(12, pinchedTextSize(16, 0.1f))
    }

    @Test
    fun klavesyPlusMinusANula() {
        assertEquals(1, textSizeKeyStep(Key.Equals))
        assertEquals(1, textSizeKeyStep(Key.NumPadAdd))
        assertEquals(-1, textSizeKeyStep(Key.Minus))
        assertNull(textSizeKeyStep(Key.Zero))
        assertEquals(NO_STEP, textSizeKeyStep(Key.A))
    }

    @Test
    fun stipnutiUkladaAzPoPusteni() {
        val settings = Settings()
        val zoom = MessageTextZoom(settings).apply { settle(16) }

        val start = zoom.startPinch()
        zoom.pinchTo(pinchedTextSize(start, 1.1f))
        zoom.pinchTo(pinchedTextSize(start, 1.2f))
        assertEquals(emptyList(), settings.saved, "během štípnutí se neukládá")
        assertEquals(19, zoom.preview)

        zoom.endPinch()
        assertEquals(listOf(19), settings.saved)
        assertEquals(19, zoom.preview, "náhled drží, dokud uložená hodnota nedorazí")
        zoom.settle(19)
        assertNull(zoom.preview)
    }

    @Test
    fun stipnutiZpetNaPuvodniNicNeulozi() {
        val settings = Settings()
        val zoom = MessageTextZoom(settings).apply { settle(16) }
        val start = zoom.startPinch()
        zoom.pinchTo(pinchedTextSize(start, 1.2f))
        zoom.pinchTo(pinchedTextSize(start, 1.0f))
        zoom.endPinch()
        assertEquals(emptyList(), settings.saved)
        assertNull(zoom.preview)
    }

    @Test
    fun klavesnicePosouvaOBodANulaVraciVychozi() {
        val settings = Settings()
        val zoom = MessageTextZoom(settings).apply { settle(19) }
        zoom.step(1)
        zoom.settle(20)
        zoom.step(1)
        assertEquals(listOf(20), settings.saved, "nad horní mez se nepřidá nic")
        zoom.step(null)
        assertEquals(listOf(20, 16), settings.saved)
    }
}
