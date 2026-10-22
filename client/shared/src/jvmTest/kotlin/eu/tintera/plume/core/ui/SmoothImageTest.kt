package eu.tintera.plume.core.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import coil3.compose.ImagePainter
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Color
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.impl.use

/**
 * Obrázek větší než plocha se na desktopu musí kreslit průměrovaně (plné rozlišení v prohlížeči,
 * větší fotka z paměti Coilu v bublině).
 *
 * Proužky po pixelu nakreslené zhruba na desetinu: [SmoothImage] přes `ImagePainter` Coilu (tatáž
 * cesta jako v aplikaci) má vyjít šedý. Bitmapa přes Compose ani s `FilterQuality.Medium` ne —
 * Compose kreslí s přísným výřezem a Skia pak mipmapy nepoužije. Kdyby to Compose změnil, druhý
 * test spadne a [SmoothImage] může pryč.
 */
class SmoothImageTest {

    @Test
    fun smoothImageAveragesWhenDrawnDownscaled() {
        val worst = worstDeviation(ImagePainter(SmoothImage(stripes())))
        assertTrue(worst < 40, "SmoothImage má průměrovat, odchylka $worst")
    }

    @Test
    fun composeMediumStillSkipsPixels() {
        val painter = BitmapPainter(stripes().asComposeImageBitmap(), filterQuality = FilterQuality.Medium)
        val worst = worstDeviation(painter)
        assertTrue(worst > 100, "Compose už s Medium průměruje (odchylka $worst), SmoothImage není potřeba")
    }

    private fun worstDeviation(painter: Painter): Int {
        val target = ImageBitmap(SMALL, SMALL)
        val size = Size(SMALL.toFloat(), SMALL.toFloat())
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(target), size) {
            with(painter) { draw(size) }
        }
        val pixels = target.toPixelMap()
        var worst = 0
        // Bez okrajových sloupců, tam filtr sahá mimo obrázek.
        for (x in 1 until SMALL - 1) for (y in 1 until SMALL - 1) {
            worst = maxOf(worst, abs((pixels[x, y].red * 255).toInt() - MID_GREY))
        }
        return worst
    }

    private fun stripes(): Bitmap = Bitmap().apply {
        allocN32Pixels(STRIPES, STRIPES)
        org.jetbrains.skia.Canvas(this).use { canvas ->
            canvas.clear(Color.WHITE)
            val black = Paint().apply { color = Color.BLACK }
            for (x in 0 until STRIPES step 2) {
                canvas.drawRect(Rect.makeXYWH(x.toFloat(), 0f, 1f, STRIPES.toFloat()), black)
            }
        }
        setImmutable()
    }

    private companion object {
        const val STRIPES = 400
        /** Ne přesná desetina: tam padá střed vzorku na hranici proužků a šedá vyjde i bez mipmap. */
        const val SMALL = 37
        const val MID_GREY = 128
    }
}
