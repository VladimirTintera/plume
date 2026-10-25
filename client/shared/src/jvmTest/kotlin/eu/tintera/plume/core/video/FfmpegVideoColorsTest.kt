package eu.tintera.plume.core.video

import androidx.compose.ui.unit.IntSize
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue
import org.jetbrains.skia.Bitmap

/**
 * Barvy po převodu snímku do BGRA (`sws_scale_frame`): pruhy červená, zelená, modrá 64 × 64
 * zakódované v h264 jednou s maticí BT.709 (HD) a jednou BT.601 (SD), obojí v omezeném rozsahu
 * a s barevným prostorem zapsaným v souboru (`ffmpeg … -colorspace bt709`). Se špatnou maticí
 * vyjde zelená do žluta nebo modré přibude zelená o desítky úrovní.
 */
class FfmpegVideoColorsTest {

    @Test
    fun bt709BarsKeepTheirColors() = assertBars("bars709.mp4")

    @Test
    fun bt601BarsKeepTheirColors() = assertBars("bars601.mp4")

    /** Ve víc vláknech vyjde totéž (převod se dělí po pruzích obrazu). */
    @Test
    fun threadedConversionKeepsColors() = assertBars("bars709.mp4", threads = 0)

    private fun assertBars(name: String, threads: Int = 1) {
        val file = File.createTempFile("bars", ".mp4")
        file.writeBytes(checkNotNull(javaClass.getResourceAsStream("/video/$name")).readBytes())
        try {
            FfmpegVideoDecoder.open(file.path, threads).use { decoder ->
                checkNotNull(decoder.decodeNext()) { "žádný snímek" }
                decoder.toImage(IntSize.Zero).use { image ->
                    val bitmap = Bitmap()
                    bitmap.allocN32Pixels(image.width, image.height)
                    image.readPixels(bitmap)
                    val y = image.height / 2
                    assertColor(bitmap.getColor(image.width / 6, y), 255, 0, 0, "$name červená")
                    assertColor(bitmap.getColor(image.width / 2, y), 0, 255, 0, "$name zelená")
                    assertColor(bitmap.getColor(image.width * 5 / 6, y), 0, 0, 255, "$name modrá")
                }
            }
        } finally {
            file.delete()
        }
    }

    private fun assertColor(argb: Int, r: Int, g: Int, b: Int, what: String) {
        val actual = Triple((argb shr 16) and 0xFF, (argb shr 8) and 0xFF, argb and 0xFF)
        val off = maxOf(abs(actual.first - r), abs(actual.second - g), abs(actual.third - b))
        assertTrue(off <= TOLERANCE, "$what: $actual, čekáno ($r, $g, $b)")
    }

    private companion object {
        /** Zaokrouhlení YUV 4:2:0 a h264; špatná matice ujede o 20 a víc. */
        const val TOLERANCE = 12
    }
}
