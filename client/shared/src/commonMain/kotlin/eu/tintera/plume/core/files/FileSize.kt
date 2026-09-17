package eu.tintera.plume.core.files

import kotlin.math.round

/**
 * Velikost souboru rozdělená na číslo a jednotku; jednotku pojmenuje až UI z prostředků.
 *
 * Desetinné místo jen u jednotek, kde má význam: „1,4 MB" ano, „1,4 B" ne. Počítá se po 1024,
 * jak velikosti souborů uvádí Telegram i systémy.
 */
data class FileSize(val value: String, val unit: FileSizeUnit)

enum class FileSizeUnit { Bytes, Kilobytes, Megabytes, Gigabytes }

fun formatFileSize(bytes: Long): FileSize {
    if (bytes < 1024) return FileSize(bytes.coerceAtLeast(0).toString(), FileSizeUnit.Bytes)

    var value = bytes.toDouble()
    var unit = FileSizeUnit.Bytes
    for (next in listOf(FileSizeUnit.Kilobytes, FileSizeUnit.Megabytes, FileSizeUnit.Gigabytes)) {
        value /= 1024
        unit = next
        if (value < 1024) break
    }

    // Do deseti jednotek desetinné místo pomáhá (1,4 MB), výš je jen šum (137,2 MB).
    val rounded = if (value < 10) round(value * 10) / 10 else round(value)
    val text = if (value < 10) {
        val whole = rounded.toInt()
        val tenths = round((rounded - whole) * 10).toInt()
        if (tenths == 0) whole.toString() else "$whole.$tenths"
    } else {
        rounded.toInt().toString()
    }
    return FileSize(text, unit)
}
