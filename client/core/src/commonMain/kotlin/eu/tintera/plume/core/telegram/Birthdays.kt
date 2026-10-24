package eu.tintera.plume.core.telegram

import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.number
import kotlinx.datetime.plus
import kotlinx.datetime.minus
import kotlinx.datetime.DatePeriod

/**
 * Datum narození (`birthdate`). Rok uživatel uvést nemusí — pak se neukazuje ani věk.
 *
 * @property day den v měsíci, 1–31.
 * @property month měsíc, 1–12.
 * @property year rok narození, `null` = neuvedený.
 */
data class Birthdate(val day: Int, val month: Int, val year: Int?) {

    /**
     * Narozeniny v roce [year]. Kdo se narodil 29. února, slaví v nepřestupném roce 28. února —
     * jinak by narozeniny tři roky ze čtyř neměl vůbec.
     */
    fun inYear(year: Int): LocalDate {
        val leapDay = month == 2 && day == 29
        return if (leapDay && !isLeapYear(year)) LocalDate(year, Month.FEBRUARY, 28) else LocalDate(year, month, day)
    }

    /** Kolik let má [today]; `null` bez roku nebo s rokem v budoucnosti (překlep). */
    fun ageOn(today: LocalDate): Int? {
        val year = year ?: return null
        val celebrated = inYear(today.year) <= today
        return (today.year - year - if (celebrated) 0 else 1).takeIf { it >= 0 }
    }

    /** Je mezi včerejškem a zítřkem? Rozhoduje to, jak kartu s narozeninami nadepsat. */
    fun closeTo(today: LocalDate): BirthdayWhen? {
        for (year in today.year - 1..today.year + 1) {
            when (inYear(year)) {
                today -> return BirthdayWhen.Today
                today.plus(DatePeriod(days = 1)) -> return BirthdayWhen.Tomorrow
                today.minus(DatePeriod(days = 1)) -> return BirthdayWhen.Yesterday
                else -> Unit
            }
        }
        return null
    }

    /** Kolik let slaví v narozeninách nejblíž [today] (dnes, zítra, včera); `null` bez roku. */
    fun turningOn(today: LocalDate): Int? {
        val year = year ?: return null
        val nearest = (today.year - 1..today.year + 1).minBy { kotlin.math.abs(inYear(it).toEpochDays() - today.toEpochDays()) }
        return (nearest - year).takeIf { it >= 0 }
    }

    companion object {
        /** Datum z pole výběru (den a měsíc vždy, rok jen když ho uživatel chce ukazovat). */
        fun of(date: LocalDate, withYear: Boolean): Birthdate =
            Birthdate(day = date.day, month = date.month.number, year = date.year.takeIf { withYear })
    }
}

enum class BirthdayWhen { Yesterday, Today, Tomorrow }

/** Kontakt, který měl nebo bude mít narozeniny (`updateContactCloseBirthdays`). */
data class CloseBirthday(
    val userId: Long,
    val name: String,
    val photoFileId: Int?,
    val birthdate: Birthdate,
)

private fun isLeapYear(year: Int): Boolean = (year % 4 == 0 && year % 100 != 0) || year % 400 == 0
