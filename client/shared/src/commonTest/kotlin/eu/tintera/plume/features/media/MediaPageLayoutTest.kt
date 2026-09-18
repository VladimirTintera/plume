package eu.tintera.plume.features.media

import androidx.compose.ui.unit.IntSize
import kotlin.test.Test
import kotlin.test.assertEquals

/** Vepsání snímku do místa mezi lištami. */
class MediaPageLayoutTest {

    @Test
    fun vysokaFotkaNaraziNaVysku() {
        assertEquals(IntSize(300, 600), fitInto(maxWidth = 800, maxHeight = 600, aspectRatio = 0.5f))
    }

    @Test
    fun sirokaFotkaNaraziNaSirku() {
        assertEquals(IntSize(800, 400), fitInto(maxWidth = 800, maxHeight = 1000, aspectRatio = 2f))
    }

    @Test
    fun neznamyPomerJeCtverec() {
        assertEquals(IntSize(500, 500), fitInto(maxWidth = 500, maxHeight = 900, aspectRatio = Float.NaN))
        assertEquals(1f, aspectRatioOf(0, 0))
    }

    @Test
    fun zadneMistoZadnaKarta() {
        assertEquals(IntSize.Zero, fitInto(maxWidth = 500, maxHeight = 0, aspectRatio = 1f))
    }
}
