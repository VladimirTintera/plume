package eu.tintera.plume.core.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.check_24dp
import org.jetbrains.compose.resources.painterResource

/**
 * **Vzorek barvy k výběru**: kruh, který se po výběru morfne do dvanáctistranné sušenky
 * ([ShapeMorphs.Highlight], „tohle je to vybrané") a dostane fajfku na ploše [checkBackdrop].
 * Výběr tak nese tvar i znak, ne jen obrys — žlutá a zelená se jinak na první pohled pletou.
 * Barva motivu v Nastavení, barva chatu v profilu, barva nového tématu ve fóru. [fill] kreslí
 * plochu vzorku, [idleIcon] je znak nevybraného vzorku („bez barvy").
 */
@Composable
fun ColorSwatch(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    checkBackdrop: Color,
    checkTint: Color,
    idleIcon: Painter? = null,
    idleIconTint: Color = Color.Unspecified,
    diameter: Dp = SWATCH_SIZE,
    fill: DrawScope.() -> Unit,
) {
    val progress = animateMorph(toShape = selected, label = "theme-color-swatch")
    Box(
        Modifier
            .size(diameter)
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

private val SWATCH_SIZE = 56.dp
private val SWATCH_GAP = 12.dp
private val CHECK_BACKDROP = 26.dp
private val CHECK_SIZE = 18.dp

/** Pootočení sušenky při výběru, ať morf není jen „naroste vlnky". */
private const val SELECTED_ROTATION = 30f
