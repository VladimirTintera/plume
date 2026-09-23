package eu.tintera.plume.features.auth

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Co z napsaného nebo vloženého textu je kód: jen číslice, nejvýš [digits] z nich.
 *
 * Vložit se dá celá SMS („Telegram code: 12345") i číslo s oddělovači, takže se ostatní znaky
 * zahazují — políčko na číslici nemá co ukázat u mezery ani u písmene. Ořez na [digits] pak
 * zabrání tomu, aby se do pole vešel kód, který se nikam nevykreslí: políčka jsou jen ta,
 * o která server řekl.
 *
 * Čistá funkce, protože hraniční případy (vložená SMS, číslo navíc, prázdno) se uvnitř
 * `@Composable` neověří.
 */
internal fun codeDigits(raw: String, digits: Int): String =
    raw.filter { it.isDigit() }.take(digits)

/**
 * Políčko, do kterého se právě píše — jen ono nese rámeček.
 *
 * U plného kódu je to POSLEDNÍ políčko, ne políčko za koncem: index [typed] by u hotového
 * kódu ukazoval mimo řadu a rámeček by zmizel přesně ve chvíli, kdy se kód odesílá.
 */
internal fun activeCodeCell(typed: Int, digits: Int): Int =
    typed.coerceIn(0, (digits - 1).coerceAtLeast(0))

/**
 * Výška políčka na kód.
 *
 * Na širokém řádku je pevná ([maxHeight]), na úzkém klesne se šířkou. Šest políček na telefonu
 * širokém 360 dp vyjde po 45 dp a při stálé výšce 64 dp jsou z nich vysoké úzké pilulky —
 * ověřeno na emulátoru zmenšeném na 720 × 1500. Poměr [CODE_CELL_MAX_RATIO] je strop, ne cíl:
 * na běžné šířce se neuplatní vůbec a políčka zůstanou skoro čtvercová.
 */
internal fun codeCellHeight(rowWidth: Dp, digits: Int, gap: Dp, maxHeight: Dp): Dp {
    if (digits <= 0) return maxHeight
    val cellWidth = ((rowWidth - gap * (digits - 1)) / digits).coerceAtLeast(0.dp)
    return minOf(maxHeight, cellWidth * CODE_CELL_MAX_RATIO)
}

/** Nejvyšší poměr výšky políčka k jeho šířce. Nad ním z políčka začne být pilulka. */
private const val CODE_CELL_MAX_RATIO = 1.2f
