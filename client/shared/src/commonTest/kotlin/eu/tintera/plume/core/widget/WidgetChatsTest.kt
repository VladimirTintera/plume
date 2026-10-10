package eu.tintera.plume.core.widget

import eu.tintera.plume.core.telegram.ChatKind
import eu.tintera.plume.core.telegram.ChatSponsor
import eu.tintera.plume.core.telegram.ChatSummary
import kotlin.test.Test
import kotlin.test.assertEquals

/** Výběr chatů do widgetu na ploše. */
class WidgetChatsTest {

    /** Připnuté a nepřečtené jdou napřed, zbytek v pořadí od TDLib, dokud je místo. */
    @Test
    fun dulezitePrvni() {
        val chats = listOf(chat(1), chat(2, unread = 3), chat(3, pinned = true), chat(4), chat(5, unread = 1, muted = true))
        assertEquals(listOf(2L, 3L, 1L, 4L, 5L), widgetChats(chats, savedMessagesChatId = null).map { it.id })
        assertEquals(listOf(2L, 3L), widgetChats(chats, savedMessagesChatId = null, max = 2).map { it.id })
    }

    /** Sponzorovaný chat ne, Uložené zprávy jen připnuté — jako v seznamu chatů. */
    @Test
    fun sponzorovanyAUlozene() {
        val chats = listOf(chat(1, sponsor = ChatSponsor.Proxy), chat(9), chat(2))
        assertEquals(listOf(2L), widgetChats(chats, savedMessagesChatId = 9).map { it.id })
        assertEquals(listOf(9L, 2L), widgetChats(listOf(chat(9, pinned = true), chat(2)), savedMessagesChatId = 9).map { it.id })
    }

    /** Odznak ve widgetu nepočítá ztlumené chaty. */
    @Test
    fun nepreciteneBezZtlumenych() {
        val snapshot = WidgetSnapshot(
            loggedIn = true,
            chats = listOf(
                WidgetChat(1, "A", null, unread = 3, muted = false, timestamp = null),
                WidgetChat(2, "B", null, unread = 50, muted = true, timestamp = null),
            ),
        )
        assertEquals(3, snapshot.unread)
    }

    private fun chat(
        id: Long,
        unread: Int = 0,
        pinned: Boolean = false,
        muted: Boolean = false,
        sponsor: ChatSponsor? = null,
    ) = ChatSummary(
        id = id,
        title = "Chat $id",
        unreadCount = unread,
        lastMessagePreview = null,
        isPinned = pinned,
        order = id,
        avatarFileId = null,
        isMuted = muted,
        isMarkedAsUnread = false,
        kind = ChatKind.Private,
        lastMessageTimestamp = null,
        presence = null,
        activity = null,
        draft = null,
        sponsor = sponsor,
    )
}
