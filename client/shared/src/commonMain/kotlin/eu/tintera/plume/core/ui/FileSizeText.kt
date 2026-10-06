package eu.tintera.plume.core.ui

import androidx.compose.runtime.Composable
import eu.tintera.plume.core.files.FileSize
import eu.tintera.plume.core.files.FileSizeUnit
import eu.tintera.plume.core.files.formatFileSize
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.decimal_number
import eu.tintera.plume.resources.file_size_b
import eu.tintera.plume.resources.file_size_gb
import eu.tintera.plume.resources.file_size_kb
import eu.tintera.plume.resources.file_size_mb
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/**
 * Velikost souboru pro člověka („1,4 MB" česky, „1.4 MB" anglicky).
 *
 * Jediné místo, kde se z [FileSize] skládá text: desetinný oddělovač i pořadí čísla a jednotky
 * patří jazyku, takže jdou z prostředků (`decimal_number`, `file_size_*`), stejně jako
 * „1,5×" u rychlosti hlasovek. Dřív si každá obrazovka skládala text sama a desetinnou tečku
 * přebírala z řetězce, který jí dal [formatFileSize].
 */
@Composable
fun fileSizeText(size: FileSize): String {
    val number = size.tenths?.let { stringResource(Res.string.decimal_number, size.whole, it.toString()) }
        ?: size.whole
    return stringResource(size.unit.label(), number)
}

@Composable
fun fileSizeText(bytes: Long): String = fileSizeText(formatFileSize(bytes))

/** Totéž mimo kompozici (notifikace), v jazyce platném TEĎ. Skládá se stejně jako [fileSizeText]. */
suspend fun fileSizeString(bytes: Long): String {
    val size = formatFileSize(bytes)
    val number = size.tenths?.let { getString(Res.string.decimal_number, size.whole, it.toString()) } ?: size.whole
    return getString(size.unit.label(), number)
}

private fun FileSizeUnit.label() = when (this) {
    FileSizeUnit.Bytes -> Res.string.file_size_b
    FileSizeUnit.Kilobytes -> Res.string.file_size_kb
    FileSizeUnit.Megabytes -> Res.string.file_size_mb
    FileSizeUnit.Gigabytes -> Res.string.file_size_gb
}
