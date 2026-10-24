package eu.tintera.plume.core.files

import kotlin.math.round

/**
 * Velikost souboru rozdělená na číslo a jednotku; jednotku pojmenuje až UI z prostředků.
 *
 * Desetinné místo jen u jednotek, kde má význam: „1,4 MB" ano, „1,4 B" ne. Počítá se po 1024,
 * jak velikosti souborů uvádí Telegram i systémy.
 *
 * **Celá část a desetiny jsou zvlášť, ne hotový řetězec.** Desetinný oddělovač patří jazyku
 * (česky čárka, anglicky tečka) a spojí je až UI formátovacím řetězcem z prostředků
 * (`fileSizeText` v `core/ui`). Dokud to byl řetězec „1.4" poskládaný tady, stála tečka
 * i v české aplikaci.
 *
 * @property tenths desetiny, nebo `null`, když se desetinné místo nepíše (celé číslo, velké
 *   hodnoty, bajty).
 */
data class FileSize(val whole: String, val tenths: Int?, val unit: FileSizeUnit)

enum class FileSizeUnit { Bytes, Kilobytes, Megabytes, Gigabytes }

fun formatFileSize(bytes: Long): FileSize {
    if (bytes < 1024) return FileSize(bytes.coerceAtLeast(0).toString(), null, FileSizeUnit.Bytes)

    var value = bytes.toDouble()
    var unit = FileSizeUnit.Bytes
    for (next in listOf(FileSizeUnit.Kilobytes, FileSizeUnit.Megabytes, FileSizeUnit.Gigabytes)) {
        value /= 1024
        unit = next
        if (value < 1024) break
    }

    // Do deseti jednotek desetinné místo pomáhá (1,4 MB), výš je jen šum (137,2 MB).
    if (value >= 10) return FileSize(round(value).toInt().toString(), null, unit)
    val rounded = round(value * 10) / 10
    val whole = rounded.toInt()
    val tenths = round((rounded - whole) * 10).toInt()
    return FileSize(whole.toString(), tenths.takeIf { it != 0 }, unit)
}
