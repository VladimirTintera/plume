package eu.tintera.plume.core.ui

import androidx.compose.runtime.Composable
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.duration_days
import eu.tintera.plume.resources.duration_hours
import eu.tintera.plume.resources.duration_minutes
import eu.tintera.plume.resources.duration_months
import eu.tintera.plume.resources.duration_short_days
import eu.tintera.plume.resources.duration_short_hours
import eu.tintera.plume.resources.duration_short_minutes
import eu.tintera.plume.resources.duration_short_months
import eu.tintera.plume.resources.duration_short_weeks
import eu.tintera.plume.resources.duration_weeks
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Délka v největší celé jednotce: „5 minut", „1 hodina", „3 dny", „1 týden", „1 měsíc".
 *
 * Sdílí ji doba ankety i automatické mazání zpráv. **Měsíc je 31 dní** — tak ho posílá Telegram
 * u mazání (2 678 400 s); 30 dní by se ukázalo jako „30 dní", ne „1 měsíc".
 */
@Composable
fun durationText(seconds: Int): String {
    val (unit, count) = durationUnit(seconds)
    return when (unit) {
        DurationUnit.Months -> pluralStringResource(Res.plurals.duration_months, count, count)
        DurationUnit.Weeks -> pluralStringResource(Res.plurals.duration_weeks, count, count)
        DurationUnit.Days -> pluralStringResource(Res.plurals.duration_days, count, count)
        DurationUnit.Hours -> pluralStringResource(Res.plurals.duration_hours, count, count)
        DurationUnit.Minutes -> pluralStringResource(Res.plurals.duration_minutes, count, count)
    }
}

/** Totéž mimo kompozici — pro texty skládané v `core/telegram` (servisní zprávy). */
suspend fun durationString(seconds: Int): String {
    val (unit, count) = durationUnit(seconds)
    return when (unit) {
        DurationUnit.Months -> getPluralString(Res.plurals.duration_months, count, count)
        DurationUnit.Weeks -> getPluralString(Res.plurals.duration_weeks, count, count)
        DurationUnit.Days -> getPluralString(Res.plurals.duration_days, count, count)
        DurationUnit.Hours -> getPluralString(Res.plurals.duration_hours, count, count)
        DurationUnit.Minutes -> getPluralString(Res.plurals.duration_minutes, count, count)
    }
}

/** Zkratka téže délky pro odznak: „1 d", „1 týd.", „1 měs.". */
@Composable
fun durationShortText(seconds: Int): String {
    val (unit, count) = durationUnit(seconds)
    return when (unit) {
        DurationUnit.Months -> stringResource(Res.string.duration_short_months, count)
        DurationUnit.Weeks -> stringResource(Res.string.duration_short_weeks, count)
        DurationUnit.Days -> stringResource(Res.string.duration_short_days, count)
        DurationUnit.Hours -> stringResource(Res.string.duration_short_hours, count)
        DurationUnit.Minutes -> stringResource(Res.string.duration_short_minutes, count)
    }
}

enum class DurationUnit { Minutes, Hours, Days, Weeks, Months }

/** Největší jednotka, ve které je délka celá; pod minutu se zaokrouhlí na minutu. */
fun durationUnit(seconds: Int): Pair<DurationUnit, Int> = when {
    seconds >= MONTH && seconds % MONTH == 0 -> DurationUnit.Months to seconds / MONTH
    seconds >= WEEK && seconds % WEEK == 0 -> DurationUnit.Weeks to seconds / WEEK
    seconds >= DAY && seconds % DAY == 0 -> DurationUnit.Days to seconds / DAY
    seconds >= HOUR && seconds % HOUR == 0 -> DurationUnit.Hours to seconds / HOUR
    else -> DurationUnit.Minutes to maxOf(1, seconds / MINUTE)
}

private const val MINUTE = 60
private const val HOUR = 60 * MINUTE
private const val DAY = 24 * HOUR
private const val WEEK = 7 * DAY
private const val MONTH = 31 * DAY
