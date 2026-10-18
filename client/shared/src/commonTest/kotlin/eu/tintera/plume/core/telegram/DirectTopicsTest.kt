package eu.tintera.plume.core.telegram

import kotlin.test.Test
import kotlin.test.assertEquals

class DirectTopicsTest {

    private fun topic(id: Long, order: Long) = DirectTopic(chatId = 1, id = id, senderId = id, title = "T$id", order = order)

    @Test
    fun vlaknaSeRadiPodlePoradiANuloveSeNeukazuje() {
        val shown = listOf(topic(1, order = 5), topic(2, order = 0), topic(3, order = 9), topic(4, order = 5))
            .shownDirectTopics()
        // Vyšší pořadí nahoře; při shodě novější vlákno (vyšší id) dřív, prázdné (0) ven.
        assertEquals(listOf(3L, 4L, 1L), shown.map { it.id })
    }
}
