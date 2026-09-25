package eu.tintera.plume.core.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.SuspendingPointerInputModifierNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.ModifierNodeElement
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Je dotyk nad pruhem reakcí ťuknutí, nebo jen kus jiného pohybu?
 *
 * Ťuknutím NENÍ:
 * - dotyk, který začal dřív než [STRAY_TAP_GRACE] po objevení pruhu ([sinceShown]) — pruh
 *   vyskočil pod prstem, který mířil jinam (dvojí ťuknutí na fotku, pokračování scrollu po
 *   omylem otevřené nabídce),
 * - dotyk, který ujel dál než [slop] ([travel] = nejdelší vzdálenost od položení) — je to tah,
 * - dotyk víc prsty naráz ([pointers]).
 */
internal fun isStrayTap(sinceShown: Duration, travel: Float, slop: Float, pointers: Int): Boolean =
    sinceShown < STRAY_TAP_GRACE || travel > slop || pointers > 1

/** Jak dlouho po objevení pruh ťuknutí nebere. Dvojí ťuknutí se vejde do ~300 ms. */
internal val STRAY_TAP_GRACE: Duration = 500.milliseconds

/**
 * Pojistka nad řadou tlačítek, která mění stav jedním ťuknutím (pruh reakcí): nechtěný dotyk
 * spotřebuje, takže `clickable` potomků ho vyhodnotí jako zrušený a nic se nestane.
 *
 * **`clickable` sám tah nezruší, dokud prst neopustí tlačítko.** `waitForUpOrCancellation`
 * končí zrušením jen při spotřebované události nebo pohybu MIMO hranice (rozšířené na 48 dp) —
 * posun uvnitř bere jako ťuknutí. Nad seznamem to nevadí, scroll pohyb spotřebuje; pruh reakcí
 * ale nad sebou nic scrollovatelného nemá, takže nedbalý švih přes srdíčko dal srdíčko.
 *
 * Běží v [PointerEventPass.Initial], tedy PŘED potomky: spotřebovaný dotyk k nim dorazí už
 * spotřebovaný. [key] je obsah pruhu (zpráva) — s jiným obsahem běží lhůta od začátku.
 *
 * **Okamžik objevení zapisuje uzel při připojení, ne blok `pointerInput`.** Ten Compose spouští
 * až s PRVNÍM dotykem (`SuspendingPointerInputModifierNodeImpl`: „Coroutine lazily launches when
 * first event comes in") — čas z něj tedy byl čas prvního dotyku a lhůta spolkla KAŽDÉ první
 * ťuknutí, i to po deseti vteřinách. Reakce z nabídky nad zprávou zabírala až na druhé ťuknutí.
 */
fun Modifier.strayTapGuard(key: Any?): Modifier = this then StrayTapGuardElement(key)

private data class StrayTapGuardElement(val key: Any?) : ModifierNodeElement<StrayTapGuardNode>() {
    override fun create() = StrayTapGuardNode()

    // Volá se jen s jiným klíčem (element je data class): nový obsah pruhu = nová lhůta.
    override fun update(node: StrayTapGuardNode) = node.restart()
}

private class StrayTapGuardNode : DelegatingNode() {
    private var shownAt: TimeMark = TimeSource.Monotonic.markNow()

    private val input = delegate(
        SuspendingPointerInputModifierNode {
            val slop = viewConfiguration.touchSlop
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val sinceShown = shownAt.elapsedNow()
                var stray = isStrayTap(sinceShown, travel = 0f, slop = slop, pointers = 1)
                if (stray) down.consume()
                var travel = 0f
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    travel = maxOf(travel, (change.position - down.position).getDistance())
                    val pointers = event.changes.count { it.pressed || it.previousPressed }
                    if (!stray) stray = isStrayTap(sinceShown, travel, slop, pointers)
                    if (stray) event.changes.forEach { it.consume() }
                    if (!change.pressed) break
                }
            }
        },
    )

    override fun onAttach() {
        shownAt = TimeSource.Monotonic.markNow()
    }

    fun restart() {
        shownAt = TimeSource.Monotonic.markNow()
        input.resetPointerInputHandler()
    }
}
