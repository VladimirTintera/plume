package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.telegram.ChatMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MediaTimestampTest {

    private fun message(id: Long, replyTo: Long? = null, timestamped: Boolean = false) = ChatMessage(
        id = id,
        isOutgoing = false,
        senderName = null,
        senderAvatarFileId = null,
        senderId = 1,
        text = "od 0:02",
        entities = emptyList(),
        media = null,
        linkPreview = null,
        reactions = emptyList(),
        sendState = null,
        replyToMessageId = replyTo,
        isService = false,
        timestamp = 0,
        isEdited = false,
        mediaAlbumId = 0,
        hasTimestampedMedia = timestamped,
    )

    @Test
    fun casVPopiskuMiriNaTutezZpravu() {
        val video = message(1, replyTo = 9, timestamped = true)
        // I když zpráva na něco odpovídá — rozhoduje `hasTimestampedMedia`.
        assertEquals(video, timestampTarget(video, listOf(video, message(9)), emptyMap()))
    }

    @Test
    fun casVOdpovediMiriNaCitovanouZpravu() {
        val video = message(1)
        val reply = message(2, replyTo = 1)
        assertEquals(video, timestampTarget(reply, listOf(video, reply), emptyMap()))
    }

    @Test
    fun citovanaMimoHistoriiSeBereZDotazenychCitaci() {
        val old = message(1)
        val reply = message(2, replyTo = 1)
        assertEquals(old, timestampTarget(reply, listOf(reply), mapOf(1L to old)))
    }

    @Test
    fun bezMediaNeniKamSkocit() {
        assertNull(timestampTarget(message(2), listOf(message(2)), emptyMap()))
        assertNull(timestampTarget(message(2, replyTo = 1), listOf(message(2, replyTo = 1)), emptyMap()))
    }

    @Test
    fun youtubeDostaneCasParametrem() {
        assertEquals(
            "https://youtube.com/watch?v=dQw4w9WgXcQ&t=43s",
            videoLinkAt("https://youtube.com/watch?v=dQw4w9WgXcQ", 43),
        )
        assertEquals("https://youtu.be/dQw4w9WgXcQ?t=90s", videoLinkAt("https://youtu.be/dQw4w9WgXcQ", 90))
        assertEquals(
            "https://m.youtube.com/watch?v=x&t=5s#komentare",
            videoLinkAt("https://m.youtube.com/watch?v=x#komentare", 5),
        )
    }

    @Test
    fun existujiciCasSePrepise() {
        assertEquals(
            "https://www.youtube.com/watch?v=x&list=y&t=10s",
            videoLinkAt("https://www.youtube.com/watch?v=x&t=99s&list=y", 10),
        )
    }

    @Test
    fun vimeoDostaneCasKotvou() {
        assertEquals("https://vimeo.com/123#t=7s", videoLinkAt("https://vimeo.com/123#t=1s", 7))
    }

    @Test
    fun jinyWebZustaneBezCasu() {
        assertEquals("https://example.com/video?id=1", videoLinkAt("https://example.com/video?id=1", 7))
        // Doména, která youtube.com jen obsahuje, YouTube není.
        assertEquals("https://notyoutube.com/v", videoLinkAt("https://notyoutube.com/v", 7))
    }
}
