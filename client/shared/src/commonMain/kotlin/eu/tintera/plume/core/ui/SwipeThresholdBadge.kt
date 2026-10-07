package eu.tintera.plume.core.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Dp

/**
 * Ikona pod taženou položkou, která ukáže, že gesto překročilo práh (archivace chatu tahem,
 * odpověď tahem na zprávu). Do prahu je to kolečko v [idleFill]; za prahem naroste, morfne do
 * sušenky ([ShapeMorphs.Rest]) a vyplní se akcentem. Haptika `threshold` ohlásí totéž hmatem —
 * tvar je pro ty, kdo ji vypnutou mají, nebo ji v ruce nepoznají.
 *
 * Sušenka tu znamená „připraveno, pusť a stane se", tedy týž význam jako u mikrofonu v prázdném
 * poli. [armed] sleduje AKTUÁLNÍ polohu tahu, ne „někdy překročeno": kdo prst vrátí před práh,
 * vidí kolečko a ví, že po puštění se nic nestane.
 *
 * Akcent se měří proti [surface], na které ikona leží (`accentOn`): v paletě se zvýšeným
 * kontrastem může být `primary` na `primaryContainer` k nerozeznání.
 */
@Composable
fun SwipeThresholdBadge(
    armed: Boolean,
    painter: Painter,
    surface: Color,
    idleFill: Color,
    idleIcon: Color,
    size: Dp,
    iconSize: Dp,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val armedFill = accentOn(surface, colors.primary, idleIcon)
    val armedIcon = if (armedFill == colors.primary) colors.onPrimary else surface
    // Rychlejší pružina než u tlačítek (`morphSpring`): odezva patří k tahu prstu a musí přijít
    // spolu s haptikou, ne o půl vteřiny později. Přeskok nechává tvar „cvaknout".
    val progress by animateFloatAsState(
        targetValue = if (armed) 1f else 0f,
        animationSpec = spring(dampingRatio = BADGE_DAMPING, stiffness = Spring.StiffnessMediumLow),
        label = "swipe-threshold",
    )
    val fill = lerp(idleFill, armedFill, progress.coerceIn(0f, 1f))
    Box(
        modifier
            .size(size)
            .graphicsLayer {
                val scale = 1f + (ARMED_SCALE - 1f) * progress
                scaleX = scale
                scaleY = scale
            }
            .drawBehind {
                val shape = MorphShape(ShapeMorphs.Rest, progress, ShapeMorphs.REST_ROTATION)
                drawOutline(shape.createOutline(this.size, layoutDirection, this), fill)
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter,
            // Gesto není prvek, se kterým by čtečka obrazovky pracovala; akci nese nabídka.
            contentDescription = null,
            tint = lerp(idleIcon, armedIcon, progress.coerceIn(0f, 1f)),
            modifier = Modifier.size(iconSize),
        )
    }
}

private const val ARMED_SCALE = 1.15f
private const val BADGE_DAMPING = 0.5f
