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
internal fun photoFrame(available: Dp, ratio: Float): DpSize {
    val safeRatio = if (ratio.isFinite() && ratio > 0f) ratio else 1f
    val height = (available / safeRatio).coerceAtMost(MAX_PHOTO_HEIGHT)
    // Podlaha nesmí přerůst nabídnutou šířku — v úzké bublině je stropem i podlahou `available`.
    val minWidth = MIN_PHOTO_WIDTH.coerceAtMost(available)
    val width = (height * safeRatio).coerceIn(minWidth, available)
    return DpSize(width, height)
}
