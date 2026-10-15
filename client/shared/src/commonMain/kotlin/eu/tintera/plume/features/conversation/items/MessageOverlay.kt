package eu.tintera.plume.features.conversation.items

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.round

/**
 * Kresba, která má přesahovat zprávu, i když ji kreslí obsah UVNITŘ ní.
 *
 * Obsah zprávy leží ve vrstvě ořezané tvarem bubliny ([MessageBox]) — i bez bubliny, u samolepky
 * a animovaného emoji. Efekt emoji nakreslený na místě se proto uřízl na hranici zprávy. Obsah
 * sem místo toho předá, CO kreslit a KDE (bod v souřadnicích rámu); kreslí se mimo ořez.
 */
@Stable
internal class MessageOverlay {
    private var host: LayoutCoordinates? = null

    private var content by mutableStateOf<(@Composable () -> Unit)?>(null)
    private var anchor by mutableStateOf(Offset.Zero)

    /** Bod [local] prvku [coordinates] jako kotva v souřadnicích rámu; `null`, dokud rám není umístěný. */
    fun anchorOf(coordinates: LayoutCoordinates, local: Offset): Offset? =
        host?.takeIf { it.isAttached && coordinates.isAttached }?.localPositionOf(coordinates, local)

    fun show(at: Offset, drawing: @Composable () -> Unit) {
        anchor = at
        content = drawing
    }

    fun hide(drawing: @Composable () -> Unit) {
        if (content === drawing) content = null
    }

    /** Rám, ke kterému se kotvy počítají, je umístěný (`onPlaced`). */
    fun hostPlaced(coordinates: LayoutCoordinates) {
        host = coordinates
    }

    /** Kresba v bodě kotvy; sama plochu nezabírá. Patří do rámu ZA ořezaný obsah. */
    @Composable
    fun Content() {
        val drawing = content ?: return
        Box(Modifier.offset { anchor.round() }) { drawing() }
    }
}

/** Vrstva nad zprávou, do které se kreslí mimo ořez; `null` mimo [MessageBox]. */
internal val LocalMessageOverlay = staticCompositionLocalOf<MessageOverlay?> { null }
