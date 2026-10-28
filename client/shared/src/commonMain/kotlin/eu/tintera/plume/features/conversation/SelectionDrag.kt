package eu.tintera.plume.features.conversation

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.ui.threshold
import eu.tintera.plume.core.ui.tick
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Kde na obrazovce leží zprávy v režimu výběru — podle toho tah pozná, nad kterou je prst.
 * Hlásí to řádky samy ([reportSelectionTarget]): pozice položek v obráceném seznamu se z
 * `LazyListLayoutInfo` počítají nejistě a album nebo dlouhá zpráva jsou jedna položka s víc
 * zprávami, takže řádek je přesnější zdroj.
 */
@Stable
internal class SelectionDragTargets {
    private val bounds = HashMap<Long, Rect>()

    fun report(messageId: Long, rect: Rect) {
        bounds[messageId] = rect
    }

    fun remove(messageId: Long) {
        bounds.remove(messageId)
    }

    /** Zpráva pod bodem [window] v souřadnicích okna; mezi zprávami ta nejbližší na výšku. */
    fun at(window: Offset): Long? {
        bounds.entries.firstOrNull { it.value.contains(window) }?.let { return it.key }
        return bounds.entries
            .filter { window.y in it.value.top - NEAREST_SLOP..it.value.bottom + NEAREST_SLOP }
            .minByOrNull { minOf(kotlin.math.abs(window.y - it.value.top), kotlin.math.abs(window.y - it.value.bottom)) }
            ?.key
    }

    private companion object {
        /** Mezera mezi bublinami: prst v ní patří sousední zprávě, ne „nikomu". */
        const val NEAREST_SLOP = 48f
    }
}

internal val LocalSelectionDragTargets = staticCompositionLocalOf<SelectionDragTargets?> { null }

/** Řádek zprávy [messageId] hlásí svoji polohu tahu výběrem; mimo výběr nedělá nic. */
@Composable
internal fun Modifier.reportSelectionTarget(messageId: Long): Modifier {
    val targets = LocalSelectionDragTargets.current ?: return this
    DisposableEffect(targets, messageId) { onDispose { targets.remove(messageId) } }
    return onGloballyPositioned { targets.report(messageId, it.boundsInWindow()) }
}

/**
 * **Výběr tahem**: v režimu výběru podržet zprávu a táhnout — vybere se všechno mezi.
 *
 * - **Gesto patří seznamu a čte v průchodu `Initial`**: dřív než překryv řádku (ťuknutí přepne
 *   jednu zprávu) i seznam (scroll). Dokud prst nepodrží, nic se nespotřebuje — obyčejný scroll
 *   i ťuknutí fungují jako dřív. Po podržení si tah bere všechny pohyby, takže seznam pod
 *   prstem nejede a puštění nic neťukne.
 * - **U okraje se seznam posouvá sám** ([EDGE]), čím blíž okraji, tím rychleji; zpráva pod prstem
 *   se po každém posunu dopočítá znovu. Seznam je obrácený: kladný posun jede ke starším,
 *   tedy nahoru.
 * - Haptika: podržení je práh ([threshold]), každá nově přejetá zpráva tik ([tick]).
 */
@Composable
internal fun Modifier.selectionDrag(
    enabled: Boolean,
    targets: SelectionDragTargets,
    listState: LazyListState,
    /**
     * Co seznam nahoře a dole překrývá (hlavička s pruhy, vstupní pole) — pás posunu leží až
     * pod tím, jinak by jeho nejrychlejší část byla pod lištou, kam prst nedosáhne.
     */
    topInset: Dp,
    bottomInset: Dp,
    onStart: (messageId: Long) -> Unit,
    onMove: (messageId: Long) -> Unit,
    onEnd: () -> Unit,
): Modifier {
    if (!enabled) return this
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val edge = with(LocalDensity.current) { EDGE.toPx() }
    val top = with(LocalDensity.current) { topInset.toPx() }
    val bottom = with(LocalDensity.current) { bottomInset.toPx() }
    val maxSpeed = with(LocalDensity.current) { MAX_SCROLL_PER_FRAME.toPx() }
    val start by rememberUpdatedState(onStart)
    val move by rememberUpdatedState(onMove)
    val end by rememberUpdatedState(onEnd)
    val origin = remember { arrayOf(Offset.Zero, Offset.Zero) }
    return this
        .onGloballyPositioned { origin[0] = it.positionInWindow(); origin[1] = Offset(it.size.width.toFloat(), it.size.height.toFloat()) }
        .pointerInput(targets) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val anchor = targets.at(origin[0] + down.position) ?: return@awaitEachGesture
                // Do podržení se nic nespotřebuje: puštění je ťuknutí, posun scroll.
                val early = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                    while (true) {
                        val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                            ?: return@withTimeoutOrNull Unit
                        if (!change.pressed || change.isConsumed) return@withTimeoutOrNull Unit
                        if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) return@withTimeoutOrNull Unit
                    }
                }
                if (early != null) return@awaitEachGesture

                haptics.threshold()
                start(anchor)
                var last = anchor
                var pointer = down.position
                fun update(h: HapticFeedback) {
                    val over = targets.at(origin[0] + pointer) ?: return
                    if (over != last) {
                        last = over
                        h.tick()
                        move(over)
                    }
                }
                // Posun u okraje: smyčka po snímcích, dokud prst drží.
                val autoScroll: Job = scope.launch {
                    while (isActive) {
                        withFrameNanos { }
                        val upper = top + edge
                        val lower = origin[1].y - bottom - edge
                        val speed = when {
                            pointer.y < upper -> ((upper - pointer.y) / edge).coerceAtMost(1f) * maxSpeed
                            pointer.y > lower -> -((pointer.y - lower) / edge).coerceAtMost(1f) * maxSpeed
                            else -> 0f
                        }
                        if (speed != 0f) {
                            listState.scrollBy(speed)
                            update(haptics)
                        }
                    }
                }
                try {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        change.consume()
                        if (!change.pressed) break
                        pointer = change.position
                        update(haptics)
                    }
                } finally {
                    autoScroll.cancel()
                    end()
                }
            }
        }
}

/** Pás u horního a dolního okraje, ve kterém se seznam při tahu posouvá sám. */
private val EDGE = 72.dp

/** Nejvyšší rychlost posunu u samého okraje. */
private val MAX_SCROLL_PER_FRAME = 18.dp
