package eu.tintera.plume.core.telegram

import kotlin.test.Test
import kotlin.test.assertEquals

/** Seznam témat — viz `ForumTopics.kt`. */
class ForumTopicsTest {

    private fun topic(id: Int, order: Long, name: String = "t$id", hidden: Boolean = false) = ForumTopic(
        chatId = 1,
        id = id,
        name = name,
        icon = TopicIcon(color = 0x6FB9F0, customEmojiId = null),
        isGeneral = id == 1,
        isClosed = false,
        isPinned = false,
        order = order,
        isHidden = hidden,
    )

    @Test
    fun radiSestupnePodlePoradiBezSkrytychAKazdeJednou() {
        val topics = listOf(topic(2, 10), topic(3, 30), topic(1, 20, hidden = true), topic(3, 30))

        assertEquals(listOf(3, 2), topics.sortedTopics().map { it.id })
    }

    @Test
    fun zmenaTemaNahradiAPosuneNahoru() {
        val topics = listOf(topic(2, 20), topic(3, 10)).sortedTopics()

        val changed = topics.withTopic(topic(3, 30, name = "nové"))

        assertEquals(listOf(3, 2), changed.map { it.id })
        assertEquals("nové", changed.first().name)
    }

    @Test
    fun zmenaNenactenehoTemaHoPrida() {
        val topics = listOf(topic(2, 20)).withTopic(topic(4, 5))

        assertEquals(listOf(2, 4), topics.map { it.id })
    }

    @Test
    fun skryteTemaZmizi() {
        val topics = listOf(topic(1, 20), topic(2, 10)).withTopic(topic(1, 20, hidden = true))

        assertEquals(listOf(2), topics.map { it.id })
    }

    @Test
    fun strankaNepriepiseTemaZmeneneUpdatem() {
        // Update dorazil dřív než stránka, která nese STARŠÍ verzi téhož tématu.
        val topics = listOf(topic(3, 50, name = "čerstvé"))

        val merged = topics.withPage(listOf(topic(3, 5, name = "staré"), topic(4, 4)))

        assertEquals(listOf(3, 4), merged.map { it.id })
        assertEquals("čerstvé", merged.first().name)
    }

    @Test
    fun smazaneTemaZmizi() {
        assertEquals(listOf(2), listOf(topic(2, 2), topic(3, 3)).withoutTopic(3).map { it.id })
    }
}
