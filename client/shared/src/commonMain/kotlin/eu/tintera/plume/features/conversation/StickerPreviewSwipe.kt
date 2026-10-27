package eu.tintera.plume.features.conversation

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalHapticFeedback
import eu.tintera.plume.core.telegram.StickerItem
import eu.tintera.plume.core.ui.tick

/**
 * **Přejíždění prstem mezi náhledy samolepek**, jako v Telegramu: podržení otevře náhled
 * ([StickerPreviewDialog]) a dokud prst neopustí displej, přejetí na jinou buňku přepne náhled na
 * její samolepku. Po puštění náhled zůstane otevřený na poslední — nese tlačítka (Poslat,
 * oblíbené, volby odeslání), takže zavřít ho puštěním jako Telegram nejde.
 *
 * - **Gesto sleduje buňka, na které začalo.** Compose posílá celý dotyk uzlům zasaženým při
 *   položení prstu, takže jiné buňky o pohybu nevědí; buňka proto zjišťuje, nad kterou buňkou
 *   prst je, z jejich poloh (tahle třída). Dialog náhledu je vlastní okno, ale dotyk, který začal
 *   v sheetu, Android doručuje dál do sheetu až do puštění.
 * - **Pohyb s otevřeným náhledem se spotřebuje**, jinak by ho po překročení prahu převzal posun
 *   mřížky nebo tah sheetu dolů. Buňka ho bere v průchodu Initial, dřív než předci v Main.
 * - **Buňky se evidují podle polohy, ne podle id**: tatáž samolepka bývá v jedné mřížce dvakrát
 *   (oblíbené i nedávné) a každá buňka má vlastní místo.
 */
@Stable
internal class StickerPreviewSwipe {
    internal class Cell(var sticker: StickerItem, var coordinates: LayoutCoordinates?)

    private val cells = mutableSetOf<Cell>()

    fun add(cell: Cell) {
        cells += cell
    }

    fun remove(cell: Cell) {
        cells -= cell
    }

    /** Samolepka pod bodem [rootPosition] (souřadnice kořene okna sheetu), nebo `null`. */
    fun stickerAt(rootPosition: Offset): StickerItem? = cells.firstOrNull { cell ->
        val coordinates = cell.coordinates ?: return@firstOrNull false
        coordinates.isAttached && coordinates.boundsInRoot().contains(rootPosition)
    }?.sticker
}

/**
 * Buňka mřížky v evidenci [swipe]: sleduje gesto podržení, které na ní začalo, a při otevřeném
 * náhledu ([previewOpen]) přepne pohyb prstu náhled přes [onPreview]. Bez [swipe] nic nedělá.
 */
@Composable
internal fun Modifier.stickerPreviewSwipe(
    swipe: StickerPreviewSwipe?,
    sticker: StickerItem,
    previewOpen: () -> Boolean,
    onPreview: (StickerItem) -> Unit,
): Modifier {
    swipe ?: return this
    val cell = remember { StickerPreviewSwipe.Cell(sticker, null) }
    cell.sticker = sticker
    DisposableEffect(swipe, cell) {
        swipe.add(cell)
        onDispose { swipe.remove(cell) }
    }
    val haptics = LocalHapticFeedback.current
    val open by rememberUpdatedState(previewOpen)
    val preview by rememberUpdatedState(onPreview)
    return this
        .onPlaced { cell.coordinates = it }
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                var shown: StickerItem? = null
                while (true) {
                    val change = awaitPointerEvent(PointerEventPass.Initial).changes
                        .firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    // Náhled otevírá podržení (`contextClickable` v buňce); do té doby se nic nebere.
                    if (!open()) continue
                    if (shown == null) shown = cell.sticker
                    change.consume()
                    val coordinates = cell.coordinates?.takeIf { it.isAttached } ?: continue
                    val target = swipe.stickerAt(coordinates.localToRoot(change.position)) ?: continue
                    if (target.id != shown?.id) {
                        shown = target
                        // Tiknutí po dílcích: „jsi nad další samolepkou" (`Haptics`).
                        haptics.tick()
                        preview(target)
                    }
                }
            }
        }
}
