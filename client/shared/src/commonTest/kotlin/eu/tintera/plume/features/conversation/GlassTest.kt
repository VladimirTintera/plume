package eu.tintera.plume.features.conversation

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class GlassTest {
    private val tint = Color(0xFF2B2930)

    /** Barva ve složkách 0–1 po matici [backdropMatrix] a tintu s krytím [TEXT_GLASS_TINT_ALPHA]. */
    private fun throughGlass(r: Float, g: Float, b: Float): FloatArray {
        val m = backdropMatrix(tint, TEXT_GLASS_BACKDROP_CONTRAST, GLASS_SATURATION).values
        val input = floatArrayOf(r, g, b)
        val t = floatArrayOf(tint.red, tint.green, tint.blue)
        return FloatArray(3) { i ->
            val row = i * 5
            val filtered = (m[row] * input[0] + m[row + 1] * input[1] + m[row + 2] * input[2] + m[row + 4] / 255f)
                .coerceIn(0f, 1f)
            TEXT_GLASS_TINT_ALPHA * t[i] + (1 - TEXT_GLASS_TINT_ALPHA) * filtered
        }
    }

    @Test
    fun luminanceLeaksAsMuchAsTheOldOpaqueTint() {
        // Dřív krytí 0,88: bílá pod sklem posunula plochu o 12 % cesty k bílé. Kontrasty se měřily na tom.
        val white = throughGlass(1f, 1f, 1f)
        val old = tint.red + 0.12f * (1 - tint.red)
        assertTrue(abs(white[0] - old) < 0.005f, "bílá: ${white[0]} vs. dřív $old")
        val black = throughGlass(0f, 0f, 0f)
        val oldBlack = tint.red * 0.88f
        assertTrue(abs(black[0] - oldBlack) < 0.005f, "černá: ${black[0]} vs. dřív $oldBlack")
    }

    @Test
    fun grayLooksExactlyAsBefore() {
        // Bez barvy (šedé bubliny, text) dává sklo přesně totéž co dřívější tint s krytím 0,88.
        val gray = throughGlass(0.5f, 0.5f, 0.5f)
        val t = floatArrayOf(tint.red, tint.green, tint.blue)
        for (i in 0..2) assertTrue(abs(gray[i] - (t[i] + 0.12f * (0.5f - t[i]))) < 0.002f, "složka $i: ${gray[i]}")
    }

    @Test
    fun colorShowsMoreThanBefore() {
        // Sytá modrá pod sklem: rozdíl červené a modré složky proti tintu musí být větší než dřív.
        val blue = throughGlass(0.1f, 0.4f, 0.9f)
        val now = (blue[2] - tint.blue) - (blue[0] - tint.red)
        val before = 0.12f * ((0.9f - tint.blue) - (0.1f - tint.red))
        assertTrue(now > 2 * before, "barva teď $now, dřív $before")
    }
}
