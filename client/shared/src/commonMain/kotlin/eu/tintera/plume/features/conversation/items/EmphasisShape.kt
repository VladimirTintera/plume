package eu.tintera.plume.features.conversation.items

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Tvar zvýrazněné zprávy: [base] (tvar bubliny) s okrajem zvlněným DOVNITŘ, vlnky posunuté
 * o [phase] délek vlny.
 *
 * Zvlňuje se samotná zpráva, ne obrys kolem ní. Vlnky jdou jen dovnitř, takže celý tvar leží
 * uvnitř bubliny — pozadí bubliny, fotka i samolepka (každá ořezaná svým zaobleným tvarem) se
 * do vlnek ořežou a nic nepřečnívá. [progress] (0 → 1) řídí výšku vlnek: zpráva se z hladkého
 * tvaru rozvlní a zase uhladí.
 *
 * Nový objekt na každý snímek je v pořádku — používá se jen v `graphicsLayer`, kde změna tvaru
 * přepočítá obrys vrstvy, ne kompozici.
 */
internal class WavyShape(
    private val base: Shape,
    private val progress: Float,
    private val phase: Float,
    private val depth: Dp = WAVE_DEPTH,
    private val wavelength: Dp = WAVE_LENGTH,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val outline = base.createOutline(size, layoutDirection, density)
        val p = progress.coerceIn(0f, 1f)
        if (p <= 0f) return outline
        val rect = when (outline) {
            is Outline.Rounded -> outline.roundRect
            is Outline.Rectangle -> RoundRect(outline.rect)
            is Outline.Generic -> return outline
        }
        return with(density) {
            Outline.Generic(wavyOutline(rect, depth.toPx() * p, wavelength.toPx(), phase))
        }
    }
}

/**
 * Fáze obíhání vlnek, 0 → 1 dokola, dokud [running] platí. Vlnek je po obvodu celý počet,
 * takže skok z 1 zpátky na 0 není vidět.
 */
@Composable
internal fun rememberWavePhase(running: Boolean): Animatable<Float, *> {
    val phase = remember { Animatable(0f) }
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        while (true) {
            phase.animateTo(1f, tween(WAVE_PERIOD_MS, easing = LinearEasing))
            phase.snapTo(0f)
        }
    }
    return phase
}

/**
 * Obrys [base] zvlněný dovnitř až o [depth], s vlnkami dlouhými zhruba [wavelength]
 * a posunutými o [phase] (v délkách vlny).
 *
 * Vlnek je celý počet — jinak by se konec obrysu nepotkal se začátkem a v jednom místě by byl
 * zub. Normála se orientuje ven od středu; tvar bubliny je konvexní, takže to stačí.
 */
internal fun wavyOutline(base: RoundRect, depth: Float, wavelength: Float, phase: Float): Path {
    val measure = PathMeasure()
    measure.setPath(Path().apply { addRoundRect(base) }, forceClosed = true)
    val length = measure.length
    val path = Path()
    if (length <= 0f || wavelength <= 0f) {
        path.addRoundRect(base)
        return path
    }

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
        // Sinus posunutý do záporu: vrcholy vlnek leží na původním okraji, žlábky o [depth] uvnitř.
        val wave = sin(2f * PI.toFloat() * (waves * i.toFloat() / steps + phase))
        val offset = -depth * (1f - wave) / 2f
        val x = position.x + nx * offset
        val y = position.y + ny * offset
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

/** Jak hluboko se okraj zvlní. Obsah bubliny má odsazení 8–14 dp, takže do textu vlnky nesáhnou. */
internal val WAVE_DEPTH = 3.dp
internal val WAVE_LENGTH = 20.dp
private const val SAMPLES_PER_WAVE = 12

/**
 * Klidové zvlnění hlasovky: podíl [WAVE_DEPTH], o který je okraj zvlněný i bez zvýraznění.
 *
 * Vlnka znamená v aplikaci „hlas" — zvlní se i karta vstupního pole při nahrávání. U hlasovky
 * stojí (neobíhá) a je mělčí než zvýraznění, aby se s ním nepletla: zvýrazněná hlasovka se
 * z klidové vlnky prohloubí a rozeběhne.
 */
internal const val VOICE_WAVE_PROGRESS = 0.7f

/** Jedna délka vlny za tuhle dobu: obíhá to pomalu, aby to vedle čtené zprávy nerušilo. */
private const val WAVE_PERIOD_MS = 2400
