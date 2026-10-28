package eu.tintera.plume.core.telegram

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals

class ChatCalendarTest {

    private val zone = TimeZone.of("Europe/Prague")

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Int =
        LocalDateTime(year, month, day, hour, minute).toInstant(zone).epochSeconds.toInt()

    @Test
    fun zpravySePocitajiPodleMistnihoDne() {
        val timestamps = listOf(
            at(2026, 10, 14, 23),
            at(2026, 10, 14, 1),
            // Půl hodiny po místní půlnoci — v UTC je to ještě 14., počítá se ale k 15.
            at(2026, 10, 15, 0, 30),
            at(2026, 9, 30, 22),
        )

        assertEquals(
            mapOf(LocalDate(2026, 10, 14) to 2, LocalDate(2026, 10, 15) to 1, LocalDate(2026, 9, 30) to 1),
            countMessagesByDay(timestamps, zone),
        )
    }

    @Test
    fun denNaHraniciUsekuSeSecte() {
        // Úsek z lokální databáze skončil uprostřed 14. 10., zbytek dne přinesl server.
        val local = mapOf(LocalDate(2026, 10, 15) to 4, LocalDate(2026, 10, 14) to 2)
        val older = mapOf(LocalDate(2026, 10, 14) to 3, LocalDate(2026, 10, 13) to 7)

        assertEquals(
            mapOf(LocalDate(2026, 10, 15) to 4, LocalDate(2026, 10, 14) to 5, LocalDate(2026, 10, 13) to 7),
            mergeDayCounts(local, older),
        )
    }

    @Test
    fun mesiceOdNejnovejsihoPresRok() {
        assertEquals(
            listOf(YearMonth(2026, 2), YearMonth(2026, 1), YearMonth(2025, 12)),
            monthsBetween(YearMonth(2026, 2), YearMonth(2025, 12)),
        )
    }

    @Test
    fun nejstarsiNovejsiNezNejnovejsiDaJedenMesic() {
        assertEquals(listOf(YearMonth(2026, 2)), monthsBetween(YearMonth(2026, 2), YearMonth(2026, 5)))
    }
}
