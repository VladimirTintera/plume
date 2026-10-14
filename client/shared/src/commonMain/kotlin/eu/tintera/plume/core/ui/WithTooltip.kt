package eu.tintera.plume.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TooltipState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.roundToIntRect
import androidx.compose.ui.window.PopupPositionProvider

/**
 * Obalí [content] M3 **plain tooltipem** s textem [label]: na desktopu se ukáže při najetí myší,
 * na dotyku při dlouhém stisku (chování `TooltipBox`).
 *
 * Bublina se kreslí z kořene kompozice ([RootOverlay]), ne z místa kotvy. Holý `TooltipBox` na
 * desktopu shodí vykreslování (`RootNodeOwner is already disposed`), když kotva s viditelnou
 * bublinou zmizí z kompozice během měření — tak to spadlo u sbaleného seznamu chatů: najetí na
 * avatar, pak tah táhlem, a `BoxWithConstraints` přepnul sloupec avatarů na plný seznam.
 *
 * Převzato z KMPMlog (`core/ui/components/Tooltip.kt`, bez nativních tooltipů pro malá okna).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WithTooltip(
    label: String?,
    modifier: Modifier = Modifier,
    position: TooltipAnchorPosition = TooltipAnchorPosition.Above,
    content: @Composable () -> Unit,
) {
    if (label.isNullOrBlank()) {
        if (modifier == Modifier) content() else Box(modifier, propagateMinConstraints = true) { content() }
        return
    }
    /*
     * Kotva zůstává v M3 TooltipBox (najetí myší, dlouhý stisk, fokus klávesnicí, přístupnost), ale se
     * stavem, který jí hlásí „neviditelný" – popup tak sama nikdy nezaloží. Bublinu kreslí druhý
     * TooltipBox z kořene kompozice nad týmž stavem.
     */
    val state = rememberTooltipState()
    val anchorState = remember(state) { AnchorOnlyTooltipState(state) }
    val anchor = remember { AnchorBounds() }
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(position),
        tooltip = {},
        state = anchorState,
        modifier = modifier
            .onGloballyPositioned { anchor.bounds = it.boundsInWindow() }
            // Ztrátu fokusu a Escape M3 obsluhuje jen u bubliny, kterou kotva vidí jako viditelnou.
            .onFocusChanged { if (!it.hasFocus && state.isVisible) state.dismiss() }
            .onPreviewKeyEvent {
                val escape = it.type == KeyEventType.KeyDown && it.key == Key.Escape && state.isVisible
                if (escape) state.dismiss()
                escape
            },
        content = content,
    )
    if (state.isVisible) {
        val placement = TooltipDefaults.rememberTooltipPositionProvider(position)
        val provider = remember(placement, anchor) { AtAnchorPositionProvider(placement, anchor) }
        RootOverlay {
            TooltipBox(
                positionProvider = provider,
                tooltip = { PlainTooltip { Text(label) } },
                state = state,
                enableUserInput = false,
            ) {}
        }
    }
}

/** Stav pro kotvu: gesta a fokus řídí skutečný [tooltip], ale kotva ho má za skrytý, takže nezaloží popup. */
@OptIn(ExperimentalMaterial3Api::class)
private class AnchorOnlyTooltipState(private val tooltip: TooltipState) : TooltipState by tooltip {
    override val isVisible: Boolean get() = false
}

/** Poslední poloha kotvy v okně; čte se až při polohování bubliny, proto to není stav Compose. */
private class AnchorBounds {
    var bounds: Rect = Rect.Zero
}

/** Polohuje bublinu vykreslenou z kořene ke **kotvě**: výchozímu poskytovateli podstrčí skutečné meze kotvy. */
private class AtAnchorPositionProvider(
    private val placement: PopupPositionProvider,
    private val anchor: AnchorBounds,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = placement.calculatePosition(anchor.bounds.roundToIntRect(), windowSize, layoutDirection, popupContentSize)
}
