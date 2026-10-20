package eu.tintera.plume.core.ui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.min

/** Kroužek příběhů: kolik jich autor má a kolik z nich je nezhlédnutých (ty nejnovější). */
@Immutable
data class StoryRingState(val total: Int, val unread: Int)

/**
 * Lem kolem avataru, který autor má příběhy. **Jde po tvaru avataru** (kruh, sušenka, čtverec),
 * ne po kruhu — tvar nese druh chatu a lem ho nesmí přebít. S víc příběhy se rozdělí na dílky,
 * od horního středu po směru hodinových ručiček, nejstarší první: zhlédnuté jsou tenké
 * v [readColor], nezhlédnuté (nejnovější, tedy na konci) v [unreadColor].
 *
 * Kreslí se VEN z rozměru avataru ([gap] + [width]), takže rozvržení řádku se nemění — okolí
 * musí mít aspoň tolik místa (řádek chatu má 12 dp nahoře i dole).
 */
fun Modifier.storyRing(
    ring: StoryRingState?,
    shape: Shape,
    unreadColor: Color,
    readColor: Color,
    gap: Dp = 2.5.dp,
    width: Dp = 2.5.dp,
    readWidth: Dp = 1.5.dp,
): Modifier = if (ring == null || ring.total <= 0) this else drawWithCache {
    val strokePx = width.toPx()
    val inset = gap.toPx() + strokePx / 2
    val outline = shape.createOutline(Size(size.width + 2 * inset, size.height + 2 * inset), layoutDirection, this)
    val path = Path().apply {
        addOutline(outline)
        translate(Offset(-inset, -inset))
    }
    val measure = PathMeasure().apply { setPath(path, false) }
    val length = measure.length
    val segments = if (length <= 0f) emptyList() else ringSegments(measure, length, ring, size.width / 2, -inset, strokePx)
    val unreadStroke = Stroke(width = strokePx, cap = StrokeCap.Round)
    val readStroke = Stroke(width = readWidth.toPx(), cap = StrokeCap.Round)
    onDrawBehind {
        segments.forEach { (segment, read) ->
            drawPath(segment, color = if (read) readColor else unreadColor, style = if (read) readStroke else unreadStroke)
        }
    }
}

/** Dílky obrysu jako cesty; druhá hodnota říká, jestli je dílek zhlédnutý. */
private fun ringSegments(
    measure: PathMeasure,
    length: Float,
    ring: StoryRingState,
    topX: Float,
    topY: Float,
    strokePx: Float,
): List<Pair<Path, Boolean>> {
    // Začátek obrysu se u každého tvaru liší (RoundRect začíná vlevo nahoře, polygon jinde),
    // proto se horní střed hledá vzorkováním.
    val samples = 128
    var start = 0f
    var best = Float.MAX_VALUE
    for (i in 0 until samples) {
        val distance = length * i / samples
        val point = measure.getPosition(distance)
        val score = abs(point.x - topX) + abs(point.y - topY)
        if (score < best) {
            best = score
            start = distance
        }
    }
    // Po směru hodinových ručiček = nahoře doprava. Obrys může jít i obráceně.
    val ahead = measure.getPosition((start + length / samples) % length)
    val direction = if (ahead.x >= measure.getPosition(start).x) 1f else -1f

    val count = ring.total
    val step = length / count
    // Kulaté konce dílek prodlouží o polovinu tloušťky na každé straně; mezera to musí unést.
    val gapLength = if (count == 1) 0f else min(strokePx + GAP_FRACTION * step, step * MAX_GAP_SHARE)
    val read = (count - ring.unread).coerceIn(0, count)
    return (0 until count).map { index ->
        val segment = Path()
        if (count == 1) {
            measure.getSegment(0f, length, segment, true)
            segment.close()
        } else {
            val from = start + direction * (index * step + gapLength / 2)
            val to = start + direction * ((index + 1) * step - gapLength / 2)
            addArc(measure, length, min(from, to), maxOf(from, to), segment)
        }
        segment to (index < read)
    }
}

/** Úsek obrysu mezi dvěma vzdálenostmi, i přes konec cesty (obrys je uzavřený). */
private fun addArc(measure: PathMeasure, length: Float, from: Float, to: Float, into: Path) {
    val a = ((from % length) + length) % length
    val b = a + (to - from)
    if (b <= length) {
        measure.getSegment(a, b, into, true)
    } else {
        measure.getSegment(a, length, into, true)
        measure.getSegment(0f, b - length, into, false)
    }
}

/** Mezera mezi dílky jako podíl jednoho dílku (navíc k tloušťce čáry). */
private const val GAP_FRACTION = 0.08f

/** Mezera nesmí sníst víc než tuhle část dílku — u desítek příběhů by z lemu zbyly tečky. */
private const val MAX_GAP_SHARE = 0.45f
