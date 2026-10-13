package eu.tintera.plume.core.telegram

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Datum narození: věk, přestupný den a „dnes / zítra / včera" i přes přelom roku. */
class BirthdateTest {

    @Test
    fun vekPribydeAzVDenNarozenin() {
        val birthdate = Birthdate(day = 13, month = 10, year = 1990)
        assertEquals(35, birthdate.ageOn(LocalDate(2026, 10, 12)))
        assertEquals(36, birthdate.ageOn(LocalDate(2026, 10, 13)))
        assertNull(Birthdate(day = 13, month = 10, year = null).ageOn(LocalDate(2026, 10, 13)))
        // Rok v budoucnosti je překlep, ne záporný věk.
        assertNull(Birthdate(day = 1, month = 1, year = 2030).ageOn(LocalDate(2026, 10, 13)))
    }

    @Test
    fun narozeni29UnoraSlaviVNeprestupnemRoce28() {
        val leap = Birthdate(day = 29, month = 2, year = 2000)
        assertEquals(LocalDate(2026, 2, 28), leap.inYear(2026))
        assertEquals(LocalDate(2028, 2, 29), leap.inYear(2028))
        assertEquals(BirthdayWhen.Today, leap.closeTo(LocalDate(2026, 2, 28)))
        assertEquals(26, leap.ageOn(LocalDate(2026, 2, 28)))
    }

    @Test
    fun dnesZitraVceraIPresNovyRok() {
        val newYearsEve = Birthdate(day = 31, month = 12, year = 1990)
        assertEquals(BirthdayWhen.Yesterday, newYearsEve.closeTo(LocalDate(2027, 1, 1)))
        assertEquals(BirthdayWhen.Today, newYearsEve.closeTo(LocalDate(2026, 12, 31)))
        assertEquals(BirthdayWhen.Tomorrow, newYearsEve.closeTo(LocalDate(2026, 12, 30)))
        assertNull(newYearsEve.closeTo(LocalDate(2026, 12, 28)))

        val newYear = Birthdate(day = 1, month = 1, year = null)
        assertEquals(BirthdayWhen.Tomorrow, newYear.closeTo(LocalDate(2026, 12, 31)))
    }

    @Test
    fun slaviPocetLetNejblizsichNarozenin() {
        val birthdate = Birthdate(day = 31, month = 12, year = 1990)
        // Zítra (31. 12. 2026) slaví 36, včera (z 1. 1. 2027) slavil taky 36.
        assertEquals(36, birthdate.turningOn(LocalDate(2026, 12, 30)))
        assertEquals(36, birthdate.turningOn(LocalDate(2027, 1, 1)))
        assertNull(Birthdate(day = 31, month = 12, year = null).turningOn(LocalDate(2027, 1, 1)))
    }

    @Test
    fun datumZKalendareBezRokuRokZahodi() {
        assertEquals(Birthdate(13, 10, null), Birthdate.of(LocalDate(2026, 10, 13), withYear = false))
        assertEquals(Birthdate(13, 10, 1990), Birthdate.of(LocalDate(1990, 10, 13), withYear = true))
    }
}
