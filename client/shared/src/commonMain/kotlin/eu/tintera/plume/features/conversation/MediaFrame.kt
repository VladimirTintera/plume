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
 * Poměr stran (šířka / výška) z metadat zprávy; [fallback], když rozměry neznáme (nula).
 *
 * Bere se z metadat, ne z načteného obrázku — jinak by seznam poskočil, až se soubor dotáhne.
 * Dřív si to každé místo počítalo samo a nulu ošetřovalo jinak, nebo vůbec (fotku zachránil až
 * `photoFrame`, který NaN bere jako čtverec).
 */
internal fun aspectRatio(width: Int, height: Int, fallback: Float = 1f): Float =
    if (width > 0 && height > 0) width.toFloat() / height.toFloat() else fallback

/**
 * Rámeček fotky nebo videa v bublině: [available] je šířka, kterou bublina nabízí, [ratio] poměr
 * stran přílohy (šířka / výška).
 *
 * Fotka se **zužuje, neořezává**. Dokud se vejde do [MAX_PHOTO_HEIGHT], zabere celou nabídnutou
 * šířku; jakmile se uplatní strop výšky, dopočítá se šířka zpátky z poměru stran — fotka tím
 * zabere méně šířky, ale je vidět celá.
 *
 * S popiskem fotka využije **celou nabízenou šířku** ([fillWidth] = true): popisek má její šířku,
 * a na zúžené fotce by se delší text lámal po slovech. Vysoký snímek se proto rozšíří na celou
 * dostupnou šířku, výška zůstane na stropu a fotka se **ořízne na střed** (`Crop`), jako
 * v Telegramu. Totéž platí pro velký obrázek náhledu odkazu.
 *
 * Bez popisku se fotka dál jen zužuje a je vidět celá.
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
    /** Zda má fotka využít celou nabízenou šířku (fotka s popiskem, velký náhled odkazu). */
    fillWidth: Boolean = false,
): DpSize {
    val safeRatio = if (ratio.isFinite() && ratio > 0f) ratio else 1f
    val natural = (available / safeRatio).coerceAtMost(MAX_PHOTO_HEIGHT)
    val minWidth = MIN_PHOTO_WIDTH.coerceAtMost(available)
    val width = if (fillWidth) available else (natural * safeRatio).coerceIn(minWidth, available)
    // Po rozšíření na plnou šířku se výška dopočítá znovu (se stropem): panorama s popiskem se tak
    // nenatáhne do výšky, jen vysoký snímek dostane strop a ořízne se na střed.
    val height = (width / safeRatio).coerceAtMost(MAX_PHOTO_HEIGHT)
    return DpSize(width, height)
}
