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

    private fun chat(id: Long, sponsor: ChatSponsor? = null) = ChatSummary(
        id = id,
        title = "Chat $id",
        unreadCount = 0,
        lastMessagePreview = null,
        isPinned = false,
        order = id,
        avatarFileId = null,
        isMuted = false,
        isMarkedAsUnread = false,
        kind = ChatKind.Private,
        lastMessageTimestamp = null,
        presence = null,
        activity = null,
        draft = null,
        sponsor = sponsor,
    )
}
