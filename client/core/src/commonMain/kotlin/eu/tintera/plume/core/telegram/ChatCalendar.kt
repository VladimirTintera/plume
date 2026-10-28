package eu.tintera.plume.core.telegram

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Úsek historie spočtený po dnech ([ConversationRepository.messageCountsByDay]): kolik zpráv
 * přišlo který MÍSTNÍ den, a kam až do minulosti úsek sahá.
 */
data class HistoryCounts(
    val days: Map<LocalDate, Int>,
    /** Nejstarší spočtená zpráva — odtud se pokračuje ke starším; `null`, když nepřišla žádná. */
    val oldestMessageId: Long?,
    /** Její čas (sekundy epochy). Den, do kterého spadá, je spočtený jen zčásti. */
    val oldestTimestamp: Int?,
    /**
     * Starší zprávy ze zdroje nejsou: z lokální databáze = dál už zařízení nic nemá, ze serveru =
     * začátek chatu.
     */
    val reachedEnd: Boolean,
)

/** Zprávy podle MÍSTNÍHO dne odeslání. */
fun countMessagesByDay(timestamps: List<Int>, zone: TimeZone): Map<LocalDate, Int> {
    val counts = LinkedHashMap<LocalDate, Int>()
    for (timestamp in timestamps) {
        val date = Instant.fromEpochSeconds(timestamp.toLong()).toLocalDateTime(zone).date
        counts[date] = (counts[date] ?: 0) + 1
    }
    return counts
}

/** Dva úseky po dnech dohromady; den na hranici úseků (spočtený zčásti v obou) se sečte. */
fun mergeDayCounts(a: Map<LocalDate, Int>, b: Map<LocalDate, Int>): Map<LocalDate, Int> {
    val merged = LinkedHashMap(a)
    b.forEach { (date, count) -> merged[date] = (merged[date] ?: 0) + count }
    return merged
}

/**
 * Měsíce od [newest] po [oldest] včetně, od nejnovějšího — osa kalendáře všech zpráv. Na rozdíl
 * od kalendáře médií ([calendarMonths]) se prázdné měsíce nevynechávají: i „tenhle měsíc nic" je
 * informace.
 */
fun monthsBetween(newest: YearMonth, oldest: YearMonth): List<YearMonth> {
    if (oldest > newest) return listOf(newest)
    val months = mutableListOf<YearMonth>()
    var month = newest
    while (month >= oldest) {
        months += month
        month = month.minusMonth()
    }
    return months
}

private fun YearMonth.minusMonth(): YearMonth =
    if (month.ordinal == 0) YearMonth(year - 1, 12) else YearMonth(year, month.ordinal)
