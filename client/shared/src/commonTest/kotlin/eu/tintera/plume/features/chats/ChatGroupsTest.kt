package eu.tintera.plume.features.chats

import eu.tintera.plume.core.telegram.ChatSummary
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Seskupení seznamu chatů.
 *
 * Skupiny nesou zaoblení bloku, takže prázdná skupina by v seznamu nechala mezeru — a právě
 * hraniční kombinace (samé připnuté, žádné nepřečtené, prázdný seznam) se na zařízení poznají
 * nejhůř.
 */
class ChatGroupsTest {

    @Test
    fun bezVycleneniJsouPripnuteAOstatni() {
        val chats = listOf(chat(1, pinned = true), chat(2, unread = 3), chat(3))

        val groups = chatGroups(chats, separateUnread = false)

        assertEquals(listOf(listOf(1L), listOf(2L, 3L)), groups.ids())
    }

    @Test
    fun sVyclenenimJsouTriSkupiny() {
        val chats = listOf(chat(1, pinned = true), chat(2, unread = 3), chat(3))

        val groups = chatGroups(chats, separateUnread = true)

        assertEquals(listOf(listOf(1L), listOf(2L), listOf(3L)), groups.ids())
    }

    /** Připnutý chat patří mezi připnuté, i když je nepřečtený — jinak by byl ve dvou skupinách. */
    @Test
    fun pripnuteNepadajiMeziNeprectene() {
        val chats = listOf(chat(1, pinned = true, unread = 5), chat(2, unread = 1))

        val groups = chatGroups(chats, separateUnread = true)

        assertEquals(listOf(listOf(1L), listOf(2L)), groups.ids())
    }

    /** Prázdná skupina by v seznamu nechala mezeru po ničem. */
    @Test
    fun prazdneSkupinyVypadnou() {
        val chats = listOf(chat(1), chat(2))

        assertEquals(listOf(listOf(1L, 2L)), chatGroups(chats, separateUnread = true).ids())
        assertEquals(listOf(listOf(1L, 2L)), chatGroups(chats, separateUnread = false).ids())
    }

    @Test
    fun prazdnySeznamNemaSkupiny() {
        assertEquals(emptyList<List<Long>>(), chatGroups(emptyList(), separateUnread = true).ids())
    }

    /** Uvnitř skupiny se nic nepřeskládává — pořadí je to od TDLib. */
    @Test
    fun poradiUvnitrSkupinyZustava() {
        val chats = listOf(chat(3, unread = 1), chat(1, unread = 2), chat(2, unread = 1))

        val groups = chatGroups(chats, separateUnread = true)

        assertEquals(listOf(listOf(3L, 1L, 2L)), groups.ids())
    }

    private fun List<List<ChatSummary>>.ids(): List<List<Long>> = map { group -> group.map { it.id } }

    private fun chat(id: Long, unread: Int = 0, pinned: Boolean = false) = ChatSummary(
        id = id,
        title = "Chat $id",
        unreadCount = unread,
        lastMessagePreview = null,
        isPinned = pinned,
        order = id,
        avatarFileId = null,
        isMuted = false,
        isMarkedAsUnread = false,
        isPrivate = true,
        lastMessageTimestamp = null,
        presence = null,
        activity = null,
        draft = null,
    )
}
