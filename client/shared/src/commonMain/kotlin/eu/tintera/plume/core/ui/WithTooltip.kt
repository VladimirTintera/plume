package eu.tintera.plume.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TooltipScope
import androidx.compose.material3.TooltipState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.roundToIntRect
import androidx.compose.ui.window.PopupPositionProvider
import kotlin.math.roundToInt

/**
 * Obalí [content] M3 **plain tooltipem** s textem [label]: na desktopu se ukáže při najetí myší,
 * na dotyku při dlouhém stisku (chování `TooltipBox`).
 *
 * Bublina se kreslí z kořene kompozice ([RootTooltipBox]), ne z místa kotvy. Holý `TooltipBox` na
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
    RootTooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(position),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
        modifier = modifier,
        content = content,
    )
}

/**
 * M3 `TooltipBox` se stejnými parametry, jen bublinu kreslí z kořene kompozice ([RootOverlay]).
 * Pro bubliny, na které [WithTooltip] nestačí: vlastní obsah (`RichTooltip` s akcí), stav řízený
 * programem (nápověda gest), vypnutá gesta (nápověda u tlačítka, které podržením nahrává).
 * **Holý `TooltipBox` mimo tenhle soubor build nepustí** (`verifyTooltipsThroughRoot`).
 *
 * - **Kotva zůstává v M3 `TooltipBox`** (najetí myší, dlouhý stisk, fokus klávesnicí, přístupnost),
 *   ale se stavem, který jí hlásí „neviditelný" — popup tak sama nikdy nezaloží. Bublinu kreslí
 *   druhý `TooltipBox` z kořene nad týmž [state], takže `show()` / `dismiss()` zvenku i animace
 *   příjezdu a odjezdu fungují beze změny.
 * - **V kořeni stojí NÁHRADNÍ kotva přesně na mezích té skutečné v okně** (`atWindowBounds`).
 *   Nestačí podstrčit meze jen poskytovateli polohy: šipka `RichTooltip` / `PlainTooltip`
 *   (`caretShape`) si polohu i stranu dopočítává ze souřadnic kotvy a z TYPU poskytovatele
 *   (`Start`, `Right` … mají šipku z boku) — obalený poskytovatel by stranu ztratil a kotva v rohu
 *   okna by šipku poslala jinam. Bublina se tak polohuje úplně stejně jako u holého `TooltipBox`.
 *   Náhradní kotva nemá vstup ani obsah, dotyky na skutečnou kotvu propouští.
 * - **Ztrátu fokusu a Escape** M3 obsluhuje jen u bubliny, kterou kotva vidí jako viditelnou, tedy
 *   tady nikdy; dělá to proto obal sám (jen s [enableUserInput], jinak bublinu řídí volající).
 * - **Tabulátor z kotvy do bubliny s akcí** ([hasAction]) mezi dvěma `TooltipBox` přenést nejde;
 *   ve čtečce obrazovky se bublina s akcí fokusovat dá (to M3 dělá u bubliny sám).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RootTooltipBox(
    positionProvider: PopupPositionProvider,
    tooltip: @Composable TooltipScope.() -> Unit,
    state: TooltipState,
    modifier: Modifier = Modifier,
    onDismissRequest: (() -> Unit)? = null,
    focusable: Boolean = false,
    enableUserInput: Boolean = true,
    hasAction: Boolean = false,
    content: @Composable () -> Unit,
) {
    val anchorState = remember(state) { AnchorOnlyTooltipState(state) }
    val anchor = remember { AnchorBounds() }
    TooltipBox(
        positionProvider = positionProvider,
        tooltip = {},
        state = anchorState,
        modifier = modifier
            .onGloballyPositioned { anchor.bounds = it.boundsInWindow().roundToIntRect() }
            .then(if (enableUserInput) Modifier.dismissOnFocusLossOrEscape(state) else Modifier),
        enableUserInput = enableUserInput,
        hasAction = hasAction,
        content = content,
    )
    if (state.isVisible) {
        RootOverlay {
            Box(Modifier.atWindowBounds(anchor)) {
                TooltipBox(
                    positionProvider = positionProvider,
                    tooltip = tooltip,
                    state = state,
                    onDismissRequest = onDismissRequest,
                    focusable = focusable,
                    enableUserInput = false,
                    hasAction = hasAction,
                ) {
                    Spacer(Modifier.fillMaxSize())
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
private fun Modifier.dismissOnFocusLossOrEscape(state: TooltipState) = this
    .onFocusChanged { if (!it.hasFocus && state.isVisible) state.dismiss() }
    .onPreviewKeyEvent {
        val escape = it.type == KeyEventType.KeyDown && it.key == Key.Escape && state.isVisible
        if (escape) state.dismiss()
        escape
    }

/** Stav pro kotvu: gesta a fokus řídí skutečný [tooltip], ale kotva ho má za skrytý, takže nezaloží popup. */
@OptIn(ExperimentalMaterial3Api::class)
private class AnchorOnlyTooltipState(private val tooltip: TooltipState) : TooltipState by tooltip {
    override val isVisible: Boolean get() = false
}

/**
 * Meze skutečné kotvy v okně. Stav Compose, aby náhradní kotva v kořeni šla za tou skutečnou,
 * když se pod viditelnou bublinou pohne (otevřená klávesnice posune tlačítko odeslání).
 */
private class AnchorBounds {
    var bounds by mutableStateOf(IntRect.Zero)
}

/**
 * Položí obsah na [anchor] v souřadnicích OKNA, ať prvek v kořeni stojí kdekoli; sám nezabírá
 * nic. Meze se čtou až při rozložení, takže pohyb kotvy přeskládá jen tenhle prvek.
 */
private fun Modifier.atWindowBounds(anchor: AnchorBounds) = layout { measurable, _ ->
    val bounds = anchor.bounds
    val placeable = measurable.measure(Constraints.fixed(bounds.width, bounds.height))
    layout(0, 0) {
        val origin = coordinates?.positionInWindow() ?: return@layout
        placeable.place(bounds.left - origin.x.roundToInt(), bounds.top - origin.y.roundToInt())
    }
}
