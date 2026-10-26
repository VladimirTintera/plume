package eu.tintera.plume.features.statistics

import eu.tintera.plume.core.telegram.StatChart
import eu.tintera.plume.core.telegram.StatChartKind
import eu.tintera.plume.core.telegram.StatSeries
import kotlin.test.Test
import kotlin.test.assertEquals

/** Měřítko osy y, poloha bodů a bod pod prstem v grafu statistik. */
class ChartGeometryTest {

    @Test
    fun osaMaJenTolikKrokuKolikJeTreba() {
        // Pevné tři kroky by graf do 340 natáhly na osu do 600.
        assertEquals(listOf(0.0, 200.0, 400.0), niceTicks(340.0))
        assertEquals(listOf(0.0, 50.0, 100.0), niceTicks(100.0))
        assertEquals(listOf(0.0, 2500.0, 5000.0, 7500.0), niceTicks(7300.0))
    }

    @Test
    fun malaCislaMajiKrokAsponJedna() {
        assertEquals(listOf(0.0, 1.0, 2.0), niceTicks(2.0))
        // Samé nuly (řada s vlastním měřítkem) dostanou osu do jedné, ne dělení nulou.
        assertEquals(listOf(0.0, 1.0), niceTicks(0.0))
    }

    @Test
    fun caraDalekoOdNulyMaOsuOdSveNejmensiHodnoty() {
        // Odběratelé 1 490 → 1 577: od nuly by růst z grafu nešel vyčíst.
        val members = chart(StatChartKind.Line, listOf(1490.0, 1577.0), listOf(1500.0, 1560.0))
        assertEquals(1490.0, members.axisMin(listOf(0, 1)))
        assertEquals(listOf(1450.0, 1500.0, 1550.0, 1600.0), niceTicks(1577.0, min = 1490.0))
        // Čára blízko nuly a sloupce začínají od nuly.
        assertEquals(0.0, chart(StatChartKind.Line, listOf(3.0, 20.0), listOf(5.0, 9.0)).axisMin(listOf(0, 1)))
        assertEquals(0.0, chart(StatChartKind.Bar, listOf(1490.0, 1577.0), listOf(1500.0, 1560.0)).axisMin(listOf(0, 1)))
    }

    @Test
    fun skladanyGrafMeriSoucetRad() {
        val chart = chart(StatChartKind.Bar, listOf(1.0, 5.0), listOf(4.0, 1.0))
        assertEquals(6.0, chart.axisMax(listOf(0, 1)))
        val lines = chart(StatChartKind.Line, listOf(1.0, 5.0), listOf(4.0, 1.0))
        assertEquals(5.0, lines.axisMax(listOf(0, 1)))
        assertEquals(4.0, lines.axisMax(listOf(1)))
    }

    @Test
    fun podilVBodu() {
        val chart = chart(StatChartKind.Area, listOf(1.0, 0.0), listOf(3.0, 0.0), percentage = true)
        assertEquals(25.0, chart.share(0, 0))
        assertEquals(0.0, chart.share(0, 1))
        assertEquals(100.0, chart.axisMax(listOf(0, 1)))
    }

    @Test
    fun bodPodPrstem() {
        // Čára: čtyři body od kraje ke kraji po 100 px.
        assertEquals(0, indexAt(40f, count = 4, width = 300f, bars = false))
        assertEquals(1, indexAt(60f, count = 4, width = 300f, bars = false))
        assertEquals(3, indexAt(500f, count = 4, width = 300f, bars = false))
        // Sloupce: každý má svůj díl šířky.
        assertEquals(0, indexAt(74f, count = 4, width = 300f, bars = true))
        assertEquals(1, indexAt(76f, count = 4, width = 300f, bars = true))
        assertEquals(0, indexAt(-10f, count = 4, width = 300f, bars = true))
        assertEquals(37.5f, pointX(0, count = 4, width = 300f, bars = true))
        assertEquals(300f, pointX(3, count = 4, width = 300f, bars = false))
    }

    private fun chart(kind: StatChartKind, a: List<Double>, b: List<Double>, percentage: Boolean = false) = StatChart(
        x = listOf(1, 2),
        series = listOf(StatSeries("a", a), StatSeries("b", b)),
        kind = kind,
        stacked = kind != StatChartKind.Line,
        percentage = percentage,
        separateScales = false,
    )
}
