package eu.tintera.plume.core.ui

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/** Plocha tlačítka: plné pro hlavní akci, tónové pro častou, obrysové pro vedlejší. */
enum class ButtonEmphasis { Filled, Tonal, Outlined }

/** Výška hlavních tlačítek (M3 Expressive „medium", 56 dp). */
val MediumButtonHeight: Dp get() = ButtonDefaults.MediumContainerHeight

/**
 * **Hlavní akce obrazovky přes celou šířku** ve velikosti M3 Expressive „medium": přihlásit,
 * připojit se ke kanálu, zapnout zámek, přidat sadu samolepek, odhlásit.
 *
 * Velikost není jen výška. Dřív se psalo `heightIn(min = 56.dp)` a z tlačítka byla 56dp pilulka
 * s písmem, odsazením a tvarem malého 40dp tlačítka. Tady jde výška, tvar ([ButtonDefaults.shapesFor]
 * — i stisk), vnitřní odsazení i písmo z jedné velikosti, takže se nemůžou rozejít.
 * Ikona patří přes [MediumButtonIcon], ať má velikost a mezeru téže velikosti.
 */
@Composable
fun MediumButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    emphasis: ButtonEmphasis = ButtonEmphasis.Filled,
    colors: ButtonColors? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val height = MediumButtonHeight
    val shapes = ButtonDefaults.shapesFor(height)
    val padding = ButtonDefaults.contentPaddingFor(height)
    val sized = modifier.heightIn(min = height)
    // Tlačítko si písmo nastaví samo (`labelLarge`); vlastní styl musí přijít až uvnitř obsahu.
    val styled: @Composable RowScope.() -> Unit = {
        ProvideTextStyle(ButtonDefaults.textStyleFor(height)) { content() }
    }
    when (emphasis) {
        ButtonEmphasis.Filled -> Button(
            onClick = onClick,
            modifier = sized,
            enabled = enabled,
            shapes = shapes,
            colors = colors ?: ButtonDefaults.buttonColors(),
            contentPadding = padding,
            content = styled,
        )
        ButtonEmphasis.Tonal -> FilledTonalButton(
            onClick = onClick,
            modifier = sized,
            enabled = enabled,
            shapes = shapes,
            colors = colors ?: ButtonDefaults.filledTonalButtonColors(),
            contentPadding = padding,
            content = styled,
        )
        ButtonEmphasis.Outlined -> OutlinedButton(
            onClick = onClick,
            modifier = sized,
            enabled = enabled,
            shapes = shapes,
            colors = colors ?: ButtonDefaults.outlinedButtonColors(),
            contentPadding = padding,
            content = styled,
        )
    }
}

/** Ikona v [MediumButton]: velikost a mezera k popisku podle velikosti tlačítka. */
@Composable
fun MediumButtonIcon(icon: DrawableResource) {
    Icon(
        painterResource(icon),
        contentDescription = null,
        modifier = Modifier
            .padding(end = ButtonDefaults.iconSpacingFor(MediumButtonHeight))
            .size(ButtonDefaults.iconSizeFor(MediumButtonHeight)),
    )
}
