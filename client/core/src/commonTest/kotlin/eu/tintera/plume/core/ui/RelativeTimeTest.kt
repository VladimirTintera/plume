package eu.tintera.plume.core.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class RelativeTimeTest {

    @Test
    fun zaokrouhlujeSeNeorezava() {
        assertEquals(RelativeTime(2, RelativeUnit.Hours, future = false), relativeTime(-(2 * 3600L - 40)))
        assertEquals(RelativeTime(2, RelativeUnit.Minutes, future = true), relativeTime(100))
    }

    @Test
    fun celaVyssiJednotkaPrejdeDoNi() {
        assertEquals(RelativeTime(1, RelativeUnit.Hours, future = true), relativeTime(3580))
        assertEquals(RelativeTime(1, RelativeUnit.Days, future = false), relativeTime(-(24 * 3600L - 600)))
    }

    @Test
    fun podMinutouSekundy() {
        assertEquals(RelativeTime(45, RelativeUnit.Seconds, future = false), relativeTime(-45))
        assertEquals(RelativeTime(0, RelativeUnit.Seconds, future = false), relativeTime(0))
    }

    @Test
    fun dlouheRozdilyVMesicichARocich() {
        assertEquals(RelativeTime(3, RelativeUnit.Months, future = true), relativeTime(90 * 86400L))
        assertEquals(RelativeTime(2, RelativeUnit.Years, future = false), relativeTime(-2 * 365 * 86400L))
    }
}
