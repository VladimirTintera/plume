package eu.tintera.plume.features.conversation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.input.pointer.pointerInput
import eu.tintera.plume.core.ui.SwipeDirection
import eu.tintera.plume.core.ui.detectHorizontalSwipe
import eu.tintera.plume.core.ui.toggle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Zafixované časy: po podržení odkrytých časů zůstanou vidět i po puštění, dokud je uživatel
 * nepřetáhne zpátky doprava.
 *
 * Obyčejná třída, ne stav Compose: nic se podle ní neskládá, čte ji jen gesto. Drží ji
 * `remember` v obrazovce, takže přežije restart `pointerInput` (změna šířky sloupce časů)
 * a s odchodem z chatu zanikne — v dalším chatu časy zafixované nejsou.
 */
internal class TimesPin {
    var pinned = false
    var holdJob: Job? = null
}

/**
 * Odkrývání časů tahem doleva nad seznamem zpráv (viz „Odkrývání časů tahem"
 * v `docs/poznatky/konverzace-pohyb.md`).
 *
 * - **Nahlédnutí:** tah doleva odkryje časy, puštění je schová.
 * - **Zafixování:** podrží-li prst časy úplně odkryté [PIN_HOLD_MS], ozve se haptika
 *   (`toggle(true)`) a časy po puštění zůstanou.
 * - **Schování zafixovaných:** tah doprava (nad bublinou místo odpovědi swipem, proto
 *   `beforeChildren`). Puštěný za polovinou dráhy časy schová (`toggle(false)`), jinak se vrátí.
 *
 * Posun se čte až při kreslení vrstvy ([timeReveal]), takže gesto konverzaci nepřeskládává.
 */
internal fun Modifier.revealTimesSwipe(
    timeReveal: Animatable<Float, AnimationVector1D>,
    revealMax: Float,
    pin: TimesPin,
    spec: AnimationSpec<Float>,
    scope: CoroutineScope,
    haptics: HapticFeedback,
    mouse: Boolean,
    onUsed: () -> Unit,
): Modifier = pointerInput(revealMax) {
    detectHorizontalSwipe(
        direction = { if (pin.pinned) SwipeDirection.Right else SwipeDirection.Left },
        beforeChildren = { pin.pinned },
        mouse = mouse,
        onEnd = { released ->
            scope.launch {
                pin.holdJob?.cancel()
                pin.holdJob = null
                onUsed()
                // Zrušené gesto (přerušení systémem) zafixované časy nechá.
                val keep = pin.pinned && (!released || timeReveal.value >= revealMax * UNPIN_FRACTION)
                if (keep) {
                    timeReveal.animateTo(revealMax, spec)
                } else {
                    if (pin.pinned) {
                        pin.pinned = false
                        haptics.toggle(false)
                    }
                    timeReveal.animateTo(0f, spec)
                }
            }
        },
    ) { delta ->
        scope.launch {
            // Jen po šířku sloupce s časy; dál už není co odkrývat a gumové tahání by mátlo.
            val next = (timeReveal.value - delta).coerceIn(0f, revealMax)
            timeReveal.snapTo(next)
            if (pin.pinned) return@launch
            // Podržení se měří od chvíle, kdy jsou časy celé venku; ucuknutí zpátky ho zruší.
            // Prst, který dál táhne doleva, časy drží celé (posun je zastropovaný) — to se počítá.
            if (next >= revealMax) {
                if (pin.holdJob == null) {
                    pin.holdJob = scope.launch {
                        delay(PIN_HOLD_MS)
                        pin.pinned = true
                        haptics.toggle(true)
                    }
                }
            } else {
                pin.holdJob?.cancel()
                pin.holdJob = null
            }
        }
    }
}

/**
 * Jak dlouho musí prst držet časy odkryté, než se zafixují. Delší než systémový dlouhý stisk
 * (400 ms): kdo jen nahlíží, zastaví se u odkrytých časů na pár set milisekund, než je přečte.
 */
private const val PIN_HOLD_MS = 600L

/** Kolik dráhy musí tah doprava ujet, aby zafixované časy schoval. */
private const val UNPIN_FRACTION = 0.5f
