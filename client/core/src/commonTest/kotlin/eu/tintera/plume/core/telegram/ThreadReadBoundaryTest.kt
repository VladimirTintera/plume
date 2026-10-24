package eu.tintera.plume.core.telegram

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ThreadReadBoundaryTest {
    private val root = 100L

    private fun message(id: Long, outgoing: Boolean = false) = ChatMessage(
        id = id,
        isOutgoing = outgoing,
        senderName = null,
        senderAvatarFileId = null,
        senderId = 7L,
        text = "komentář",
        entities = emptyList(),
        media = null,
        linkPreview = null,
        reactions = emptyList(),
        sendState = null,
        replyToMessageId = null,
        isService = false,
        timestamp = 1,
        isEdited = false,
    )

    /** Od nejnovějšího, jako historie. */
    private val comments = listOf(message(150), message(140), message(130), message(120), message(110))

    @Test
    fun hraniceJeTesnePodNtymNejnovejsimKomentarem() {
        assertEquals(129L, threadReadBoundary(comments, unreadCount = 3, reachedStart = false, rootMessageId = root))
    }

    @Test
    fun vlastniKomentareSeNepocitaji() {
        val withOwn = listOf(message(150), message(145, outgoing = true), message(140), message(130))
        assertEquals(139L, threadReadBoundary(withOwn, unreadCount = 2, reachedStart = false, rootMessageId = root))
    }

    @Test
    fun kdyzNactenePocetNepokryjiNeniHraniceZnama() {
        assertNull(threadReadBoundary(comments, unreadCount = 8, reachedStart = false, rootMessageId = root))
    }

    @Test
    fun naZacatkuVlaknaJsouNeprecteneVsechnyAHraniciJeKoren() {
        assertEquals(root, threadReadBoundary(comments, unreadCount = 8, reachedStart = true, rootMessageId = root))
    }

    @Test
    fun korenSeMeziKomentareNepocita() {
        assertEquals(root, threadReadBoundary(comments + message(root), unreadCount = 6, reachedStart = false, rootMessageId = root))
    }

    @Test
    fun bezNeprectenychNeniHranice() {
        assertNull(threadReadBoundary(comments, unreadCount = 0, reachedStart = true, rootMessageId = root))
    }
}
