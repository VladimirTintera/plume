package eu.tintera.plume.core.calendar

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** Událost, kterou si uživatel přidá do kalendáře. Čas je místní, jak ho zpráva myslí. */
data class CalendarEvent(
    val title: String,
    val description: String,
    val place: String?,
    val start: LocalDateTime,
    /** Celý den: z [start] platí jen datum. */
    val allDay: Boolean,
)

/**
 * Otevře kalendář s předvyplněnou událostí. **Nic se neukládá samo**: uživatel událost uvidí
 * v editoru svého kalendáře a uloží ji tam — název od modelu nebo z prvního řádku zprávy si
 * může opravit a kalendář si vybere.
 *
 * Android: `ACTION_INSERT` do `CalendarContract.Events` (bez oprávnění), a kde kalendář není,
 * soubor `.ics` k otevření jinou aplikací. iOS: `EKEventEditViewController` (od iOS 17 bez
 * přístupu ke kalendáři).
 */
expect class CalendarEvents {
    /** `false`, když událost nemá kdo převzít; uživateli se to musí říct. */
    fun add(event: CalendarEvent): Boolean
}

/** Začátek a konec události: hodina od začátku, celodenní do půlnoci dalšího dne. */
internal fun CalendarEvent.span(zone: TimeZone): Pair<Instant, Instant> =
    if (allDay) {
        start.date.atStartOfDayIn(zone) to start.date.plus(DatePeriod(days = 1)).atStartOfDayIn(zone)
    } else {
        val begin = start.toInstant(zone)
        begin to begin.plus(1, DateTimeUnit.HOUR, zone)
    }

/**
 * Událost jako iCalendar (RFC 5545) — záložní cesta, kde systémový kalendář chybí.
 *
 * - Čas v UTC (`…Z`): „plovoucí" čas bez pásma by si každý kalendář vyložil po svém.
 * - Text escapovaný (`\\`, `;`, `,`, nový řádek) a řádky zalomené po 75 bajtech, jak chce norma;
 *   zalamuje se po bajtech UTF-8, ne po znacích, aby čeština normu nepřetáhla.
 */
fun CalendarEvent.toIcs(zone: TimeZone, uid: String, stamp: Instant): String {
    val (begin, end) = span(zone)
    val lines = buildList {
        add("BEGIN:VCALENDAR")
        add("VERSION:2.0")
        add("PRODID:-//Plume//Message context//EN")
        add("BEGIN:VEVENT")
        add("UID:$uid")
        add("DTSTAMP:${utc(stamp)}")
        if (allDay) {
            add("DTSTART;VALUE=DATE:${date(start)}")
            add("DTEND;VALUE=DATE:${date(LocalDateTime(start.date.plus(DatePeriod(days = 1)), start.time))}")
        } else {
            add("DTSTART:${utc(begin)}")
            add("DTEND:${utc(end)}")
        }
        add("SUMMARY:${escape(title)}")
        place?.let { add("LOCATION:${escape(it)}") }
        if (description.isNotBlank()) add("DESCRIPTION:${escape(description)}")
        add("END:VEVENT")
        add("END:VCALENDAR")
    }
    return lines.joinToString("") { fold(it) + "\r\n" }
}

private fun utc(instant: Instant): String {
    val at = instant.toLocalDateTime(TimeZone.UTC)
    return date(at) + "T" + two(at.hour) + two(at.minute) + two(at.second) + "Z"
}

private fun date(at: LocalDateTime): String = at.year.toString().padStart(4, '0') + two(at.month.ordinal + 1) + two(at.day)

private fun two(value: Int) = value.toString().padStart(2, '0')

private fun escape(text: String): String = text
    .replace("\\", "\\\\")
    .replace(";", "\\;")
    .replace(",", "\\,")
    .replace("\r\n", "\\n")
    .replace("\n", "\\n")

/** Zalomení řádku po 75 bajtech; pokračování začíná mezerou (RFC 5545, 3.1). */
private fun fold(line: String): String {
    val out = StringBuilder()
    var bytes = 0
    var index = 0
    while (index < line.length) {
        // Celý znak i s náhradním párem (emoji) — rozpůlený by se nedal přečíst.
        val end = if (line[index].isHighSurrogate() && index + 1 < line.length) index + 2 else index + 1
        val piece = line.substring(index, end)
        val size = piece.encodeToByteArray().size
        if (bytes + size > MAX_LINE_BYTES) {
            out.append("\r\n ")
            bytes = 1
        }
        out.append(piece)
        bytes += size
        index = end
    }
    return out.toString()
}

private const val MAX_LINE_BYTES = 75
