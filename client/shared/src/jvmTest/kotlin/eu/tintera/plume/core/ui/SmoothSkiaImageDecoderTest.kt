package eu.tintera.plume.core.ui

import coil3.BitmapImage
import coil3.PlatformContext
import coil3.decode.DecodeResult
import coil3.decode.ImageSource
import coil3.decode.SkiaImageDecoder
import coil3.request.Options
import coil3.size.Precision
import coil3.size.Size
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import okio.Buffer
import okio.FileSystem
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Color
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.impl.use

/**
 * Zmenšení obrázku na desktopu musí průměrovat, ne brát každý n-tý pixel.
 *
 * Svislé proužky po jednom pixelu (černá, bílá) zmenšené zhruba na desetinu: s filtrováním vyjde šedá,
 * nejbližší soused vybere jen jednu z barev. Výchozí dekodér Coilu je tu pro srovnání — kdyby
 * ho Coil opravil, test na něm spadne a vlastní dekodér může pryč.
 */
class SmoothSkiaImageDecoderTest {

    @Test
    fun downscaleAveragesFineDetail() = runTest {
        val pixels = decode { source, options -> SmoothSkiaImageDecoder(source, options).decode() }

        // Coil násobí poměrem v plovoucí čárce, takže může vyjít o pixel méně.
        assertTrue(pixels.width in SMALL - 1..SMALL, "šířka ${pixels.width}")
        val worst = pixels.greys().maxOf { abs(it - MID_GREY) }
        assertTrue(worst < 40, "zmenšené proužky mají být šedé, nejhorší odchylka $worst")
    }

    @Test
    fun coilDefaultDecoderStillSamplesNearest() = runTest {
        val pixels = decode { source, options -> SkiaImageDecoder(source, options).decode() }

        assertTrue(pixels.greys().all { it == 0 || it == 255 }, "Coil už zmenšuje s filtrováním")
    }

    private suspend fun decode(decoder: suspend (ImageSource, Options) -> DecodeResult): Bitmap {
        val options = Options(
            context = PlatformContext.INSTANCE,
            size = Size(SMALL, SMALL),
            precision = Precision.INEXACT,
        )
        val source = ImageSource(Buffer().write(stripesPng()), FileSystem.SYSTEM)
        return when (val image = decoder(source, options).image) {
            is SmoothImage -> image.bitmap
            is BitmapImage -> image.bitmap
            else -> error("nečekaný obrázek $image")
        }
    }

    private fun stripesPng(): ByteArray {
        val bitmap = Bitmap().apply { allocN32Pixels(STRIPES, STRIPES) }
        Canvas(bitmap).use { canvas ->
            canvas.clear(Color.WHITE)
            val black = Paint().apply { color = Color.BLACK }
            for (x in 0 until STRIPES step 2) {
                canvas.drawRect(Rect.makeXYWH(x.toFloat(), 0f, 1f, STRIPES.toFloat()), black)
            }
        }
        return checkNotNull(Image.makeFromBitmap(bitmap).encodeToData(EncodedImageFormat.PNG)).bytes
    }

    /** Červená složka každého pixelu kromě okrajových sloupců (tam filtr sahá mimo obrázek). */
    private fun Bitmap.greys(): List<Int> = buildList {
        for (x in 1 until width - 1) for (y in 1 until height - 1) add(Color.getR(getColor(x, y)))
    }

    private companion object {
        const val STRIPES = 400
        /**
         * Zmenšení NE na celý zlomek: při přesné desetině padá střed každého vzorku na hranici
         * dvou proužků a šedá vyjde i z obyčejného bilineárního filtru.
         */
        const val SMALL = 37
        const val MID_GREY = 128
    }
}
