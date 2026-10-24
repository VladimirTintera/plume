package eu.tintera.plume.features.chats

import eu.tintera.plume.core.telegram.ChatKind
import eu.tintera.plume.core.telegram.ChatSummary
import kotlin.test.Test
import kotlin.test.assertEquals

/** Koho jde přidat do nové skupiny. */
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
    fun tajnyChatNeniClovek() {
        // Id tajného chatu není id uživatele; server by ho jako člena odmítl.
        val secret = chat(-2_000_000_000_001).copy(isSecret = true)
        assertEquals(listOf(1L), groupMemberCandidates(listOf(chat(1), secret), savedMessagesChatId = null).map { it.id })
    }

    @Test
    fun bezUlozenychZpravSeNicNevyrazuje() {
        assertEquals(listOf(1L), groupMemberCandidates(listOf(chat(1)), savedMessagesChatId = null).map { it.id })
    }
}
