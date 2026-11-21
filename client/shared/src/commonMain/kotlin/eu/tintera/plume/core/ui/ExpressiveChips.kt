package eu.tintera.plume.core.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ChipColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * `AssistChip`, který mění tvar při stisku jako ostatní čipy a tlačítka (M3 Expressive).
 *
 * material3 dává `shapes: ChipShapes` jen čipům s výběrem (`FilterChip`, `InputChip`); assist
 * a suggestion čip ho nemají, takže by vedle morfujících tlačítek zůstaly jediné ztuhlé. Tvar
 * tu proto nese [chipPressShape] se stejnými hodnotami jako `FilterChipDefaults.shapes()`
 * (12 dp, stisknutý 8 dp). Holé `AssistChip` / `SuggestionChip` hlídá `verifyChipShapes`.
 */
@Composable
fun ExpressiveAssistChip(
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    colors: ChipColors = AssistChipDefaults.assistChipColors(),
    border: BorderStroke? = AssistChipDefaults.assistChipBorder(enabled),
    interactionSource: MutableInteractionSource? = null,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    AssistChip(
        onClick = onClick,
        label = label,
        modifier = modifier,
        enabled = enabled,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        shape = chipPressShape(source),
        colors = colors,
        border = border,
        interactionSource = source,
    )
}

/** `SuggestionChip` s tvarem při stisku; proč obal, viz [ExpressiveAssistChip]. */
@Composable
fun ExpressiveSuggestionChip(
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: @Composable (() -> Unit)? = null,
    colors: ChipColors = SuggestionChipDefaults.suggestionChipColors(),
    border: BorderStroke? = SuggestionChipDefaults.suggestionChipBorder(enabled),
    interactionSource: MutableInteractionSource? = null,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    SuggestionChip(
        onClick = onClick,
        label = label,
        modifier = modifier,
        enabled = enabled,
        icon = icon,
        shape = chipPressShape(source),
        colors = colors,
        border = border,
        interactionSource = source,
    )
}

/**
 * Tvar čipu podle stisku: 12 dp, stisknutý 8 dp — jako `FilterChipDefaults.shapes()`, ať čip
 * bez výběru a s výběrem reagují stejně. Pružina je `fastSpatialSpec` jako v material3
 * (`SelectableChip`); překmit nevadí, roh nejede k nule.
 */
@Composable
fun chipPressShape(interactionSource: InteractionSource): Shape {
    val pressed by interactionSource.collectIsPressedAsState()
    val corner by animateDpAsState(
        targetValue = if (pressed) CHIP_PRESSED_CORNER else CHIP_CORNER,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "chip-corner",
    )
    return RoundedCornerShape(corner)
}

/** Zaoblení čipu v klidu (`ShapeTokens.CornerMedium`, výchozí `ChipShapes.shape`). */
private val CHIP_CORNER = 12.dp

/** Zaoblení stisknutého čipu (`ShapeTokens.CornerSmall`, výchozí `ChipShapes.pressedShape`). */
private val CHIP_PRESSED_CORNER = 8.dp
