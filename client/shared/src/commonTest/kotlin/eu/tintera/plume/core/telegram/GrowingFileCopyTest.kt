package eu.tintera.plume.core.telegram

import okio.FileSystem
import okio.Path
import okio.use
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Kopie rostoucího souboru pro předběžné nahrávání hlasovky. */
class GrowingFileCopyTest {
    private val fileSystem = FileSystem.SYSTEM
    private val folder: Path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "growing-copy-${Random.nextLong()}"
    private val source = folder / "voice.ogg"
    private val target = folder / "upload"

    init {
        fileSystem.createDirectories(folder)
    }

    @AfterTest
    fun cleanUp() {
        fileSystem.deleteRecursively(folder, mustExist = false)
    }

    private fun append(bytes: ByteArray) {
        fileSystem.appendingSink(source).use { sink -> okio.Buffer().write(bytes).let { sink.write(it, it.size) } }
    }

    @Test
    fun kopirujeJenToCoPribylo() {
        val copy = GrowingFileCopy(source, target)
        assertEquals(0, copy.copyAvailable(), "zdroj ještě není")

        append(ByteArray(100) { it.toByte() })
        assertEquals(100, copy.copyAvailable())
        append(ByteArray(50) { 7 })
        assertEquals(150, copy.copyAvailable())

        assertEquals(150L, copy.finish(source))
        assertContentEquals(fileSystem.read(source) { readByteArray() }, fileSystem.read(target) { readByteArray() })
    }

    /** Konec, který nahrávač dopsal po poslední kopii, se dokopíruje při dokončení. */
    @Test
    fun dokonceniDokopirujeZbytek() {
        val copy = GrowingFileCopy(source, target)
        append(ByteArray(70000) { (it % 251).toByte() })
        copy.copyAvailable()
        append(ByteArray(10) { 1 })

        assertEquals(70010L, copy.finish(source))
        assertContentEquals(fileSystem.read(source) { readByteArray() }, fileSystem.read(target) { readByteArray() })
    }

    /** Nahrávač přepsal začátek (MP4 dopíše index) — předem nahrané se použít nesmí. */
    @Test
    fun prepsanyZacatekSeNepouzije() {
        val copy = GrowingFileCopy(source, target)
        append(ByteArray(100) { 1 })
        copy.copyAvailable()
        fileSystem.write(source) { write(ByteArray(120) { 2 }) }

        assertNull(copy.finish(source))
    }

    /** Výsledek je jiný soubor (spojené úseky po pauze). */
    @Test
    fun jinySouborSeNepouzije() {
        val copy = GrowingFileCopy(source, target)
        append(ByteArray(10) { 1 })
        copy.copyAvailable()

        assertNull(copy.finish(folder / "joined.ogg"))
    }
}
