package eu.tintera.plume.core.ui

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Čas událostí starších než „dnes".
 *
 * Používá se tam, kde jde o **stáří**, ne o pořadí během dne: v seznamu chatů u poslední zprávy
 * a u „naposledy viděn". Samotné hodiny by u události z loňska nic neřekly, a naopak plné datum
 * u dnešní zprávy je zbytečně upovídané, takže se podrobnost s věkem snižuje.
 *
 * V konverzaci se naopak vypisují jen hodiny — tam je datum v hlavičce dne a zprávy jdou
 * chronologicky za sebou.
 */
fun Int.asRelativeTime(): String {
    val zone = TimeZone.currentSystemDefault()
    val moment = Instant.fromEpochSeconds(toLong()).toLocalDateTime(zone)
    val today = Clock.System.now().toLocalDateTime(zone).date

    val days = (today.toEpochDays() - moment.date.toEpochDays()).toInt()

    return when {
        // Záporná hodnota znamená čas v budoucnosti — u posunutých hodin na zařízení se to stane.
        days <= 0 -> "${moment.hour.pad()}:${moment.minute.pad()}"
        days == 1 -> "včera"
        days < 7 -> moment.date.dayOfWeek.shortCzech()
        else -> "${moment.date.day}. ${moment.date.month.number}."
    }
}

private fun Int.pad(): String = toString().padStart(2, '0')

private fun DayOfWeek.shortCzech(): String = when (this) {
    DayOfWeek.MONDAY -> "po"
    DayOfWeek.TUESDAY -> "út"
    DayOfWeek.WEDNESDAY -> "st"
    DayOfWeek.THURSDAY -> "čt"
    DayOfWeek.FRIDAY -> "pá"
    DayOfWeek.SATURDAY -> "so"
    else -> "ne"
}

/**
 * Datum pro předěl dne v konverzaci.
 *
 * Podrobnost se s věkem **zvyšuje**, ne snižuje jako u [asRelativeTime]: předěl je nadpis, pod
 * kterým následuje celý blok zpráv, takže musí říct dost na to, aby se v historii dalo
 * zorientovat. „po" by u zprávy z loňska bylo k ničemu.
 *
 * Rok se dopisuje jen tehdy, když nesedí s letoškem — u letošních dat je to šum.
 */
fun Int.asDayLabel(): String {
    val zone = TimeZone.currentSystemDefault()
    val date = Instant.fromEpochSeconds(toLong()).toLocalDateTime(zone).date
    val today = Clock.System.now().toLocalDateTime(zone).date

    val days = (today.toEpochDays() - date.toEpochDays()).toInt()

    return when {
        // Záporná hodnota znamená čas v budoucnosti — u posunutých hodin na zařízení se to stane.
        days <= 0 -> "Dnes"
        days == 1 -> "Včera"
        days < 7 -> date.dayOfWeek.longCzech()
        date.year == today.year -> "${date.day}. ${date.month.czechGenitive()}"
        else -> "${date.day}. ${date.month.czechGenitive()} ${date.year}"
    }
}

/**
 * Jsou oba časy ve stejném dni?
 *
 * Porovnává se **místní datum**, ne rozdíl sekund: den má 24 hodin jen někdy — na přechodu
 * letního času je kratší nebo delší a půlnoc se posouvá podle časového pásma.
 */
fun Int.isSameDayAs(other: Int): Boolean = localDate() == other.localDate()

private fun Int.localDate(): LocalDate = Instant.fromEpochSeconds(toLong())
    .toLocalDateTime(TimeZone.currentSystemDefault())
    .date

/** Dlouhý název dne. V předělu je na zkratku dost místa a celé slovo se čte líp. */
private fun DayOfWeek.longCzech(): String = when (this) {
    DayOfWeek.MONDAY -> "Pondělí"
    DayOfWeek.TUESDAY -> "Úterý"
    DayOfWeek.WEDNESDAY -> "Středa"
    DayOfWeek.THURSDAY -> "Čtvrtek"
    DayOfWeek.FRIDAY -> "Pátek"
    DayOfWeek.SATURDAY -> "Sobota"
    else -> "Neděle"
}

/** Druhý pád, protože měsíc stojí za dnem: „5. února", ne „5. únor". */
private fun Month.czechGenitive(): String = when (this) {
    Month.JANUARY -> "ledna"
    Month.FEBRUARY -> "února"
    Month.MARCH -> "března"
    Month.APRIL -> "dubna"
    Month.MAY -> "května"
    Month.JUNE -> "června"
    Month.JULY -> "července"
    Month.AUGUST -> "srpna"
    Month.SEPTEMBER -> "září"
    Month.OCTOBER -> "října"
    Month.NOVEMBER -> "listopadu"
    else -> "prosince"
}
