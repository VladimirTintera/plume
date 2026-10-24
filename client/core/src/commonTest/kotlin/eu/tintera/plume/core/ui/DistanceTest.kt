package eu.tintera.plume.core.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class DistanceTest {

    @Test
    fun podKilometremMetry() {
        assertEquals(Distance(0, null, kilometers = false), distanceOf(0))
        assertEquals(Distance(120, null, kilometers = false), distanceOf(120))
        assertEquals(Distance(999, null, kilometers = false), distanceOf(999))
        // Záporné číslo od serveru nedává smysl, nesmí z něj vyjít „-5 m".
        assertEquals(Distance(0, null, kilometers = false), distanceOf(-5))
    }

    @Test
    fun doDesetiKilometruJednoDesetinneMisto() {
        assertEquals(Distance(1, null, kilometers = true), distanceOf(1_000))
        assertEquals(Distance(1, 2, kilometers = true), distanceOf(1_234))
        // Zaokrouhluje se, neořezává: 1 960 m je „2 km", ne „1,9 km".
        assertEquals(Distance(2, null, kilometers = true), distanceOf(1_960))
        assertEquals(Distance(9, 9, kilometers = true), distanceOf(9_940))
    }

    @Test
    fun odDesetiKilometruCelaCisla() {
        // 9 960 m by se zaokrouhlilo na „10,0 km" — patří už mezi celé kilometry.
        assertEquals(Distance(10, null, kilometers = true), distanceOf(9_960))
        assertEquals(Distance(12, null, kilometers = true), distanceOf(12_400))
        assertEquals(Distance(100, null, kilometers = true), distanceOf(100_000))
    }
}
