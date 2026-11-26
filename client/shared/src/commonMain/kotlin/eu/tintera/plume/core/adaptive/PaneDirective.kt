package eu.tintera.plume.core.adaptive

import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import androidx.window.core.layout.computeWindowSizeClass

/**
 * [PaneScaffoldDirective] odvozený z rozměrů **content-oblasti**, ne celého okna.
 *
 * Scéna panelů (`PaneScene`) z něj bere výchozí šířku panelu (`defaultPanePreferredWidth`, na XL
 * širší) — počet panelů rozhoduje shell sám podle šířky ([TWO_PANE_MIN_WIDTH],
 * [THREE_PANE_MIN_WIDTH]). Velikostní třída se počítá z naměřené šířky content-oblasti (typicky
 * z `BoxWithConstraints`), ne z [currentWindowAdaptiveInfoV2] celého okna.
 *
 * Pozn.: posture (pant) se přebírá z okna; scéna ho zatím nepoužívá (`docs/navrh-scena-chatu.md`).
 */
@Composable
fun contentPaneScaffoldDirective(width: Dp, height: Dp): PaneScaffoldDirective {
    // Z okna se bere jen posture (pant); velikostní třída se počítá z content-oblasti níž.
    // V2 zná i Large a Extra-large: s V1 by i okno přes dva monitory bylo jen Expanded a dostalo
    // výchozí šířku panelu pro tablet.
    val windowInfo = currentWindowAdaptiveInfoV2()
    val contentSizeClass = WindowSizeClass.BREAKPOINTS_V2
        .computeWindowSizeClass(widthDp = width.value, heightDp = height.value)
    return calculatePaneScaffoldDirective(
        WindowAdaptiveInfo(contentSizeClass, windowInfo.windowPosture),
    )
}

/**
 * Okraj karty (`PaneCard`: konverzace, profil) od seznamu, od sousední karty i od kraje okna.
 * Nedělá ho scaffold, ale karta: mezera z direktivy je všude stejná a seznam, který leží na ploše
 * okna, by od kraje odsadila taky. Seznam má u řádků vlastní okraj, takže stačí menší než 24 dp z M3.
 */
val PANE_SPACING = 12.dp

/**
 * Horní okraj karty pod stavovou lištou: tolik, kolik má nad sebou pilulka hledání v seznamu
 * vedle (`AppBarWithSearch` ji odsadí o interní `AppBarWithSearchVerticalPadding`, 4 dp). S
 * [PANE_SPACING] i nahoře začínala karta o 8 dp níž a hrany obou panelů nelícovaly.
 */
val PANE_TOP_SPACING = 4.dp

/** Šířka seznamu chatů vedle konverzace — výchozí šířka panelu v M3. */
val LIST_PANE_WIDTH = ContentWidth.Pane

/** Nejužší konverzace, ve které se ještě dá číst a psát. */
val MIN_CONVERSATION_WIDTH = 360.dp

/**
 * Od téhle šířky content-oblasti má smysl ukázat seznam a konverzaci vedle sebe.
 *
 * Není to standardní práh M3 (dva panely dává až Expanded, od 840 dp), ale seznam, nejužší karta
 * konverzace a její okraje od seznamu a od kraje okna (744 dp) — dva panely tak dostane i vnitřní
 * displej Foldu naležato (Medium). Standardní dvoupanel už od Medium (600 dp) by konverzaci nechal 216 dp.
 */
val TWO_PANE_MIN_WIDTH = LIST_PANE_WIDTH + PANE_SPACING * 2 + MIN_CONVERSATION_WIDTH

/**
 * Od téhle šířky se profil otevírá jako třetí panel vedle seznamu a konverzace. Pod ní se seznam
 * při otevřeném profilu schová a vedle sebe zůstane konverzace s profilem.
 *
 * Standardní práh M3 pro tři panely (Large), a sedí i na obsah: seznam a profil po 360 dp,
 * konverzaci zbyde 480 dp.
 */
val THREE_PANE_MIN_WIDTH = WindowSizeClass.WIDTH_DP_LARGE_LOWER_BOUND.dp
