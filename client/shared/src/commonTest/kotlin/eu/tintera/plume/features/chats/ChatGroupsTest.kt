package eu.tintera.plume.features.chats

import eu.tintera.plume.core.telegram.ChatKind
import eu.tintera.plume.core.telegram.ChatSponsor
import eu.tintera.plume.core.telegram.ChatSummary
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Seskupení seznamu chatů.
 *
 * Skupiny nesou zaoblení bloku, takže prázdná skupina by v seznamu nechala mezeru — a právě
 * hraniční kombinace (samé připnuté, samé odložené, prázdný seznam) se na zařízení poznají
 * nejhůř.
 */
class ChatGroupsTest {

    /** Uložené zprávy jsou v menu horní lišty; v seznamu jen připnuté. */
    @Test
    fun ulozeneZpravyJenPripnute() {
        val saved = 9L
        assertEquals(
            listOf(listOf(2L, 3L)),
            chatGroups(listOf(chat(2), chat(saved), chat(3)), savedMessagesChatId = saved).ids(),
        )
        assertEquals(
            listOf(listOf(saved), listOf(2L)),
            chatGroups(listOf(chat(saved, pinned = true), chat(2)), savedMessagesChatId = saved).ids(),
        )
    }

    /**
     * Vynořený odložený chat má vlastní blok nad připnutými — i připnutý;
     * ve dvou skupinách naráz být nesmí.
     */
    @Test
    fun vynoreneOdlozeneJsouNadPripnutymi() {
        val chats = listOf(chat(1, pinned = true), chat(2, unread = 3), chat(3), chat(4, pinned = true))

        assertEquals(
            listOf(listOf(2L, 4L), listOf(1L), listOf(3L)),
            chatGroups(chats, due = setOf(2L, 4L)).ids(),
        )
        // Odložený chat, který v seznamu není (jiná složka), blok nezaloží.
        assertEquals(
            listOf(listOf(1L, 4L), listOf(2L, 3L)),
            chatGroups(chats, due = setOf(99L)).ids(),
        )
    }

    /** Nepřečtené vlastní blok nemají: zůstávají mezi ostatními tam, kde je má TDLib. */
    @Test
    fun pripnuteAOstatniNeprecteneBezVlastnihoBloku() {
        val chats = listOf(chat(1, pinned = true, unread = 5), chat(2, unread = 3), chat(3), chat(4, unread = 1))

        val groups = chatGroups(chats)

        assertEquals(listOf(listOf(1L), listOf(2L, 3L, 4L)), groups.ids())
    }

    /** Prázdná skupina by v seznamu nechala mezeru po ničem. */
    @Test
    fun prazdneSkupinyVypadnou() {
        val chats = listOf(chat(1), chat(2))

        assertEquals(listOf(listOf(1L, 2L)), chatGroups(chats).ids())
    }

    @Test
    fun prazdnySeznamNemaSkupiny() {
        assertEquals(emptyList<List<Long>>(), chatGroups(emptyList()).ids())
    }

    /** Uvnitř skupiny se nic nepřeskládává — pořadí je to od TDLib. */
    @Test
    fun poradiUvnitrSkupinyZustava() {
        val chats = listOf(chat(3, unread = 1), chat(1, unread = 2), chat(2, unread = 1))

        val groups = chatGroups(chats)

        assertEquals(listOf(listOf(3L, 1L, 2L)), groups.ids())
    }

    private fun List<List<ChatSummary>>.ids(): List<List<Long>> = map { group -> group.map { it.id } }

    @Test
    fun sponzorovanyChatJeNahoreZvlastNadPripnutymi() {
        val sponsored = chat(4).copy(sponsor = ChatSponsor.Proxy)
        val pinned = chat(1, pinned = true)
        val rest = chat(2)
        assertEquals(
            listOf(listOf(sponsored), listOf(pinned), listOf(rest)),
            chatGroups(listOf(pinned, sponsored, rest)),
        )
    }

    private fun chat(
        id: Long,
        unread: Int = 0,
        pinned: Boolean = false,
        kind: ChatKind = ChatKind.Private,
    ) = ChatSummary(
        id = id,
        title = "Chat $id",
        unreadCount = unread,
        lastMessagePreview = null,
        isPinned = pinned,
        order = id,
        avatarFileId = null,
        isMuted = false,
        isMarkedAsUnread = false,
        kind = kind,
        lastMessageTimestamp = null,
        presence = null,
        activity = null,
        draft = null,
    )
}
