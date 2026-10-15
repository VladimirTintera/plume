package eu.tintera.plume.core.telegram

import eu.tintera.plume.features.conversation.testMessage
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals

class MediaCalendarTest {

    private val zone = TimeZone.of("Europe/Prague")

    private fun at(year: Int, month: Int, day: Int, hour: Int, id: Long): ChatMessage =
        testMessage(id).copy(timestamp = LocalDateTime(year, month, day, hour, 0).toInstant(zone).epochSeconds.toInt())

    @Test
    fun dnySeRadiOdNejnovejsiho() {
        val page = listOf(
            MediaCalendarDay(2, at(2026, 10, 14, 12, id = 20)),
            MediaCalendarDay(1, at(2026, 9, 30, 8, id = 10)),
        )
        val days = mergeCalendarDays(emptyList(), page, zone)

        assertEquals(listOf(LocalDate(2026, 10, 14), LocalDate(2026, 9, 30)), days.map { it.date })
        assertEquals(listOf(2, 1), days.map { it.count })
    }

    @Test
    fun neuplnyDenZDalsiStrankySeSecte() {
        val first = mergeCalendarDays(emptyList(), listOf(MediaCalendarDay(3, at(2026, 10, 14, 18, id = 30))), zone)
        // Na další stránce přijde tentýž den se zbytkem a starší první zprávou.
        val merged = mergeCalendarDays(first, listOf(MediaCalendarDay(2, at(2026, 10, 14, 7, id = 25))), zone)

        assertEquals(1, merged.size)
        assertEquals(5, merged.single().count)
        assertEquals(25L, merged.single().message.id)
    }

    @Test
    fun denSePocitaMistne() {
        // 23:30 UTC 13. 10. je v Praze už 14. 10. — a s dnem 14. 10. se sloučí.
        val lateUtc = testMessage(40).copy(
            timestamp = LocalDateTime(2026, 10, 13, 23, 30).toInstant(TimeZone.UTC).epochSeconds.toInt(),
        )
        val days = mergeCalendarDays(
            emptyList(),
            listOf(MediaCalendarDay(1, at(2026, 10, 14, 12, id = 41)), MediaCalendarDay(1, lateUtc)),
            zone,
        )

        assertEquals(listOf(LocalDate(2026, 10, 14)), days.map { it.date })
        assertEquals(2, days.single().count)
    }

    @Test
    fun mesiceJenSMediiOdPondeli() {
        val days = mergeCalendarDays(
            emptyList(),
            listOf(MediaCalendarDay(1, at(2026, 10, 14, 12, id = 2)), MediaCalendarDay(1, at(2026, 6, 1, 12, id = 1))),
            zone,
        )
        val months = calendarMonths(days)

        // Červenec až září médium nemají, vynechají se.
        assertEquals(listOf(YearMonth(2026, 10), YearMonth(2026, 6)), months.map { it.month })
        // 1. 10. 2026 je čtvrtek: tři prázdná políčka (po, út, st) před ním.
        val october = months.first().cells
        assertEquals(listOf(null, null, null, LocalDate(2026, 10, 1)), october.take(4))
        assertEquals(0, october.size % 7)
        assertEquals(31, october.count { it != null })
        // 1. 6. 2026 je pondělí: bez prázdných políček na začátku.
        assertEquals(LocalDate(2026, 6, 1), months.last().cells.first())
    }
}
