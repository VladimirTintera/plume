package eu.tintera.plume.core.telegram

import okio.FileSystem
import okio.Path
import okio.buffer
import okio.use

/**
 * Kopie souboru, který někdo jiný teprve zapisuje (nahrávač), do cíle, odkud ho čte TDLib.
 * Kopíruje se jen to, co v zdroji přibylo; [copied] je souvislá délka hotová v cíli.
 *
 * Nahrávač smí na konci přepsat začátek souboru (MP4 dopíše index), proto [finish] porovná,
 * jestli zkopírované odpovídá hotovému souboru — když ne, předem nahrané se použít nesmí.
 */
internal class GrowingFileCopy(
    private val source: Path,
    private val target: Path,
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
) {
    var copied: Long = 0
        private set

    /** Dokopíruje, co ve zdroji přibylo. Vrátí novou délku hotovou v cíli. */
    fun copyAvailable(): Long {
        val size = fileSystem.metadataOrNull(source)?.size ?: return copied
        if (size <= copied) return copied
        fileSystem.openReadOnly(source).use { input ->
            fileSystem.openReadWrite(target, mustCreate = false, mustExist = false).use { output ->
                val buffer = ByteArray(CHUNK)
                while (copied < size) {
                    val read = input.read(copied, buffer, 0, minOf(CHUNK.toLong(), size - copied).toInt())
                    if (read <= 0) break
                    output.write(copied, buffer, 0, read)
                    copied += read
                }
            }
        }
        return copied
    }

    /**
     * Zdroj je hotový v [finalPath]: je-li to týž soubor a zkopírovaný začátek se nezměnil,
     * dokopíruje zbytek a vrátí celou délku. `null` = cíl neodpovídá hotovému souboru.
     */
    fun finish(finalPath: Path): Long? {
        if (finalPath != source) return null
        if (!prefixMatches()) return null
        copyAvailable()
        val size = fileSystem.metadataOrNull(source)?.size ?: return null
        return copied.takeIf { it == size }
    }

    private fun prefixMatches(): Boolean {
        if (copied == 0L) return true
        val written = fileSystem.source(target).buffer().use { it.readByteString(copied) }
        val original = fileSystem.source(source).buffer().use { input ->
            if ((fileSystem.metadataOrNull(source)?.size ?: 0) < copied) return false
            input.readByteString(copied)
        }
        return written == original
    }

    private companion object {
        const val CHUNK = 64 * 1024
    }
}
