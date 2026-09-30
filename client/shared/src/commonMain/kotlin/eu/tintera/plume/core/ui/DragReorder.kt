package eu.tintera.plume.core.ui

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.zIndex

/**
 * Přeskládání položek `LazyColumn` tažením za úchyt.
 *
 * - **Pořadí drží volající** (seznam ve stavu obrazovky nebo ViewModelu), tady se jen hlásí
 *   přesun: [onMove] dostane klíč tažené položky a klíč té, na jejíž místo se dostala. Seznam
 *   se tím přeskládá a tažená položka jede dál pod prstem.
 * - **Tažená položka se posouvá jen kreslením** ([Modifier.draggedItem], `graphicsLayer`),
 *   ne layoutem — tah tak nic nerekomponuje. Po výměně se posun o rozdíl pozic srovná, jinak
 *   by položka skočila o výšku souseda.
 * - **Přeskládat jde jen mezi položkami, které [canMove] pustí** (připnutá témata mezi sebou,
 *   ne s hledáním nad nimi).
 * - Výměnu ohlásí haptika `tick` — přeskládání je procházení po dílcích.
 */
@Stable
class DragReorderState internal constructor(
    private val listState: LazyListState,
    private val haptics: HapticFeedback,
) {
    internal var onMove: (from: Any, to: Any) -> Unit = { _, _ -> }
    internal var canMove: (Any) -> Boolean = { true }

    /** Klíč právě tažené položky; `null` = nic se netáhne. */
    var draggedKey by mutableStateOf<Any?>(null)
        private set

    internal var offset by mutableFloatStateOf(0f)
        private set

    internal fun start(key: Any) {
        draggedKey = key
        offset = 0f
    }

    internal fun drag(delta: Float) {
        val key = draggedKey ?: return
        offset += delta
        val items = listState.layoutInfo.visibleItemsInfo
        val dragged = items.firstOrNull { it.key == key } ?: return
        val middle = dragged.offset + offset + dragged.size / 2f
        val target = items.firstOrNull { item ->
            item.key != key && canMove(item.key) && middle.toInt() in item.offset until item.offset + item.size
        } ?: return
        // Po výměně stojí tažená položka tam, kde byl soused; posun se o ten rozdíl srovná.
        offset -= target.offset.toFloat() - dragged.offset
        onMove(key, target.key)
        haptics.tick()
    }

    internal fun end() {
        draggedKey = null
        offset = 0f
    }
}

@Composable
fun rememberDragReorderState(
    listState: LazyListState,
    canMove: (Any) -> Boolean,
    onMove: (from: Any, to: Any) -> Unit,
): DragReorderState {
    val haptics = LocalHapticFeedback.current
    val state = remember(listState) { DragReorderState(listState, haptics) }
    val currentMove by rememberUpdatedState(onMove)
    val currentCanMove by rememberUpdatedState(canMove)
    state.onMove = { from, to -> currentMove(from, to) }
    state.canMove = { key -> currentCanMove(key) }
    return state
}

/** Položka, která se dá táhnout: posun pod prstem a nad sousedy, dokud se táhne. */
fun Modifier.draggedItem(state: DragReorderState, key: Any): Modifier =
    zIndex(if (state.draggedKey == key) 1f else 0f)
        .graphicsLayer { translationY = if (state.draggedKey == key) state.offset else 0f }

/** Úchyt: tah začíná hned, bez podržení — podržení položky patří její nabídce. */
fun Modifier.dragHandle(state: DragReorderState, key: Any): Modifier =
    pointerInput(state, key) {
        detectVerticalDragGestures(
            onDragStart = { state.start(key) },
            onDragEnd = state::end,
            onDragCancel = state::end,
            onVerticalDrag = { change, amount ->
                change.consume()
                state.drag(amount)
            },
        )
    }
