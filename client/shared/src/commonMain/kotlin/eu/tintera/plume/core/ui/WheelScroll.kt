package eu.tintera.plume.core.ui

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.ScrollState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Svislé kolečko myši nad vodorovně posouvanou řadou ji posouvá do strany ([state] je
 * `ScrollState` z `horizontalScroll`, `LazyListState` z `LazyRow`…).
 *
 * Compose u vodorovného posunu svislé kolečko ZAHODÍ (`toSingleAxisDeltaFromAngle` vrátí pro
 * převážně svislý pohyb 0) a tah myší posun nebere vůbec (`CanDragCalculation` u `scrollable`
 * vynechává myš). Vodorovně posouvá jen kolečko s náklonem, Shift + kolečko a touchpad do strany
 * — myší bez Shiftu se tak k tomu, co přeteklo za okraj, nedalo dostat.
 *
 * - **Jen převážně svislý pohyb** — vodorovný (touchpad, Shift) obslouží posun sám.
 * - **Jen když je v tom směru kam posouvat**; jinak událost propadne dál (rodič, seznam).
 * - **Krok je [WHEEL_STEP] na zub** (`scrollDelta` je v zubech kolečka na desktopu i na
 *   Androidu, touchpad posílá zlomky) a posun dojede animací; rychlé točení se sčítá.
 *
 * **NEDÁVAT na řadu uvnitř svislého seznamu** (reakce pod zprávou, řádek lidí v hledání,
 * záložky profilu): kolečko by si ji bralo pokaždé, když přes ni kurzor při posouvání
 * seznamu přejede, a seznam by se zastavil.
 *
 * U `horizontalScroll` patří modifikátor ZA posun (blíž obsahu), aby kolečko dostal dřív;
 * u `LazyRow` jde na řadu zvenku — posun uvnitř svislé kolečko nespotřebuje, takže dojde i sem.
 */
@Composable
fun Modifier.wheelScrollsHorizontally(state: ScrollableState): Modifier =
    this then WheelScrollElement(state, MaterialTheme.motionScheme.defaultEffectsSpec())

/**
 * `horizontalScroll` s kolečkem myši ([wheelScrollsHorizontally]) — pro řady, které se posouvají
 * do strany a NELEŽÍ ve svislém seznamu.
 */
@Composable
fun Modifier.horizontalScrollWithWheel(state: ScrollState = rememberScrollState()): Modifier =
    horizontalScroll(state).wheelScrollsHorizontally(state)

private class WheelScrollElement(
    private val state: ScrollableState,
    private val spec: AnimationSpec<Float>,
) : ModifierNodeElement<WheelScrollNode>() {
    override fun create() = WheelScrollNode(state, spec)

    override fun update(node: WheelScrollNode) {
        node.state = state
        node.spec = spec
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "wheelScrollsHorizontally"
    }

    override fun equals(other: Any?): Boolean = other is WheelScrollElement && other.state === state && other.spec == spec

    override fun hashCode(): Int = 31 * state.hashCode() + spec.hashCode()
}

private class WheelScrollNode(
    var state: ScrollableState,
    var spec: AnimationSpec<Float>,
) : Modifier.Node(), PointerInputModifierNode, CompositionLocalConsumerModifierNode {
    /** Kolik ještě dojet; při rychlém točení se přičítá k rozjeté animaci. */
    private var pending = 0f
    private var job: Job? = null

    override fun onPointerEvent(pointerEvent: PointerEvent, pass: PointerEventPass, bounds: IntSize) {
        if (pass != PointerEventPass.Main || pointerEvent.type != PointerEventType.Scroll) return
        val change = pointerEvent.changes.firstOrNull() ?: return
        if (change.isConsumed) return
        val delta = pointerEvent.changes.fold(Offset.Zero) { sum, it -> sum + it.scrollDelta }
        if (delta.y == 0f || abs(delta.y) <= abs(delta.x)) return
        val forward = delta.y > 0f
        if (forward && !state.canScrollForward || !forward && !state.canScrollBackward) return
        pending += delta.y * with(currentValueOf(LocalDensity)) { WHEEL_STEP.toPx() }
        change.consume()
        if (job?.isActive != true) {
            job = coroutineScope.launch {
                while (pending != 0f) {
                    val distance = pending
                    pending = 0f
                    state.animateScrollBy(distance, spec)
                }
            }
        }
    }

    override fun onCancelPointerInput() = Unit

    override fun onDetach() {
        pending = 0f
        job = null
    }
}

/** Posun řady na jeden zub kolečka — o kus víc než jeden čip nebo složka (48 dp a mezera). */
private val WHEEL_STEP = 64.dp
