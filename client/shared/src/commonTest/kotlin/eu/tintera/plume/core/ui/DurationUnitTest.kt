package eu.tintera.plume.core.ui

import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.relative_in_days
import eu.tintera.plume.resources.relative_in_hours
import eu.tintera.plume.resources.relative_in_minutes
import kotlin.test.Test
import kotlin.test.assertEquals

/** Délka v největší celé jednotce — sdílí ji doba ankety i automatické mazání. */
class DurationUnitTest {

    private val day = 24 * 60 * 60

    @Test
    fun nejvetsiCelaJednotka() {
        assertEquals(DurationUnit.Days to 1, durationUnit(day))
        assertEquals(DurationUnit.Weeks to 1, durationUnit(7 * day))
        // Měsíc je 31 dní, jak ho Telegram posílá u automatického mazání.
        assertEquals(DurationUnit.Months to 1, durationUnit(31 * day))
        assertEquals(DurationUnit.Hours to 8, durationUnit(8 * 3600))
        assertEquals(DurationUnit.Minutes to 5, durationUnit(5 * 60))
    }

    @Test
    fun necelaJednotkaSpadneNizsi() {
        // 30 dní není měsíc ani celé týdny.
        assertEquals(DurationUnit.Days to 30, durationUnit(30 * day))
        assertEquals(DurationUnit.Days to 2, durationUnit(2 * day))
        assertEquals(DurationUnit.Minutes to 90, durationUnit(90 * 60))
    }

    @Test
    fun podMinutuJeMinuta() {
        assertEquals(DurationUnit.Minutes to 1, durationUnit(20))
    }

    @Test
    fun cekaniSeZaokrouhlujeNahoru() {
        // 6 dní a 23 hodin je „za 7 dní" — dolů by slibovalo dřív, než to půjde.
        assertEquals(UiText(Res.plurals.relative_in_days, 7, 7), waitUiText(6 * 86_400 + 23 * 3_600))
        assertEquals(UiText(Res.plurals.relative_in_days, 1, 1), waitUiText(86_400))
        assertEquals(UiText(Res.plurals.relative_in_hours, 2, 2), waitUiText(3_600 + 1))
        assertEquals(UiText(Res.plurals.relative_in_minutes, 1, 1), waitUiText(5))
    }
}
