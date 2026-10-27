package eu.tintera.plume.features.conversation

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.Test
import kotlin.test.assertEquals

class DatePickerStartTest {

    private val zone = TimeZone.of("Europe/Prague")
    private val today = LocalDate(2026, 10, 27)

    private fun at(date: LocalDate, hours: Int = 0) = (date.atStartOfDayIn(zone).epochSeconds + hours * 3600).toInt()

    @Test
    fun zacinaNaDniPredelu() {
        assertEquals(LocalDate(2026, 5, 23), datePickerStart(at(LocalDate(2026, 5, 23), hours = 21), zone, today))
    }

    @Test
    fun denJeMistni() {
        // 23:30 místního času je v UTC už jiný den; kalendář má začít na tom místním.
        assertEquals(LocalDate(2026, 5, 23), datePickerStart(at(LocalDate(2026, 5, 23)) + 23 * 3600 + 1800, zone, today))
    }

    @Test
    fun bezDneNeboVBudoucnostiDnes() {
        assertEquals(today, datePickerStart(null, zone, today))
        assertEquals(today, datePickerStart(at(LocalDate(2026, 11, 2)), zone, today))
    }
}
