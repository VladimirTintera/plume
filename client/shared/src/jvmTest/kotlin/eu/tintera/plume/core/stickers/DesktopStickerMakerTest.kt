package eu.tintera.plume.core.stickers

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.jetbrains.skia.Color
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface

/** Samolepka na desktopu: WebP 512 px ze Skii, obálka 100 × 100 a fotka otočená podle EXIF. */
class DesktopStickerMakerTest {

    private val directory = createTempDirectory("stickers").toFile()
    private val maker = DesktopStickerMaker(directory)

    @Test
    fun stickerIsWebpWithLongerSide512WithoutBackgroundRemoval() = runTest {
        assertFalse(maker.canRemoveBackground, "na desktopu model segmentace není")
        val sticker = maker.make(photo(800, 600, "png"), removeBackground = false, outline = true).getOrThrow()
        assertEquals(512 to 384, sticker.width to sticker.height)
        val bytes = File(sticker.path).readBytes()
        assertTrue(bytes.isWebp())
        assertEquals(512 to 384, Image.makeFromEncoded(bytes).let { it.width to it.height })
    }

    @Test
    fun coverIs100SquareUnder32Kb() = runTest {
        val sticker = maker.make(photo(600, 900, "png"), removeBackground = false, outline = false).getOrThrow()
        val cover = maker.makeCover(sticker.path).getOrThrow()
        val bytes = File(cover.path).readBytes()
        assertTrue(bytes.isWebp())
        assertTrue(bytes.size <= COVER_MAX_BYTES, "obálka má ${bytes.size} B")
        assertEquals(COVER_SIDE to COVER_SIDE, Image.makeFromEncoded(bytes).let { it.width to it.height })
    }

    @Test
    fun exifOrientationTurnsPhotoUpright() = runTest {
        // Na šířku uložená fotka s EXIF „otočit o 90° doprava" (6): na výšku 600 × 800.
        val path = photo(800, 600, "jpg", exifOrientation = 6)
        val sticker = maker.make(path, removeBackground = false, outline = false).getOrThrow()
        assertEquals(384 to 512, sticker.width to sticker.height)
    }

    /** Fotka: horní polovina červená, spodní modrá; JPEG volitelně s EXIF orientací. */
    private fun photo(width: Int, height: Int, extension: String, exifOrientation: Int? = null): String {
        val surface = Surface.makeRasterN32Premul(width, height)
        surface.canvas.drawRect(Rect.makeWH(width.toFloat(), height / 2f), Paint().apply { color = Color.RED })
        surface.canvas.drawRect(Rect.makeXYWH(0f, height / 2f, width.toFloat(), height / 2f), Paint().apply { color = Color.BLUE })
        val format = if (extension == "png") EncodedImageFormat.PNG else EncodedImageFormat.JPEG
        var bytes = surface.makeImageSnapshot().encodeToData(format, 90)!!.bytes
        if (exifOrientation != null) bytes = withExifOrientation(bytes, exifOrientation)
        return File(directory, "photo.$extension").apply { writeBytes(bytes) }.path
    }

    /** JPEG s vloženým segmentem APP1 (EXIF), jediný záznam: Orientation (0x0112). */
    private fun withExifOrientation(jpeg: ByteArray, orientation: Int): ByteArray {
        val exif = byteArrayOf(
            0xFF.toByte(), 0xE1.toByte(), 0x00, 0x22, // APP1, délka 34
            'E'.code.toByte(), 'x'.code.toByte(), 'i'.code.toByte(), 'f'.code.toByte(), 0, 0,
            'M'.code.toByte(), 'M'.code.toByte(), 0x00, 0x2A, 0x00, 0x00, 0x00, 0x08, // TIFF, big endian
            0x00, 0x01, // jeden záznam
            0x01, 0x12, 0x00, 0x03, 0x00, 0x00, 0x00, 0x01, 0x00, orientation.toByte(), 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, // další IFD není
        )
        return jpeg.copyOfRange(0, 2) + exif + jpeg.copyOfRange(2, jpeg.size)
    }

    private fun ByteArray.isWebp() = size > 12 && decodeToString(0, 4) == "RIFF" && decodeToString(8, 12) == "WEBP"
}
