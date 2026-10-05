package eu.tintera.plume.core.telegram

import kotlin.test.Test
import kotlin.test.assertEquals

class SavedTopicsTest {

    private fun topic(id: Long, order: Long) = SavedTopic(id = id, kind = SavedTopicKind.FromChat(id), order = order)

    @Test
    fun nulovePoradiSeVyradi() {
        val shown = listOf(topic(1, 10), topic(2, 0), topic(3, 30)).shownSavedTopics()
        assertEquals(listOf(3L, 1L), shown.map { it.id })
    }

    @Test
    fun stejnePoradiRozhodneId() {
        val shown = listOf(topic(1, 5), topic(2, 5)).shownSavedTopics()
        assertEquals(listOf(2L, 1L), shown.map { it.id })
    }
}
