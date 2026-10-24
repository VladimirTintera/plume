package eu.tintera.plume.core.telegram

/**
 * Profil firmy (Telegram Business): adresa a otevírací doba z `UserFullInfo.businessInfo`.
 * Zakládat ho chce Business, číst ho může každý. Úvodní stránka (`startPage`) patří do prázdné
 * konverzace, ne do profilu, a tady není.
 */
data class BusinessDetails(
    /** Adresa, jak ji firma zapsala; `null` = žádná. */
    val address: String? = null,
    /** Souřadnice adresy, když je firma zadala. */
    val latitude: Double? = null,
    val longitude: Double? = null,
    /** Otevírací doba v MÍSTNÍM čase uživatele (`localOpeningHours`); prázdné = firma ji neuvedla. */
    val openingHours: List<OpeningInterval> = emptyList(),
)

/**
 * Úsek otevírací doby v minutách týdne od pondělní půlnoci: [startMinute] včetně, [endMinute]
 * bez. TDLib je dává už rozdělené po dnech, takže úsek přes půlnoc nepřeteče.
 */
data class OpeningInterval(val startMinute: Int, val endMinute: Int)

/** Co je s firmou právě teď — podle místního času a otevírací doby. */
sealed interface OpeningState {
    /** Otevřeno nepřetržitě celý týden. */
    data object AlwaysOpen : OpeningState

    /** Otevřeno do [closesAt] (minuta dne, 0–1440). */
    data class Open(val closesAt: Int) : OpeningState

    /**
     * Zavřeno, otevírá za [inDays] dní (0 = dnes) v [opensAt] (minuta dne). `null` [opensAt] =
     * otevírací doba je prázdná, firma neotevírá nikdy.
     */
    data class Closed(val opensAt: Int?, val inDays: Int = 0, val dayIndex: Int = 0) : OpeningState
}

const val MINUTES_PER_DAY = 24 * 60
private const val MINUTES_PER_WEEK = 7 * MINUTES_PER_DAY

/** Úseky po dnech: index 0 = pondělí, každý jako minuty dne (začátek až konec bez). */
fun List<OpeningInterval>.byDay(): List<List<IntRange>> {
    val days = List(7) { mutableListOf<IntRange>() }
    sortedBy { it.startMinute }.forEach { interval ->
        val day = (interval.startMinute / MINUTES_PER_DAY) % 7
        val start = interval.startMinute % MINUTES_PER_DAY
        val end = (interval.endMinute - interval.startMinute + start).coerceAtMost(MINUTES_PER_DAY)
        days[day] += start until end
    }
    return days
}

/** Den otevřený celých 24 hodin. */
fun List<IntRange>.isWholeDay(): Boolean = any { it.first == 0 && it.last == MINUTES_PER_DAY - 1 }

/** Stav firmy v minutě týdne [minuteOfWeek] (0 = pondělí 0:00). */
fun List<OpeningInterval>.stateAt(minuteOfWeek: Int): OpeningState {
    if (isEmpty()) return OpeningState.Closed(opensAt = null)
    val days = byDay()
    if (days.all { it.isWholeDay() }) return OpeningState.AlwaysOpen
    val now = minuteOfWeek.mod(MINUTES_PER_WEEK)
    // Úseky jako minuty týdne, i o týden posunuté — kvůli neděli večer a pondělku ráno.
    val spans = days.flatMapIndexed { day, ranges ->
        ranges.map { (day * MINUTES_PER_DAY + it.first) until (day * MINUTES_PER_DAY + it.last + 1) }
    }.sortedBy { it.first }
    val weekSpans = spans + spans.map { (it.first + MINUTES_PER_WEEK) until (it.last + 1 + MINUTES_PER_WEEK) }
    weekSpans.firstOrNull { now in it }?.let { open ->
        // Navazující úseky (do půlnoci a od půlnoci) se počítají jako jeden.
        var end = open.last + 1
        while (true) {
            val next = weekSpans.firstOrNull { it.first == end } ?: break
            end = next.last + 1
        }
        return OpeningState.Open(closesAt = end % MINUTES_PER_DAY)
    }
    val next = weekSpans.first { it.first > now }.first
    val todayStart = now - now % MINUTES_PER_DAY
    return OpeningState.Closed(
        opensAt = next % MINUTES_PER_DAY,
        inDays = (next - todayStart) / MINUTES_PER_DAY,
        dayIndex = (next / MINUTES_PER_DAY) % 7,
    )
}
