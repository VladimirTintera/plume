package eu.tintera.plume.features.statistics

import eu.tintera.plume.core.telegram.StatChart
import eu.tintera.plume.core.telegram.StatChartKind
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Čárky na ose y: od [min] (zaokrouhleného dolů na krok) po aspoň [max], krok „hezké" velikosti
 * (1, 2, 2,5, 5 × 10ⁿ). Kroků je jen tolik, kolik je potřeba — s pevným počtem by graf do 340
 * měl osu do 600 a čára by vyplnila sotva půlku výšky. Počty jsou celá čísla, takže krok pod
 * jedna se nepoužije.
 */
internal fun niceTicks(max: Double, count: Int = 3, min: Double = 0.0): List<Double> {
    val bottom = min.coerceAtLeast(0.0)
    val top = if (max > bottom) max else bottom + 1.0
    val raw = (top - bottom) / count
    val magnitude = 10.0.pow(floor(log10(raw)))
    val step = (NICE_STEPS.first { it * magnitude >= raw - raw * 1e-9 } * magnitude).coerceAtLeast(1.0)
    val low = floor(bottom / step) * step
    // Spodek zaokrouhlený dolů může přidat krok navíc; víc než o jeden to nebude.
    val steps = ceil((top - low) / step - 1e-9).toInt().coerceIn(1, count + 1)
    return List(steps + 1) { low + it * step }
}

/**
 * Odkud osa y začíná. Sloupce a plochy vždycky od nuly — jejich výška JE hodnota. Čára od nuly,
 * jen když k ní má blízko: počet odběratelů, který za měsíc vzroste z 1 490 na 1 577, by při
 * ose od nuly byl vodorovná čára v horní třetině grafu a růst by z něj nešel vyčíst.
 */
internal fun StatChart.axisMin(seriesIndices: List<Int>): Double {
    if (isStackedDrawing || isBars) return 0.0
    val min = seriesIndices.minOfOrNull { k -> series[k].values.minOrNull() ?: 0.0 } ?: 0.0
    return if (min > axisMax(seriesIndices) * LINE_ZERO_SHARE) min else 0.0
}

/** Čára, jejíž nejmenší hodnota je pod touhle částí největší, má osu od nuly. */
private const val LINE_ZERO_SHARE = 0.5

private val NICE_STEPS = doubleArrayOf(1.0, 2.0, 2.5, 5.0, 10.0)

/** Kreslí se graf jako sloupce: jeden sloupec na bod, ne čára přes body. */
internal val StatChart.isBars: Boolean get() = kind == StatChartKind.Bar

/** Skládají se řady na sebe — sloupce vždycky (Telegram jiné nepošle), plochy podle příznaku. */
internal val StatChart.isStackedDrawing: Boolean get() = isBars || (kind == StatChartKind.Area && (stacked || percentage))

/**
 * Nejvyšší hodnota, kterou má osa y pojmout: součet řad u skládaného grafu, jinak maximum
 * z [seriesIndices]. Podílový graf má osu v procentech, tedy vždycky sto.
 */
internal fun StatChart.axisMax(seriesIndices: List<Int>): Double = when {
    percentage -> 100.0
    isStackedDrawing -> x.indices.maxOfOrNull { i -> seriesIndices.sumOf { series[it].values[i] } } ?: 0.0
    else -> seriesIndices.maxOfOrNull { k -> series[k].values.maxOrNull() ?: 0.0 } ?: 0.0
}

/** Podíl řady [seriesIndex] v bodě [index] v procentech — u podílového grafu. */
internal fun StatChart.share(seriesIndex: Int, index: Int): Double {
    val total = series.sumOf { it.values[index] }
    return if (total == 0.0) 0.0 else series[seriesIndex].values[index] / total * 100.0
}

/**
 * Vodorovná poloha bodu [index] z [count] na šířce [width]: sloupce mají každý svůj díl a stojí
 * uprostřed něj, čára jde od kraje ke kraji.
 */
internal fun pointX(index: Int, count: Int, width: Float, bars: Boolean): Float = when {
    bars -> (index + 0.5f) * width / count
    count <= 1 -> width / 2f
    else -> index * width / (count - 1)
}

/** Který bod leží nejblíž vodorovné poloze [x] — pod prstem při ťuknutí nebo tahu. */
internal fun indexAt(x: Float, count: Int, width: Float, bars: Boolean): Int {
    if (count <= 1 || width <= 0f) return 0
    val index = if (bars) floor(x / (width / count)).toInt() else (x / (width / (count - 1))).roundToInt()
    return index.coerceIn(0, count - 1)
}
