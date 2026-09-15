package eu.tintera.plume.core.ui

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone
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
        else -> "${moment.date.dayOfMonth}. ${moment.date.monthNumber}."
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
