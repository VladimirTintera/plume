package eu.tintera.plume.features.statistics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.StatChart
import eu.tintera.plume.core.telegram.StatChartKind
import eu.tintera.plume.core.telegram.StatGraph
import eu.tintera.plume.core.telegram.StatGraphEntry
import eu.tintera.plume.core.telegram.StatGraphKind
import eu.tintera.plume.core.ui.GROUP_CORNER
import eu.tintera.plume.core.ui.compactCountText
import eu.tintera.plume.core.ui.groupRow
import eu.tintera.plume.core.ui.grouped
import eu.tintera.plume.core.ui.longName
import eu.tintera.plume.core.ui.name
import eu.tintera.plume.core.ui.tabularNumbers
import eu.tintera.plume.resources.Res
import eu.tintera.plume.core.ui.standaloneName
import eu.tintera.plume.resources.calendar_month_year
import eu.tintera.plume.resources.date_day_month
import eu.tintera.plume.resources.date_day_month_year
import eu.tintera.plume.resources.decimal_number
import eu.tintera.plume.resources.statistics_graph_actions
import eu.tintera.plume.resources.statistics_graph_by_hour
import eu.tintera.plume.resources.statistics_graph_by_weekday
import eu.tintera.plume.resources.statistics_graph_instant_view
import eu.tintera.plume.resources.statistics_graph_interactions
import eu.tintera.plume.resources.statistics_graph_joins
import eu.tintera.plume.resources.statistics_graph_joins_by_source
import eu.tintera.plume.resources.statistics_graph_languages
import eu.tintera.plume.resources.statistics_graph_members
import eu.tintera.plume.resources.statistics_graph_message_content
import eu.tintera.plume.resources.statistics_graph_mutes
import eu.tintera.plume.resources.statistics_graph_reactions
import eu.tintera.plume.resources.statistics_graph_story_interactions
import eu.tintera.plume.resources.statistics_graph_story_reactions
import eu.tintera.plume.resources.statistics_graph_views_by_hour
import eu.tintera.plume.resources.statistics_graph_views_by_source
import eu.tintera.plume.resources.statistics_other
import eu.tintera.plume.resources.statistics_percent
import eu.tintera.plume.resources.statistics_period
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Karta grafu ze statistik: nadpis, datum (rozsah, nebo vybraný bod), legenda s hodnotami
 * vybraného bodu a graf. Ťuknutí nebo tah vodorovně po grafu vybere bod; druhé ťuknutí na týž
 * bod výběr zruší. Graf, který server teprve dopočítává, ukáže místo sebe čekání téže výšky.
 *
 * Volající sem posílá jen grafy, které je co kreslit — [StatGraph.Failed] a prázdné vynechá.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun StatGraphCard(entry: StatGraphEntry, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(GROUP_CORNER))
            .background(MaterialTheme.colorScheme.groupRow)
            .padding(16.dp),
    ) {
        Text(stringResource(entry.kind.title()), style = MaterialTheme.typography.titleMedium)
        when (val graph = entry.graph) {
            is StatGraph.Ready -> ChartContent(graph.chart, entry.kind)
            is StatGraph.Pending -> Box(Modifier.fillMaxWidth().height(CHART_HEIGHT + 40.dp), contentAlignment = Alignment.Center) {
                LoadingIndicator()
            }
            StatGraph.Failed -> Unit
        }
    }
}

/**
 * Karta grafu, který nespočítal server, ale aplikace (statistika chatu): stejný vzhled a výběr
 * bodu jako [StatGraphCard], jen s vlastním nadpisem. [monthly]: body jsou začátky měsíců
 * a popisují se měsícem a rokem, ne dnem.
 */
@Composable
internal fun StatChartCard(title: String, chart: StatChart, monthly: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(GROUP_CORNER))
            .background(MaterialTheme.colorScheme.groupRow)
            .padding(16.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        ChartContent(chart, kind = null, monthly = monthly)
    }
}

/** Graf stojí za kreslení: je hotový a není samá nula, nebo se teprve počítá. */
internal fun StatGraph.isWorthShowing(): Boolean = when (this) {
    is StatGraph.Ready -> !chart.isEmpty
    is StatGraph.Pending -> true
    StatGraph.Failed -> false
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChartContent(raw: StatChart, kind: StatGraphKind?, monthly: Boolean = false) {
    val otherName = stringResource(Res.string.statistics_other)
    val chart = remember(raw, otherName) { raw.folded(MAX_SERIES, otherName) }
    val colors = seriesColors(chart.series.size)
    var selected by remember(chart) { mutableStateOf<Int?>(null) }
    val labelStyle = when {
        monthly -> XLabel.Month
        kind == StatGraphKind.ActivityByWeek -> XLabel.Weekday
        chart.hourly -> XLabel.Hour
        else -> XLabel.Date
    }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val tabular = MaterialTheme.typography.bodySmall.tabularNumbers()

    // Vybraný bod, jinak celé období — ať je vidět, za kdy graf je.
    val pick = selected
    val first = xLabel(chart.x.first(), labelStyle)
    val last = xLabel(chart.x.last(), labelStyle)
    Text(
        text = if (pick != null) xLabel(chart.x[pick], labelStyle) else stringResource(Res.string.statistics_period, first, last),
        style = tabular,
        color = muted,
        modifier = Modifier.padding(top = 2.dp),
    )

    // Legenda je vždycky, když je řad víc — barva sama řadu nepojmenuje. S vybraným bodem nese
    // i hodnoty. Jediná řada má hodnotu velkým písmem pod datem, a to VŽDYCKY (bez výběru
    // poslední bod): řádek, který by naskočil až s výběrem, by graf pod ním posunul.
    if (chart.series.size >= 2) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            chart.series.forEachIndexed { k, series ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(colors[k]))
                    Spacer(Modifier.width(6.dp))
                    val value = pick?.let { valueText(chart, k, it) }
                    Text(
                        text = if (value != null) "${series.name} · $value" else series.name,
                        style = tabular,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    } else {
        Text(valueText(chart, 0, pick ?: chart.x.lastIndex), style = MaterialTheme.typography.titleLarge.tabularNumbers())
    }

    val onTap: (Int) -> Unit = { index -> selected = if (selected == index) null else index }
    val onDrag: (Int) -> Unit = { index -> selected = index }
    // Dvě měřítka v jednom grafu se číst nedají: každá řada má vlastní menší graf.
    val groups = if (chart.separateScales) chart.series.indices.map { listOf(it) } else listOf(chart.series.indices.toList())
    val axes = groups.map { axisFor(chart, it) }
    // Okraj s popisky osy y je SPOLEČNÝ všem dílům: s vlastním by „2,5 tis." a „20" posunuly
    // začátek grafů a týž den by nestál nad sebou.
    val measurer = rememberTextMeasurer()
    val axisStyle = MaterialTheme.typography.labelSmall.tabularNumbers().copy(color = muted)
    val density = LocalDensity.current
    val gutterPx = remember(axes, axisStyle) {
        axes.flatMap { it.labels }.maxOf { measurer.measure(it, axisStyle).size.width } + with(density) { 8.dp.toPx() }
    }
    groups.forEachIndexed { i, indices ->
        if (chart.separateScales) {
            Text(
                chart.series[indices.single()].name,
                style = MaterialTheme.typography.labelMedium,
                color = muted,
                modifier = Modifier.padding(top = 12.dp),
            )
        } else {
            Spacer(Modifier.height(12.dp))
        }
        ChartCanvas(
            chart, indices, axes[i], measurer, axisStyle, gutterPx, colors, selected, onTap, onDrag,
            if (chart.separateScales) SPLIT_CHART_HEIGHT else CHART_HEIGHT,
        )
    }

    // Data pod začátkem a koncem grafu, ne pod popisky osy y.
    Row(Modifier.fillMaxWidth().padding(start = with(density) { gutterPx.toDp() }, top = 4.dp)) {
        Text(first, style = MaterialTheme.typography.labelSmall.tabularNumbers(), color = muted)
        Spacer(Modifier.weight(1f))
        Text(last, style = MaterialTheme.typography.labelSmall.tabularNumbers(), color = muted)
    }
}

/** Čárky osy y a jejich popisky. */
private data class Axis(val ticks: List<Double>, val labels: List<String>)

@Composable
private fun axisFor(chart: StatChart, seriesIndices: List<Int>): Axis {
    val ticks = remember(chart, seriesIndices) { niceTicks(chart.axisMax(seriesIndices), min = chart.axisMin(seriesIndices)) }
    return Axis(ticks, ticks.map { tick -> if (chart.percentage) percentText(tick) else compactCountText(tick.roundToInt()) })
}

/** Jeden graf; [gutterPx] je okraj s popisky osy y vlevo — dotyk se přepočítává na body bez něj. */
@Composable
private fun ChartCanvas(
    chart: StatChart,
    seriesIndices: List<Int>,
    axis: Axis,
    measurer: TextMeasurer,
    labelStyle: TextStyle,
    gutterPx: Float,
    colors: List<Color>,
    selected: Int?,
    onTap: (Int) -> Unit,
    onDrag: (Int) -> Unit,
    height: Dp,
) {
    val scheme = MaterialTheme.colorScheme
    val ticks = axis.ticks
    val bars = chart.isBars
    val count = chart.x.size

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(height)
            .pointerInput(chart, gutterPx) {
                detectTapGestures { offset -> onTap(indexAt(offset.x - gutterPx, count, size.width - gutterPx, bars)) }
            }
            .pointerInput(chart, gutterPx) {
                // Jen vodorovný tah: svislý patří posunu obrazovky.
                detectHorizontalDragGestures(
                    onDragStart = { offset -> onDrag(indexAt(offset.x - gutterPx, count, size.width - gutterPx, bars)) },
                ) { change, _ ->
                    change.consume()
                    onDrag(indexAt(change.position.x - gutterPx, count, size.width - gutterPx, bars))
                }
            },
    ) {
        // Popisky osy y mají vlastní okraj vlevo — nad daty by je čára nebo sloupec překryly.
        val labels = axis.labels.map { measurer.measure(it, labelStyle) }
        val half = labels.first().size.height / 2f
        val plot = PlotArea(top = half, bottom = size.height - half, min = ticks.first(), max = ticks.last())

        ticks.forEachIndexed { i, tick ->
            val y = plot.y(tick)
            drawLine(scheme.outlineVariant, Offset(gutterPx, y), Offset(size.width, y), strokeWidth = 1f)
            drawText(labels[i], topLeft = Offset(0f, y - half))
        }

        translate(left = gutterPx) {
            val width = size.width - gutterPx
            when {
                bars -> drawBars(chart, seriesIndices, colors, plot, selected, scheme.groupRow, width)
                chart.isStackedDrawing -> drawAreas(chart, seriesIndices, colors, plot, scheme.groupRow, width)
                else -> drawLines(chart, seriesIndices, colors, plot, width)
            }
            if (selected != null) drawSelection(chart, seriesIndices, colors, plot, selected, width, scheme.onSurfaceVariant, scheme.groupRow)
        }
    }
}

private fun DrawScope.drawSelection(
    chart: StatChart,
    seriesIndices: List<Int>,
    colors: List<Color>,
    plot: PlotArea,
    selected: Int,
    width: Float,
    crosshair: Color,
    surface: Color,
) {
    val bars = chart.isBars
    val x = pointX(selected, chart.x.size, width, bars)
    // Sloupec se vybere tím, že ostatní ustoupí (drawBars); čára dostane svislici a body.
    if (bars) return
    drawLine(crosshair, Offset(x, plot.top), Offset(x, plot.bottom), strokeWidth = 1.dp.toPx())
    if (chart.isStackedDrawing) return
    seriesIndices.forEach { k ->
        val center = Offset(x, plot.y(chart.series[k].values[selected]))
        drawCircle(surface, radius = 6.dp.toPx(), center = center)
        drawCircle(colors[k], radius = 4.dp.toPx(), center = center)
    }
}

/** Svislé rozpětí kreslicí plochy a převod hodnoty na výšku. */
private class PlotArea(val top: Float, val bottom: Float, val min: Double, val max: Double) {
    fun y(value: Double): Float = bottom - (((value - min) / (max - min)).toFloat().coerceIn(0f, 1f)) * (bottom - top)
}

private fun DrawScope.drawLines(chart: StatChart, seriesIndices: List<Int>, colors: List<Color>, plot: PlotArea, width: Float) {
    val count = chart.x.size
    val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    seriesIndices.forEach { k ->
        val values = chart.series[k].values
        val path = Path()
        values.forEachIndexed { i, value ->
            val x = pointX(i, count, width, bars = false)
            val y = plot.y(value)
            when {
                i == 0 -> path.moveTo(x, y)
                // Schodový graf drží hodnotu až k dalšímu bodu, teprve tam skočí.
                chart.kind == StatChartKind.Step -> {
                    path.lineTo(x, plot.y(values[i - 1]))
                    path.lineTo(x, y)
                }
                else -> path.lineTo(x, y)
            }
        }
        drawPath(path, colors[k], style = stroke)
    }
}

/** Plochy na sobě; u podílového grafu přepočtené na sto procent v každém bodě. */
private fun DrawScope.drawAreas(chart: StatChart, seriesIndices: List<Int>, colors: List<Color>, plot: PlotArea, surface: Color, width: Float) {
    val count = chart.x.size
    val totals = chart.x.indices.map { i -> seriesIndices.sumOf { chart.series[it].values[i] } }
    fun scaled(value: Double, i: Int) = if (chart.percentage) {
        if (totals[i] == 0.0) 0.0 else value / totals[i] * 100.0
    } else {
        value
    }
    var lower = List(count) { 0.0 }
    val uppers = mutableListOf<List<Double>>()
    seriesIndices.forEach { k ->
        val upper = List(count) { i -> lower[i] + scaled(chart.series[k].values[i], i) }
        val path = Path()
        upper.forEachIndexed { i, v ->
            val x = pointX(i, count, width, bars = false)
            if (i == 0) path.moveTo(x, plot.y(v)) else path.lineTo(x, plot.y(v))
        }
        for (i in count - 1 downTo 0) path.lineTo(pointX(i, count, width, bars = false), plot.y(lower[i]))
        path.close()
        drawPath(path, colors[k])
        uppers += upper
        lower = upper
    }
    // Spára v barvě plochy mezi vrstvami, ať se sousední barvy neslévají.
    uppers.dropLast(1).forEach { upper ->
        val path = Path()
        upper.forEachIndexed { i, v ->
            val x = pointX(i, count, width, bars = false)
            if (i == 0) path.moveTo(x, plot.y(v)) else path.lineTo(x, plot.y(v))
        }
        drawPath(path, surface, style = Stroke(width = 1.5.dp.toPx(), join = StrokeJoin.Round))
    }
}

/**
 * Sloupce, řady na sobě. Mezi sloupci spára v barvě plochy, horní roh zaoblený; s vybraným
 * bodem ostatní sloupce ustoupí do pozadí.
 */
private fun DrawScope.drawBars(
    chart: StatChart,
    seriesIndices: List<Int>,
    colors: List<Color>,
    plot: PlotArea,
    selected: Int?,
    surface: Color,
    width: Float,
) {
    val count = chart.x.size
    val slot = width / count
    val gap = when {
        slot > 6.dp.toPx() -> 2.dp.toPx()
        slot > 3.dp.toPx() -> 1f
        else -> 0f
    }
    val radius = min(4.dp.toPx(), (slot - gap) / 2f)
    for (i in 0 until count) {
        val left = i * slot + gap / 2f
        val right = (i + 1) * slot - gap / 2f
        val alpha = if (selected == null || selected == i) 1f else 0.45f
        var base = 0.0
        val topSeries = seriesIndices.lastOrNull { chart.series[it].values[i] > 0.0 }
        seriesIndices.forEach { k ->
            val value = chart.series[k].values[i]
            if (value <= 0.0) return@forEach
            val top = plot.y(base + value)
            val bottom = plot.y(base)
            val corner = if (k == topSeries) CornerRadius(radius) else CornerRadius.Zero
            val path = Path().apply {
                addRoundRect(RoundRect(left, top, right, bottom, topLeftCornerRadius = corner, topRightCornerRadius = corner))
            }
            drawPath(path, colors[k].copy(alpha = alpha))
            // Spára mezi díly téhož sloupce.
            if (k != seriesIndices.first() && gap > 0f) {
                drawLine(surface, Offset(left, bottom), Offset(right, bottom), strokeWidth = 1.dp.toPx())
            }
            base += value
        }
    }
}

/**
 * Hodnota řady [k] v bodě [index]: celé číslo, u podílového grafu procento. **Ne zkrácené**
 * jako na ose („1,3 tis."): zkrácení zaokrouhluje dolů, takže bod ležící u 1 400 by se
 * odečetl jako 1,3 tisíce. Kdo ťuká na bod, chce číslo přesně.
 */
@Composable
private fun valueText(chart: StatChart, k: Int, index: Int): String =
    if (chart.percentage) percentText(chart.share(k, index)) else chart.series[k].values[index].roundToLong().grouped()

/** Procento: pod deset s desetinou („4,5 %"), výš celé. */
@Composable
internal fun percentText(value: Double): String {
    val tenths = (value * 10).roundToInt()
    val number = if (abs(tenths) < 100 && tenths % 10 != 0) {
        stringResource(Res.string.decimal_number, (tenths / 10).toString(), abs(tenths % 10).toString())
    } else {
        (tenths / 10).toString()
    }
    return stringResource(Res.string.statistics_percent, number)
}

private enum class XLabel { Date, Hour, Weekday, Month }

/**
 * Popis bodu na ose x. **V UTC**: Telegram počítá dny i hodiny statistik v UTC, takže půlnoc
 * bodu by se v pásmu na západ od Greenwiche posunula na předchozí den.
 */
@Composable
private fun xLabel(epochMillis: Long, style: XLabel): String {
    val moment = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(TimeZone.UTC)
    return when (style) {
        XLabel.Hour -> "${moment.hour.toString().padStart(2, '0')}:00"
        XLabel.Weekday -> stringResource(moment.date.dayOfWeek.longName())
        XLabel.Month -> stringResource(Res.string.calendar_month_year, stringResource(moment.date.month.standaloneName()), moment.year)
        XLabel.Date -> {
            val month = stringResource(moment.date.month.name())
            val thisYear = Clock.System.now().toLocalDateTime(TimeZone.UTC).year
            if (moment.year == thisYear) {
                stringResource(Res.string.date_day_month, moment.date.day, month)
            } else {
                stringResource(Res.string.date_day_month_year, moment.date.day, month, moment.year)
            }
        }
    }
}

private fun StatGraphKind.title(): StringResource = when (this) {
    StatGraphKind.MemberCount -> Res.string.statistics_graph_members
    StatGraphKind.Joins -> Res.string.statistics_graph_joins
    StatGraphKind.Mutes -> Res.string.statistics_graph_mutes
    StatGraphKind.ViewsByHour -> Res.string.statistics_graph_views_by_hour
    StatGraphKind.ViewsBySource -> Res.string.statistics_graph_views_by_source
    StatGraphKind.JoinsBySource -> Res.string.statistics_graph_joins_by_source
    StatGraphKind.Languages -> Res.string.statistics_graph_languages
    StatGraphKind.PostInteractions -> Res.string.statistics_graph_interactions
    StatGraphKind.PostReactions -> Res.string.statistics_graph_reactions
    StatGraphKind.StoryInteractions -> Res.string.statistics_graph_story_interactions
    StatGraphKind.StoryReactions -> Res.string.statistics_graph_story_reactions
    StatGraphKind.InstantView -> Res.string.statistics_graph_instant_view
    StatGraphKind.MessageContent -> Res.string.statistics_graph_message_content
    StatGraphKind.Actions -> Res.string.statistics_graph_actions
    StatGraphKind.ActivityByDay -> Res.string.statistics_graph_by_hour
    StatGraphKind.ActivityByWeek -> Res.string.statistics_graph_by_weekday
}

/**
 * Barvy řad. **Jediná řada je `primary`** — barva tam nic nerozlišuje a graf má ladit
 * s aplikací. **Víc řad bere pevnou paletu**, ne odstíny odvozené z motivu: ty (po kruhu
 * od `primary` jako [eu.tintera.plume.core.ui.senderColor]) při změření propadly — sousední
 * barvy pro barvoslepé splývaly (ΔE kolem 3 při cíli 8) a při sytosti 48 vypadaly šedivě.
 * Paleta je ověřená na barvoslepost ve světlém i tmavém režimu (viz
 * `docs/poznatky/profil-chatu.md`, „Statistiky"); pořadí je pevné, ať barva patří pořád
 * téže řadě.
 */
@Composable
private fun seriesColors(count: Int): List<Color> {
    val scheme = MaterialTheme.colorScheme
    if (count <= 1) return listOf(scheme.primary)
    val palette = if (scheme.surface.luminance() < 0.5f) SERIES_DARK else SERIES_LIGHT
    return List(count) { Color(palette[it % palette.size]) }
}

private val SERIES_LIGHT = longArrayOf(
    0xFF2A78D6, 0xFFEB6834, 0xFF1BAF7A, 0xFFEDA100, 0xFFE87BA4, 0xFF008300, 0xFF6250D6, 0xFFE34948,
)
private val SERIES_DARK = longArrayOf(
    0xFF3987E5, 0xFFD95926, 0xFF199E70, 0xFFC98500, 0xFFD55181, 0xFF008300, 0xFF9085E9, 0xFFE66767,
)

/** Víc řad oko barvou nerozezná; zbytek se sečte do „Ostatní" ([StatChart.folded]). */
private const val MAX_SERIES = 8

private val CHART_HEIGHT = 180.dp
private val SPLIT_CHART_HEIGHT = 120.dp
