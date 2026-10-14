package eu.tintera.plume.features.calls

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.ui.longPress
import kotlin.time.TimeSource

/**
 * Ukazovátko na obrazovce, kterou sdílí protistrana: podržení prstu (myši) a tah posílají
 * místo protistraně, která ho ukáže přes svou obrazovku. Krátké ťuknutí zůstává ovládání
 * (schovat / ukázat), proto gesto začíná až podržením a pak všechno spotřebuje.
 */
@Stable
internal class CallPointerState {
    /** Kde je prst teď (pro vlastní kroužek), `null` = neukazuje se. */
    var finger by mutableStateOf<Offset?>(null)
}

/**
 * Gesto ukazovátka nad obrazem protistrany. [frameAspect] je poměr stran jejího obrazu
 * (šířka / výška); [onPoint] dostane místo v poměru k obrazu (0–1) a jestli je prst dole.
 * Posílá se nejvýš po [SEND_INTERVAL_MS] — signalizace jde přes server a víc zpráv by ji
 * jen zahltilo; zvednutí prstu jde vždycky.
 *
 * Patří ZA modifikátor ovládání (`clickable`): v hlavním průchodu jde událost napřed sem
 * a spotřebovaná už ťuknutí nepřepne.
 */
@Composable
internal fun Modifier.callPointer(
    enabled: Boolean,
    frameAspect: Float,
    state: CallPointerState,
    onPoint: (x: Float, y: Float, down: Boolean) -> Unit,
): Modifier {
    if (!enabled) return this
    val haptics = LocalHapticFeedback.current
    val point by rememberUpdatedState(onPoint)
    val aspect by rememberUpdatedState(frameAspect)
    return pointerInput(Unit) {
        awaitEachGesture {
            val first = awaitFirstDown(requireUnconsumed = false)
            // Do podržení: zvednutí nebo pohyb přes práh = obyčejné ťuknutí / tah, ne ukazovátko.
            val short = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                while (true) {
                    val change = awaitPointerEvent().changes.firstOrNull { it.id == first.id } ?: return@withTimeoutOrNull true
                    if (!change.pressed || (change.position - first.position).getDistance() > viewConfiguration.touchSlop) {
                        return@withTimeoutOrNull true
                    }
                }
                @Suppress("UNREACHABLE_CODE")
                true
            }
            if (short != null) return@awaitEachGesture
            haptics.longPress()
            var lastSent = TimeSource.Monotonic.markNow()
            fun send(position: Offset, down: Boolean, force: Boolean) {
                state.finger = position.takeIf { down }
                val normalized = pointerOnFrame(position, size, aspect) ?: return
                if (!force && lastSent.elapsedNow().inWholeMilliseconds < SEND_INTERVAL_MS) return
                lastSent = TimeSource.Monotonic.markNow()
                point(normalized.x, normalized.y, down)
            }
            send(first.position, down = true, force = true)
            while (true) {
                val change = awaitPointerEvent().changes.firstOrNull { it.id == first.id } ?: break
                change.consume()
                if (!change.pressed) {
                    send(change.position, down = false, force = true)
                    break
                }
                send(change.position, down = true, force = false)
            }
            state.finger = null
        }
    }
}

/** Vlastní kroužek pod prstem, než značka dorazí ve sdíleném obrazu (cesta tam a zpět ~0,4 s). */
@Composable
internal fun CallPointerFinger(state: CallPointerState, modifier: Modifier = Modifier) {
    val finger = state.finger ?: return
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier.fillMaxSize()) {
        drawCircle(color.copy(alpha = 0.3f), radius = 28.dp.toPx(), center = finger)
        drawCircle(color, radius = 28.dp.toPx(), center = finger, style = Stroke(3.dp.toPx()))
    }
}

/**
 * Místo dotyku → místo v obrazu protistrany (0–1), stejně jako ho kreslí `CallVideo`: obraz
 * stejně orientovaný jako plocha ji vyplní (okraje se oříznou), jinak orientovaný se vejde
 * celý (s pruhy). Dotyk mimo obraz (v pruhu) = `null`.
 */
internal fun pointerOnFrame(position: Offset, size: IntSize, frameAspect: Float): Offset? {
    if (size.width <= 0 || size.height <= 0 || frameAspect <= 0f) return null
    val boxAspect = size.width.toFloat() / size.height
    val fill = (frameAspect > 1f) == (boxAspect > 1f)
    val widthBound = boxAspect > frameAspect
    // Vyplnění: obraz roste, dokud nepokryje plochu; přizpůsobení: dokud se nedotkne okraje.
    val (imageWidth, imageHeight) = if (fill == widthBound) {
        size.width.toFloat() to size.width / frameAspect
    } else {
        size.height * frameAspect to size.height.toFloat()
    }
    val x = (position.x - (size.width - imageWidth) / 2f) / imageWidth
    val y = (position.y - (size.height - imageHeight) / 2f) / imageHeight
    return Offset(x, y).takeIf { x in 0f..1f && y in 0f..1f }
}

private const val SEND_INTERVAL_MS = 50L
