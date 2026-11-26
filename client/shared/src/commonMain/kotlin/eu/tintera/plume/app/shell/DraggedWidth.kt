package eu.tintera.plume.app.shell

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Šířka panelu pod vlastním táhlem (`PaneResize`): za tahu jde přímo za prstem, po puštění
 * dojede na kotvu jednou pružinou — tak, jak to dělá táhlo knihovny.
 *
 * Dřív šířka za tahu skákala po krocích a knihovna každý krok animovala vlastní pružinou panelů
 * (`AnimatedPane`), takže panely prst dotahovaly a cukaly; po puštění pak skočila na kotvu bez
 * animace. Proto za tahu i za dojezdu ([active]) scéna Chatů animaci šířek vypíná
 * (`PaneSceneLayout.instant`) a pohyb řídí jen tahle šířka.
 */
@Stable
internal class DraggedWidth(private val scope: CoroutineScope, private val spec: () -> AnimationSpec<Float>) {
    /** Šířka za tahu nebo dojezdu; `null` = platí kotva. */
    var value: Dp? by mutableStateOf(null)
        private set

    /** Táhne se, nebo se dojíždí na kotvu. */
    val active: Boolean get() = value != null

    private var settling: Job? = null

    /** Posun o [delta] od [from] (platná šířka, když tah teprve začíná), omezený [clamp]. */
    fun drag(from: Dp, delta: Dp, clamp: (Dp) -> Dp) {
        settling?.cancel()
        value = clamp((value ?: from) + delta)
    }

    /** Dojezd z [from] na [target]; na konci [onSettled] (zapsat kotvu) a šířka se vrátí kotvě. */
    fun settle(from: Dp, target: Dp, onSettled: () -> Unit) {
        settling?.cancel()
        settling = scope.launch {
            animate(from.value, target.value, animationSpec = spec()) { width, _ -> value = width.dp }
            onSettled()
            value = null
        }
    }
}

/** [DraggedWidth] s pružinou z `MotionScheme` aplikace (prostorový pohyb, jako panely). */
@Composable
internal fun rememberDraggedWidth(): DraggedWidth {
    val scope = rememberCoroutineScope()
    val spec by rememberUpdatedState(MaterialTheme.motionScheme.defaultSpatialSpec<Float>())
    return remember(scope) { DraggedWidth(scope) { spec } }
}
