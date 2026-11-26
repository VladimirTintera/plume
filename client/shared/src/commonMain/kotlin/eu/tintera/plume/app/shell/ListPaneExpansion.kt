package eu.tintera.plume.app.shell

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.VerticalDragHandle
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.PaneExpansionAnchor
import androidx.compose.material3.adaptive.layout.PaneExpansionState
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldScope
import androidx.compose.material3.adaptive.layout.defaultDragHandleSemantics
import androidx.compose.material3.adaptive.layout.rememberPaneExpansionState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.adaptive.ListPaneAnchor
import eu.tintera.plume.core.adaptive.listPaneAnchors
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
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/** Táhlo mezi seznamem chatů a konverzací: stav knihovny, zvolená kotva a rozbalení. */
internal class ListPaneExpansion @OptIn(ExperimentalMaterial3AdaptiveApi::class) constructor(
    val state: PaneExpansionState,
    /** Kotva, kterou si uživatel zvolil; platí i ve třech panelech, kde se táhlo nekreslí. */
    val anchor: ListPaneAnchor,
    /** Poslední nesbalená kotva — na tu se sbalený seznam rozbalí, ne vždy na výchozí šířku. */
    val expandedAnchor: ListPaneAnchor,
    /** Dojet táhlem knihovny na [ListPaneAnchor] (dva panely; ve třech řídí šířku [ListPaneWidth]). */
    val animateTo: (ListPaneAnchor) -> Unit,
) {
    /** Rozbalit sbalený seznam na [expandedAnchor] (hledání, karty nad seznamem). */
    val expand: () -> Unit = { animateTo(expandedAnchor) }
}

/**
 * Stav táhla s kotvami [listPaneAnchors] a zapamatovanou kotvou z `AppSettings`.
 *
 * - **Ukládá se jen kotva, kterou zvolil uživatel.** Stav začíná na uložené kotvě; dokud se
 *   nastavení nenačte, je to výchozí, a ta se neukládá (shoduje se s `null`), takže uloženou
 *   nepřepíše. Načtená kotva se pak do stavu jen promítne.
 * - **Kotvy závisí na výchozí šířce panelu** (na XL je širší), a když se změní, knihovna kotvu
 *   stavu ztratí; efekt ji podle uložené obnoví.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
internal fun rememberListPaneExpansion(defaultWidth: Dp): ListPaneExpansion {
    val settings = koinInject<AppSettings>()
    val saved by settings.listPaneAnchor.collectAsStateWithLifecycle()
    val chosen = saved ?: ListPaneAnchor.Default
    val anchors = remember(defaultWidth) { listPaneAnchors(defaultWidth) }
    val state = rememberPaneExpansionState(
        anchors = anchors,
        initialAnchoredIndex = remember { chosen.ordinal },
    )

    LaunchedEffect(state, anchors, chosen) {
        val target = anchors[chosen.ordinal]
        if (state.currentAnchor != target) {
            // Kotvy do stavu dává efekt knihovny spuštěný dřív (`restore`); kdyby přesto ještě
            // neplatily, `animateTo` vyhodí — kotva se pak promítne při příští změně.
            state.animateToIfAnchored(target)
        }
    }
    LaunchedEffect(state, anchors) {
        snapshotFlow { state.currentAnchor }.collect { current ->
            val anchor = ListPaneAnchor.entries.getOrNull(anchors.indexOf(current)) ?: return@collect
            if (anchor != (settings.listPaneAnchor.value ?: ListPaneAnchor.Default)) settings.setListPaneAnchor(anchor)
        }
    }

    // Jen v paměti: po spuštění se sbaleným seznamem se rozbalí na výchozí šířku.
    var expandedAnchor by remember { mutableStateOf(ListPaneAnchor.Default) }
    SideEffect { if (chosen != ListPaneAnchor.Collapsed) expandedAnchor = chosen }

    val scope = rememberCoroutineScope()
    return remember(state, chosen, anchors, expandedAnchor) {
        ListPaneExpansion(
            state = state,
            anchor = chosen,
            expandedAnchor = expandedAnchor,
            animateTo = { target -> scope.launch { state.animateToIfAnchored(anchors[target.ordinal]) } },
        )
    }
}

/**
 * `animateTo`, které kotvu mimo seznam kotev stavu jen přeskočí. `runCatching` by spolkl
 * i zrušení korutiny, proto výslovně jen `IllegalArgumentException` z jeho `require`.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
private suspend fun PaneExpansionState.animateToIfAnchored(anchor: PaneExpansionAnchor) {
    try {
        animateTo(anchor)
    } catch (_: IllegalArgumentException) {
    }
}

/**
 * Šířka seznamu ve třech panelech a táhlo, které ji mění; [resizing] viz [DraggedWidth].
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
 * Táhlo seznamu ve třech panelech (seznam | konverzace | profil), kde to knihovní nekreslí
 * (`PaneResize`). Šířka jde metadaty seznamu ([listWidthBesideProfile]); po puštění dojede
 * pružinou na nejbližší kotvu ([DraggedWidth]) a uloží se jako `AppSettings.listPaneAnchor` —
 * tatáž kotva pak platí i ve dvou panelech s táhlem knihovny. Do stavu se kotva zapíše hned
 * (`settled`), ne až z toku nastavení, jinak by seznam na snímek skočil zpátky.
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
