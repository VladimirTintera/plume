package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.telegram.ChatKind
import eu.tintera.plume.core.telegram.ChatSponsor
import eu.tintera.plume.core.telegram.ChatSummary
import kotlin.test.Test
import kotlin.test.assertEquals

/** Výběr chatů pro rychlý přepínač v konverzaci. */
class ChatSwitcherTest {

    /** Naposledy otevřené napřed, pak hlavní seznam; otevřený chat ani dvakrát týž ne. */
    @Test
    fun historiePakSeznam() {
        val chats = listOf(chat(1), chat(2), chat(3), chat(4))
        assertEquals(
            listOf(3L, 4L, 2L),
            switcherChats(recent = listOf(1, 3, 4), chats = chats, archived = emptyList(), currentChatId = 1, savedMessagesChatId = null, max = 3).map { it.id },
        )
    }

    /** Bez historie (po startu) vršek seznamu. */
    @Test
    fun bezHistorie() {
        val chats = listOf(chat(1), chat(2), chat(3))
        assertEquals(
            listOf(2L, 3L),
            switcherChats(recent = emptyList(), chats = chats, archived = emptyList(), currentChatId = 1, savedMessagesChatId = null).map { it.id },
        )
    }

    /** Archivovaný chat jen z historie, neznámé id se přeskočí. */
    @Test
    fun archivJenZHistorie() {
        val result = switcherChats(
            recent = listOf(99, 7),
            chats = listOf(chat(1)),
            archived = listOf(chat(7), chat(8)),
            currentChatId = 0,
            savedMessagesChatId = null,
        )
        assertEquals(listOf(7L, 1L), result.map { it.id })
    }

    /** Sponzorovaný chat nikdy, Uložené zprávy ze seznamu jen připnuté, z historie vždy. */
    @Test
    fun sponzorovanyAUlozene() {
        val chats = listOf(chat(1, sponsor = ChatSponsor.Proxy), chat(9), chat(2))
        assertEquals(
            listOf(2L),
            switcherChats(recent = listOf(1), chats = chats, archived = emptyList(), currentChatId = 0, savedMessagesChatId = 9).map { it.id },
        )
        assertEquals(
            listOf(9L, 2L),
            switcherChats(recent = listOf(9), chats = chats, archived = emptyList(), currentChatId = 0, savedMessagesChatId = 9).map { it.id },
        )
    }

    /**
     * Nepřečtený chat hluboko v seznamu se do nabídky vejde (slibuje ho odznak na šipce), hned za
     * posledním otevřeným; ztlumený nepřečtený přednost nemá.
     */
    @Test
    fun neprecteneZaPoslednim() {
        val chats = (1L..10L).map { chat(it, unread = if (it == 9L) 2 else 0, muted = false) } +
            chat(11, unread = 4, muted = true)
        assertEquals(
            listOf(5L, 9L, 4L, 2L),
            switcherChats(recent = listOf(1, 5, 4), chats = chats, archived = emptyList(), currentChatId = 1, savedMessagesChatId = null, max = 4).map { it.id },
        )
    }

    /** Otevřený nepřečtený chat se z počtu na šipce odečte, ztlumený ani přečtený ne. */
    @Test
    fun neprecteneJinde() {
        val chats = listOf(chat(1, unread = 3), chat(2, unread = 1, muted = true), chat(3, markedUnread = true), chat(4))
        assertEquals(2, unreadElsewhere(unmutedUnreadChats = 3, chats = chats, currentChatId = 1))
        assertEquals(2, unreadElsewhere(unmutedUnreadChats = 3, chats = chats, currentChatId = 3))
        assertEquals(3, unreadElsewhere(unmutedUnreadChats = 3, chats = chats, currentChatId = 2))
        assertEquals(3, unreadElsewhere(unmutedUnreadChats = 3, chats = chats, currentChatId = 4))
        // Chat mimo hlavní seznam (archiv, odkaz) v počtu TDLib není.
        assertEquals(3, unreadElsewhere(unmutedUnreadChats = 3, chats = chats, currentChatId = 99))
        // Počet od TDLib ještě nedorazil: odznak nesmí jít do minusu.
        assertEquals(0, unreadElsewhere(unmutedUnreadChats = 0, chats = chats, currentChatId = 1))
    }

    private fun chat(
        id: Long,
        sponsor: ChatSponsor? = null,
        unread: Int = 0,
        muted: Boolean = false,
        markedUnread: Boolean = false,
    ) = ChatSummary(
        id = id,
        title = "Chat $id",
        unreadCount = unread,
        lastMessagePreview = null,
        isPinned = false,
        order = id,
        avatarFileId = null,
        isMuted = muted,
        isMarkedAsUnread = markedUnread,
        kind = ChatKind.Private,
        lastMessageTimestamp = null,
        presence = null,
        activity = null,
        draft = null,
        sponsor = sponsor,
    )
}
