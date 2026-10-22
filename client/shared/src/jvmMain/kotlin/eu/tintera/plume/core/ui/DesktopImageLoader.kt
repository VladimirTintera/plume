package eu.tintera.plume.core.ui

import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.asImage
import coil3.decode.DecodeResult
import coil3.decode.DecodeUtils
import coil3.decode.Decoder
import coil3.decode.ImageSource
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import coil3.request.maxBitmapSize
import coil3.size.Precision
import coil3.util.component1
import coil3.util.component2
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.impl.use

/**
 * Coil na desktopu s dekodérem, který obrázek zmenšuje s filtrováním.
 *
 * **Výchozí `SkiaImageDecoder` zmenšuje nejbližším sousedem.** Dekóduje celý obrázek a na cílovou
 * velikost ho překreslí se `SamplingMode.DEFAULT`, což je ve Skie `NEAREST` bez mipmap (Coil 3.5,
 * na `main` 3.6 stejně, `coil3.util.makeFromImage`). Při zmenšení fotky na plochu okna se tak
 * z každých pár pixelů vezme jeden a ostatní se zahodí: písmo a hrany jsou zubaté, jemný vzor se
 * rozpadne do moiré. Na Androidu to není (dekóduje `BitmapFactory` s filtrováním). iOS jede přes
 * týž kód Coilu, takže ho to nejspíš potkává taky — tam se to neověřovalo ani neopravilo.
 *
 * Kreslení v Compose to nespraví: dekódovaný obrázek už má velikost plochy, takže se kreslí
 * zhruba 1 : 1 a zuby jsou v něm samotném.
 *
 * Vlastní dekodér dělá totéž co výchozí (stejný výpočet velikosti, stejné meze), jen zmenšuje
 * přes mipmapy (`LINEAR` / `LINEAR`): Skia si k obrázku postaví řadu polovičních zmenšenin
 * průměrováním a vzorkuje mezi dvěma nejbližšími. Coil přidává komponenty od uživatele PŘED
 * výchozí, takže se výchozí dekodér k obrázku nedostane.
 */
object DesktopImageLoader {

    /** Volat jednou při startu, dřív než se vykreslí první obrázek. */
    fun install() {
        SingletonImageLoader.setSafe { context ->
            ImageLoader.Builder(context)
                .components { add(SmoothSkiaImageDecoder.Factory()) }
                .build()
        }
    }
}

internal class SmoothSkiaImageDecoder(
    private val source: ImageSource,
    private val options: Options,
) : Decoder {

    override suspend fun decode(): DecodeResult {
        val bytes = source.source().use { it.readByteArray() }
        val image = Image.makeFromEncoded(bytes)
        val bitmap: Bitmap
        val sampled: Boolean
        try {
            bitmap = image.scaledFor(options)
            sampled = bitmap.width < image.width || bitmap.height < image.height
        } finally {
            image.close()
        }
        bitmap.setImmutable()
        return DecodeResult(image = bitmap.asImage(), isSampled = sampled)
    }

    class Factory : Decoder.Factory {
        override fun create(result: SourceFetchResult, options: Options, imageLoader: ImageLoader): Decoder =
            SmoothSkiaImageDecoder(result.source, options)
    }
}

/** Velikost počítá stejně jako Coil (`makeFromImage`), liší se jen vzorkování. */
private fun Image.scaledFor(options: Options): Bitmap {
    val (dstWidth, dstHeight) = DecodeUtils.computeDstSize(
        srcWidth = width,
        srcHeight = height,
        targetSize = options.size,
        scale = options.scale,
        maxSize = options.maxBitmapSize,
    )
    var multiplier = DecodeUtils.computeSizeMultiplier(
        srcWidth = width,
        srcHeight = height,
        dstWidth = dstWidth,
        dstHeight = dstHeight,
        scale = options.scale,
        maxSize = options.maxBitmapSize,
    )
    // Zvětšuje se jen tam, kde si požadavek přesnou velikost vynutí.
    if (options.precision == Precision.INEXACT) multiplier = multiplier.coerceAtMost(1.0)

    val outWidth = (multiplier * width).toInt()
    val outHeight = (multiplier * height).toInt()
    val bitmap = Bitmap()
    bitmap.allocN32Pixels(outWidth, outHeight)
    Canvas(bitmap).use { canvas ->
        canvas.drawImageRect(
            image = this,
            srcLeft = 0f,
            srcTop = 0f,
            srcRight = width.toFloat(),
            srcBottom = height.toFloat(),
            dstLeft = 0f,
            dstTop = 0f,
            dstRight = outWidth.toFloat(),
            dstBottom = outHeight.toFloat(),
            samplingMode = SMOOTH_SAMPLING,
            paint = null,
            strict = false,
        )
    }
    return bitmap
}

private val SMOOTH_SAMPLING = FilterMipmap(FilterMode.LINEAR, MipmapMode.LINEAR)
