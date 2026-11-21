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
import eu.tintera.plume.core.adaptive.clampListBesideProfile
import eu.tintera.plume.core.adaptive.listWidthBesideProfile
import eu.tintera.plume.core.adaptive.nearestListAnchorBesideProfile
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
    val expand: () -> Unit,
)

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

    val scope = rememberCoroutineScope()
    return remember(state, chosen, anchors) {
        ListPaneExpansion(
            state = state,
            anchor = chosen,
            expand = { scope.launch { state.animateToIfAnchored(anchors[ListPaneAnchor.Default.ordinal]) } },
        )
    }
}

/** Táhlo M3 v mezeře mezi panely. Šipkami a čtečkou jde přepínat kotvy (výchozí sémantika). */
@OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalMaterial3Api::class)
internal val ListPaneDragHandle: @Composable ThreePaneScaffoldScope.(PaneExpansionState) -> Unit = { state ->
    val interactionSource = remember { MutableInteractionSource() }
    VerticalDragHandle(
        modifier = Modifier.paneExpansionDraggable(
            state = state,
            minTouchTargetSize = LocalMinimumInteractiveComponentSize.current,
            interactionSource = interactionSource,
        ),
        interactionSource = interactionSource,
    )
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

/** Šířka seznamu ve třech panelech a táhlo, které ji mění; [resizing] viz [DraggedWidth]. */
internal class ListPaneWidth(val width: Dp, val resize: PaneResize, val resizing: Boolean)

/**
 * Táhlo seznamu ve třech panelech (seznam | konverzace | profil), kde to knihovní nekreslí
 * (`PaneResize`). Šířka jde metadaty seznamu ([listWidthBesideProfile]); po puštění dojede
 * pružinou na nejbližší kotvu ([DraggedWidth]) a uloží se jako `AppSettings.listPaneAnchor` —
 * tatáž kotva pak platí i ve dvou panelech s táhlem knihovny. Do stavu se kotva zapíše hned
 * (`settled`), ne až z toku nastavení, jinak by seznam na snímek skočil zpátky.
 */
@Composable
internal fun rememberListPaneResize(expansion: ListPaneExpansion, defaultWidth: Dp, total: Dp): ListPaneWidth {
    val settings = koinInject<AppSettings>()
    val dragged = rememberDraggedWidth()
    var settled by remember { mutableStateOf<ListPaneAnchor?>(null) }
    // Až kotvu potvrdí nastavení, místní už není potřeba (zápis až po kompozici).
    SideEffect { if (settled == expansion.anchor) settled = null }
    val anchor = settled ?: expansion.anchor
    val width = dragged.value ?: listWidthBesideProfile(anchor, total, defaultWidth, GAPS_BESIDE_PROFILE)

    val currentWidth by rememberUpdatedState(width)
    val currentTotal by rememberUpdatedState(total)
    val currentDefault by rememberUpdatedState(defaultWidth)
    val currentAnchor by rememberUpdatedState(anchor)
    val label = stringResource(Res.string.list_pane_width)
    val resize = remember(label, settings, dragged) {
        val settle = { next: ListPaneAnchor ->
            val target = listWidthBesideProfile(next, currentTotal, currentDefault, GAPS_BESIDE_PROFILE)
            dragged.settle(from = currentWidth, target = target) {
                settled = next
                settings.setListPaneAnchor(next)
            }
        }
        PaneResize(
            edge = PaneEdge.End,
            // Seznam je vlevo: tah doprava (kladný posun) ho rozšiřuje.
            onDrag = { delta ->
                dragged.drag(from = currentWidth, delta = delta) { clampListBesideProfile(it, currentTotal, currentDefault, GAPS_BESIDE_PROFILE) }
            },
            onDragStopped = {
                settle(nearestListAnchorBesideProfile(currentWidth, currentTotal, currentDefault, GAPS_BESIDE_PROFILE))
            },
            onCycle = {
                val entries = ListPaneAnchor.entries
                settle(entries[(currentAnchor.ordinal + 1) % entries.size])
            },
            label = label,
        )
    }
    return ListPaneWidth(width, resize, resizing = dragged.active)
}

/** Mezery ve třech panelech mimo seznam a obě karty: od seznamu ke konverzaci a okraje profilu. */
private val GAPS_BESIDE_PROFILE = PANE_SPACING + PROFILE_CARD_MARGINS
