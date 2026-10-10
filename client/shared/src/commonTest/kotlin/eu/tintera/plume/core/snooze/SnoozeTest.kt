package eu.tintera.plume.core.snooze

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

/** Doby odložení a rozdělení odložených chatů na spící a vynořené. */
class SnoozeTest {

    private val zone = TimeZone.of("Europe/Prague")

    @Test
    fun odpoledneJeVecerAZitraRano() {
        assertEquals(
            listOf(SnoozeOption.InHour, SnoozeOption.Tonight, SnoozeOption.TomorrowMorning),
            snoozeOptions(LocalDateTime(2026, 10, 10, 14, 30)),
        )
    }

    /** Od šesti večer by „Dnes večer" bylo za chvilku — na to je „Za hodinu". */
    @Test
    fun vecerUzDnesVecerNeni() {
        assertEquals(
            listOf(SnoozeOption.InHour, SnoozeOption.TomorrowMorning),
            snoozeOptions(LocalDateTime(2026, 10, 10, 18, 0)),
        )
    }

    /** Ve dvě v noci je ráno dnešní, ne zítřejší. */
    @Test
    fun poPulnociJeRanoDnesni() {
        assertEquals(
            listOf(SnoozeOption.InHour, SnoozeOption.Tonight, SnoozeOption.Morning),
            snoozeOptions(LocalDateTime(2026, 10, 11, 2, 0)),
        )
        assertEquals(
            LocalDateTime(2026, 10, 11, 8, 0).toInstant(zone),
            snoozeUntil(SnoozeOption.Morning, LocalDateTime(2026, 10, 11, 2, 0).toInstant(zone), zone),
        )
    }

    @Test
    fun casyOdlozeni() {
        val now = LocalDateTime(2026, 10, 10, 14, 30).toInstant(zone)
        assertEquals(now + 1.hours, snoozeUntil(SnoozeOption.InHour, now, zone))
        assertEquals(LocalDateTime(2026, 10, 10, 20, 0).toInstant(zone), snoozeUntil(SnoozeOption.Tonight, now, zone))
        assertEquals(
            LocalDateTime(2026, 10, 11, 8, 0).toInstant(zone),
            snoozeUntil(SnoozeOption.TomorrowMorning, now, zone),
        )
    }

    /** Přes změnu času (25. 10. 2026 končí letní čas) je zítřejší ráno pořád v 8:00 místního. */
    @Test
    fun zitraRanoPresZmenuCasu() {
        val now = LocalDateTime(2026, 10, 24, 22, 0).toInstant(zone)
        assertEquals(
            LocalDateTime(2026, 10, 25, 8, 0).toInstant(zone),
            snoozeUntil(SnoozeOption.TomorrowMorning, now, zone),
        )
    }

    @Test
    fun spiciAVynorene() {
        val list = listOf(ChatSnooze(1, chatId = 10, until = 1_000), ChatSnooze(1, chatId = 11, until = 2_000))
        assertEquals(ChatSnoozeState(sleeping = mapOf(10L to 1_000L, 11L to 2_000L)), snoozeState(list, now = 999))
        // Právě končící odložení už patří mezi vynořené.
        assertEquals(ChatSnoozeState(sleeping = mapOf(11L to 2_000L), due = setOf(10L)), snoozeState(list, now = 1_000))
        assertEquals(ChatSnoozeState(due = setOf(10L, 11L)), snoozeState(list, now = 5_000))
    }
}
