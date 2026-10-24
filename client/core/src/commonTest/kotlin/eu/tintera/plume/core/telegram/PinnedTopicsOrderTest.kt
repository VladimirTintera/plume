package eu.tintera.plume.core.telegram

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PinnedTopicsOrderTest {

    private fun topic(id: Int, order: Long, pinned: Boolean) = ForumTopic(
        chatId = -100,
        id = id,
        name = "Téma $id",
        icon = TopicIcon(color = TOPIC_COLORS.first(), customEmojiId = null),
        isGeneral = false,
        isClosed = false,
        isPinned = pinned,
        order = order,
    )

    private val manager = TopicRights(canManage = true)

    /** Pořadí je to, které seznam ukazuje: podle `order` sestupně, jen připnutá. */
    @Test
    fun pripnutaVPoradiSeznamu() {
        val topics = listOf(topic(1, 10, false), topic(2, 30, true), topic(3, 40, true), topic(4, 20, true))
        assertEquals(listOf(3, 2, 4), topics.pinnedTopics().map { it.id })
    }

    /**
     * `order` od TDLib pořadí připnutých nenese (po `setPinnedForumTopics` se nemění), takže
     * rozhoduje pořadí od serveru; nově připnuté (v pořadí ještě není) jde nahoru.
     */
    @Test
    fun pripnutaPodlePoradiOdServeruNePodleOrder() {
        val topics = listOf(topic(1, 10, true), topic(7, 90, true), topic(5, 50, false), topic(9, 20, true))
        assertEquals(listOf(9, 1, 7, 5), topics.sortedTopics(pinnedOrder = listOf(1, 7)).map { it.id })
        // Bez známého pořadí zůstává `order`.
        assertEquals(listOf(7, 9, 1, 5), topics.sortedTopics().map { it.id })
    }

    @Test
    fun presunDoluIPosunNahoru() {
        val pinned = listOf(topic(3, 40, true), topic(2, 30, true), topic(4, 20, true))
        assertEquals(listOf(2, 4, 3), pinned.moved(fromId = 3, toId = 4).map { it.id })
        assertEquals(listOf(4, 3, 2), pinned.moved(fromId = 4, toId = 3).map { it.id })
    }

    /** Tah skončil nad tématem, které mezitím zmizelo: seznam zůstane. */
    @Test
    fun neznameIdNechaPoradi() {
        val pinned = listOf(topic(3, 40, true), topic(2, 30, true))
        assertEquals(pinned, pinned.moved(fromId = 3, toId = 99))
    }

    @Test
    fun usporadatJenSPravemAAspoinDvema() {
        val two = listOf(topic(1, 20, true), topic(2, 10, true))
        val one = listOf(topic(1, 20, true), topic(2, 10, false))
        assertTrue(canReorderPinned(two, manager))
        assertFalse(canReorderPinned(one, manager))
        assertFalse(canReorderPinned(two, TopicRights(canCreate = true)))
    }
}
