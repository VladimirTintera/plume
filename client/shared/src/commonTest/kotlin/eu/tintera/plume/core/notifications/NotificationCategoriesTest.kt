package eu.tintera.plume.core.notifications

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NotificationCategoriesTest {

    @Test
    fun kazdaKombinaceMaVlastniKategorii() {
        val ids = NotificationCategories.all.map { it.first }
        assertEquals(4, ids.toSet().size)
        ids.forEach { id -> assertEquals(1, ids.count { it == id }) }
    }

    @Test
    fun akceOdpovidajiPravum() {
        val actions = NotificationCategories.all.toMap()
        assertEquals(
            listOf(NotificationCategories.ACTION_REPLY, NotificationCategories.ACTION_MARK_READ, NotificationCategories.ACTION_MUTE),
            actions.getValue(NotificationCategories.chatCategory(canReply = true, canMute = true)),
        )
        // Kanál bez práva psát, zmínka: jen přečíst.
        assertEquals(
            listOf(NotificationCategories.ACTION_MARK_READ),
            actions.getValue(NotificationCategories.chatCategory(canReply = false, canMute = false)),
        )
    }

    @Test
    fun nejvysTriAkce() {
        // iOS ukáže u notifikace nejvýš čtyři akce, Android tři — drží se menší z mezí.
        NotificationCategories.all.forEach { (_, actions) -> assertTrue(actions.size <= 3) }
    }
}
