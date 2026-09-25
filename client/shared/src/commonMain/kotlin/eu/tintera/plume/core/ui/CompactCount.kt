package eu.tintera.plume.core.ui

import androidx.compose.runtime.Composable
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.count_millions
import eu.tintera.plume.resources.count_thousands
import eu.tintera.plume.resources.decimal_number
import org.jetbrains.compose.resources.stringResource

/** Řád zkráceného počtu. */
enum class CountScale { Units, Thousands, Millions }

/**
 * Počet zkrácený na řád, jak ho ukazují zhlédnutí u příspěvku („12 tis.", „1,2 mil.").
 * Jako [eu.tintera.plume.core.files.FileSize]: číslice zvlášť, desetinný oddělovač a jednotku
 * dodá jazyk ([compactCountText]).
 */
data class CompactCount(val whole: Int, val tenths: Int?, val scale: CountScale)

/**
 * Pod tisíc celé číslo, nad ním tisíce a miliony — do deseti s jedním desetinným místem
 * („1,2 tis."), výš bez něj („123 tis."): tam je desetina jen šum.
 *
 * **Zaokrouhluje se DOLŮ.** 999 999 zhlédnutí není „1 mil." a příspěvek nemá ukazovat víc
 * zhlédnutí, než kolik jich má. Tak to dělají i oficiální klienti.
 */
fun compactCount(value: Int): CompactCount {
    val count = value.coerceAtLeast(0)
    val (scale, unit) = when {
        count < 1_000 -> return CompactCount(count, null, CountScale.Units)
        count < 1_000_000 -> CountScale.Thousands to 1_000
        else -> CountScale.Millions to 1_000_000
    }
    val whole = count / unit
    if (whole >= 10) return CompactCount(whole, null, scale)
    val tenths = count % unit / (unit / 10)
    return CompactCount(whole, tenths.takeIf { it != 0 }, scale)
}

/** Zkrácený počet pro člověka: „1,2 tis." česky, „1.2K" anglicky. */
@Composable
fun compactCountText(value: Int): String {
    val count = compactCount(value)
    val number = count.tenths?.let { stringResource(Res.string.decimal_number, count.whole.toString(), it.toString()) }
        ?: count.whole.toString()
    return when (count.scale) {
        CountScale.Units -> number
        CountScale.Thousands -> stringResource(Res.string.count_thousands, number)
        CountScale.Millions -> stringResource(Res.string.count_millions, number)
    }
}
