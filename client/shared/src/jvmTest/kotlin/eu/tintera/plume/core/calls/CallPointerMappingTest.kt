package eu.tintera.plume.core.calls

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import eu.tintera.plume.features.calls.pointerOnFrame
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CallPointerMappingTest {

    private fun assertNear(expected: Offset, actual: Offset?) {
        requireNotNull(actual)
        assertEquals(expected.x, actual.x, 0.001f)
        assertEquals(expected.y, actual.y, 0.001f)
    }

    @Test
    fun portraitScreenOnPortraitPhoneFillsAndCrops() {
        // Telefon na výšku 1000 × 2000 ukazuje obrazovku 1 : 2,22 — vyplní a ořízne se.
        val size = IntSize(1000, 2000)
        val aspect = 0.45f
        assertNear(Offset(0.5f, 0.5f), pointerOnFrame(Offset(500f, 1000f), size, aspect))
        // Plocha (1 : 2) je širší než obraz, takže vede šířka: obraz 1000 × 2222, nahoře
        // a dole přečnívá o 111 px.
        assertNear(Offset(0f, 111f / 2222f), pointerOnFrame(Offset(0f, 0f), size, aspect))
    }

    @Test
    fun portraitScreenInLandscapeWindowFitsWithBars() {
        // Okno na šířku 2000 × 1000, obrazovka telefonu na výšku 1 : 2 → vejde se celá, pruhy.
        val size = IntSize(2000, 1000)
        val aspect = 0.5f
        assertNear(Offset(0.5f, 0.5f), pointerOnFrame(Offset(1000f, 500f), size, aspect))
        assertNear(Offset(0f, 0f), pointerOnFrame(Offset(750f, 0f), size, aspect))
        assertNear(Offset(1f, 1f), pointerOnFrame(Offset(1250f, 1000f), size, aspect))
        // V pruhu vedle obrazu se neukazuje.
        assertNull(pointerOnFrame(Offset(100f, 500f), size, aspect))
    }

    @Test
    fun sharedScreenFitsWholeEvenInSameOrientation() {
        // Sdílená obrazovka (fit): 1 : 2,22 na ploše 1 : 2 se vejde celá (900 px), po stranách pruhy 50 px.
        val size = IntSize(1000, 2000)
        assertNear(Offset(0f, 0f), pointerOnFrame(Offset(50f, 0f), size, 0.45f, fit = true))
        assertNull(pointerOnFrame(Offset(20f, 1000f), size, 0.45f, fit = true))
    }
}
