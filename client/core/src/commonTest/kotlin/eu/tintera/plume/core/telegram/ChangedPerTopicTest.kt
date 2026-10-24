package eu.tintera.plume.core.telegram

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ChangedPerTopicTest {

    private data class Update(val topicId: Int, val unread: Int)

    /** Ozvěna vlastního čtení (stejný update znovu) neprojde; změna a jiné téma ano. */
    @Test
    fun stejnyUpdateTehozTematuNeprojde() = runTest {
        val updates = flowOf(
            Update(1, 0),
            Update(1, 0),
            Update(7, 0),
            Update(1, 3),
            Update(7, 0),
            Update(1, 3),
            Update(1, 0),
        )
        assertEquals(
            listOf(Update(1, 0), Update(7, 0), Update(1, 3), Update(1, 0)),
            updates.changedPerTopic { it.topicId }.toList(),
        )
    }
}
