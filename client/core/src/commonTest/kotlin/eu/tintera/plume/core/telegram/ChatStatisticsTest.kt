package eu.tintera.plume.core.telegram

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** JSON grafu od serveru (formát, který Telegram posílá všem klientům) na [StatChart]. */
class ChatStatisticsTest {

    @Test
    fun cariSeDvemaMeritkyRozumi() {
        // Tvar grafu interakcí příspěvků: zhlédnutí a sdílení, každé s vlastním měřítkem.
        val chart = assertNotNull(
            parseStatChart(
                """
                {"columns":[["x",1700000000000,1700086400000,1700172800000],["y0",120,340,95],["y1",3,8,1]],
                 "types":{"y0":"line","y1":"line","x":"x"},
                 "names":{"y0":"Views","y1":"Shares"},
                 "colors":{"y0":"BLUE#3497ED","y1":"GREEN#4BD964"},
                 "y_scaled":true,"xTickFormatter":"function(x){return x}"}
                """.trimIndent(),
            ),
        )
        assertEquals(listOf(1700000000000, 1700086400000, 1700172800000), chart.x)
        assertEquals(listOf("Views", "Shares"), chart.series.map { it.name })
        assertEquals(listOf(120.0, 340.0, 95.0), chart.series[0].values)
        assertEquals(StatChartKind.Line, chart.kind)
        assertTrue(chart.separateScales)
        assertFalse(chart.hourly)
        assertFalse(chart.isEmpty)
    }

    @Test
    fun skladaneSloupceAPodilPlochy() {
        val bars = assertNotNull(
            parseStatChart(
                """{"columns":[["x",1,2],["y0",1,2],["y1",3,4]],"types":{"y0":"bar","y1":"bar","x":"x"},"stacked":true}""",
            ),
        )
        assertEquals(StatChartKind.Bar, bars.kind)
        assertTrue(bars.stacked)
        assertFalse(bars.percentage)

        val area = assertNotNull(
            parseStatChart(
                """{"columns":[["x",1,2],["y0",5,5]],"types":{"y0":"area","x":"x"},"percentage":true,"stacked":true}""",
            ),
        )
        assertEquals(StatChartKind.Area, area.kind)
        assertTrue(area.percentage)
        // Jediná řada s vlastním měřítkem není důvod graf dělit.
        assertFalse(area.separateScales)
    }

    @Test
    fun bodyPoHodinachSePoznajiPodleKroku() {
        val chart = assertNotNull(
            parseStatChart("""{"columns":[["x",0,3600000,7200000],["y0",1,2,3]],"types":{"y0":"line","x":"x"}}"""),
        )
        assertTrue(chart.hourly)
    }

    @Test
    fun bezNazvuRadyZustaneJejiKlic() {
        val chart = assertNotNull(parseStatChart("""{"columns":[["x",1],["y0",1]],"types":{"y0":"step","x":"x"}}"""))
        assertEquals("y0", chart.series.single().name)
        assertEquals(StatChartKind.Step, chart.kind)
    }

    @Test
    fun nesrozumitelnyJsonNeniGraf() {
        assertNull(parseStatChart(""))
        assertNull(parseStatChart("{}"))
        assertNull(parseStatChart("""{"columns":[["y0",1,2]],"types":{"y0":"line"}}"""))
        // Řada jiné délky než osa x by se nakreslila posunutá.
        assertNull(parseStatChart("""{"columns":[["x",1,2],["y0",1]],"types":{"y0":"line","x":"x"}}"""))
        assertNull(parseStatChart("""{"columns":[["x",1,2]],"types":{"x":"x"}}"""))
    }

    @Test
    fun nulyJsouPrazdnyGraf() {
        val chart = assertNotNull(parseStatChart("""{"columns":[["x",1,2],["y0",0,0]],"types":{"y0":"line","x":"x"}}"""))
        assertTrue(chart.isEmpty)
    }

    @Test
    fun nejmensiRadySeSectouDoOstatnich() {
        val chart = StatChart(
            x = listOf(1, 2),
            series = listOf(
                StatSeries("cs", listOf(10.0, 10.0)),
                StatSeries("de", listOf(1.0, 0.0)),
                StatSeries("en", listOf(5.0, 5.0)),
                StatSeries("sk", listOf(0.0, 2.0)),
            ),
            kind = StatChartKind.Area,
            stacked = true,
            percentage = true,
            separateScales = false,
        )
        val folded = chart.folded(max = 3, otherName = "Other")
        // Ponechané řady drží pořadí od serveru, ať barva patří pořád téže řadě.
        assertEquals(listOf("cs", "en", "Other"), folded.series.map { it.name })
        assertEquals(listOf(1.0, 2.0), folded.series.last().values)
        assertEquals(chart, chart.folded(max = 4, otherName = "Other"))
    }
}
