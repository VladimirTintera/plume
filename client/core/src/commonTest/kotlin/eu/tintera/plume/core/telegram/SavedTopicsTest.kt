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

    @Test
    fun pripnutaVPoradiSeznamu() {
        val topics = listOf(topic(1, 30).copy(isPinned = true), topic(2, 20), topic(3, 10).copy(isPinned = true))
        assertEquals(listOf(1L, 3L), topics.pinnedSavedTopics().map { it.id })
    }

    @Test
    fun presunNaMistoJinehoTematu() {
        val topics = listOf(topic(1, 3), topic(2, 2), topic(3, 1))
        assertEquals(listOf(2L, 3L, 1L), topics.movedSavedTopic(1, 3).map { it.id })
        // Neznámé id (téma mezitím zmizelo) seznam nechá.
        assertEquals(listOf(1L, 2L, 3L), topics.movedSavedTopic(1, 99).map { it.id })
    }

    @Test
    fun ukladanePoradiRespektujeZmenyJinde() {
        // Skládalo se 1, 2, 3; mezitím jinde odepnuli 2 a připnuli 4.
        assertEquals(listOf(4L, 3L, 1L), savedPinnedOrderToSave(reordered = listOf(3, 2, 1), current = listOf(4, 1, 3)))
    }
}
