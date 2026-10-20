package eu.tintera.plume.core.telegram

import kotlin.test.Test
import kotlin.test.assertEquals

class BusinessTest {

    private fun at(day: Int, hour: Int, minute: Int = 0) = day * MINUTES_PER_DAY + hour * 60 + minute

    /** Po–pá 9–17. */
    private val workdays = (0..4).map { OpeningInterval(at(it, 9), at(it, 17)) }

    @Test
    fun otevrenoDoKonceUseku() {
        assertEquals(OpeningState.Open(closesAt = 17 * 60), workdays.stateAt(at(2, 10)))
    }

    @Test
    fun predOtevrenimOteviraDnes() {
        assertEquals(OpeningState.Closed(opensAt = 9 * 60, inDays = 0, dayIndex = 2), workdays.stateAt(at(2, 7)))
    }

    @Test
    fun vPatekVecerOteviraVPondeli() {
        assertEquals(OpeningState.Closed(opensAt = 9 * 60, inDays = 3, dayIndex = 0), workdays.stateAt(at(4, 18)))
    }

    @Test
    fun vNedeliOteviraZitra() {
        assertEquals(OpeningState.Closed(opensAt = 9 * 60, inDays = 1, dayIndex = 0), workdays.stateAt(at(6, 12)))
    }

    @Test
    fun usekPresPulnocSeSpoji() {
        // So 20:00–24:00 a ne 0:00–2:00: v sobotu ve 23 je otevřeno až do 2:00.
        val night = listOf(OpeningInterval(at(5, 20), at(6, 0)), OpeningInterval(at(6, 0), at(6, 2)))
        assertEquals(OpeningState.Open(closesAt = 2 * 60), night.stateAt(at(5, 23)))
    }

    @Test
    fun nonstop() {
        val always = (0..6).map { OpeningInterval(at(it, 0), at(it + 1, 0)) }
        assertEquals(OpeningState.AlwaysOpen, always.stateAt(at(3, 3)))
    }

    @Test
    fun bezOteviraciDobyNikdyNeotevira() {
        assertEquals(OpeningState.Closed(opensAt = null), emptyList<OpeningInterval>().stateAt(at(0, 10)))
    }

    @Test
    fun poDnechSPauzou() {
        val split = listOf(OpeningInterval(at(1, 8), at(1, 12)), OpeningInterval(at(1, 13), at(1, 17)))
        val days = split.byDay()
        assertEquals(listOf(8 * 60 until 12 * 60, 13 * 60 until 17 * 60), days[1])
        assertEquals(emptyList(), days[0])
    }
}
