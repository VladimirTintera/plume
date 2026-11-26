package eu.tintera.plume.core.ui

import androidx.compose.foundation.MutatePriority
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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

/**
 * Obalí [content] M3 **plain tooltipem** s textem [label]: na desktopu se ukáže při najetí myší,
 * na dotyku při dlouhém stisku (chování `TooltipBox`). Bez textu se kreslí jen [content].
 *
 * Jen zkratka pro nejčastější bublinu; vlastní obsah, stav řízený programem nebo vypnutá gesta
 * jdou rovnou přes M3 `TooltipBox`. Do Compose Multiplatform 1.12 se bubliny kreslily z kořene
 * kompozice (obal `RootTooltipBox`), protože holý `TooltipBox` na desktopu shodil vykreslování,
 * když kotva s viditelnou bublinou zmizela během měření; 1.13 to opravuje — viz „Bubliny
 * (tooltip)" v `docs/poznatky/desktop.md`.
 *
 * **Esc bublinu jen schová a jde dál** (na Zpět), viz [EscapeTransparentTooltipState].
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
    val base = rememberTooltipState()
    val state = remember(base) { EscapeTransparentTooltipState(base) }
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(position),
        tooltip = { PlainTooltip { Text(label) } },
        state = state,
        // Před obsluhou klávesnice bubliny: `modifier` je v řetězu kotvy první (viz [EscapeTransparentTooltipState]).
        modifier = modifier.onPreviewKeyEvent { event ->
            if (event.key == Key.Escape && event.type == KeyEventType.KeyDown && state.isVisible) state.hideForEscape()
            false
        },
        content = content,
    )
}

/**
 * Stav bubliny, přes který Esc PROJDE na Zpět.
 *
 * M3 `TooltipBox` má na kotvě obsluhu klávesnice (`keyboardBehavior` v `BasicTooltip.kt`,
 * `onPreviewKeyEvent`): je-li bublina vidět a kotva zaostřená, Esc bublinu zavře a klávesu
 * SPOTŘEBUJE. Na desktopu převádí Esc na Zpět až scéna, když klávesu nikdo nespotřeboval
 * (`ComposeSceneMediator.onKeyEvent` → `BackNavigationEventInput`), takže Zpět se nestalo.
 * Kotva bývá zaostřená po kliknutí myší a bublina je vidět, dokud nad ní kurzor stojí: po
 * kliknutí na přepínač bočního panelu se první Esc ztratil a panel zavřel až druhý — jako by
 * zpět „nefungovalo" (viz „Vlastní scéna Chatů" v `docs/poznatky/navigace.md`).
 *
 * Bublina s textem je jen popisek, ne obsah, se kterým by se dalo pracovat; Esc má tedy dělat
 * totéž co bez ní. Obsluha v [WithTooltip] proto před tou z M3 bublinu schová a tenhle stav pak
 * hlásí „neviditelná", dokud se bublina znovu neukáže ([show]) — M3 klávesu pustí dál
 * a dojde až na Zpět (nebo na pole, které Esc chce). `dismiss` sám nestačí: `isVisible` v M3
 * platí, dokud bublina neodjede (`transition.currentState || targetState`).
 *
 * **Závisí na tom, že M3 čte `state.isVisible`.** Ověřeno v bajtkódu material3-desktop
 * 1.13.0-rc01 (`BasicTooltipKt$keyboardBehavior$2`); pojistka je
 * `TooltipTest.escapeOverTooltipGoesThrough`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Stable
private class EscapeTransparentTooltipState(private val base: TooltipState) : TooltipState by base {
    private var hiddenByEscape by mutableStateOf(false)

    override val isVisible: Boolean get() = base.isVisible && !hiddenByEscape

    fun hideForEscape() {
        hiddenByEscape = true
        base.dismiss()
    }

    override suspend fun show(mutatePriority: MutatePriority) {
        hiddenByEscape = false
        base.show(mutatePriority)
    }
}
