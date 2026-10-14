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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.adaptive.ListPaneAnchor
import eu.tintera.plume.core.adaptive.listPaneAnchors
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
            semanticsProperties = state.defaultDragHandleSemantics(),
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
