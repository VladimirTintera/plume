package eu.tintera.plume.core.ui

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class StartOfLocalDayTest {

    private val prague = TimeZone.of("Europe/Prague")

    @Test
    fun zpravyTehozDneDajiStejnyZacatek() {
        // 6. 10. 2026 08:00 a 23:30 pražského času (CEST, UTC+2)
        val rano = 1_791_266_400
        val vecer = rano + 15 * 3600 + 30 * 60
        assertEquals(rano.startOfLocalDay(prague), vecer.startOfLocalDay(prague))
        assertEquals(1_791_237_600, rano.startOfLocalDay(prague)) // 6. 10. 00:00 CEST
    }

    @Test
    fun pulnocZacinaNovyDen() {
        val pulnoc = 1_791_237_600 + 24 * 3600 // 7. 10. 00:00 CEST
        assertNotEquals((pulnoc - 1).startOfLocalDay(prague), pulnoc.startOfLocalDay(prague))
        assertEquals(pulnoc, pulnoc.startOfLocalDay(prague))
    }
}
