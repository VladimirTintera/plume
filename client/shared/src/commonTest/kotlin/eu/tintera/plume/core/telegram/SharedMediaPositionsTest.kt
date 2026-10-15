package eu.tintera.plume.core.telegram

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SharedMediaPositionsTest {

    /** Body po deseti zprávách: pozice 0, 10, 20, 30; id klesají se stářím. */
    private val positions = SharedMediaPositions(
        totalCount = 35,
        positions = listOf(
            MediaPosition(0, messageId = 4000, timestamp = 400),
            MediaPosition(10, messageId = 3000, timestamp = 300),
            MediaPosition(20, messageId = 2000, timestamp = 200),
            MediaPosition(30, messageId = 1000, timestamp = 100),
        ),
    )

    @Test
    fun nejblizsiBod() {
        assertEquals(10, positions.nearest(12)?.position)
        assertEquals(30, positions.nearest(34)?.position)
        assertEquals(0, positions.nearest(0)?.position)
        assertNull(SharedMediaPositions(0, emptyList()).nearest(5))
    }

    @Test
    fun poziceSeOdhadneMeziBody() {
        assertEquals(15, positions.estimatePosition(2500))
        assertEquals(20, positions.estimatePosition(2000))
    }

    @Test
    fun mimoBodyStojiNaKraji() {
        // Novější než první bod: na začátku. Starší než poslední: na posledním bodu.
        assertEquals(0, positions.estimatePosition(5000))
        assertEquals(30, positions.estimatePosition(500))
        assertNull(SharedMediaPositions(0, emptyList()).estimatePosition(1))
    }
}
