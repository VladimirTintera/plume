package eu.tintera.plume.core.ui

import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChange
import kotlin.math.abs
import kotlin.math.hypot

/** Kterým směrem se tah bere. */
internal enum class SwipeDirection { Left, Right, Either }

/** Co dělat s rozjetým tahem nad svisle scrollovaným seznamem. */
internal enum class SwipeDecision { Undecided, Swipe, Ignore }

/**
 * Patří tah s posunem [dx], [dy] od položení prstu gestu do strany ve směru [direction]?
 *
 * **Rozhoduje se až po delším pohybu a jen u tahu, který jde VÝRAZNĚ víc do strany.** Všechna
 * tři gesta do strany nad svislým seznamem (odkrývání časů a odpověď v konverzaci, archivace
 * v seznamu chatů) si tah zpočátku brala po obyčejném touch slopu — a palec jde na začátku
 * scrollu obloukem do strany, takže se při scrollování vysouvaly časy, zakládaly odpovědi
 * a chaty odlétaly do archivu. Teď:
 * - rozhodnutí padne, až je pohyb delší než [slop] (detektor dává dvojnásobek touch slopu);
 *   svislý scroll má svůj práh kratší, takže šikmý tah si seznam vezme dřív, než se tu
 *   o čemkoli rozhodne,
 * - vodorovná složka musí být aspoň [HORIZONTAL_DOMINANCE]× větší než svislá (odchylka od
 *   vodorovné do ~27°),
 * - a jen směrem [direction]; tah opačným směrem může patřit jinému gestu (doleva časy,
 *   doprava odpověď).
 */
internal fun horizontalSwipeDecision(
    dx: Float,
    dy: Float,
    slop: Float,
    direction: SwipeDirection,
): SwipeDecision {
    if (hypot(dx, dy) < slop) return SwipeDecision.Undecided
    val directionOk = when (direction) {
        SwipeDirection.Left -> dx < 0f
        SwipeDirection.Right -> dx > 0f
        SwipeDirection.Either -> dx != 0f
    }
    return if (directionOk && abs(dx) >= HORIZONTAL_DOMINANCE * abs(dy)) SwipeDecision.Swipe else SwipeDecision.Ignore
}

/** Kolikrát výraznější musí být vodorovná složka tahu než svislá. */
private const val HORIZONTAL_DOMINANCE = 2f

/** Kolikrát delší pohyb než touch slop je potřeba, než si tah vezme gesto do strany. */
private const val SWIPE_SLOP_MULTIPLIER = 2f

/**
 * Tah do strany nad svisle scrollovaným seznamem (nebo nad jeho položkou). [onDrag] dostává
 * vodorovný posun — první volání celý posun nasbíraný během rozhodování, ať plocha nezůstane
 * o kus pozadu za prstem —, [onEnd] se volá po puštění (`released = true`) i po zrušení.
 *
 * Dokud se nerozhodne, nic nespotřebovává. Jakmile událost spotřebuje někdo jiný — seznam
 * svislým scrollem, položka vlastním tahem —, gesto je jeho a tady se končí. Spotřebování se
 * kontroluje i ve fázi `Final`: položka seznamu dostává události DŘÍV než seznam, takže ve
 * fázi `Main` by rozjetý scroll neviděla (stejně to dělá `awaitTouchSlopOrCancellation`).
 *
 * [mouse] `false` = tah myší se nebere. V konverzaci na desktopu patří tah myší výběru textu
 * (`bubbleTextSelectable`); mimo text by jinak dál zakládal odpověď nebo odkrýval časy.
 */
internal suspend fun PointerInputScope.detectHorizontalSwipe(
    direction: SwipeDirection,
    onEnd: (released: Boolean) -> Unit,
    mouse: Boolean = true,
    onDrag: (Float) -> Unit,
) = detectHorizontalSwipe(direction = { direction }, beforeChildren = { false }, onEnd, mouse, onDrag)

/**
 * Totéž, jen se [direction] a [beforeChildren] čtou znovu na začátku KAŽDÉHO tahu — gesto tak
 * může měnit chování podle stavu, aniž by se `pointerInput` restartoval (restart by rozjetý tah
 * zrušil a zavolal [onEnd]).
 *
 * [beforeChildren] `true` = o tahu se rozhoduje ve fázi `Initial`, tedy DŘÍV než potomci.
 * Zafixované časy v konverzaci se schovávají tahem doprava, a ten nad bublinou jinak vyhraje
 * odpověď swipem: bublina je potomek, dostane událost ve fázi `Main` dřív než seznam a obě
 * gesta se rozhodují na stejném prahu. Spotřebování se dál kontroluje ve fázi `Final`, takže
 * svislý scroll seznamu má přednost i tak.
 */
internal suspend fun PointerInputScope.detectHorizontalSwipe(
    direction: () -> SwipeDirection,
    beforeChildren: () -> Boolean,
    onEnd: (released: Boolean) -> Unit,
    mouse: Boolean = true,
    onDrag: (Float) -> Unit,
) {
    val slop = viewConfiguration.touchSlop * SWIPE_SLOP_MULTIPLIER
    awaitEachGesture {
        val pass = if (beforeChildren()) PointerEventPass.Initial else PointerEventPass.Main
        val down = awaitFirstDown(requireUnconsumed = false, pass = pass)
        if (!mouse && down.type == PointerType.Mouse) return@awaitEachGesture
        val swipeDirection = direction()
        var total = Offset.Zero
        // Fáze rozhodování: jen se sleduje.
        while (true) {
            val change = awaitPointerEvent(pass).changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
            if (!change.pressed || change.isConsumed) return@awaitEachGesture
            total += change.positionChange()
            when (horizontalSwipeDecision(total.x, total.y, slop, swipeDirection)) {
                SwipeDecision.Undecided -> {
                    awaitPointerEvent(PointerEventPass.Final)
                    if (change.isConsumed) return@awaitEachGesture
                }
                SwipeDecision.Ignore -> return@awaitEachGesture
                SwipeDecision.Swipe -> {
                    change.consume()
                    onDrag(total.x)
                    break
                }
            }
        }
        // Tah je náš: sleduje se až do puštění.
        var released = false
        try {
            while (true) {
                val change = awaitPointerEvent(pass).changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) {
                    released = true
                    break
                }
                onDrag(change.positionChange().x)
                change.consume()
            }
        } finally {
            onEnd(released)
        }
    }
}
