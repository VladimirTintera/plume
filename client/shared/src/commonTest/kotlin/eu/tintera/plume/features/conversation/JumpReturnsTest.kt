package eu.tintera.plume.features.conversation

import kotlin.test.Test
import kotlin.test.assertEquals

class JumpReturnsTest {

    @Test
    fun retezeneSkokySeVraciOdPosledniho() {
        // Citace ve zprávě 50 vedla na 30, citace v ní na 10: návrat napřed k 30, pak k 50.
        assertEquals(listOf(50L, 30L), emptyList<Long>().withReturnPoint(50).withReturnPoint(30))
    }

    @Test
    fun opakovanySkokZTeZeZpravyJiPresuneNahoru() {
        assertEquals(listOf(30L, 50L), listOf(50L, 30L).withReturnPoint(50))
    }

    @Test
    fun zasobnikMaStrop() {
        val full = (1L..25L).fold(emptyList<Long>()) { points, id -> points.withReturnPoint(id) }
        assertEquals(20, full.size)
        assertEquals(25L, full.last())
        assertEquals(6L, full.first())
    }

    @Test
    fun videnaNeboMinutaZpravaZmizi() {
        // Vidět je zpráva 30 (a starší): k 30 se vracet nemá, k 50 pořád.
        assertEquals(listOf(50L), listOf(50L, 30L).notPassed(newestVisibleId = 30))
        assertEquals(emptyList(), listOf(50L, 30L).notPassed(newestVisibleId = 60))
    }

    @Test
    fun cilSkokuStarsiNezZdrojBodNezrusi() {
        // Po doskočení na citovanou zprávu 10 je vidět nejvýš 12; zdroj 50 zůstává.
        assertEquals(listOf(50L), listOf(50L).notPassed(newestVisibleId = 12))
    }
}
