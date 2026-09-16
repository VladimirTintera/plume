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
 * tedy podle celého okna. Jenže obsah běží v menší ploše — vlevo ukusuje navigační rail, k tomu
 * padding. Strategie by pak počítala se šířkou, kterou reálně nemá, a rozhodla špatně. Tahle funkce
 * spočítá directive z naměřené šířky content-oblasti (typicky z `BoxWithConstraints`).
 *
 * Pozn.: posture (hinge) se přebírá z okna — hinge bounds jsou v souřadnicích okna, takže u skládaček
 * jde o aproximaci; primární cíl je správný počet panelů podle šířky.
 */
@Composable
fun contentPaneScaffoldDirective(width: Dp, height: Dp): PaneScaffoldDirective {
    // Z okna se bere jen posture (pant); velikostní třída se počítá z content-oblasti níž.
    val windowInfo = currentWindowAdaptiveInfoV2()
    val contentSizeClass = WindowSizeClass.BREAKPOINTS_V1
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

/** Od téhle šířky content-oblasti má smysl ukázat seznam a konverzaci vedle sebe. */
val TWO_PANE_MIN_WIDTH = 720.dp

private fun PaneScaffoldDirective.withHorizontalPartitions(n: Int): PaneScaffoldDirective =
    PaneScaffoldDirective(
        maxHorizontalPartitions = n,
        // Mezera mezi panely = 0; rozestup řeší až vlastní okraje obsahu, jinak by se scaffold
        // padding sčítal s okraji karet a vznikla dvojitá mezera.
        horizontalPartitionSpacerSize = 0.dp,
        maxVerticalPartitions = maxVerticalPartitions,
        verticalPartitionSpacerSize = verticalPartitionSpacerSize,
        defaultPanePreferredWidth = defaultPanePreferredWidth,
        excludedBounds = excludedBounds,
    )
