package eu.tintera.plume.features.conversation

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.materialkolor.dynamicColorScheme
import eu.tintera.plume.app.root.presentation.theme.BrandPaletteStyle
import eu.tintera.plume.app.root.presentation.theme.seed
import eu.tintera.plume.core.settings.ThemeColor
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class GlassTest {

    /** Barva po matici [backdropMatrix] a tintu [tint] s krytím [GLASS_TINT_ALPHA] — totéž, co kreslí sklo. */
    private fun throughGlass(tint: Color, leak: Float, under: Color): Color {
        val m = backdropMatrix(tint, glassContrast(leak), GLASS_SATURATION).values
        val input = floatArrayOf(under.red, under.green, under.blue)
        val t = floatArrayOf(tint.red, tint.green, tint.blue)
        val out = FloatArray(3) { i ->
            val row = i * 5
            val filtered = (m[row] * input[0] + m[row + 1] * input[1] + m[row + 2] * input[2] + m[row + 4] / 255f)
                .coerceIn(0f, 1f)
            GLASS_TINT_ALPHA * t[i] + (1 - GLASS_TINT_ALPHA) * filtered
        }
        return Color(out[0], out[1], out[2])
    }

    private val dark = Color(0xFF2B2930)

    @Test
    fun luminanceLeaksExactlyTheConfiguredShare() {
        val white = throughGlass(dark, PILL_GLASS_LEAK, Color.White)
        val expected = dark.red + PILL_GLASS_LEAK * (1 - dark.red)
        assertTrue(abs(white.red - expected) < 0.005f, "bílá: ${white.red} vs. $expected")
        val black = throughGlass(dark, PILL_GLASS_LEAK, Color.Black)
        assertTrue(abs(black.red - dark.red * (1 - PILL_GLASS_LEAK)) < 0.005f, "černá: ${black.red}")
    }

    @Test
    fun grayLooksLikePlainTintWithTheSameLeak() {
        val gray = throughGlass(dark, PILL_GLASS_LEAK, Color(0.5f, 0.5f, 0.5f))
        val t = floatArrayOf(dark.red, dark.green, dark.blue)
        val g = floatArrayOf(gray.red, gray.green, gray.blue)
        for (i in 0..2) assertTrue(abs(g[i] - (t[i] + PILL_GLASS_LEAK * (0.5f - t[i]))) < 0.002f, "složka $i: ${g[i]}")
    }

    @Test
    fun colorShowsMoreThanPlainTint() {
        // Sytá modrá pod sklem: rozdíl modré a červené složky musí být větší než u holého tintu.
        val blue = throughGlass(dark, PILL_GLASS_LEAK, Color(0.1f, 0.4f, 0.9f))
        val now = (blue.blue - dark.blue) - (blue.red - dark.red)
        val plain = PILL_GLASS_LEAK * ((0.9f - dark.blue) - (0.1f - dark.red))
        assertTrue(now > 2 * plain, "barva teď $now, holý tint $plain")
    }

    /**
     * Nejhorší případ pro text na pilulce, ve všech osmi paletách: pod celým textem úplně bílá
     * plocha v tmavém motivu, úplně černá ve světlém. `onSurface` a `onSurfaceVariant` musí držet
     * AA; primární popisek („Připnutá zpráva“) vědomě níž (viz [PILL_GLASS_LEAK]), ale ne pod 3.
     */
    @Test
    fun pillTextContrastInWorstCase() {
        for (theme in ThemeColor.entries) for (isDark in listOf(false, true)) {
            val s = dynamicColorScheme(seedColor = theme.seed, isDark = isDark, isAmoled = false, style = BrandPaletteStyle)
            val tint = if (isDark) s.surfaceContainerHigh else s.surfaceContainerLowest
            val leak = if (isDark) PILL_GLASS_LEAK else PILL_GLASS_LEAK_LIGHT
            val pill = throughGlass(tint, leak, if (isDark) Color.White else Color.Black)
            val where = "$theme ${if (isDark) "tmavý" else "světlý"}"
            assertTrue(contrast(s.onSurface, pill) >= 4.5f, "$where onSurface ${contrast(s.onSurface, pill)}")
            assertTrue(contrast(s.onSurfaceVariant, pill) >= 4.5f, "$where onSurfaceVariant ${contrast(s.onSurfaceVariant, pill)}")
            assertTrue(contrast(s.primary, pill) >= 3f, "$where primary ${contrast(s.primary, pill)}")
        }
    }

    /**
     * Totéž pro hlavičku na skle ([headerGlassStyle]): barva stránky místo bílé pilulky, únik
     * pilulky. Jméno je `onSurface`, podtitul a ikony `onSurfaceVariant`, „píše…“ `primary`.
     */
    @Test
    fun headerTextContrastInWorstCase() {
        for (theme in ThemeColor.entries) for (isDark in listOf(false, true)) {
            val s = dynamicColorScheme(seedColor = theme.seed, isDark = isDark, isAmoled = false, style = BrandPaletteStyle)
            val leak = if (isDark) PILL_GLASS_LEAK else PILL_GLASS_LEAK_LIGHT
            val header = throughGlass(s.surface, leak, if (isDark) Color.White else Color.Black)
            val where = "$theme ${if (isDark) "tmavý" else "světlý"}"
            assertTrue(contrast(s.onSurface, header) >= 4.5f, "$where onSurface ${contrast(s.onSurface, header)}")
            assertTrue(contrast(s.onSurfaceVariant, header) >= 4.5f, "$where onSurfaceVariant ${contrast(s.onSurfaceVariant, header)}")
            assertTrue(contrast(s.primary, header) >= 3f, "$where primary ${contrast(s.primary, header)}")
        }
    }

    private fun contrast(a: Color, b: Color): Float {
        val x = a.luminance() + 0.05f
        val y = b.luminance() + 0.05f
        return maxOf(x, y) / minOf(x, y)
    }
}
