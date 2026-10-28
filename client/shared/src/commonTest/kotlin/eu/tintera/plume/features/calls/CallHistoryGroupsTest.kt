package eu.tintera.plume.features.calls

import eu.tintera.plume.core.telegram.CallHistoryEntry
import eu.tintera.plume.core.telegram.CallOutcome
import eu.tintera.plume.core.telegram.ChatKind
import eu.tintera.plume.core.telegram.ChatSummary
import kotlin.test.Test
import kotlin.test.assertEquals

class CallHistoryGroupsTest {

    private fun entry(chatId: Long, messageId: Long, date: Int, outcome: CallOutcome = CallOutcome.Missed, video: Boolean = false) =
        CallHistoryEntry(
            chat = ChatSummary(
                id = chatId,
                title = "Člověk $chatId",
                unreadCount = 0,
                lastMessagePreview = null,
                isPinned = false,
                order = chatId,
                avatarFileId = null,
                isMuted = false,
                isMarkedAsUnread = false,
                kind = ChatKind.Private,
                lastMessageTimestamp = null,
                presence = null,
                activity = null,
                draft = null,
            ),
            messageId = messageId,
            outcome = outcome,
            isVideo = video,
            durationSeconds = 0,
            isGroup = false,
            date = date,
            callUserId = chatId,
        )

    /** Poledne UTC: o pár minut sem a tam je to tentýž den v každém obvyklém pásmu. */
    private val noon = 1_700_049_600

    private fun rows(entries: List<CallHistoryEntry>) = groupCallHistory(entries).map { group -> group.messageIds }

    @Test
    fun stejneHovoryPoSobeJsouJedenRadek() {
        val entries = listOf(entry(1, 3, noon + 600), entry(1, 2, noon + 300), entry(1, 1, noon))
        assertEquals(listOf(listOf(3L, 2L, 1L)), rows(entries))
    }

    @Test
    fun jinyVysledekDruhNeboChatRaduPrerusi() {
        val entries = listOf(
            entry(1, 6, noon + 600),
            entry(1, 5, noon + 500, outcome = CallOutcome.Incoming),
            entry(1, 4, noon + 400),
            entry(1, 3, noon + 300, video = true),
            entry(2, 2, noon + 200),
            entry(1, 1, noon + 100),
        )
        assertEquals(listOf(listOf(6L), listOf(5L), listOf(4L), listOf(3L), listOf(2L), listOf(1L)), rows(entries))
    }

    @Test
    fun jinyDenJeNovyRadek() {
        val entries = listOf(entry(1, 2, noon), entry(1, 1, noon - 2 * 86_400))
        assertEquals(listOf(listOf(2L), listOf(1L)), rows(entries))
    }
}
