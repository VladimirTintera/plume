package eu.tintera.plume.core.adaptive

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Standardní šířky obsahu. Na ploše přes dva monitory by se bez nich konverzace, Nastavení
 * i formuláře natáhly přes tisíce dp: přijaté zprávy u levého okraje, odeslané u pravého,
 * řádek nastavení s přepínačem na druhém konci obrazovky.
 *
 * Škála se opírá o hodnoty M3 (360 výchozí panel, 640 strop bottom sheetu, 840 hranice
 * Expanded), aby šířky v aplikaci nebyly náhodná čísla po souborech. **Nová maximální šířka
 * rozvržení patří sem**, ne do konstanty v obrazovce; drobné šířky uvnitř bublin (čip, štítek,
 * anketa) jsou věc té bubliny.
 */
object ContentWidth {
    /** Panel vedle jiného: seznam chatů, profil. Výchozí šířka panelu M3. */
    val Pane: Dp = 360.dp

    /** Jednoduchý formulář: přihlášení, kód, heslo. */
    val Form: Dp = 420.dp

    /** Bublina zprávy a karta v konverzaci (klávesnice bota pod ní). */
    val Bubble: Dp = 560.dp

    /** Bottom sheet — výchozí strop M3 (`BottomSheetDefaults.SheetMaxWidth`). */
    val Sheet: Dp = 640.dp

    /** Obsah na čtení: Nastavení, článek Instant View. */
    val Reading: Dp = 720.dp

    /** Sloupec konverzace — zprávy i vstupní pole; hlavička zůstává přes celý panel. */
    val Conversation: Dp = 840.dp
}

/**
 * Obsah nejvýš [maxWidth] široký, na ploše širší vycentrovaný. Sám zabere celou šířku, takže
 * se dá dát rovnou na slot (`bottomBar`) bez obalu.
 */
fun Modifier.centeredColumn(maxWidth: Dp): Modifier =
    fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = maxWidth).fillMaxWidth()

/** Vodorovný okraj, který obsah plochy široké [available] stáhne do sloupce [maxWidth] uprostřed. */
fun columnInset(available: Dp, maxWidth: Dp): Dp = ((available - maxWidth) / 2).coerceAtLeast(0.dp)
