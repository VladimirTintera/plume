package eu.tintera.plume.features.conversation.items

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Oslava výhry u hodu kostkou: sprška konfet ze středu kostky, nahoru a do stran, pak dolů.
 *
 * Kreslí se bez velikosti — do layoutu nezasahuje, jen přesahuje kostku, a jako efekt zprávy
 * ve vrstvě nad konverzací ([PlayEffect]). Barvy jsou z tématu (primary, secondary, tertiary a jejich kontejnery), ne pevná
 * duha: konfety mají ladit s aplikací, ne vypadat jako vložený obrázek. Náhoda je semínkem
 * [seed], takže tatáž zpráva vybuchne vždycky stejně.
 */
@Composable
internal fun DiceConfetti(seed: Long, onFinished: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val colors = listOf(
        scheme.primary,
        scheme.secondary,
        scheme.tertiary,
        scheme.primaryContainer,
        scheme.tertiaryContainer,
        scheme.inversePrimary,
    )
    val particles = remember(seed) { confettiParticles(seed) }
    val progress = remember(seed) { Animatable(0f) }
    val finished by rememberUpdatedState(onFinished)
    LaunchedEffect(seed) {
        progress.animateTo(1f, tween(CONFETTI_DURATION_MS, easing = LinearEasing))
        finished()
    }
    Canvas(
        modifier.layout { measurable, _ ->
            val size = CONFETTI_AREA.roundToPx()
            val placeable = measurable.measure(Constraints.fixed(size, size))
            layout(0, 0) { placeable.place(-size / 2, -size / 2) }
        },
    ) {
        val t = progress.value * CONFETTI_DURATION_MS / 1000f
        val unit = size.minDimension / 2
        val alpha = ((1f - progress.value) / FADE_PORTION).coerceIn(0f, 1f)
        particles.forEach { p ->
            val x = center.x + p.vx * unit * t
            val y = center.y + p.vy * unit * t + GRAVITY * unit * t * t / 2
            val piece = Size(p.width.dp.toPx(), p.height.dp.toPx())
            rotate(p.spin * t + p.angle, Offset(x, y)) {
                drawRoundRect(
                    color = colors[p.color % colors.size].copy(alpha = alpha),
                    topLeft = Offset(x - piece.width / 2, y - piece.height / 2),
                    size = piece,
                    cornerRadius = CornerRadius(piece.minDimension / 2 * p.roundness),
                )
            }
        }
    }
}

private class Particle(
    /** Rychlost v poloměrech plochy za vteřinu. */
    val vx: Float,
    val vy: Float,
    val width: Float,
    val height: Float,
    val angle: Float,
    val spin: Float,
    val color: Int,
    /** 0 = obdélníček, 1 = kolečko. */
    val roundness: Float,
)

private fun confettiParticles(seed: Long): List<Particle> {
    val random = Random(seed)
    return List(CONFETTI_COUNT) {
        // Kužel nahoru, s rozptylem do stran; rychlost se liší, ať to není prstenec.
        val direction = (-PI / 2 + (random.nextFloat() - 0.5f) * CONE).toFloat()
        val speed = 0.9f + random.nextFloat() * 1.3f
        Particle(
            vx = cos(direction) * speed,
            vy = sin(direction) * speed,
            width = 5f + random.nextFloat() * 4f,
            height = 8f + random.nextFloat() * 6f,
            angle = random.nextFloat() * 360f,
            spin = (random.nextFloat() - 0.5f) * 720f,
            color = random.nextInt(6),
            roundness = if (random.nextInt(4) == 0) 1f else 0.2f,
        )
    }
}

private val CONFETTI_AREA = 320.dp

private const val CONFETTI_COUNT = 70

private const val CONFETTI_DURATION_MS = 1_800

/** Zrychlení dolů v poloměrech plochy za vteřinu na druhou. */
private const val GRAVITY = 2.4f

/** Úhel kuželu, do kterého konfety vylétnou (v radiánech, kolem svislice). */
private const val CONE = PI * 0.9

/** Poslední část animace, během které konfety mizí. */
private const val FADE_PORTION = 0.35f
