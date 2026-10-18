package eu.tintera.plume.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import eu.tintera.plume.app.root.presentation.theme.expressiveColorScheme
import eu.tintera.plume.app.root.presentation.theme.seed
import eu.tintera.plume.core.settings.ThemeColor
import eu.tintera.plume.core.ui.ColorSwatch
import eu.tintera.plume.core.ui.ShapeMorphs
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.chat_color_default
import eu.tintera.plume.resources.format_color_reset_24dp
import eu.tintera.plume.resources.theme_color_blue
import eu.tintera.plume.resources.theme_color_green
import eu.tintera.plume.resources.theme_color_orange
import eu.tintera.plume.resources.theme_color_pink
import eu.tintera.plume.resources.theme_color_purple
import eu.tintera.plume.resources.theme_color_red
import eu.tintera.plume.resources.theme_color_teal
import eu.tintera.plume.resources.theme_color_yellow
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Řada vzorků barvy motivu, jako „Tapeta a styl" na Pixelu. Každý vzorek je kreslený z TÉHOŽ
 * schématu, jaké by aplikace dostala ([expressiveColorScheme]) — horní půlka primární, dole
 * sekundární a terciární kontejner — takže ukazuje celou paletu, ne jen výchozí odstín.
 *
 * [selected] je `null`, když platí barvy z tapety: vybraný vzorek by tvrdil, že se aplikace
 * kreslí z něj, a nekreslí. Ťuknutí barvy z tapety vypne (viz `chooseThemeColor`).
 *
 * **Vybraný vzorek se morfne do dvanáctistranné sušenky** ([ShapeMorphs.Highlight]), tvaru,
 * který v aplikaci znamená „tohle je to vybrané / podržené" (podbarvení podržené samolepky).
 * Fajfka uprostřed nese výběr i bez tvaru.
 */
@Composable
internal fun ThemeColorPicker(
    selected: ThemeColor?,
    isDark: Boolean,
    onSelect: (ThemeColor) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Vzorek „Podle aplikace" na začátku řady (barva konverzace); `null` = nenabízet. Když se
     * nabízí, je vybraný právě tehdy, když je [selected] `null`.
     */
    onSelectDefault: (() -> Unit)? = null,
    /** Schéma, ze kterého se vzorek kreslí — totéž, jaké výběr opravdu dá. */
    schemeOf: @Composable (ThemeColor) -> ColorScheme = { expressiveColorScheme(isDark, it.seed) },
) {
    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(SWATCH_GAP, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(SWATCH_GAP),
    ) {
        if (onSelectDefault != null) {
            DefaultColorSwatch(selected = selected == null, onClick = onSelectDefault)
        }
        ThemeColor.entries.forEach { color ->
            val scheme = schemeOf(color)
            ColorSwatch(
                selected = color == selected,
                label = stringResource(color.label),
                onClick = { onSelect(color) },
                checkBackdrop = scheme.surface,
                checkTint = scheme.primary,
            ) {
                val half = size.height / 2f
                drawRect(scheme.primary, size = Size(size.width, half))
                drawRect(scheme.secondaryContainer, topLeft = Offset(0f, half), size = Size(size.width / 2f, half))
                drawRect(scheme.tertiaryContainer, topLeft = Offset(size.width / 2f, half), size = Size(size.width / 2f, half))
            }
        }
    }
}

/**
 * „Podle aplikace": neutrální plocha s přeškrtnutou kapkou, jako „bez barvy" u štítků v Keepu.
 * Kreslit ho barvami aplikace by nešlo — v profilu chatu je kolem už paleta chatu a vzorek by
 * se od modré nelišil.
 */
@Composable
private fun DefaultColorSwatch(selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    ColorSwatch(
        selected = selected,
        label = stringResource(Res.string.chat_color_default),
        onClick = onClick,
        checkBackdrop = colors.surface,
        checkTint = colors.onSurface,
        idleIcon = painterResource(Res.drawable.format_color_reset_24dp),
        idleIconTint = colors.onSurfaceVariant,
    ) {
        drawRect(colors.surfaceContainerHighest)
    }
}

internal val ThemeColor.label: StringResource
    get() = when (this) {
        ThemeColor.BLUE -> Res.string.theme_color_blue
        ThemeColor.TEAL -> Res.string.theme_color_teal
        ThemeColor.GREEN -> Res.string.theme_color_green
        ThemeColor.YELLOW -> Res.string.theme_color_yellow
        ThemeColor.ORANGE -> Res.string.theme_color_orange
        ThemeColor.RED -> Res.string.theme_color_red
        ThemeColor.PINK -> Res.string.theme_color_pink
        ThemeColor.PURPLE -> Res.string.theme_color_purple
    }

private val SWATCH_GAP = 12.dp
