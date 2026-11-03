package eu.tintera.plume.core.files

import java.awt.datatransfer.DataFlavor
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Obsah schránky macOS ([macClipboardEntries]): každý typ nese data, která slibuje. */
class MacClipboardEntriesTest {

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
    fun jpegJdeJakoSkutecnePngPuvodniSouborAOdkaz() {
        val file = imageFile("jpg")
        val (png, jpeg, url) = macClipboardEntries(file, image)
        assertEquals(0x89.toByte(), png.bytes[0])
        assertEquals(4, decode(png.bytes).width)
        assertContentEquals(file.readBytes(), jpeg.bytes)
        assertEquals(file.canonicalFile, Path.of(URI(url.bytes.decodeToString())).toFile().canonicalFile)
    }

    @Test
    fun pngJdeBezPrevoduAJpegNelze() {
        val file = imageFile("png")
        val entries = macClipboardEntries(file, image)
        assertEquals(2, entries.size)
        assertContentEquals(file.readBytes(), entries.first().bytes)
    }

    @Test
    fun jinyFormatSePrevedeNaPng() {
        val entries = macClipboardEntries(imageFile("bmp"), image)
        assertEquals(2, entries.size)
        assertEquals(3, decode(entries.first().bytes).height)
    }

    @Test
    fun odkazNaSouborJeUplnaAdresaSZakodovanymJmenem() {
        val file = File(directory, "Žluťoučký soubor.pdf").also { it.writeText("x") }
        val url = macClipboardEntries(file, null).single().bytes.decodeToString()
        assertTrue(url.startsWith("file:///"), url)
        assertTrue(url.endsWith("%20soubor.pdf"), url)
        assertEquals(file.canonicalFile, Path.of(URI(url)).toFile().canonicalFile)
    }

    @Test
    fun schrankaNenabiziTypyAwtAPokazdeDaCteniOdZacatku() {
        val file = imageFile("jpg")
        val entries = macClipboardEntries(file, image)
        val selection = MacClipboardSelection(entries)
        assertFalse(selection.isDataFlavorSupported(DataFlavor.imageFlavor))
        assertFalse(selection.isDataFlavorSupported(DataFlavor.javaFileListFlavor))
        repeat(2) {
            val stream = selection.getTransferData(entries[1].flavor) as InputStream
            assertContentEquals(file.readBytes(), stream.readBytes())
        }
    }
}
