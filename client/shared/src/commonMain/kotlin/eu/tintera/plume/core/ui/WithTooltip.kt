package eu.tintera.plume.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Obalí [content] M3 **plain tooltipem** s textem [label]: na desktopu se ukáže při najetí myší,
 * na dotyku při dlouhém stisku (chování `TooltipBox`). Bez textu se kreslí jen [content].
 *
 * Jen zkratka pro nejčastější bublinu; vlastní obsah, stav řízený programem nebo vypnutá gesta
 * jdou rovnou přes M3 `TooltipBox`. Do Compose Multiplatform 1.12 se bubliny kreslily z kořene
 * kompozice (obal `RootTooltipBox`), protože holý `TooltipBox` na desktopu shodil vykreslování,
 * když kotva s viditelnou bublinou zmizela během měření; 1.13 to opravuje — viz „Bubliny
 * (tooltip)" v `docs/poznatky/desktop.md`.
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
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(position),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
        modifier = modifier,
        content = content,
    )
}
