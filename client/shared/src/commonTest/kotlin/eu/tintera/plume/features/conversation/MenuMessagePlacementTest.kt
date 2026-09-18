package eu.tintera.plume.features.conversation

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Kam se zpráva při otevřené nabídce posune a jak se zmenší. Obrazovka 800 dp, nahoře mez 40,
 * dole 40, pruh reakcí zabere 60 a nabídka pod zprávou 200 — na zprávu zbývá 460 (od 100 do 560).
 */
class MenuMessagePlacementTest {

    private fun place(top: Int, height: Int, quickRow: Int = 60, menu: Int = 200) = placeMenuMessage(
        anchorTop = top.dp,
        anchorHeight = height.dp,
        screenHeight = 800.dp,
        topMargin = 40.dp,
        bottomMargin = 40.dp,
        quickRowSpace = quickRow.dp,
        menuSpace = menu.dp,
    )

    private fun assertClose(expected: Dp, actual: Dp) =
        assertTrue(kotlin.math.abs((expected - actual).value) < 0.01f, "čekáno $expected, je $actual")

    @Test
    fun zpravaVPohleduSeNehne() {
        val result = place(top = 300, height = 100)
        assertEquals(300.dp, result.top)
        assertEquals(1f, result.scale)
    }

    @Test
    fun zpravaPodHlavickouSjedeDolu() {
        // Nad ní se musí vejít pruh reakcí (40 + 60).
        assertEquals(100.dp, place(top = -50, height = 200).top)
    }

    @Test
    fun zpravaUSpodniHranyVyjedeNahoruAPodNiSeVejdeNabidka() {
        // Spodek zprávy nejvýš na 560, pod ní nabídka.
        assertEquals(360.dp, place(top = 620, height = 200).top)
    }

    @Test
    fun bezPruhuReakciStaciHorniMez() {
        assertEquals(40.dp, place(top = 10, height = 100, quickRow = 0).top)
    }

    @Test
    fun vysokaZpravaSeZmensi() {
        // 575 dp se do 460 vejde při měřítku 0,8 — celá, bez ořezu.
        val result = place(top = 0, height = 575)
        assertEquals(100.dp, result.top)
        assertClose(460.dp, result.visibleHeight)
        assertTrue(kotlin.math.abs(result.scale - 0.8f) < 0.001f)
        assertFalse(result.isCropped(575.dp))
    }

    @Test
    fun velmiVysokaZpravaSeZmensiNaPodlahuAOrizne() {
        // 1200 dp by se muselo zmenšit pod 0,6 — zůstane 0,6 a ořízne se na 460.
        val result = place(top = -300, height = 1200)
        assertEquals(MIN_MESSAGE_SCALE, result.scale)
        assertClose(460.dp, result.visibleHeight)
        assertTrue(result.isCropped(1200.dp))
    }

    @Test
    fun nizkeOknoNeskonciZapornouVyskou() {
        val result = place(top = 0, height = 300, menu = 900)
        assertEquals(0.dp, result.visibleHeight)
    }
}
