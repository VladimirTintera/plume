package eu.tintera.plume.app.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.adaptive.ListPaneAnchor
import eu.tintera.plume.core.adaptive.PANE_SPACING
import eu.tintera.plume.core.adaptive.PROFILE_CARD_MARGINS
import eu.tintera.plume.core.adaptive.PaneEdge
import eu.tintera.plume.core.adaptive.PaneResize
import eu.tintera.plume.core.adaptive.clampListWidth
import eu.tintera.plume.core.adaptive.listWidth
import eu.tintera.plume.core.adaptive.reserveBesideProfile
import eu.tintera.plume.core.adaptive.nearestListAnchor
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.list_pane_width
import org.jetbrains.compose.resources.stringResource
import eu.tintera.plume.core.settings.AppSettings
import org.koin.compose.koinInject

/**
 * Kotva seznamu chatů, kterou si uživatel zvolil (`AppSettings.listPaneAnchor`), a ta, na kterou
 * se sbalený seznam rozbalí. Šířku z ní počítá táhlo seznamu ([rememberListPaneResize]).
 */
internal class ListPaneExpansion(
    val anchor: ListPaneAnchor,
    /** Poslední nesbalená kotva — na tu se sbalený seznam rozbalí, ne vždy na výchozí šířku. */
    val expandedAnchor: ListPaneAnchor,
)

/**
 * Zvolená kotva z `AppSettings`; dokud se nastavení nenačte, výchozí. Ukládá ji táhlo
 * ([rememberListPaneResize]), výchozí kotva se neukládá (shoduje se s `null`).
 *
 * Dřív tu žil i stav knihovního táhla (`PaneExpansionState`) pro dva panely. Chaty kreslí scéna
 * Chatů s vlastním táhlem i ve dvou panelech, takže zbyla jen kotva.
 */
@Composable
internal fun rememberListPaneExpansion(): ListPaneExpansion {
    val settings = koinInject<AppSettings>()
    val saved by settings.listPaneAnchor.collectAsStateWithLifecycle()
    val chosen = saved ?: ListPaneAnchor.Default
    // Jen v paměti: po spuštění se sbaleným seznamem se rozbalí na výchozí šířku.
    var expandedAnchor by remember { mutableStateOf(ListPaneAnchor.Default) }
    SideEffect { if (chosen != ListPaneAnchor.Collapsed) expandedAnchor = chosen }
    return remember(chosen, expandedAnchor) { ListPaneExpansion(anchor = chosen, expandedAnchor = expandedAnchor) }
}

/**
 * Šířka seznamu vedle konverzace a táhlo, které ji mění; [resizing] viz [DraggedWidth].
 * [anchor] je kotva, na které seznam stojí nebo na kterou právě dojíždí, [settleTo] na ni dojede
 * stejně jako po puštění táhla (přepínač sbalení).
 */
internal class ListPaneWidth(
    val width: Dp,
    val resize: PaneResize,
    val resizing: Boolean,
    val anchor: ListPaneAnchor,
    val settleTo: (ListPaneAnchor) -> Unit,
)

/**
 * Táhlo seznamu ve scéně Chatů (`PaneResize`), ve dvou i třech panelech. Šířku dostává scéna
 * (`ChatSceneLayout.listWidth`); po puštění dojede pružinou na nejbližší kotvu ([DraggedWidth])
 * a uloží se jako `AppSettings.listPaneAnchor`. Do stavu se kotva zapíše hned (`settled`), ne až
 * z toku nastavení, jinak by seznam na snímek skočil zpátky.
 */
@Composable
internal fun rememberListPaneResize(
    expansion: ListPaneExpansion,
    defaultWidth: Dp,
    total: Dp,
    /**
     * Co musí zůstat vedle seznamu (`listWidth`). Výchozí je profil a konverzace ve třech panelech;
     * scéna Chatů má táhlo i ve dvou panelech a rezervuje tam jen konverzaci ([RESERVE_BESIDE_LIST]).
     * `null` = výchozí.
     */
    reserve: Dp? = null,
): ListPaneWidth {
    @Suppress("NAME_SHADOWING")
    val reserve = reserve ?: reserveBesideProfile(defaultWidth, GAPS_BESIDE_PROFILE)
    val settings = koinInject<AppSettings>()
    val dragged = rememberDraggedWidth()
    var settled by remember { mutableStateOf<ListPaneAnchor?>(null) }
    // Až kotvu potvrdí nastavení, místní už není potřeba (zápis až po kompozici).
    SideEffect { if (settled == expansion.anchor) settled = null }
    val anchor = settled ?: expansion.anchor
    val width = dragged.value ?: listWidth(anchor, total, defaultWidth, reserve)

    val currentWidth by rememberUpdatedState(width)
    val currentTotal by rememberUpdatedState(total)
    val currentDefault by rememberUpdatedState(defaultWidth)
    val currentAnchor by rememberUpdatedState(anchor)
    val currentReserve by rememberUpdatedState(reserve)
    val label = stringResource(Res.string.list_pane_width)
    val settle = remember(settings, dragged) {
        { next: ListPaneAnchor ->
            val target = listWidth(next, currentTotal, currentDefault, currentReserve)
            dragged.settle(from = currentWidth, target = target) {
                settled = next
                settings.setListPaneAnchor(next)
            }
        }
    }
    val resize = remember(label, settle, dragged) {
        PaneResize(
            edge = PaneEdge.End,
            // Seznam je vlevo: tah doprava (kladný posun) ho rozšiřuje.
            onDrag = { delta ->
                dragged.drag(from = currentWidth, delta = delta) { clampListWidth(it, currentTotal, currentReserve) }
            },
            onDragStopped = {
                settle(nearestListAnchor(currentWidth, currentTotal, currentDefault, currentReserve))
            },
            onCycle = {
                val entries = ListPaneAnchor.entries
                settle(entries[(currentAnchor.ordinal + 1) % entries.size])
            },
            label = label,
        )
    }
    return ListPaneWidth(width, resize, resizing = dragged.active, anchor = anchor, settleTo = settle)
}

/** Mezery ve třech panelech mimo seznam a obě karty: od seznamu ke konverzaci a okraje profilu. */
private val GAPS_BESIDE_PROFILE = PANE_SPACING + PROFILE_CARD_MARGINS
