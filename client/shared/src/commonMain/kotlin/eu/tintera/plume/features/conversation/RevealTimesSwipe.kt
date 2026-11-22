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

    /**
     * Kam až prst v tomhle tahu dojel, v px odkrytí — BEZ stropu šířkou sloupce. `null` mezi
     * tahy. Posun časů se ze stropu počítá až z téhle hodnoty: kdo přetáhne dál doleva, má
     * rezervu a malý pohyb zpátky časy nestáhne (dřív stáhl hned první pixel).
     */
    var finger: Float? = null
}

/** Co udělat s odpočtem zafixování po pohybu prstu. */
internal enum class HoldAction { Start, Cancel, None }

/**
 * Odpočet zafixování podle polohy prstu [finger] (px odkrytí bez stropu), když je odpočet
 * [holding] spuštěný, nebo ne.
 *
 * **Dva prahy, ne jeden.** Odpočet začne, když jsou časy skoro celé venku
 * ([PIN_START_FRACTION]), a zruší se, až když prst opravdu ucukne zpátky
 * ([PIN_CANCEL_FRACTION]). S jediným prahem na plné šířce ho rušilo chvění prstu: skutečný
 * prst v klidu cuká o pixel dva oběma směry, každý pohyb doprava časy stáhl pod strop
 * a fixace se chytila jen tomu, kdo pořád táhl doleva. Emulátor to neukázal — `input
 * motionevent` v klidu stojí na místě.
 */
internal fun holdAction(finger: Float, revealMax: Float, holding: Boolean): HoldAction = when {
    !holding && finger >= revealMax * PIN_START_FRACTION -> HoldAction.Start
    holding && finger < revealMax * PIN_CANCEL_FRACTION -> HoldAction.Cancel
    else -> HoldAction.None
}

/**
 * Odkrývání časů tahem doleva nad seznamem zpráv (viz „Odkrývání časů tahem"
 * v `docs/poznatky/konverzace-pohyb.md`).
 *
 * - **Nahlédnutí:** tah doleva odkryje časy, puštění je schová.
 * - **Zafixování:** podrží-li prst časy (skoro) celé odkryté [PIN_HOLD_MS], ozve se haptika
 *   (`toggle(true)`) a časy po puštění zůstanou; pravidlo je v [holdAction].
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
                pin.finger = null
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
            // Prst se sleduje bez stropu (začíná tam, kde časy právě jsou), posun časů jen po
            // šířku sloupce; dál už není co odkrývat a gumové tahání by mátlo.
            val finger = ((pin.finger ?: timeReveal.value) - delta).coerceAtLeast(0f)
            pin.finger = finger
            timeReveal.snapTo(finger.coerceAtMost(revealMax))
            if (pin.pinned) return@launch
            when (holdAction(finger, revealMax, holding = pin.holdJob != null)) {
                HoldAction.Start -> pin.holdJob = scope.launch {
                    delay(PIN_HOLD_MS)
                    pin.pinned = true
                    haptics.toggle(true)
                }
                HoldAction.Cancel -> {
                    pin.holdJob?.cancel()
                    pin.holdJob = null
                }
                HoldAction.None -> Unit
            }
        }
    }
}

/**
 * Jak dlouho musí prst držet časy odkryté, než se zafixují. Delší než systémový dlouhý stisk
 * (400 ms): kdo jen nahlíží, zastaví se u odkrytých časů na pár set milisekund, než je přečte.
 */
private const val PIN_HOLD_MS = 600L

/** Od jaké části šířky sloupce časů začne odpočet zafixování. */
private const val PIN_START_FRACTION = 0.85f

/** Pod jakou část šířky musí prst ucuknout, aby se odpočet zrušil. */
private const val PIN_CANCEL_FRACTION = 0.6f

/** Kolik dráhy musí tah doprava ujet, aby zafixované časy schoval. */
private const val UNPIN_FRACTION = 0.5f
