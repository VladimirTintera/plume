package eu.tintera.plume.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import eu.tintera.plume.core.telegram.DateTimeFormat
import eu.tintera.plume.core.telegram.DateTimePrecision
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.date_day_month_year
import eu.tintera.plume.resources.date_numeric
import eu.tintera.plume.resources.date_upcoming_time
import eu.tintera.plume.resources.date_weekday_date
import eu.tintera.plume.resources.relative_ago_days
import eu.tintera.plume.resources.relative_ago_hours
import eu.tintera.plume.resources.relative_ago_minutes
import eu.tintera.plume.resources.relative_ago_months
import eu.tintera.plume.resources.relative_ago_seconds
import eu.tintera.plume.resources.relative_ago_years
import eu.tintera.plume.resources.relative_in_days
import eu.tintera.plume.resources.relative_in_hours
import eu.tintera.plume.resources.relative_in_minutes
import eu.tintera.plume.resources.relative_in_months
import eu.tintera.plume.resources.relative_in_seconds
import eu.tintera.plume.resources.relative_in_years
import eu.tintera.plume.resources.weekday_in_text_1
import eu.tintera.plume.resources.weekday_in_text_2
import eu.tintera.plume.resources.weekday_in_text_3
import eu.tintera.plume.resources.weekday_in_text_4
import eu.tintera.plume.resources.weekday_in_text_5
import eu.tintera.plume.resources.weekday_in_text_6
import eu.tintera.plume.resources.weekday_in_text_7
import kotlin.math.abs
import kotlinx.datetime.DayOfWeek
import org.jetbrains.compose.resources.StringResource
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Datum a čas ze zprávy ([eu.tintera.plume.core.telegram.MessageEntityType.DateTime]) v místním
 * pásmu a v jazyce aplikace — „zítra v 18:00" od pisatele v jiném pásmu je u čtenáře jiný čas.
 *
 * Skládá se v kompozici, ne při převodu zprávy: jazyk se může změnit a relativní čas („za
 * 5 minut") stárne. Počítá se vůči okamžiku složení; po vteřinách se nepřepočítává — odpočet
 * by kvůli jednomu úseku překresloval konverzaci.
 */
@Composable
fun dateTimeText(unixTime: Long, format: DateTimeFormat): String = when (format) {
    DateTimeFormat.Relative -> {
        val now = remember(unixTime) { Clock.System.now().epochSeconds }
        relativeText(relativeTime(unixTime - now))
    }
    is DateTimeFormat.Absolute -> absoluteText(unixTime, format)
}

@Composable
private fun relativeText(relative: RelativeTime): String {
    val resource = if (relative.future) {
        when (relative.unit) {
            RelativeUnit.Seconds -> Res.plurals.relative_in_seconds
            RelativeUnit.Minutes -> Res.plurals.relative_in_minutes
            RelativeUnit.Hours -> Res.plurals.relative_in_hours
            RelativeUnit.Days -> Res.plurals.relative_in_days
            RelativeUnit.Months -> Res.plurals.relative_in_months
            RelativeUnit.Years -> Res.plurals.relative_in_years
        }
    } else {
        when (relative.unit) {
            RelativeUnit.Seconds -> Res.plurals.relative_ago_seconds
            RelativeUnit.Minutes -> Res.plurals.relative_ago_minutes
            RelativeUnit.Hours -> Res.plurals.relative_ago_hours
            RelativeUnit.Days -> Res.plurals.relative_ago_days
            RelativeUnit.Months -> Res.plurals.relative_ago_months
            RelativeUnit.Years -> Res.plurals.relative_ago_years
        }
    }
    return pluralStringResource(resource, relative.count, relative.count)
}

@Composable
private fun absoluteText(unixTime: Long, format: DateTimeFormat.Absolute): String {
    val moment = Instant.fromEpochSeconds(unixTime).toLocalDateTime(TimeZone.currentSystemDefault())
    val date = when (format.date) {
        DateTimePrecision.None -> null
        DateTimePrecision.Short -> stringResource(Res.string.date_numeric, moment.day, moment.month.number, moment.year)
        DateTimePrecision.Long -> stringResource(
            Res.string.date_day_month_year,
            moment.day,
            stringResource(moment.month.name()),
            moment.year,
        )
    }
    val time = when (format.time) {
        DateTimePrecision.None -> null
        DateTimePrecision.Short -> "${moment.hour.pad()}:${moment.minute.pad()}"
        DateTimePrecision.Long -> "${moment.hour.pad()}:${moment.minute.pad()}:${moment.second.pad()}"
    }
    val weekday = if (format.dayOfWeek) stringResource(moment.dayOfWeek.inTextName()) else null
    val day = when {
        weekday != null && date != null -> stringResource(Res.string.date_weekday_date, weekday, date)
        else -> weekday ?: date
    }
    return when {
        day != null && time != null -> stringResource(Res.string.date_upcoming_time, day, time)
        else -> day ?: time.orEmpty()
    }
}

/**
 * Den v týdnu uprostřed věty. `day_long_*` jsou nadpisy předělů („Středa"), a česky se uprostřed
 * věty píše malým písmenem — anglicky velkým, takže to nejde vyřešit `lowercase()`.
 */
private fun DayOfWeek.inTextName(): StringResource = when (this) {
    DayOfWeek.MONDAY -> Res.string.weekday_in_text_1
    DayOfWeek.TUESDAY -> Res.string.weekday_in_text_2
    DayOfWeek.WEDNESDAY -> Res.string.weekday_in_text_3
    DayOfWeek.THURSDAY -> Res.string.weekday_in_text_4
    DayOfWeek.FRIDAY -> Res.string.weekday_in_text_5
    DayOfWeek.SATURDAY -> Res.string.weekday_in_text_6
    else -> Res.string.weekday_in_text_7
}

private fun Int.pad(): String = toString().padStart(2, '0')

enum class RelativeUnit { Seconds, Minutes, Hours, Days, Months, Years }

/** Relativní čas v největší jednotce, která se vejde aspoň jednou; [future] = „za", jinak „před". */
data class RelativeTime(val count: Int, val unit: RelativeUnit, val future: Boolean)

/**
 * Jednotka pro rozdíl [deltaSeconds] (kladný = v budoucnu).
 *
 * **Zaokrouhluje se, ne ořezává.** S ořezem vyšla 1 h 59 min jako „před 1 hodinou" — a stačí,
 * aby hodiny telefonu šly o pár vteřin pozadu, a z „před 2 hodinami" od pisatele je hodina.
 * Co se zaokrouhlí na celou vyšší jednotku (59 min 40 s), přejde do ní („1 hodina", ne
 * „60 minut"). Měsíc je 30 dní, rok 365 — u relativního času jde o řád, ne o kalendář.
 */
fun relativeTime(deltaSeconds: Long): RelativeTime {
    val seconds = abs(deltaSeconds)
    val index = UNITS.indexOfLast { seconds >= it.second }.coerceAtLeast(0)
    val (unit, size) = UNITS[index]
    val count = (seconds + size / 2) / size
    val next = UNITS.getOrNull(index + 1)
    val promoted = next != null && count * size >= next.second
    return RelativeTime(
        count = if (promoted) 1 else count.toInt(),
        unit = if (promoted) next.first else unit,
        future = deltaSeconds > 0,
    )
}

private val UNITS = listOf(
    RelativeUnit.Seconds to 1L,
    RelativeUnit.Minutes to 60L,
    RelativeUnit.Hours to 60 * 60L,
    RelativeUnit.Days to 24 * 60 * 60L,
    RelativeUnit.Months to 30 * 24 * 60 * 60L,
    RelativeUnit.Years to 365 * 24 * 60 * 60L,
)
