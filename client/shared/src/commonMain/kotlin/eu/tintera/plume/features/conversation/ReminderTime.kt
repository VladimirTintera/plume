package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.ui.foldDiacritics
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.atTime
import kotlinx.datetime.plus

/**
 * Termín ze zprávy pro připomínku („sraz v pátek v 15:00", „zítra ráno", „12. 10. v 9:30",
 * „tomorrow at 3pm"), nebo `null`, když v textu žádný není. Česky i anglicky, bez modelu: pravidla
 * se dají otestovat a nic se kvůli tomu neposílá ani nestahuje.
 *
 * - **Vztažné slovo se počítá od ODESLÁNÍ zprávy** ([sentAt]), ne od teď: „zítra v 10" napsané
 *   včera znamená dnes v 10. Jestli termín mezitím minul, rozhodne volající.
 * - **Čas bez dne** je den odeslání; když už ten čas tehdy minul, následující den („v 8"
 *   napsané večer míní ráno).
 * - **Den bez času** dostane ráno (8:00, jako odložení chatu), nebo část dne ze zprávy
 *   („v pátek večer" → 19:00).
 * - **Den v týdnu** je nejbližší po dni odeslání; týž den jen s časem, který ještě nebyl
 *   („v pátek v 15:00" napsané v pátek ráno je dnes).
 * - **Radši nic než nesmysl**: „5.10" bez tečky na konci je spíš číslo než datum, holé číslo
 *   („přijde 5 lidí") není čas. Čas chce dvojtečku, předložku („v 9.30", „v 15"), nebo am/pm.
 *
 * Text se porovnává bez diakritiky ([foldDiacritics]), takže „zitra" i „zítra" platí stejně.
 */
fun reminderTimeFromText(text: String, sentAt: LocalDateTime): LocalDateTime? = textTime(text, sentAt)?.at

/**
 * Termín ze zprávy i s tím, z čeho se poskládal. Připomínce stačí [at]; událost do kalendáře
 * (`findMessageContext`) podle příznaků rozhoduje, jak moc si může být jistá — „v 8" samo o sobě
 * na připomínku stačí, na schůzku ne.
 */
internal class TextTime(
    val at: LocalDateTime,
    /** Den ze zprávy: datum, vztažné slovo („zítra") nebo den v týdnu. */
    val hasDay: Boolean,
    /** Datum psané číslem („12. 10."), ne jen slovem. */
    val hasDate: Boolean,
    /** Přesný čas („15:00", „v 9.30", „3pm", „v 15"). */
    val hasClock: Boolean,
    /** Část dne bez přesného času („večer", „odpoledne"). */
    val hasPart: Boolean,
)

/** Viz [reminderTimeFromText]; tytéž pravidla, jen s příznaky ([TextTime]). */
internal fun textTime(text: String, sentAt: LocalDateTime): TextTime? {
    var rest = " " + foldDiacritics(text).lowercase() + " "

    // Datum „12. 10." / „12.10.2026" — dřív než čas, ať se „12.10" nepřečte jako 12:10.
    var date: LocalDate? = null
    DATE.find(rest)?.let { match ->
        val day = match.groupValues[1].toInt()
        val month = match.groupValues[2].toInt()
        val year = match.groupValues[3].toIntOrNull()
        date = dateOrNull(year ?: sentAt.year, month, day)?.let { candidate ->
            if (year == null && candidate < sentAt.date) dateOrNull(sentAt.year + 1, month, day) else candidate
        }
        if (date != null) rest = rest.replaceRange(match.range, " ")
    }
    // Svátek s pevným datem („na Silvestra", „den před Štědrým dnem", „on Christmas Eve") —
    // model z něj datum počítal špatně (viz [holidayDate]). Platí až po vztažném slovu a dni
    // v týdnu (níž): „Silvestr" je i jméno, a „zítra" řekne den jistěji.
    var holiday: LocalDate? = null
    if (date == null) {
        holidayDate(rest, sentAt.date)?.let { (found, range) ->
            holiday = found
            rest = rest.replaceRange(range, " ")
        }
    }

    val time = explicitTime(rest)
    val part = PARTS.entries.firstOrNull { (words, _) -> words.any { word(rest, it) } }?.value
    // „v 3 odpoledne", „v 8 večer": hodina do 11 s odpolední částí dne je odpoledne.
    val hour = time?.let { if (it.hour < 12 && part != null && part.hour >= 12) LocalTime(it.hour + 12, it.minute) else it }

    val offset = OFFSETS.entries.firstOrNull { (words, _) -> words.any { word(rest, it) } }?.value
    val weekday = WEEKDAYS.entries.firstOrNull { (words, _) -> words.any { word(rest, it) } }?.value

    val day: LocalDate? = date
        ?: offset?.let { sentAt.date.plus(DatePeriod(days = it)) }
        ?: weekday?.let { nextWeekday(sentAt, it, hour) }
        ?: holiday

    if (day == null) {
        // Bez dne jen s časem; samotná část dne („ráno") je moc vágní na připomínku.
        val at = hour ?: return null
        val sameDay = sentAt.date.atTime(at)
        val next = if (sameDay > sentAt) sameDay else sentAt.date.plus(DatePeriod(days = 1)).atTime(at)
        return TextTime(next, hasDay = false, hasDate = false, hasClock = true, hasPart = part != null)
    }
    return TextTime(
        at = day.atTime(hour ?: part ?: MORNING),
        hasDay = true,
        hasDate = date != null,
        hasClock = hour != null,
        hasPart = part != null,
    )
}

/**
 * Svátek s pevným datem v textu (bez diakritiky, malými písmeny) a úsek, který zabírá — i s „den
 * před" / „den po" před ním. Datum je nejbližší výskyt od dne odeslání [sentOn] (dnešek platí).
 *
 * Kvůli tomu, že to model počítal špatně: „den před Silvestrem" mu vyšlo 24. 12. místo 30. 12.
 * Pravidla mají před modelem přednost, takže co poznají tady, model nepočítá vůbec.
 * Pohyblivé svátky (Velikonoce) a obecné „o Vánocích" (několik dní) se nehledají.
 */
internal fun holidayDate(text: String, sentOn: LocalDate): Pair<LocalDate, IntRange>? {
    for ((pattern, monthDay) in HOLIDAYS) {
        val match = pattern.find(text) ?: continue
        val (month, day) = monthDay
        val thisYear = LocalDate(sentOn.year, month, day)
        var date = if (thisYear >= sentOn) thisYear else LocalDate(sentOn.year + 1, month, day)
        var range = match.range
        RELATIVE_DAY.findAll(text.substring(0, match.range.first)).lastOrNull()
            ?.takeIf { it.range.last == match.range.first - 1 }
            ?.let { relative ->
                date = date.plus(DatePeriod(days = if (relative.groupValues[1].let { it == "pred" || it == "before" }) -1 else 1))
                range = relative.range.first..match.range.last
            }
        return date to range
    }
    return null
}

/** „den před " / „den po " / „the day before " / „day after " těsně před svátkem. */
private val RELATIVE_DAY = Regex("(?<![\\p{L}])(?:the\\s+)?(?:den|day)\\s+(pred|po|before|after)\\s+")

/** Svátky s pevným datem, česky (ve všech pádech) a anglicky. */
private val HOLIDAYS: List<Pair<Regex, Pair<Int, Int>>> = listOf(
    holiday("silvestr\\p{L}*|new year['’]?s eve") to (12 to 31),
    holiday("stedr\\p{L}*\\s+(?:den|dne|dnem|dni)|stedrej\\s+den|christmas eve") to (12 to 24),
    holiday("boz\\p{L}*\\s+hod\\p{L}*|christmas day") to (12 to 25),
    holiday("(?:na\\s+)?novy\\s+rok|noveho\\s+roku|new year['’]?s day") to (1 to 1),
    // „Valentýn" je i jméno („Valentýn přijde"), proto česky jen se „svatého".
    holiday("(?:sv\\.|svateho)\\s+valentyn\\p{L}*|valentine['’]?s day") to (2 to 14),
    holiday("halloween\\p{L}*") to (10 to 31),
)

private fun holiday(alternatives: String) = Regex("(?<![\\p{L}])(?:$alternatives)(?![\\p{L}])")

/**
 * Čas „3pm", „15:00", „v 9.30" (tečka jen s předložkou — „verze 5.10" čas není), „v 15".
 * Am/pm napřed: „3:30pm" by jinak vyšlo jako 3:30 ráno.
 */
private fun explicitTime(text: String): LocalTime? {
    AM_PM.find(text)?.let { match ->
        val hour = match.groupValues[1].toInt()
        val minute = match.groupValues[2].ifEmpty { "0" }.toInt()
        if (hour in 1..12 && minute in 0..59) {
            val pm = match.groupValues[3].startsWith("p")
            return LocalTime((hour % 12) + if (pm) 12 else 0, minute)
        }
    }
    (CLOCK.find(text) ?: DOT_CLOCK.find(text))?.let { match ->
        val hour = match.groupValues[1].toInt()
        val minute = match.groupValues[2].toInt()
        if (hour in 0..23 && minute in 0..59) return LocalTime(hour, minute)
    }
    AT_HOUR.find(text)?.let { match ->
        val hour = match.groupValues[1].toInt()
        if (hour in 0..23) return LocalTime(hour, 0)
    }
    return null
}

/**
 * Nejbližší [target] po dni odeslání. Týž den jen tehdy, když termín ([time]) ještě nebyl —
 * bez času týž den znamená příští týden.
 */
private fun nextWeekday(sentAt: LocalDateTime, target: DayOfWeek, time: LocalTime?): LocalDate {
    val ahead = (target.ordinal - sentAt.dayOfWeek.ordinal + 7) % 7
    if (ahead == 0 && time != null && sentAt.date.atTime(time) > sentAt) return sentAt.date
    return sentAt.date.plus(DatePeriod(days = if (ahead == 0) 7 else ahead))
}

private fun dateOrNull(year: Int, month: Int, day: Int): LocalDate? =
    runCatching { LocalDate(year, month, day) }.getOrNull()

/** Celé slovo (víceslovné spojení) uprostřed textu, ne kus jiného slova. */
private fun word(text: String, phrase: String): Boolean = Regex("(?<![\\p{L}\\d])" + Regex.escape(phrase) + "(?![\\p{L}\\d])").containsMatchIn(text)

private val DATE = Regex("(?<![\\d.])(\\d{1,2})\\.\\s?(\\d{1,2})\\.(?:\\s?(\\d{4}))?")
private val CLOCK = Regex("(?<![\\d.:])(\\d{1,2}):(\\d{2})(?![\\d.:])")
private val DOT_CLOCK = Regex("(?<![\\p{L}])(?:v|ve|at|od|do|kolem|around)\\s(\\d{1,2})\\.(\\d{2})(?![\\d.:])")
private val AM_PM = Regex("(?<![\\d.:])(\\d{1,2})(?::(\\d{2}))?\\s?(am|pm|a\\.m\\.|p\\.m\\.)(?![\\p{L}])")
private val AT_HOUR = Regex("(?<![\\p{L}])(?:v|ve|at|od|kolem|around)\\s(\\d{1,2})(?:\\s?(?:h|hod|hodin|hodiny|o'clock))?(?![\\d.:,\\p{L}])")

private val MORNING = LocalTime(8, 0)

/** Části dne, když chybí přesný čas. Pořadí rozhoduje: „dopoledne" dřív než „poledne". */
private val PARTS: Map<List<String>, LocalTime> = linkedMapOf(
    listOf("dopoledne", "late morning") to LocalTime(10, 0),
    listOf("odpoledne", "afternoon") to LocalTime(15, 0),
    listOf("v poledne", "poledne", "noon", "at noon") to LocalTime(12, 0),
    listOf("rano", "morning") to MORNING,
    listOf("vecer", "evening", "tonight") to LocalTime(19, 0),
)

/** Vztažné dny. „Pozítří" dřív než „zítra", jinak by vyhrálo kratší slovo uvnitř delšího spojení. */
private val OFFSETS: Map<List<String>, Int> = linkedMapOf(
    listOf("pozitri", "day after tomorrow") to 2,
    listOf("zitra", "zejtra", "tomorrow") to 1,
    listOf("dnes", "dneska", "today", "tonight") to 0,
)

/** Dny v týdnu ve tvarech, jak se píšou („v pondělí", „ve středu", „on friday"). */
private val WEEKDAYS: Map<List<String>, DayOfWeek> = linkedMapOf(
    listOf("pondeli", "monday") to DayOfWeek.MONDAY,
    listOf("utery", "tuesday") to DayOfWeek.TUESDAY,
    listOf("streda", "stredu", "wednesday") to DayOfWeek.WEDNESDAY,
    listOf("ctvrtek", "thursday") to DayOfWeek.THURSDAY,
    listOf("patek", "friday") to DayOfWeek.FRIDAY,
    listOf("sobota", "sobotu", "saturday") to DayOfWeek.SATURDAY,
    listOf("nedele", "nedeli", "sunday") to DayOfWeek.SUNDAY,
)
