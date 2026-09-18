package eu.tintera.plume.features.conversation

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

/** Strop výšky fotky v bublině. Svislé snímky obrazovky by jinak zabraly celou obrazovku. */
internal val MAX_PHOTO_HEIGHT = 320.dp

/**
 * Nejmenší šířka fotky v bublině.
 *
 * Pod ní se poměr stran obětuje a fotka se do stran ořízne: u extrémně vysokého snímku (panorama
 * na výšku) by dopočítaná šířka vyšla na pár desítek dp a z fotky by byl proužek, ve kterém není
 * poznat nic. Ořez je tady menší zlo než nečitelná nudle.
 */
internal val MIN_PHOTO_WIDTH = 140.dp

/**
 * Nejmenší šířka fotky s popiskem, jako podíl nabídnuté šířky.
 *
 * Popisek má šířku fotky (jedna karta), takže u vysokého snímku zúženého na pár set bodů se text
 * lámal po dvou třech slovech a delší popis byl nečitelný. S popiskem proto fotka dostane aspoň
 * tuhle šířku a **ořízne se na střed** — poměr stran zůstává, jen je vidět prostředek, jako
 * v Telegramu. Bez popisku se fotka dál jen zužuje a je vidět celá.
 */
internal const val CAPTIONED_MIN_WIDTH_FRACTION = 0.75f

/**
 * Rámeček fotky nebo videa v bublině: [available] je šířka, kterou bublina nabízí, [ratio] poměr
 * stran přílohy (šířka / výška).
 *
 * Fotka se **zužuje, neořezává**. Dokud se vejde do [MAX_PHOTO_HEIGHT], zabere celou nabídnutou
 * šířku; jakmile se uplatní strop výšky, dopočítá se šířka zpátky z poměru stran — fotka tím
 * zabere méně šířky, ale je vidět celá.
 *
 * Předtím se výška stropem jen useknula a šířka zůstala na `fillMaxWidth()`, takže `Crop` ze
 * svislého snímku obrazovky nechal jen prostřední pruh a nešlo z něj přečíst ani co na něm je.
 *
 * Počítá se ručně, ne přes `aspectRatio`: ten si výšku dopočítá ze šířky a strop z předřazeného
 * `heightIn` neuplatní (viz `CLAUDE.md`, „Gotchas").
 *
 * Neznámé rozměry (nula, nekonečno, NaN) berou čtverec — je méně rušivý než nulová výška.
 */
internal fun photoFrame(
    available: Dp,
    ratio: Float,
    /** Podíl [available], pod který se fotka nezúží (viz [CAPTIONED_MIN_WIDTH_FRACTION]). */
    minWidthFraction: Float = 0f,
): DpSize {
    val safeRatio = if (ratio.isFinite() && ratio > 0f) ratio else 1f
    // Podlaha nesmí přerůst nabídnutou šířku — v úzké bublině je stropem i podlahou `available`.
    val minWidth = maxOf(MIN_PHOTO_WIDTH, available * minWidthFraction).coerceAtMost(available)
    val natural = (available / safeRatio).coerceAtMost(MAX_PHOTO_HEIGHT)
    val width = (natural * safeRatio).coerceIn(minWidth, available)
    // Po rozšíření na podlahu se výška dopočítá znovu (se stropem): panorama s popiskem se tak
    // nenatáhne do výšky, jen vysoký snímek dostane strop a ořízne se na střed.
    val height = (width / safeRatio).coerceAtMost(MAX_PHOTO_HEIGHT)
    return DpSize(width, height)
}
