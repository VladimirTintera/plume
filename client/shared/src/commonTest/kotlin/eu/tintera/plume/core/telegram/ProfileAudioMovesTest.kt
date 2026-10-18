package eu.tintera.plume.core.telegram

import kotlin.test.Test
import kotlin.test.assertEquals

class ProfileAudioMovesTest {

    /** Přehraje tahy na [current] stejně jako server a vrátí výsledné pořadí. */
    private fun apply(current: List<Int>, moves: List<Pair<Int, Int>>): List<Int> {
        val order = current.toMutableList()
        moves.forEach { (fileId, after) ->
            order.remove(fileId)
            order.add(if (after == 0) 0 else order.indexOf(after) + 1, fileId)
        }
        return order
    }

    @Test
    fun stejnePoradiNepotrebujeZadnyTah() {
        assertEquals(emptyList(), profileAudioMoves(listOf(1, 2, 3), listOf(1, 2, 3)))
    }

    @Test
    fun posledniNaZacatekJeJedenTah() {
        val moves = profileAudioMoves(listOf(1, 2, 3, 4), listOf(4, 1, 2, 3))
        assertEquals(listOf(4 to 0), moves)
    }

    @Test
    fun prvniNaKonecVychaziSpravne() {
        val current = listOf(1, 2, 3, 4)
        val target = listOf(2, 3, 4, 1)
        val moves = profileAudioMoves(current, target)
        assertEquals(listOf(1 to 4), moves)
        assertEquals(target, apply(current, moves))
    }

    @Test
    fun obraceniPoradiVychaziSpravne() {
        val current = listOf(1, 2, 3, 4, 5)
        val target = current.reversed()
        assertEquals(target, apply(current, profileAudioMoves(current, target)))
    }

    @Test
    fun zamichanePoradiVychaziSpravne() {
        val current = (1..8).toList()
        val target = listOf(5, 1, 8, 2, 3, 7, 4, 6)
        assertEquals(target, apply(current, profileAudioMoves(current, target)))
    }
}
