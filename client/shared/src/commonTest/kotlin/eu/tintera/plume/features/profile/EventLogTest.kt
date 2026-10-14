package eu.tintera.plume.features.profile

import kotlin.test.Test
import kotlin.test.assertEquals

class EventLogTest {

    /** Dny jsou souvislé úseky — stejný den oddělený jiným by byl dva bloky, ne jeden. */
    @Test
    fun souvisleUsekySeSkladajiPoSousedech() {
        assertEquals(
            listOf(listOf(1, 1), listOf(2), listOf(1)),
            listOf(1, 1, 2, 1).groupConsecutive { a, b -> a == b },
        )
        assertEquals(emptyList(), emptyList<Int>().groupConsecutive { a, b -> a == b })
    }
}
