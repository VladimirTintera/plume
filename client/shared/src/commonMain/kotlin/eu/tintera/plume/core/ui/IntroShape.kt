package eu.tintera.plume.core.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Uvítací tvar: naroste, protočí se a projde sledem tvarů M3 Expressive ([ShapeMorphs.Intro]),
 * až se zastaví na zaoblené hvězdě; ikona uvnitř naskočí za ním. Běží při každém zobrazení
 * a pak stojí.
 *
 * **Je to jeden z pevných významů tvarů v aplikaci** (viz [ShapeMorphs]): uvítání. Sdílí ho
 * prázdný stav ([IntroEmptyState] — placeholder detailu, prázdný archiv) a přihlašovací
 * obrazovka. Že se týmž tvarem vítá „tady zatím nic není" i „vítej v aplikaci", je záměr —
 * jsou to dvě podoby téhož okamžiku a dva různé vzhledy by jazyk rozmělnily.
 *
 * Tvar se čte až při kreslení a otáčí se ve vlastní vrstvě, takže animace nic nerekomponuje
 * a ikona zůstává rovně.
 */
@Composable
fun IntroShape(
    modifier: Modifier = Modifier,
    shapeSize: Dp = DEFAULT_SHAPE_SIZE,
    color: Color = MaterialTheme.colorScheme.primaryContainer,
    /** Obsah uvnitř tvaru. Naskočí za ním; o průhlednost a zvětšení se stará [IntroShape]. */
    content: @Composable () -> Unit,
) {
    val morphs = ShapeMorphs.Intro
    val progress = remember { Animatable(0f) }
    val rotation = remember { Animatable(START_ROTATION) }
    val scale = remember { Animatable(START_SCALE) }
    val iconReveal = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // Tvary se střídají po dráze se zpomalením na konci; otočení a zvětšení jsou pružiny,
        // takže hvězda na konci ještě lehce dokmitne, když už tvar stojí.
        launch { progress.animateTo(morphs.size.toFloat(), tween(MORPH_MILLIS, easing = EmphasizedDecelerate)) }
        launch { rotation.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = 25f)) }
        launch { scale.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 150f)) }
        launch {
            delay(ICON_DELAY_MILLIS)
            iconReveal.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 300f))
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(shapeSize)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            },
    ) {
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { rotationZ = rotation.value }
                .drawBehind {
                    val p = progress.value.coerceIn(0f, morphs.size.toFloat())
                    val index = p.toInt().coerceAtMost(morphs.lastIndex)
                    val outline = MorphShape(morphs[index], p - index).createOutline(size, layoutDirection, this)
                    drawOutline(outline, color)
                },
        )
        Box(
            Modifier.graphicsLayer {
                scaleX = iconReveal.value
                scaleY = iconReveal.value
                alpha = iconReveal.value.coerceIn(0f, 1f)
            },
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

/** Zpomalení na konci podle M3 „emphasized decelerate". */
internal val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

private val DEFAULT_SHAPE_SIZE = 176.dp

/** Velikost ikony uvnitř tvaru, když ji volající nemění. */
val INTRO_ICON_SIZE = 56.dp

private const val START_ROTATION = -300f
private const val START_SCALE = 0.4f
private const val MORPH_MILLIS = 1600
private const val ICON_DELAY_MILLIS = 450L
