package eu.tintera.plume.core.telegram

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.yearMonth
import kotlin.time.Instant

/** Den kalendáře médií po sloučení stránek: MÍSTNÍ datum, počet médií a první z nich. */
data class CalendarDayMedia(val date: LocalDate, val count: Int, val message: ChatMessage)

/**
 * Přidá stránku od serveru ([page], od nejnovějšího dne) k už načteným dnům ([loaded]).
 *
 * - **Den se počítá MÍSTNĚ** z času zprávy. TDLib dny dělí podle volby `utc_time_offset`, kterou
 *   Plume nenastavuje; dva jeho dny tak můžou připadnout na jeden místní — sečtou se.
 * - **Poslední den stránky bývá neúplný** a na další stránce přijde znovu se zbytkem (TDLib:
 *   „can return partial result for the last returned day"). I ten se sečte; první zprávou dne
 *   je pak ta starší.
 *
 * Výsledek je seřazený od nejnovějšího dne.
 */
fun mergeCalendarDays(
    loaded: List<CalendarDayMedia>,
    page: List<MediaCalendarDay>,
    zone: TimeZone = TimeZone.currentSystemDefault(),
): List<CalendarDayMedia> {
    val byDate = LinkedHashMap<LocalDate, CalendarDayMedia>()
    loaded.forEach { byDate[it.date] = it }
    for (day in page) {
        val date = Instant.fromEpochSeconds(day.message.timestamp.toLong()).toLocalDateTime(zone).date
        val known = byDate[date]
        byDate[date] = if (known == null) {
            CalendarDayMedia(date, day.count, day.message)
        } else {
            val first = if (day.message.timestamp < known.message.timestamp) day.message else known.message
            CalendarDayMedia(date, known.count + day.count, first)
        }
    }
    return byDate.values.sortedByDescending { it.date }
}

/**
 * Měsíc kalendáře k vykreslení: [cells] jsou řádky po sedmi od pondělí, `null` = políčko mimo
 * měsíc (před prvním dnem a za posledním).
 */
data class CalendarMonth(val month: YearMonth, val cells: List<LocalDate?>)

/**
 * Měsíce, ve kterých nějaké médium je, od nejnovějšího. Měsíc bez médií se vynechá — kalendář
 * chatu s fotkami jednou za rok by jinak byl hlavně prázdné stránky.
 *
 * **Týden začíná pondělím** (ISO a česká zvyklost); angličtina v aplikaci je obecná, ne
 * americká, takže neděle na začátku by tu byla výjimka bez důvodu.
 */
fun calendarMonths(days: List<CalendarDayMedia>): List<CalendarMonth> = calendarMonthsOf(days.map { it.date })

/** Měsíce, do kterých [dates] padají, od nejnovějšího (viz [calendarMonths]). */
fun calendarMonthsOf(dates: Collection<LocalDate>): List<CalendarMonth> =
    dates.map { it.yearMonth }
        .distinct()
        .sortedDescending()
        .map { calendarMonth(it) }

/** Políčka jednoho měsíce po řádcích od pondělí (viz [calendarMonths]). */
fun calendarMonth(month: YearMonth): CalendarMonth {
    val first = month.firstDay
    val leading = first.dayOfWeek.isoDayNumber - DayOfWeek.MONDAY.isoDayNumber
    val cells = buildList<LocalDate?> {
        repeat(leading) { add(null) }
        var date = first
        while (date.yearMonth == month) {
            add(date)
            date = date.plus(DatePeriod(days = 1))
        }
        while (size % 7 != 0) add(null)
    }
    return CalendarMonth(month, cells)
}
