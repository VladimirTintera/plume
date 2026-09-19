package eu.tintera.plume.features.conversation.items

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Vlnitý obrys kolem zvýrazněné zprávy: kopíruje její tvar o kousek vedle a vlnky po něm
 * pomalu obíhají.
 *
 * Kreslí se KOLEM tvaru, ne místo něj. Zvlnit samotnou bublinu by znamenalo ořezávat podle
 * vlnek i obsah (text, fotku) a měnit tvar, se kterým počítá nabídka nad zprávou; obrys vedle
 * funguje stejně u bubliny, holé fotky i samolepky.
 *
 * [progress] (0 → 1) řídí průhlednost i výšku vlnek — obrys se „rozvlní" z hladké čáry. Obojí
 * i [phase] se čte až při kreslení, takže obíhání nic nerekomponuje.
 */
internal fun Modifier.emphasisRing(
    shape: Shape,
    color: Color,
    progress: () -> Float,
    phase: () -> Float,
): Modifier = drawBehind {
    val p = progress().coerceIn(0f, 1f)
    if (p <= 0f) return@drawBehind
    val gap = RING_GAP.toPx()
    val base = when (val outline = shape.createOutline(size, layoutDirection, this)) {
        is Outline.Rounded -> outline.roundRect.inflate(gap)
        is Outline.Rectangle -> RoundRect(outline.rect).inflate(gap)
        is Outline.Generic -> return@drawBehind
    }
    drawPath(
        path = wavyOutline(base, RING_AMPLITUDE.toPx() * p, RING_WAVELENGTH.toPx(), phase()),
        color = color.copy(alpha = color.alpha * p),
        style = Stroke(width = RING_WIDTH.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

/**
 * Fáze obíhání, 0 → 1 dokola, dokud [running] platí. Vlnek je po obvodu celý počet, takže
 * skok z 1 zpátky na 0 není vidět.
 */
@Composable
internal fun rememberRingPhase(running: Boolean): Animatable<Float, *> {
    val phase = remember { Animatable(0f) }
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        while (true) {
            phase.animateTo(1f, tween(RING_PERIOD_MS, easing = LinearEasing))
            phase.snapTo(0f)
        }
    }
    return phase
}

/**
 * Obrys [base] zvlněný o [amplitude] do stran, s vlnkami dlouhými zhruba [wavelength]
 * a posunutými o [phase] (v délkách vlny).
 *
 * Vlnek je celý počet — jinak by se konec obrysu nepotkal se začátkem a v jednom místě by byl
 * zub. Normála se orientuje ven od středu; tvar bubliny je konvexní, takže to stačí.
 */
internal fun wavyOutline(base: RoundRect, amplitude: Float, wavelength: Float, phase: Float): Path {
    val measure = PathMeasure()
    measure.setPath(Path().apply { addRoundRect(base) }, forceClosed = true)
    val length = measure.length
    val path = Path()
    if (length <= 0f || wavelength <= 0f) return path

    val waves = max(1, (length / wavelength).roundToInt())
    val steps = waves * SAMPLES_PER_WAVE
    val centerX = (base.left + base.right) / 2f
    val centerY = (base.top + base.bottom) / 2f
    for (i in 0..steps) {
        val distance = length * i / steps
        val position = measure.getPosition(distance)
        val tangent = measure.getTangent(distance)
        val norm = sqrt(tangent.x * tangent.x + tangent.y * tangent.y).takeIf { it > 0f } ?: 1f
        var nx = tangent.y / norm
        var ny = -tangent.x / norm
        if ((position.x - centerX) * nx + (position.y - centerY) * ny < 0f) {
            nx = -nx
            ny = -ny
        }
        val offset = amplitude * sin(2f * PI.toFloat() * (waves * i.toFloat() / steps + phase))
        val x = position.x + nx * offset
        val y = position.y + ny * offset
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

/** Obdélník se zaoblením rozšířený o [by] na všechny strany; nároží rostou s ním, aby obrys zůstal souběžný. */
private fun RoundRect.inflate(by: Float) = RoundRect(
    left = left - by,
    top = top - by,
    right = right + by,
    bottom = bottom + by,
    topLeftCornerRadius = topLeftCornerRadius.grow(by),
    topRightCornerRadius = topRightCornerRadius.grow(by),
    bottomRightCornerRadius = bottomRightCornerRadius.grow(by),
    bottomLeftCornerRadius = bottomLeftCornerRadius.grow(by),
)

private fun CornerRadius.grow(by: Float) = CornerRadius(x + by, y + by)

/** Mezera mezi zprávou a středem obrysu — vlnky se jí nesmí dotknout. */
private val RING_GAP = 4.dp
private val RING_AMPLITUDE = 1.5.dp
private val RING_WAVELENGTH = 20.dp
private val RING_WIDTH = 2.dp

/**
 * Jak daleko obrys sahá za zprávu. O tolik si zvýrazněná zpráva udělá místo nahoře i dole —
 * zprávy v bloku jsou od sebe 4 dp a obrys by jinak ležel přes fajfku doručení pod ní
 * i přes souseda nad ní.
 */
internal val RING_ROOM = RING_GAP + RING_AMPLITUDE + RING_WIDTH / 2
private const val SAMPLES_PER_WAVE = 12

/** Jedna délka vlny za tuhle dobu: obíhá to pomalu, aby to vedle čtené zprávy nerušilo. */
private const val RING_PERIOD_MS = 2400
