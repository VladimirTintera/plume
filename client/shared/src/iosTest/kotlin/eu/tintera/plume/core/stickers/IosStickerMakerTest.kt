package eu.tintera.plume.core.stickers

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.test.runTest
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Color
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Surface
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import platform.Foundation.create
import platform.Foundation.writeToFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Výroba samolepky na iOS bez odstranění pozadí (to chce skutečnou fotku a model Vision):
 * rozměry podle Telegramu, WebP ze Skii a obálka 100 × 100 do 32 kB.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosStickerMakerTest {

    private val maker = IosStickerMaker()

    @Test
    fun stickerIsWebpWithLongerSide512() = runTest {
        val sticker = maker.make(photo(800, 600), removeBackground = false, outline = true).getOrThrow()
        assertEquals(512 to 384, sticker.width to sticker.height, "bez odstranění pozadí se obrys nekreslí")
        val bytes = read(sticker.path)
        assertTrue(bytes.isWebp(), "samolepka je WebP")
        val decoded = Image.makeFromEncoded(bytes)
        assertEquals(512 to 384, decoded.width to decoded.height)
    }

    @Test
    fun coverIs100SquareUnder32Kb() = runTest {
        val sticker = maker.make(photo(600, 900), removeBackground = false, outline = false).getOrThrow()
        val cover = maker.makeCover(sticker.path).getOrThrow()
        assertEquals(COVER_SIDE to COVER_SIDE, cover.width to cover.height)
        val bytes = read(cover.path)
        assertTrue(bytes.isWebp())
        assertTrue(bytes.size <= COVER_MAX_BYTES, "obálka má ${bytes.size} B")
        val decoded = Image.makeFromEncoded(bytes)
        assertEquals(COVER_SIDE to COVER_SIDE, decoded.width to decoded.height)
    }

    @Test
    fun outlineSurroundsSubjectAndFits512() {
        // Vyříznutý objekt: neprůhledný kruh na průhledném pozadí, jak ho vrací Vision.
        val surface = Surface.makeRasterN32Premul(400, 300)
        surface.canvas.drawCircle(200f, 150f, 140f, Paint().apply { color = Color.makeRGB(220, 40, 40) })
        val outlined = withOutline(surface.makeImageSnapshot())
        assertEquals(512 to 392, outlined.width to outlined.height, "objekt 480 × 360 a obrys 16 px kolem")

        val pixels = Bitmap.makeFromImage(outlined)
        val centerX = outlined.width / 2
        val centerY = outlined.height / 2
        // Kruh má po zmenšení poloměr 168 px; obrys je za jeho okrajem.
        assertEquals(Color.makeRGB(220, 40, 40), pixels.getColor(centerX, centerY), "objekt zůstal navrchu")
        assertEquals(Color.WHITE, pixels.getColor(centerX, centerY - 168 - 8), "bílý obrys nad objektem")
        assertEquals(0, Color.getA(pixels.getColor(2, 2)), "roh mimo obrys je průhledný")
    }

    /** Fotka: barevný kruh na jiném pozadí, uložená jako PNG. */
    private fun photo(width: Int, height: Int): String {
        val surface = Surface.makeRasterN32Premul(width, height)
        surface.canvas.clear(Color.makeRGB(40, 90, 200))
        surface.canvas.drawCircle(width / 2f, height / 2f, minOf(width, height) / 3f, Paint().apply { color = Color.makeRGB(220, 40, 40) })
        val png = surface.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)!!.bytes
        val path = NSTemporaryDirectory() + NSUUID().UUIDString + ".png"
        png.usePinned { NSData.create(bytes = it.addressOf(0), length = png.size.toULong()).writeToFile(path, atomically = true) }
        return path
    }

    private fun read(path: String): ByteArray {
        val data = NSFileManager.defaultManager.contentsAtPath(path)!!
        return ByteArray(data.length.toInt()).apply { usePinned { platform.posix.memcpy(it.addressOf(0), data.bytes, data.length) } }
    }

    private fun ByteArray.isWebp() = size > 12 &&
        decodeToString(0, 4) == "RIFF" && decodeToString(8, 12) == "WEBP"
}
