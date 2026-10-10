package eu.tintera.plume.core.ui

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Otočení a ořez fotky: body tahů musí po cestě tam a zpátky skončit tam, kde začaly. */
class PhotoMarkupGeometryTest {

    /** Otočení po směru hodin: levý horní roh originálu je v otočené fotce vpravo nahoře. */
    @Test
    fun otoceniPoSmeruHodin() {
        assertEquals(1f to 0f, rotatePoint(0f, 0f, 1))
        assertEquals(1f to 1f, rotatePoint(0f, 0f, 2))
        assertEquals(0f to 1f, rotatePoint(0f, 0f, 3))
        assertEquals(0f to 0f, rotatePoint(0f, 0f, 4))
    }

    @Test
    fun otoceniTamAZpatky() {
        for (turns in -1..5) {
            val (x, y) = rotatePoint(0.2f, 0.7f, turns)
            assertClose(0.2f to 0.7f, unrotatePoint(x, y, turns))
        }
    }

    @Test
    fun vysledekTamAZpatky() {
        val markup = PhotoMarkup(quarterTurns = 1, crop = CropBox(0.1f, 0.2f, 0.6f, 0.9f))
        val (x, y) = originalToResult(0.3f, 0.4f, markup)
        assertClose(0.3f to 0.4f, resultToOriginal(x, y, markup))
    }

    /** Výřez levé půlky vodorovné fotky 2000×1000 po otočení: 1000 široký, 1000 vysoký. */
    @Test
    fun rozmeryVysledku() {
        assertEquals(1000 to 2000, rotatedSize(2000, 1000, 1))
        assertEquals(1000 to 1000, resultSize(2000, 1000, PhotoMarkup(quarterTurns = 1, crop = CropBox(0f, 0f, 1f, 0.5f))))
        assertEquals(2000 to 1000, resultSize(2000, 1000, PhotoMarkup()))
    }

    /** Výřez se po „Otočit" otočí s fotkou, ať ukazuje na týž obsah. */
    @Test
    fun vyrezSeOtociSFotkou() {
        val crop = CropBox(0.1f, 0.2f, 0.5f, 0.6f)
        val turned = crop.rotated(1)
        // Levý horní roh výřezu je po otočení vpravo nahoře, tedy (1 − 0.6, 0.1).
        assertClose(0.4f to 0.1f, turned.left to turned.top)
        assertClose(0.8f to 0.5f, turned.right to turned.bottom)
        val back = turned.rotated(3)
        assertClose(crop.left to crop.top, back.left to back.top)
        assertClose(crop.right to crop.bottom, back.right to back.bottom)
    }

    /** Posun celého výřezu se o okraj zastaví a výřez nezmenší. */
    @Test
    fun posunVyrezuZustaneVeFotce() {
        val moved = CropBox(0.2f, 0.2f, 0.6f, 0.6f).dragged(CropHandle.Move, 0.9f, -0.5f)
        assertClose(0.6f to 0f, moved.left to moved.top)
        assertClose(1f to 0.4f, moved.right to moved.bottom)
    }

    /** Roh nejde přetáhnout přes protější — výřez zůstane aspoň tak velký jako minimum. */
    @Test
    fun rohNejdePretahnout() {
        val crop = CropBox(0.2f, 0.2f, 0.6f, 0.6f).dragged(CropHandle.TopLeft, 0.9f, 0.9f)
        assertClose(0.5f to 0.5f, crop.left to crop.top)
        val out = CropBox(0.2f, 0.2f, 0.6f, 0.6f).dragged(CropHandle.BottomRight, 0.9f, 0.9f)
        assertClose(1f to 1f, out.right to out.bottom)
    }

    @Test
    fun prazdnaUprava() {
        assertTrue(PhotoMarkup().isEmpty)
        assertTrue(PhotoMarkup(quarterTurns = 4).isEmpty)
        assertTrue(!PhotoMarkup(quarterTurns = 1).isEmpty)
        assertTrue(!PhotoMarkup(crop = CropBox(0f, 0f, 0.5f, 1f)).isEmpty)
    }

    private fun assertClose(expected: Pair<Float, Float>, actual: Pair<Float, Float>) {
        assertTrue(abs(expected.first - actual.first) < 1e-5f && abs(expected.second - actual.second) < 1e-5f, "$expected ≠ $actual")
    }
}
