package eu.tintera.plume.features.chats

import eu.tintera.plume.core.telegram.ChatKind
import eu.tintera.plume.core.telegram.ChatSummary
import eu.tintera.plume.core.telegram.newChatAutoDelete
import kotlin.test.Test
import kotlin.test.assertEquals

/** Koho jde přidat do nové skupiny a s jakou dobou mazání vznikne. */
class CreateChatTest {

    private fun chat(id: Long, kind: ChatKind = ChatKind.Private) = ChatSummary(
        id = id,
        title = "Chat $id",
        unreadCount = 0,
        lastMessagePreview = null,
        isPinned = false,
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

    @Test
    fun doSkupinyJdouJenLideABezUlozenychZprav() {
        val chats = listOf(chat(1), chat(2, ChatKind.Group), chat(3, ChatKind.Channel), chat(9), chat(4))
        assertEquals(listOf(1L, 4L), groupMemberCandidates(chats, savedMessagesChatId = 9).map { it.id })
    }

    @Test
    fun bezUlozenychZpravSeNicNevyrazuje() {
        assertEquals(listOf(1L), groupMemberCandidates(listOf(chat(1)), savedMessagesChatId = null).map { it.id })
    }

    @Test
    fun vychoziDobaMazaniProjdeJenVCelychDnechDoRoku() {
        assertEquals(0, newChatAutoDelete(null))
        assertEquals(0, newChatAutoDelete(0))
        assertEquals(86_400, newChatAutoDelete(86_400))
        assertEquals(31 * 86_400, newChatAutoDelete(31 * 86_400))
        // Hodina by u supergroupy neprošla a s ní ani celé založení.
        assertEquals(0, newChatAutoDelete(3_600))
        assertEquals(0, newChatAutoDelete(366 * 86_400))
        assertEquals(0, newChatAutoDelete(-86_400))
    }
}
