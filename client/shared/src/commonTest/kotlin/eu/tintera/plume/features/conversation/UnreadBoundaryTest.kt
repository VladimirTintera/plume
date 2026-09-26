package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.telegram.ChatMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** První nepřečtená je nejstarší PŘÍCHOZÍ zpráva za hranicí — vlastní odpovědi se přeskakují. */
class UnreadBoundaryTest {

    private fun message(id: Long, isOutgoing: Boolean = false) = ChatMessage(
        id = id,
        isOutgoing = isOutgoing,
        senderName = null,
        senderAvatarFileId = null,
        senderId = 1L,
        text = "zpráva $id",
        entities = emptyList(),
        media = null,
        linkPreview = null,
        reactions = emptyList(),
        sendState = null,
        replyToMessageId = null,
        isService = false,
        timestamp = 0,
        isEdited = false,
        mediaAlbumId = 0L,
    )

    @Test
    fun nejstarsiPrichoziZaHranici() {
        // Od nejnovější, jako v konverzaci.
        val messages = listOf(message(4), message(3), message(2, isOutgoing = true), message(1))
        assertEquals(3L, firstUnreadId(messages, boundary = 1L))
    }

    @Test
    fun samaVlastniZaHraniciNeniNeprectena() {
        assertNull(firstUnreadId(listOf(message(2, isOutgoing = true), message(1)), boundary = 1L))
    }

    @Test
    fun historieKHraniciNesahaNeboHraniceNeni() {
        // Nejstarší načtená nemusí být první nepřečtená, když hranice je hlouběji.
        assertNull(firstUnreadId(listOf(message(3), message(2)), boundary = 1L))
        assertNull(firstUnreadId(listOf(message(3), message(2)), boundary = null))
    }
}
