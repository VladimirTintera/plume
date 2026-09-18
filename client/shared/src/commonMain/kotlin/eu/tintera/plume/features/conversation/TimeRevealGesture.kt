package eu.tintera.plume.features.conversation

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChange
import kotlin.math.abs
import kotlin.math.hypot

/** Co dělat s rozjetým tahem nad seznamem zpráv. */
internal enum class TimeRevealDecision { Undecided, Reveal, Ignore }

/**
 * Patří tah s posunem [dx], [dy] od položení prstu odkrývání časů?
 *
 * **Rozhoduje se až po delším pohybu a jen u tahu, který jde VÝRAZNĚ víc do strany.** Dřív
 * stačilo, aby prst ujel doleva o jeden touch slop dřív, než seznam poznal svislý scroll — a palec
 * levé ruky jde na začátku tahu obloukem doleva, takže se místo scrollu vysouvaly časy. Teď:
 * - rozhodnutí padne, až je pohyb delší než [slop] (volající dává dvojnásobek touch slopu);
 *   svislý scroll má svůj práh kratší, takže šikmý tah si seznam vezme dřív, než se tu
 *   o čemkoli rozhodne,
 * - vodorovná složka musí být aspoň [HORIZONTAL_DOMINANCE]× větší než svislá (odchylka od
 *   vodorovné do ~27°),
 * - a jen doleva — doprava není co odkrývat (a nad bublinou to je odpověď).
 */
internal fun timeRevealDecision(dx: Float, dy: Float, slop: Float): TimeRevealDecision = when {
    hypot(dx, dy) < slop -> TimeRevealDecision.Undecided
    dx < 0f && abs(dx) >= HORIZONTAL_DOMINANCE * abs(dy) -> TimeRevealDecision.Reveal
    else -> TimeRevealDecision.Ignore
}

private const val HORIZONTAL_DOMINANCE = 2f

/** Kolikrát delší pohyb než touch slop je potřeba, než si tah vezme odkrývání časů. */
private const val REVEAL_SLOP_MULTIPLIER = 2f

/**
 * Tah doleva nad seznamem, který odkrývá časy. [onDrag] dostává vodorovný posun (záporný
 * doleva), [onEnd] se volá po puštění i zrušení.
 *
 * Dokud se nerozhodne, nic nespotřebovává. Jakmile událost spotřebuje někdo jiný — seznam
 * svislým scrollem, bublina tahem na odpověď —, gesto je jeho a tady se končí.
 */
internal suspend fun PointerInputScope.detectTimeReveal(
    onEnd: () -> Unit,
    onDrag: (Float) -> Unit,
) {
    val slop = viewConfiguration.touchSlop * REVEAL_SLOP_MULTIPLIER
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var total = Offset.Zero
        // Fáze rozhodování: jen se sleduje.
        while (true) {
            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
            if (!change.pressed || change.isConsumed) return@awaitEachGesture
            total += change.positionChange()
            when (timeRevealDecision(total.x, total.y, slop)) {
                TimeRevealDecision.Undecided -> Unit
                TimeRevealDecision.Ignore -> return@awaitEachGesture
                TimeRevealDecision.Reveal -> {
                    change.consume()
                    // Posun nasbíraný během rozhodování se nezahazuje, jinak by plocha
                    // po rozhodnutí skočila o kus pozadu za prstem.
                    onDrag(total.x)
                    break
                }
            }
        }
        // Tah je náš: sleduje se až do puštění.
        try {
            while (true) {
                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                onDrag(change.positionChange().x)
                change.consume()
            }
        } finally {
            onEnd()
        }
    }
}
