package eu.tintera.plume.core.adaptive

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.VerticalDragHandle
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Na kterém okraji panelu, který hlásí svou polohu, táhlo stojí. */
enum class PaneEdge {
    /** Levý okraj: profil, předěl s konverzací. */
    Start,

    /**
     * Uprostřed VIDITELNÉ mezery za pravým okrajem: seznam, předěl s konverzací. Mezera, kterou
     * oko vidí, začíná u řádků seznamu ([LIST_CONTENT_INSET] před okrajem panelu) a končí u karty
     * konverzace ([PANE_SPACING] za ním) — od okraje panelu počítané táhlo se lepilo na kartu.
     */
    End,
}

/** Vnitřní okraj řádků seznamu chatů u stran panelu; podle něj se táhlo seznamu staví doprostřed mezery. */
val LIST_CONTENT_INSET = 16.dp

/**
 * Vlastní táhlo mezi panely tam, kde táhlo knihovny (`paneExpansionDragHandle`) nestačí: ve
 * třech panelech ho `ThreePaneScaffold` nekreslí vůbec, a ve dvou počítá šířku PRVNÍHO panelu.
 * Šířka pak jde metadaty panelu (`Panes.width`) a tah ji mění.
 *
 * **Táhlo se kreslí nad panely, ne v panelu.** Obsah panelu se ořezává na jeho okraj, takže
 * táhlo na předělu by bylo napůl uříznuté — a uvnitř panelu leželo přes jeho obsah (dlaždice
 * „Ztlumit" v profilu). Panel jen hlásí, kde je ([paneBounds], zapisuje `PaneCard`),
 * a [PaneHandleOverlay] nad celou scénou posadí táhlo na předěl.
 */
@Stable
class PaneResize(
    /** Strana panelu, na které táhlo stojí. */
    val edge: PaneEdge,
    /** Tah o [delta] (kladné = doprava). */
    val onDrag: (delta: Dp) -> Unit,
    /** Puštění: dorovnat na kotvu a uložit. */
    val onDragStopped: () -> Unit,
    /** Čtečka obrazovky: další kotva dokola. */
    val onCycle: () -> Unit,
    /** Popis pro čtečku („Šířka profilu"). */
    val label: String,
) {
    /** Kde v okně leží panel; zapisuje `PaneCard`, čte [PaneHandleOverlay]. */
    val paneBounds: MutableState<Rect?> = mutableStateOf(null)
}

/** Táhlo profilu vedle konverzace (Chaty, dva i tři panely); jinde `null`. */
val LocalProfilePaneResize = compositionLocalOf<PaneResize?> { null }

/** Táhlo seznamu chatů ve třech panelech (ve dvou je táhlo knihovny); jinde `null`. */
val LocalListPaneResize = compositionLocalOf<PaneResize?> { null }

/**
 * Vrstva přes celou scénu, která posadí táhla [resizes] na předěly jejich panelů, svisle
 * doprostřed. Nic jiného nezachytává — mimo táhla jde dotyk do panelů pod ní.
 */
@Composable
fun PaneHandleOverlay(resizes: List<PaneResize>, modifier: Modifier = Modifier) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    // Střed viditelné mezery od řádků seznamu ke kartě konverzace (viz [PaneEdge.End]).
    val gapCenter = with(density) { ((PANE_SPACING - LIST_CONTENT_INSET) / 2).toPx() }
    Box(modifier.fillMaxSize().onGloballyPositioned { origin = it.boundsInWindow().topLeft }) {
        resizes.forEach { resize ->
            val pane = resize.paneBounds.value ?: return@forEach
            val edgeX = when (resize.edge) {
                PaneEdge.Start -> pane.left
                PaneEdge.End -> pane.right + gapCenter
            }
            PaneHandle(
                resize,
                Modifier.layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                    // Uzel přes celou vrstvu: táhlo posunuté mimo vlastní rámeček by jinak nebralo dotyk.
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        placeable.place(
                            x = (edgeX - origin.x - placeable.width / 2f).roundToInt(),
                            y = (pane.center.y - origin.y - placeable.height / 2f).roundToInt(),
                        )
                    }
                },
            )
        }
    }
}

/** Táhlo M3. Tahem šířka panelu; čtečka obrazovky přepíná kotvy akcí táhla. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaneHandle(resize: PaneResize, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val interactionSource = remember { MutableInteractionSource() }
    val dragState = rememberDraggableState { deltaPx -> resize.onDrag(with(density) { deltaPx.toDp() }) }
    VerticalDragHandle(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .draggable(
                state = dragState,
                orientation = Orientation.Horizontal,
                interactionSource = interactionSource,
                onDragStopped = { resize.onDragStopped() },
            )
            .semantics {
                contentDescription = resize.label
                onClick { resize.onCycle(); true }
            },
        interactionSource = interactionSource,
    )
}
