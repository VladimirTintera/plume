package eu.tintera.plume.core.calendar

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class CalendarEventIcsTest {
    private val prague = TimeZone.of("Europe/Prague")
    private val stamp = Instant.fromEpochSeconds(0)

    @Test
    fun casovaUdalostVUtc() {
        val ics = CalendarEvent(
            title = "Sraz; kino, pak večeře",
            description = "řádek 1\nřádek 2",
            place = "Černý Most",
            start = LocalDateTime(2026, 10, 15, 15, 0),
            allDay = false,
        ).toIcs(prague, uid = "1@plume", stamp = stamp)
        val lines = ics.split("\r\n")
        // Letní čas: Praha je UTC+2.
        assertTrue("DTSTART:20261015T130000Z" in lines)
        assertTrue("DTEND:20261015T140000Z" in lines)
        assertTrue("DTSTAMP:19700101T000000Z" in lines)
        assertTrue("SUMMARY:Sraz\\; kino\\, pak večeře" in lines)
        assertTrue("LOCATION:Černý Most" in lines)
        assertTrue("DESCRIPTION:řádek 1\\nřádek 2" in lines)
        assertEquals("BEGIN:VCALENDAR", lines.first())
        assertTrue(ics.endsWith("END:VCALENDAR\r\n"))
    }

    @Test
    fun celodenniUdalostJeDatum() {
        val ics = CalendarEvent("Narozeniny", "", null, LocalDateTime(2026, 12, 31, 8, 0), allDay = true)
            .toIcs(prague, uid = "1@plume", stamp = stamp)
        assertTrue("DTSTART;VALUE=DATE:20261231\r\n" in ics)
        assertTrue("DTEND;VALUE=DATE:20270101\r\n" in ics)
        assertTrue("DESCRIPTION" !in ics)
    }

    @Test
    fun dlouheRadkySeZalamujiPoBajtech() {
        val ics = CalendarEvent("ž".repeat(100), "", null, LocalDateTime(2026, 10, 15, 15, 0), allDay = false)
            .toIcs(prague, uid = "1@plume", stamp = stamp)
        ics.split("\r\n").forEach { assertTrue(it.encodeToByteArray().size <= 75, it) }
        // Po spojení pokračovacích řádků zůstane název celý.
        assertTrue("SUMMARY:" + "ž".repeat(100) in ics.replace("\r\n ", ""))
    }
}
