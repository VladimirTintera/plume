package eu.tintera.plume.core.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Kdy dotyk nad pruhem reakcí reakci dá a kdy je to jen kus jiného pohybu. */
class StrayTapGuardTest {

    private val slop = 16f

    @Test
    fun klidneTuknutiPoChvilceProjde() {
        assertFalse(isStrayTap(sinceShown = 2.seconds, travel = 3f, slop = slop, pointers = 1))
    }

    @Test
    fun tuknutiHnedPoObjeveniPruhuNeplati() {
        // Druhé ťuknutí dvojitého ťuknutí na fotku, pruh mezitím vyskočil pod prstem.
        assertTrue(isStrayTap(sinceShown = 200.milliseconds, travel = 0f, slop = slop, pointers = 1))
    }

    @Test
    fun tahPresReakciNeniTuknuti() {
        // Posun uvnitř tlačítka `clickable` sám nezruší — přesně tudy srdíčko padalo.
        assertTrue(isStrayTap(sinceShown = 2.seconds, travel = 20f, slop = slop, pointers = 1))
    }

    @Test
    fun viceprstuNeniTuknuti() {
        assertTrue(isStrayTap(sinceShown = 2.seconds, travel = 0f, slop = slop, pointers = 2))
    }
}
