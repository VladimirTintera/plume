package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.ai.TranscriptLine
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.SendState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Které zprávy jdou do shrnutí a jak se podepíšou. */
class ChatSummaryTest {

    private fun message(
        id: Long,
        outgoing: Boolean = false,
        text: String = "zprava$id",
        sender: String? = "Jana",
        service: Boolean = false,
    ) = ChatMessage(
        id = id,
        isOutgoing = outgoing,
        senderName = sender,
        senderAvatarFileId = null,
        senderId = 1L,
        text = text,
        entities = emptyList(),
        media = null,
        linkPreview = null,
        reactions = emptyList(),
        sendState = SendState.Sent,
        replyToMessageId = null,
        isService = service,
        timestamp = id.toInt(),
        isEdited = false,
    )

    /** Stav konverzace drží zprávy od nejnovější. */
    private fun newestFirst(vararg ids: Long) = ids.sortedDescending().map { message(it) }

    @Test
    fun nepreceteneMajiPrednost() {
        val source = summarySource(newestFirst(1, 2, 3, 4, 5), unreadBoundaryId = 3)
        assertTrue(source.unread)
        assertEquals(listOf(4L, 5L), source.messages.map { it.id })
    }

    @Test
    fun bezNeprectenychPosledniZpravyChronologicky() {
        val source = summarySource(newestFirst(1, 2, 3), unreadBoundaryId = null)
        assertFalse(source.unread)
        assertEquals(listOf(1L, 2L, 3L), source.messages.map { it.id })
    }

    @Test
    fun zaHraniciJenVlastniZpravyNejsouNeprectene() {
        val messages = listOf(message(5, outgoing = true), message(4), message(3))
        val source = summarySource(messages, unreadBoundaryId = 4)
        assertFalse(source.unread)
        assertEquals(listOf(3L, 4L, 5L), source.messages.map { it.id })
    }

    @Test
    fun servisniOznameniSeVynechaji() {
        val messages = listOf(message(3), message(2, service = true), message(1))
        assertEquals(listOf(1L, 3L), summarySource(messages, null).messages.map { it.id })
    }

    @Test
    fun beruSeJenPosledniZpravy() {
        val messages = (1L..(SUMMARY_RECENT_MESSAGES + 10L)).reversed().map { message(it) }
        val source = summarySource(messages, null)
        assertEquals(SUMMARY_RECENT_MESSAGES, source.messages.size)
        assertEquals(SUMMARY_RECENT_MESSAGES + 10L, source.messages.last().id)
    }

    @Test
    fun podpisyVPrepisu() {
        val lines = transcriptLines(
            listOf(
                message(1, sender = null),
                message(2, outgoing = true),
                message(3, text = " "),
            ),
            chatTitle = "Petr",
            me = "Já",
        )
        assertEquals(listOf(TranscriptLine("Petr", "zprava1"), TranscriptLine("Já", "zprava2")), lines)
    }
}
