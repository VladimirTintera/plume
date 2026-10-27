package eu.tintera.plume.features.media

import kotlin.test.Test
import kotlin.test.assertEquals

class WheelPagingTest {

    @Test
    fun zubKoleckaListujeHned() {
        assertEquals(1, WheelPaging().onScroll(1f, 0))
        assertEquals(-1, WheelPaging().onScroll(-1f, 0))
    }

    /** Pomalé otáčení po zubech: každý zub je nové gesto a přelistuje. */
    @Test
    fun zubySPrestavkouListujiKazdy() {
        val paging = WheelPaging()
        assertEquals(1, paging.onScroll(1f, 0))
        assertEquals(1, paging.onScroll(1f, 500))
        assertEquals(-1, paging.onScroll(-1f, 1_000))
    }

    /** Roztočené kolečko listuje dál, ale ne rychleji než jednou za odstup. */
    @Test
    fun roztoceneKoleckoListujeSOdstupem() {
        val paging = WheelPaging()
        val steps = (0..10).map { paging.onScroll(1f, it * 50L) }
        // Zuby po 50 ms: stránka v 0, 150, 300 a 450 ms.
        assertEquals(listOf(1, 0, 0, 1, 0, 0, 1, 0, 0, 1, 0), steps)
    }

    /** Touchpad posílá zlomky: listuje se až po nasčítání zubu, a v jednom tahu jen jednou. */
    @Test
    fun touchpadListujeJednouZaTah() {
        val paging = WheelPaging()
        val steps = (0 until 40).map { paging.onScroll(0.3f, it * 16L) }
        assertEquals(1, steps.count { it != 0 })
        assertEquals(1, steps[3])
    }

    /** Dojezd touchpadu patří k tahu; nový tah po přestávce přelistuje znovu. */
    @Test
    fun novyTahPoPrestavce() {
        val paging = WheelPaging()
        repeat(5) { paging.onScroll(0.4f, it * 16L) }
        assertEquals(0, paging.onScroll(0.4f, 250))
        assertEquals(0, paging.onScroll(0.4f, 600))
        assertEquals(0, paging.onScroll(0.4f, 616))
        assertEquals(1, paging.onScroll(0.4f, 632))
    }

    /** Drobný pohyb oběma směry se vyruší a nepřelistuje nic. */
    @Test
    fun drobnyPohybNic() {
        val paging = WheelPaging()
        assertEquals(0, paging.onScroll(0.6f, 0))
        assertEquals(0, paging.onScroll(-0.6f, 16))
        assertEquals(0, paging.onScroll(0.6f, 32))
    }
}
