package eu.tintera.plume.core.ui

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Color
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface

/**
 * Úprava fotky na desktopu: tahy, otočení a ořez jako na mobilech, fotka otočená podle EXIF
 * (jak ji ukazuje editor) a rozměry přílohy po otočení.
 */
class DesktopPhotoMarkupRendererTest {

    private val directory = createTempDirectory("markup").toFile()
    private val renderer = DesktopPhotoMarkupRenderer(directory)

    @Test
    fun strokeIsDrawnInOriginalCoordinates() = runTest {
        // Svislá čára perem přes levou čtvrtinu fotky, celá v modré spodní polovině.
        val stroke = MarkupStroke(MarkupTool.Pen, 0xFFE53935, floatArrayOf(0.25f, 0.6f, 0.25f, 0.9f))
        val marked = renderer.render(photo(800, 600), PhotoMarkup(strokes = listOf(stroke)))
        assertEquals(800 to 600, marked.width to marked.height)
        val pixels = decoded(marked.path)
        assertReddish(pixels.getColor(200, 450), "na tahu")
        assertBluish(pixels.getColor(600, 450), "mimo tah")
    }

    @Test
    fun quarterTurnRotatesClockwiseAndCropCutsResult() = runTest {
        // Červená nahoře se po otočení o čtvrt otáčky po směru hodin ocitne vpravo.
        val rotated = renderer.render(photo(800, 600), PhotoMarkup(quarterTurns = 1))
        assertEquals(600 to 800, rotated.width to rotated.height)
        val pixels = decoded(rotated.path)
        assertReddish(pixels.getColor(500, 400), "vpravo")
        assertBluish(pixels.getColor(100, 400), "vlevo")

        // Výřez levé poloviny otočené fotky je celý modrý.
        val cropped = renderer.render(photo(800, 600), PhotoMarkup(quarterTurns = 1, crop = CropBox(0f, 0f, 0.5f, 1f)))
        assertEquals(300 to 800, cropped.width to cropped.height)
        assertBluish(decoded(cropped.path).getColor(280, 400), "u pravého okraje výřezu")
    }

    @Test
    fun exifOrientedPhotoIsRenderedUpright() = runTest {
        val marked = renderer.render(photo(800, 600, exifOrientation = 6), PhotoMarkup(quarterTurns = 0, strokes = listOf(dot())))
        assertEquals(600 to 800, marked.width to marked.height, "tak jak ji ukazuje editor")
    }

    @Test
    fun attachmentSizeFollowsExif() {
        val upright = File(photo(800, 600, exifOrientation = 6))
        assertEquals(600 to 800, uprightSize(upright, 800, 600))
        val plain = File(photo(800, 600))
        assertEquals(800 to 600, uprightSize(plain, 800, 600))
    }

    private fun dot() = MarkupStroke(MarkupTool.Pen, 0xFF000000, floatArrayOf(0.5f, 0.5f))

    /** Fotka JPEG: horní polovina červená, spodní modrá; volitelně s EXIF orientací. */
    private fun photo(width: Int, height: Int, exifOrientation: Int? = null): String {
        val surface = Surface.makeRasterN32Premul(width, height)
        surface.canvas.drawRect(Rect.makeWH(width.toFloat(), height / 2f), Paint().apply { color = Color.RED })
        surface.canvas.drawRect(Rect.makeXYWH(0f, height / 2f, width.toFloat(), height / 2f), Paint().apply { color = Color.BLUE })
        var bytes = surface.makeImageSnapshot().encodeToData(EncodedImageFormat.JPEG, 95)!!.bytes
        if (exifOrientation != null) bytes = withExifOrientation(bytes, exifOrientation)
        return File.createTempFile("photo", ".jpg", directory).apply { writeBytes(bytes) }.path
    }

    /** JPEG s vloženým segmentem APP1 (EXIF), jediný záznam: Orientation (0x0112). */
    private fun withExifOrientation(jpeg: ByteArray, orientation: Int): ByteArray {
        val exif = byteArrayOf(
            0xFF.toByte(), 0xE1.toByte(), 0x00, 0x22,
            'E'.code.toByte(), 'x'.code.toByte(), 'i'.code.toByte(), 'f'.code.toByte(), 0, 0,
            'M'.code.toByte(), 'M'.code.toByte(), 0x00, 0x2A, 0x00, 0x00, 0x00, 0x08,
            0x00, 0x01,
            0x01, 0x12, 0x00, 0x03, 0x00, 0x00, 0x00, 0x01, 0x00, orientation.toByte(), 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00,
        )
        return jpeg.copyOfRange(0, 2) + exif + jpeg.copyOfRange(2, jpeg.size)
    }

    private fun decoded(path: String): Bitmap = Bitmap.makeFromImage(Image.makeFromEncoded(File(path).readBytes()))

    private fun assertReddish(color: Int, where: String) =
        assertTrue(Color.getR(color) > 180 && Color.getB(color) < 90, "$where čekám červenou, je #${Integer.toHexString(color)}")

    private fun assertBluish(color: Int, where: String) =
        assertTrue(Color.getB(color) > 180 && Color.getR(color) < 90, "$where čekám modrou, je #${Integer.toHexString(color)}")
}
