package eu.tintera.plume.features.chats

import eu.tintera.plume.core.telegram.ChatKind
import eu.tintera.plume.core.telegram.ChatSponsor
import eu.tintera.plume.core.telegram.ChatSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Uspořádání připnutých chatů. `setPinnedChats` bere seznam jako NOVÝ STAV připnutých, takže
 * chyba v tom, co se odešle, by chaty potichu odepnula nebo připnula.
 */
class PinnedReorderTest {

    @Test
    fun pripnuteVPoradiOdTdlibBezSponzorovaneho() {
        val chats = listOf(
            chat(1).copy(sponsor = ChatSponsor.Proxy),
            chat(2, pinned = true),
            chat(3),
            chat(4, pinned = true),
        )
        assertEquals(listOf(2L, 4L), pinnedChats(chats).map { it.id })
    }

    @Test
    fun preskladatJdeAzOdDvouPripnutych() {
        assertFalse(canReorderPinned(listOf(chat(1, pinned = true), chat(2))))
        assertTrue(canReorderPinned(listOf(chat(1, pinned = true), chat(2, pinned = true))))
    }

    @Test
    fun presunNaMistoJinehoChatu() {
        val chats = listOf(chat(1), chat(2), chat(3))
        assertEquals(listOf(3L, 1L, 2L), chats.movedChat(3, 1).map { it.id })
        assertEquals(listOf(2L, 3L, 1L), chats.movedChat(1, 3).map { it.id })
        // Neznámé id (chat mezitím zmizel) seznam nechá být.
        assertEquals(listOf(1L, 2L, 3L), chats.movedChat(9, 1).map { it.id })
    }

    @Test
    fun ulozeniSrovnaPoradiSTimCoJePripnuteTed() {
        // Beze změn jinde se odešle přesně složené pořadí.
        assertEquals(listOf(3L, 1L, 2L), pinnedOrderToSave(listOf(3, 1, 2), current = listOf(1, 2, 3)))
        // Chat mezitím odepnutý jinde se znovu nepřipne.
        assertEquals(listOf(3L, 1L), pinnedOrderToSave(listOf(3, 1, 2), current = listOf(1, 3)))
        // Chat mezitím připnutý jinde neodepne — zůstane nahoře, kam ho dal server.
        assertEquals(listOf(4L, 3L, 1L, 2L), pinnedOrderToSave(listOf(3, 1, 2), current = listOf(4, 1, 2, 3)))
    }

    private fun chat(id: Long, pinned: Boolean = false) = ChatSummary(
        id = id,
        title = "Chat $id",
        unreadCount = 0,
        lastMessagePreview = null,
        isPinned = pinned,
        order = id,
        avatarFileId = null,
        isMuted = false,
        isMarkedAsUnread = false,
        kind = ChatKind.Private,
        lastMessageTimestamp = null,
        presence = null,
        activity = null,
        draft = null,
    )
}
