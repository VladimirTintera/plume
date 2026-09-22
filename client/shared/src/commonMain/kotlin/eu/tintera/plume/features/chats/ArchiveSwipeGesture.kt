package eu.tintera.plume.features.chats

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChange
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min

/** Co dělat s rozjetým tahem nad položkou seznamu chatů. */
internal enum class ArchiveSwipeDecision { Undecided, Swipe, Ignore }

/**
 * Patří tah s posunem [dx], [dy] od položení prstu archivaci?
 *
 * **Rozhoduje se až po delším pohybu a jen u tahu, který jde VÝRAZNĚ víc do strany.** Dřív to byl
 * `SwipeToDismissBox`, tedy `draggable` po obyčejném touch slopu: stačilo, aby prst ujel do strany
 * o osm bodů dřív, než seznam poznal svislý scroll — a palec jde na začátku tahu obloukem, takže
 * se při scrollování chaty házely do archivu. Teď:
 * - rozhodnutí padne, až je pohyb delší než [slop] (volající dává dvojnásobek touch slopu);
 *   svislý scroll má svůj práh kratší, takže šikmý tah si seznam vezme dřív, než se tu
 *   o čemkoli rozhodne,
 * - vodorovná složka musí být aspoň [HORIZONTAL_DOMINANCE]× větší než svislá (odchylka od
 *   vodorovné do ~27°),
 * - a je jedno, na kterou stranu — obě archivují, viz `SwipeToArchive`.
 */
internal fun archiveSwipeDecision(dx: Float, dy: Float, slop: Float): ArchiveSwipeDecision = when {
    hypot(dx, dy) < slop -> ArchiveSwipeDecision.Undecided
    abs(dx) >= HORIZONTAL_DOMINANCE * abs(dy) -> ArchiveSwipeDecision.Swipe
    else -> ArchiveSwipeDecision.Ignore
}

/**
 * O kolik se musí chat o šířce [width] odtáhnout, aby ho puštění archivovalo. Nejvýš [cap] —
 * na široké ploše (tablet, dvoupanel) by podíl šířky znamenal tah přes půl obrazovky.
 *
 * Nulová šířka (první snímek, než je položka změřená) znamená prah, kterého nejde dosáhnout:
 * tah, o kterém nevíme, jak je dlouhý, nesmí chat archivovat.
 */
internal fun archiveSwipeThreshold(width: Float, cap: Float): Float =
    if (width <= 0f) Float.POSITIVE_INFINITY else min(width * COMMIT_FRACTION, cap)

/** Kolikrát výraznější musí být vodorovná složka tahu než svislá. */
private const val HORIZONTAL_DOMINANCE = 2f

/** Kolikrát delší pohyb než touch slop je potřeba, než si tah vezme archivace. */
private const val SWIPE_SLOP_MULTIPLIER = 2f

/**
 * Podíl šířky řádku, o který se musí chat odtáhnout.
 *
 * `SwipeToDismissBox` měl 56 dp a k tomu zkratku přes rychlost: tah rychlejší než 125 dp/s
 * archivoval bez ohledu na to, jak daleko došel. Právě ta zkratka dělala z každého škubnutí
 * archivaci — proto se tu rychlost nepočítá vůbec a rozhoduje jen ušlá vzdálenost.
 */
private const val COMMIT_FRACTION = 0.4f

/**
 * Tah do strany nad položkou seznamu chatů. [onDrag] dostává vodorovný posun, [onEnd] se volá po
 * puštění (`released = true`) i po zrušení gesta.
 *
 * Dokud se nerozhodne, nic nespotřebovává — jakmile událost spotřebuje seznam svislým scrollem,
 * gesto je jeho a tady se končí. Stejný vzor jako `detectTimeReveal` v konverzaci.
 */
internal suspend fun PointerInputScope.detectArchiveSwipe(
    onEnd: (released: Boolean) -> Unit,
    onDrag: (Float) -> Unit,
) {
    val slop = viewConfiguration.touchSlop * SWIPE_SLOP_MULTIPLIER
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var total = Offset.Zero
        // Fáze rozhodování: jen se sleduje.
        while (true) {
            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
            if (!change.pressed || change.isConsumed) return@awaitEachGesture
            total += change.positionChange()
            when (archiveSwipeDecision(total.x, total.y, slop)) {
                ArchiveSwipeDecision.Undecided -> Unit
                ArchiveSwipeDecision.Ignore -> return@awaitEachGesture
                ArchiveSwipeDecision.Swipe -> {
                    change.consume()
                    // Posun nasbíraný během rozhodování se nezahazuje, jinak by položka
                    // po rozhodnutí skočila o kus pozadu za prstem.
                    onDrag(total.x)
                    break
                }
            }
        }
        // Tah je náš: sleduje se až do puštění.
        var released = false
        try {
            while (true) {
                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
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
