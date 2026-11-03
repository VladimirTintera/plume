package eu.tintera.plume.core.files

import java.awt.datatransfer.DataFlavor
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

/** Obrázek do schránky macOS ([macClipboardImages]): každý typ nese data, která slibuje. */
class MacClipboardImagesTest {

    private val directory: File = Files.createTempDirectory("plume-clipboard-test").toFile()
    private val image = BufferedImage(4, 3, BufferedImage.TYPE_INT_RGB)

    @AfterTest
    fun cleanUp() {
        directory.deleteRecursively()
    }

    private fun imageFile(format: String): File =
        File(directory, "fotka.$format").also { ImageIO.write(image, format, it) }

    private fun decode(bytes: ByteArray) = assertNotNull(ImageIO.read(ByteArrayInputStream(bytes)))

    @Test
    fun jpegJdeJakoSkutecnePngIPuvodniSoubor() {
        val file = imageFile("jpg")
        val (png, jpeg) = macClipboardImages(file, image)
        assertEquals(0x89.toByte(), png.bytes[0])
        assertEquals(4, decode(png.bytes).width)
        assertContentEquals(file.readBytes(), jpeg.bytes)
    }

    @Test
    fun pngJdeBezPrevoduAJpegNelze() {
        val file = imageFile("png")
        val images = macClipboardImages(file, image)
        assertContentEquals(file.readBytes(), images.single().bytes)
    }

    @Test
    fun jinyFormatSePrevedeNaPng() {
        val images = macClipboardImages(imageFile("bmp"), image)
        assertEquals(3, decode(images.single().bytes).height)
    }

    @Test
    fun schrankaNenabiziImageFlavorAPokazdeDaCteniOdZacatku() {
        val file = imageFile("jpg")
        val images = macClipboardImages(file, image)
        val selection = FileSelection(file, encodedImages = images)
        assertFalse(selection.isDataFlavorSupported(DataFlavor.imageFlavor))
        assertEquals(listOf(file), selection.getTransferData(DataFlavor.javaFileListFlavor))
        repeat(2) {
            val stream = selection.getTransferData(images[1].flavor) as InputStream
            assertContentEquals(file.readBytes(), stream.readBytes())
        }
    }
}
