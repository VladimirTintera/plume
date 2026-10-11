package eu.tintera.plume.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.material3.MaterialTheme
import eu.tintera.plume.app.root.presentation.theme.expressiveColorScheme
import eu.tintera.plume.app.root.presentation.theme.seed
import eu.tintera.plume.core.settings.ThemeColor
import eu.tintera.plume.core.ui.MorphShape
import eu.tintera.plume.core.ui.ShapeMorphs
import eu.tintera.plume.core.ui.animateMorph
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.chat_color_default
import eu.tintera.plume.resources.check_24dp
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

@Composable
private fun ColorSwatch(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    checkBackdrop: Color,
    checkTint: Color,
    idleIcon: Painter? = null,
    idleIconTint: Color = Color.Unspecified,
    fill: DrawScope.() -> Unit,
) {
    val progress = animateMorph(toShape = selected, label = "theme-color-swatch")
    Box(
        Modifier
            .size(SWATCH_SIZE)
            // Ořez (a s ním tvar vlnky) čte tvar při kreslení; dotyk zůstává v obdélníku
            // vzorku — vlnitý obrys do ořezu vrstvy nepatří (viz docs/poznatky/konverzace-zobrazeni.md, „Vzhled konverzace").
            .clip(CircleShape)
            .semantics { contentDescription = label }
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                interactionSource = null,
                indication = ripple(),
                onClick = onClick,
            )
            .drawWithContent {
                val outline = MorphShape(ShapeMorphs.Highlight, progress, SELECTED_ROTATION)
                    .createOutline(size, layoutDirection, this)
                clipPath(Path().apply { addOutline(outline) }) { fill() }
                drawContent()
            },
        contentAlignment = Alignment.Center,
    ) {
        if (idleIcon != null) {
            AnimatedVisibility(
                visible = !selected,
                enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                exit = fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()),
            ) {
                // Dekorace: co vzorek znamená, říká popis pro čtečku.
                Icon(idleIcon, contentDescription = null, tint = idleIconTint)
            }
        }
        AnimatedVisibility(
            visible = selected,
            enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                scaleIn(MaterialTheme.motionScheme.defaultSpatialSpec()),
            exit = fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                scaleOut(MaterialTheme.motionScheme.defaultSpatialSpec()),
        ) {
            // Fajfka na ploše VZORKU, ne aplikace: primární barva je proti své ploše čitelná
            // v každé paletě, kdežto proti ploše jiné palety by se slít mohla.
            Box(
                Modifier.size(CHECK_BACKDROP).background(checkBackdrop, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(Res.drawable.check_24dp),
                    // Výběr čte čtečka z `selectable`, fajfka je dekorace.
                    contentDescription = null,
                    tint = checkTint,
                    modifier = Modifier.size(CHECK_SIZE),
                )
            }
        }
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

private val SWATCH_SIZE = 56.dp
private val SWATCH_GAP = 12.dp
private val CHECK_BACKDROP = 26.dp
private val CHECK_SIZE = 18.dp

/** Pootočení sušenky při výběru, ať morf není jen „naroste vlnky". */
private const val SELECTED_ROTATION = 30f
