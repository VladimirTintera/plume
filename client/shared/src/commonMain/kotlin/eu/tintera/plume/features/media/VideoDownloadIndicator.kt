package eu.tintera.plume.features.media

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.FileState
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.media_download_progress
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource

/**
 * Stav stahování videa, které už hraje.
 *
 * - **Sbalený je to kroužek v pravém horním rohu**, nerušivý — video už běží a velký průběh
 *   uprostřed by ho zakrýval.
 * - **Ťuknutí ho rozvine do čáry přes spodní hranu karty** a další ho zase svine. Na čáře je vidět,
 *   KDE v souboru stažený úsek leží, a to v měřítku, ve kterém se dá porovnat s posuvníkem.
 * - **Kroužek se opravdu rozvine, neprolne se**: je to tentýž oblouk, jehož úhel klesá k nule
 *   a délka roste na šířku karty, a přitom se dotočí do vodorovné polohy. Kreslí se ručně na
 *   `Canvas` — žádná komponenta M3 přechod z kruhového průběhu do lineárního nemá.
 * - **Kroužek i čára ukazují tentýž úsek** ([downloadedFractions]), ne jednou úsek a jednou
 *   procento: jinak by se při rozvinutí obsah změnil pod rukama.
 * - **Horní roh ustupuje stavové liště**, jen když pod ni karta opravdu sahá. Insety samy nevědí,
 *   kde prvek leží (viz CLAUDE.md, „Navigace"), takže se porovnávají s polohou karty v okně.
 */
@Composable
internal fun VideoDownloadIndicator(
    range: ClosedFloatingPointRange<Float>,
    visible: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val presence by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "download-indicator-presence",
    )
    val unroll by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.slowSpatialSpec(),
        label = "download-indicator-unroll",
    )
    if (!visible && presence == 0f) return

    // Vlnky obíhají jen na rozvinuté čáře; svinutý kroužek stojí a nic nepřekresluje.
    var wavePhase by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(expanded, visible) {
        if (!expanded || !visible) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                wavePhase = (wavePhase + (now - last) / WAVE_PERIOD_NANOS) % 1f
                last = now
            }
        }
    }

    val density = LocalDensity.current
    val safe = WindowInsets.safeDrawing
    var topInset by remember { mutableFloatStateOf(0f) }
    val scrim = MaterialTheme.colorScheme.scrim
    val description = stringResource(Res.string.media_download_progress)

    Box(
        modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                topInset = (safe.getTop(density) - coordinates.boundsInWindow().top).coerceAtLeast(0f)
            },
    ) {
        Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = presence }) {
            drawUnrolled(
                // Pružina přestřelí; geometrie mimo 0..1 by oblouk přetočila.
                t = unroll.coerceIn(0f, 1f),
                range = range,
                corner = Offset(size.width - CORNER_CENTER.toPx(), topInset + CORNER_CENTER.toPx()),
                lineY = size.height - STRIP_HEIGHT.toPx() / 2,
                scrim = scrim,
                wavePhase = wavePhase,
            )
        }

        if (visible) {
            val target = Modifier
                .semantics {
                    contentDescription = description
                    role = Role.Button
                }
                .pointerInput(Unit) { detectTapGestures { onToggle() } }
            if (expanded) {
                Box(target.align(Alignment.BottomCenter).fillMaxWidth().height(STRIP_HEIGHT))
            } else {
                Box(
                    target
                        .align(Alignment.TopEnd)
                        .offset { IntOffset(0, topInset.roundToInt()) }
                        .padding(CORNER_CENTER - TOUCH_SIZE / 2)
                        .size(TOUCH_SIZE),
                )
            }
        }
    }
}

/**
 * Oblouk rozvinutý z [t] = 0 (kruh kolem [corner]) do [t] = 1 (vodorovná čára ve výšce [lineY]).
 *
 * Oblouk se drží za střed své tětivy: úhel klesá k nule, délka roste z obvodu na šířku čáry
 * a poloměr z toho vychází (`R = L / θ`). Neotočený oblouk je „∩" procházený po směru hodin, takže
 * na konci jde zleva doprava; na začátku je otočený o 180°, takže průběh začíná nahoře jako
 * u běžného kruhového indikátoru.
 *
 * **Stažený úsek se s rozvinutím rozvlní** jako M3 Expressive `LinearWavyProgressIndicator`
 * (vlnitý průběh, rovná dráha). Hotová komponenta to být nemůže: umí jen průběh od nuly, kdežto
 * stažený úsek po skoku pro `moov` leží uprostřed souboru. Kroužek v rohu zůstává rovný — nad
 * hrajícím videem by vlnění pořád tahalo oko; vlní se až čára, na kterou se uživatel dívá schválně.
 */
private fun DrawScope.drawUnrolled(
    t: Float,
    range: ClosedFloatingPointRange<Float>,
    corner: Offset,
    lineY: Float,
    scrim: Color,
    wavePhase: Float,
) {
    val radius = RING_RADIUS.toPx()
    val inset = STRIP_INSET.toPx()
    val length = mix(2f * PI.toFloat() * radius, size.width - 2 * inset, t)
    // Úhel klesá rychleji než délka roste: ve druhé půlce už je to skoro čára, jen se natahuje.
    val sweep = 360f * (1f - t) * (1f - t)
    /*
     * Kotva je střed TĚTIVY, ne vrchol oblouku: „∩" pak leží celý nad cílovou linkou a konce
     * na ni dosednou. S kotvou ve vrcholu visely konce při rozvíjení pod linkou, a to až pod
     * spodní hranu karty.
     */
    val anchor = mix(corner - Offset(0f, radius), Offset(size.width / 2, lineY), t)
    // Otáčení dozní dřív, než se oblouk narovná. S lineárním otáčením byl oblouk v půlce skoro
    // rovný a stál svisle — čára se otočila jako vrtule a skočila dolů přes okraj karty.
    val rotation = 180f * (1f - t) * (1f - t) * (1f - t)
    val stroke = mix(RING_STROKE.toPx(), LINE_STROKE.toPx(), t)
    // Vlnka naroste až ve druhé půlce rozvinutí, kdy už je oblouk skoro rovný.
    val amplitude = WAVE_AMPLITUDE.toPx() * ((t - 0.5f) * 2f).coerceIn(0f, 1f)
    val wavelength = WAVE_LENGTH.toPx()
    val flat = sweep < FLAT_BELOW_DEGREES
    val arcRadius = if (flat) 0f else length / (sweep * PI.toFloat() / 180f)
    val sag = if (flat) 0f else arcRadius * (1f - cos(sweep / 2 * PI.toFloat() / 180f))
    val mid = anchor - Offset(0f, sag)

    /** Bod ve vzdálenosti [f] (0..1) podél oblouku, odsazený o [lift] ven z oblouku (nahoru). */
    fun pointAt(f: Float, lift: Float): Offset = if (flat) {
        Offset(mid.x - length / 2 + f * length, mid.y - lift)
    } else {
        val angle = (270f - sweep / 2 + f * sweep) * PI.toFloat() / 180f
        val r = arcRadius + lift
        mid + Offset(0f, arcRadius) + Offset(r * cos(angle), r * sin(angle))
    }

    // Podklad pod kroužkem; u čáry ho nahrazuje tmavý lem, ať je vidět i na světlém záběru.
    drawCircle(scrim.copy(alpha = 0.45f * (1f - t)), radius = BACKDROP_RADIUS.toPx(), center = corner)

    rotate(rotation, pivot = anchor) {
        fun segment(from: Float, to: Float, color: Color, width: Float, wavy: Boolean = false) {
            if (to <= from) return
            if (wavy && amplitude > 0f) {
                // Fáze se vztahuje k levému okraji čáry, ne k úseku: vlnky stojí na místě
                // i když úsek roste, a obíhají jen s [wavePhase].
                val path = Path()
                val steps = ((to - from) * length / WAVE_STEP.toPx()).toInt().coerceAtLeast(2)
                for (i in 0..steps) {
                    val f = from + (to - from) * i / steps
                    val lift = amplitude * sin(2f * PI.toFloat() * (f * length / wavelength - wavePhase))
                    val point = pointAt(f, lift)
                    if (i == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
                }
                drawPath(path, color, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
            } else if (flat) {
                drawLine(color, pointAt(from, 0f), pointAt(to, 0f), strokeWidth = width, cap = StrokeCap.Round)
            } else {
                val center = mid + Offset(0f, arcRadius)
                drawArc(
                    color = color,
                    startAngle = 270f - sweep / 2 + from * sweep,
                    sweepAngle = (to - from) * sweep,
                    useCenter = false,
                    topLeft = center - Offset(arcRadius, arcRadius),
                    size = Size(2 * arcRadius, 2 * arcRadius),
                    style = Stroke(width, cap = StrokeCap.Round),
                )
            }
        }
        // Lem musí pokrýt i výkyv vlnky, jinak by na světlém záběru vlnky přečnívaly.
        segment(0f, 1f, scrim.copy(alpha = 0.35f * t), stroke + 3.dp.toPx() + 2 * amplitude)
        segment(0f, 1f, Color.White.copy(alpha = 0.3f), stroke)
        segment(range.start, range.endInclusive, Color.White, stroke, wavy = true)
    }
}

/**
 * Stažený úsek jako podíl délky souboru (0..1).
 *
 * TDLib hlásí jen SOUVISLÝ úsek od `downloadOffset` ([FileState.Downloading.downloaded]); co se
 * stáhlo před přesunem stahování jinam, v něm není. Ukazuje se tedy to, z čeho přehrávač právě čte.
 */
internal fun downloadedFractions(state: FileState.Downloading): ClosedFloatingPointRange<Float> {
    val total = state.totalSize
    if (total <= 0 || state.downloadedPrefixSize <= 0) return 0f..0f
    val start = (state.downloadOffset.toDouble() / total).toFloat().coerceIn(0f, 1f)
    val end = ((state.downloadOffset + state.downloadedPrefixSize).toDouble() / total).toFloat().coerceIn(start, 1f)
    return start..end
}

private fun mix(from: Float, to: Float, t: Float) = from + (to - from) * t

private fun mix(from: Offset, to: Offset, t: Float) = Offset(mix(from.x, to.x, t), mix(from.y, to.y, t))

/** Výška pásu u spodní hrany, ve kterém leží čára a který přijímá ťuknutí. */
internal val STRIP_HEIGHT = 32.dp
private val STRIP_INSET = 16.dp
private val CORNER_CENTER = 32.dp
private val TOUCH_SIZE = 48.dp
private val RING_RADIUS = 9.dp
private val BACKDROP_RADIUS = 17.dp
private val RING_STROKE = 3.dp
private val LINE_STROKE = 4.dp
private const val FLAT_BELOW_DEGREES = 0.5f
private val WAVE_AMPLITUDE = 3.dp
private val WAVE_LENGTH = 32.dp
private val WAVE_STEP = 2.dp
private const val WAVE_PERIOD_NANOS = 1_200_000_000f
