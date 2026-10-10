package eu.tintera.plume.core.snooze

import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/** Na kdy jde chat odložit. Konkrétní okamžik dává [snoozeUntil]. */
enum class SnoozeOption {
    /** Za hodinu od teď. */
    InHour,

    /** Dnes ve 20:00. */
    Tonight,

    /** Dnes v 8:00 — po půlnoci, než se rozední, je „ráno" ještě dnešek. */
    Morning,

    /** Zítra v 8:00. */
    TomorrowMorning,
}

/**
 * Nabídka dob odložení v místním čase [now].
 *
 * - **„Dnes večer" jen do 18:00.** Později by to bylo za chvilku, a na to je „Za hodinu".
 * - **Po půlnoci do 6:00 je ráno dnešní**, ne zítřejší: ve dvě v noci „zítra ráno" znamená
 *   za více než den, a to nikdo nechce.
 */
fun snoozeOptions(now: LocalDateTime): List<SnoozeOption> = buildList {
    add(SnoozeOption.InHour)
    if (now.hour < TONIGHT_OFFER_UNTIL_HOUR) add(SnoozeOption.Tonight)
    add(if (now.hour < EARLY_MORNING_UNTIL_HOUR) SnoozeOption.Morning else SnoozeOption.TomorrowMorning)
}

/** Okamžik, kdy odložení [option] skončí, v časovém pásmu [zone]. */
fun snoozeUntil(option: SnoozeOption, now: Instant, zone: TimeZone): Instant {
    val today = now.toLocalDateTime(zone).date
    return when (option) {
        SnoozeOption.InHour -> now + 1.hours
        SnoozeOption.Tonight -> today.atTime(TONIGHT).toInstant(zone)
        SnoozeOption.Morning -> today.atTime(MORNING).toInstant(zone)
        SnoozeOption.TomorrowMorning -> today.plus(DatePeriod(days = 1)).atTime(MORNING).toInstant(zone)
    }
}

private val TONIGHT = LocalTime(20, 0)
private val MORNING = LocalTime(8, 0)
private const val TONIGHT_OFFER_UNTIL_HOUR = 18
private const val EARLY_MORNING_UNTIL_HOUR = 6
