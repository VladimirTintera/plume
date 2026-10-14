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
 * List-detail strategie defaultně rozhoduje single/two-pane podle [currentWindowAdaptiveInfoV2],
 * tedy podle celého okna. Jenže obsah může běžet v menší ploše (padding, kdysi rail). Strategie by pak počítala se šířkou, kterou reálně nemá, a rozhodla špatně. Tahle funkce
 * spočítá directive z naměřené šířky content-oblasti (typicky z `BoxWithConstraints`).
 *
 * Pozn.: posture (hinge) se přebírá z okna — hinge bounds jsou v souřadnicích okna, takže u skládaček
 * jde o aproximaci; primární cíl je správný počet panelů podle šířky.
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
 * Directive vynucená na **jeden** horizontální panel.
 *
 * Dvoupanelový režim rezervuje detail-pane i bez vybraného detailu a vyplní ho placeholderem —
 * seznam chatů by pak zůstal zbytečně úzký. Když v zásobníku detail není, použij tuhle variantu;
 * po otevření konverzace se vrať k [twoPane].
 */
fun PaneScaffoldDirective.singlePane(): PaneScaffoldDirective = withHorizontalPartitions(1)

/**
 * Directive vynucená na **dva** horizontální panely (seznam + konverzace vedle sebe).
 *
 * Standardní dvoupanelový breakpoint je až 840 dp. U seznamu chatů dávají dva panely smysl dřív,
 * proto máme vlastní (nižší) práh [TWO_PANE_MIN_WIDTH] a dvoupanel si vynutíme sami.
 */
fun PaneScaffoldDirective.twoPane(): PaneScaffoldDirective = withHorizontalPartitions(2)

/**
 * Directive vynucená na **tři** horizontální panely (seznam, konverzace a profil vedle sebe).
 *
 * Jen když je co dát do třetího panelu: scaffold se třemi oddíly rozbalí třetí panel vždycky,
 * i prázdný (`tertiaryPane ?: {}` v `ThreePaneScaffold`), a vzal by konverzaci místo.
 */
fun PaneScaffoldDirective.threePane(): PaneScaffoldDirective = withHorizontalPartitions(3)

/**
 * Mezera mezi seznamem a kartou vedle něj. Nedělá ji scaffold, ale karta (`PaneCard`): mezera
 * z direktivy by oddělila i konverzaci od profilu, které jsou jedna karta. Seznam leží na ploše
 * okna a jeho řádky mají vlastní okraj, takže stačí menší než 24 dp z M3.
 */
val PANE_SPACING = 12.dp

/** Šířka seznamu chatů vedle konverzace — výchozí šířka panelu v M3. */
val LIST_PANE_WIDTH = ContentWidth.Pane

/** Nejužší konverzace, ve které se ještě dá číst a psát. */
val MIN_CONVERSATION_WIDTH = 360.dp

/**
 * Od téhle šířky content-oblasti má smysl ukázat seznam a konverzaci vedle sebe.
 *
 * Není to standardní práh M3 (dva panely dává až Expanded, od 840 dp), ale seznam, mezera a nejužší
 * konverzace (744 dp) — dva panely tak dostane i vnitřní displej Foldu naležato (Medium).
 * Standardní dvoupanel už od Medium (600 dp) by konverzaci nechal 216 dp.
 */
val TWO_PANE_MIN_WIDTH = LIST_PANE_WIDTH + PANE_SPACING + MIN_CONVERSATION_WIDTH

/**
 * Od téhle šířky se profil otevírá jako třetí panel vedle seznamu a konverzace. Pod ní se seznam
 * při otevřeném profilu schová a vedle sebe zůstane konverzace s profilem.
 *
 * Standardní práh M3 pro tři panely (Large), a sedí i na obsah: seznam a profil po 360 dp,
 * konverzaci zbyde 480 dp.
 */
val THREE_PANE_MIN_WIDTH = WindowSizeClass.WIDTH_DP_LARGE_LOWER_BOUND.dp

private fun PaneScaffoldDirective.withHorizontalPartitions(n: Int): PaneScaffoldDirective =
    PaneScaffoldDirective(
        maxHorizontalPartitions = n,
        // Mezeru mezi panely si dělá karta sama (`PaneCard`): tahle by rozdělila i konverzaci
        // od profilu. Táhlo stojí na hranici panelů, tedy v mezeře před kartou.
        horizontalPartitionSpacerSize = 0.dp,
        maxVerticalPartitions = maxVerticalPartitions,
        verticalPartitionSpacerSize = verticalPartitionSpacerSize,
        defaultPanePreferredWidth = defaultPanePreferredWidth,
        defaultPanePreferredHeight = defaultPanePreferredHeight,
        excludedBounds = excludedBounds,
        // Scaffold jinak při každé změně aktuálního panelu (otevření konverzace vedle seznamu)
        // zavolá na panel `requestFocus()`. Fokus propadne na první fokusovatelný prvek, tedy
        // vstupní pole konverzace, a vyjede klávesnice, aniž by uživatel do pole ťukl. Na
        // compactu se to nedělo, protože jeden panel scaffold nekreslí.
        shouldAutoFocusCurrentDestination = false,
    )
