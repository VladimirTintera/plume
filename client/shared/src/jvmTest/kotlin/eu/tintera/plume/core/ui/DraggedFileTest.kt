package eu.tintera.plume.core.ui

import eu.tintera.plume.core.files.FileSelection
import java.awt.datatransfer.DataFlavor
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Soubor tažený z okna ven ([stageDraggedFile]): pod čitelným jménem, bez kopírování (tah začíná
 * na hlavním vlákně) a každý tah ve vlastním adresáři.
 */
class DraggedFileTest {

    private val directory: File = Files.createTempDirectory("plume-drag-test").toFile()
    private val root = File(directory, "drag")
    private val source = File(directory, "5868681087951770588_121.jpg").apply { writeText("fotka") }

    @AfterTest
    fun cleanUp() {
        directory.deleteRecursively()
    }

    @Test
    fun tahneSePodCitelnymJmenemBezKopie() {
        val staged = stageDraggedFile(DraggedFile(source.path, "Plume_20260918_233017.jpg"), root)!!
        assertEquals("Plume_20260918_233017.jpg", staged.name)
        assertEquals("fotka", staged.readText())
        // Tvrdý odkaz: tentýž soubor na disku, ne kopie.
        assertTrue(Files.isSameFile(staged.toPath(), source.toPath()))
    }

    @Test
    fun kazdyTahMaVlastniAdresarAStaryMaze() {
        val first = stageDraggedFile(DraggedFile(source.path, "a.jpg"), root)!!
        val second = stageDraggedFile(DraggedFile(source.path, "a.jpg"), root)!!
        assertNotEquals(first.parentFile, second.parentFile)
        assertFalse(first.exists())
        // Smazaný odkaz zdroj nechá být.
        assertTrue(source.exists())
    }

    @Test
    fun nestazenySouborSeNetahne() {
        assertNull(stageDraggedFile(DraggedFile(File(directory, "chybi.pdf").path, "chybi.pdf"), root))
    }

    @Test
    fun tahNeseSeznamSouboru() {
        val selection = FileSelection(source)
        assertEquals(listOf(DataFlavor.javaFileListFlavor), selection.transferDataFlavors.toList())
        assertEquals(listOf(source), selection.getTransferData(DataFlavor.javaFileListFlavor))
    }
}
