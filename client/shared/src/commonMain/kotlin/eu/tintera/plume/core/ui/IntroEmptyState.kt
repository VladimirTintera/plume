package eu.tintera.plume.core.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * Prázdný stav s úvodní animací: tvar za ikonou naroste, protočí se a projde sledem tvarů
 * M3 Expressive ([ShapeMorphs.Intro]), až se zastaví na zaoblené hvězdě; ikona a text naskočí
 * za ním. Běží při každém zobrazení a pak se nic nehýbe — prázdný stav, který pořád pulzuje,
 * by táhl pozornost od toho, co se na obrazovce dá dělat.
 *
 * Sdílí ho placeholder detailu konverzace a prázdný archiv — jeden vzhled pro „tady nic není",
 * ať se dva prázdné stavy vedle sebe nepřou.
 *
 * Plocha je `surface` jako konverzace i seznam, takže výměna za obsah nemění pozadí.
 */
@Composable
fun IntroEmptyState(
    icon: DrawableResource,
    title: String,
    text: String,
    modifier: Modifier = Modifier,
    /** Plocha pod stavem. Výchozí `surface` sedí na konverzaci a seznam; hledání má vlastní. */
    containerColor: Color = MaterialTheme.colorScheme.surface,
) {
    val morphs = ShapeMorphs.Intro
    val progress = remember { Animatable(0f) }
    val rotation = remember { Animatable(START_ROTATION) }
    val scale = remember { Animatable(START_SCALE) }
    val iconReveal = remember { Animatable(0f) }
    val textReveal = remember { Animatable(0f) }

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
        launch {
            delay(TEXT_DELAY_MILLIS)
            textReveal.animateTo(1f, tween(TEXT_MILLIS, easing = EmphasizedDecelerate))
        }
    }

    val shapeColor = MaterialTheme.colorScheme.primaryContainer

    Surface(modifier = modifier.fillMaxSize(), color = containerColor) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(32.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(SHAPE_SIZE)
                        .graphicsLayer {
                            scaleX = scale.value
                            scaleY = scale.value
                        },
                ) {
                    // Tvar se čte až při kreslení a točí se ve vlastní vrstvě — animace nic
                    // nerekomponuje a ikona zůstává rovně.
                    Box(
                        Modifier
                            .matchParentSize()
                            .graphicsLayer { rotationZ = rotation.value }
                            .drawBehind {
                                val p = progress.value.coerceIn(0f, morphs.size.toFloat())
                                val index = p.toInt().coerceAtMost(morphs.lastIndex)
                                val outline = MorphShape(morphs[index], p - index)
                                    .createOutline(size, layoutDirection, this)
                                drawOutline(outline, shapeColor)
                            },
                    )
                    Icon(
                        painterResource(icon),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .size(ICON_SIZE)
                            .graphicsLayer {
                                scaleX = iconReveal.value
                                scaleY = iconReveal.value
                                alpha = iconReveal.value.coerceIn(0f, 1f)
                            },
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.graphicsLayer {
                        alpha = textReveal.value
                        translationY = (1f - textReveal.value) * TEXT_RISE.toPx()
                    },
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** Zpomalení na konci podle M3 „emphasized decelerate". */
private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

private val SHAPE_SIZE = 176.dp
private val ICON_SIZE = 56.dp
private val TEXT_RISE = 16.dp

private const val START_ROTATION = -300f
private const val START_SCALE = 0.4f
private const val MORPH_MILLIS = 1600
private const val ICON_DELAY_MILLIS = 450L
private const val TEXT_DELAY_MILLIS = 650L
private const val TEXT_MILLIS = 500
