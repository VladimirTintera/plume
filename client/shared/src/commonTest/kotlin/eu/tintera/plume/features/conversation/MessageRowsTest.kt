package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.SendState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Skládání alb do položek konverzace. Zprávy jdou jako v konverzaci: od nejnovější. */
class MessageRowsTest {

    private fun message(
        id: Long,
        album: Long = 0L,
        text: String = "",
        timestamp: Int = id.toInt(),
        sendState: SendState? = null,
    ) = ChatMessage(
        id = id,
        isOutgoing = sendState != null,
        senderName = null,
        senderAvatarFileId = null,
        senderId = 1L,
        text = text,
        entities = emptyList(),
        media = null,
        linkPreview = null,
        reactions = emptyList(),
        sendState = sendState,
        replyToMessageId = null,
        isService = false,
        timestamp = timestamp,
        isEdited = false,
        mediaAlbumId = album,
    )

    @Test
    fun samostatneZpravyZustavajiSamostatne() {
        val rows = groupAlbums(listOf(message(3), message(2), message(1)))
        assertEquals(listOf(1, 1, 1), rows.map { it.messages.size })
    }

    @Test
    fun albumJeJednaPolozka() {
        val rows = groupAlbums(listOf(message(5), message(4, album = 9), message(3, album = 9), message(2, album = 9), message(1)))
        assertEquals(listOf(1, 3, 1), rows.map { it.messages.size })
        val album = rows[1]
        assertTrue(album.isAlbum)
        // Mřížka jde od nejstarší.
        assertEquals(listOf(2L, 3L, 4L), album.members.map { it.id })
    }

    @Test
    fun dveSousedniAlbaSeNeslevaji() {
        val rows = groupAlbums(listOf(message(4, album = 2), message(3, album = 2), message(2, album = 1), message(1, album = 1)))
        assertEquals(listOf(2, 2), rows.map { it.messages.size })
    }

    @Test
    fun albumPreruseneJinouZpravouSeRozdeli() {
        val rows = groupAlbums(listOf(message(3, album = 7), message(2), message(1, album = 7)))
        assertEquals(3, rows.size)
    }

    @Test
    fun hlavniZpravaAlbaJeTaSPopiskem() {
        val row = groupAlbums(listOf(message(3, album = 1), message(2, album = 1, text = "Výlet"), message(1, album = 1))).single()
        assertEquals(2L, row.primary.id)
        assertEquals("Výlet", row.display.text)
        // Čas bubliny je čas nejnovější fotky.
        assertEquals(3, row.display.timestamp)
    }

    @Test
    fun bezPopiskuJeHlavniNejstarsi() {
        val row = groupAlbums(listOf(message(2, album = 1), message(1, album = 1))).single()
        assertEquals(1L, row.primary.id)
    }

    @Test
    fun albumJeOdeslaneAzCele() {
        val row = groupAlbums(
            listOf(message(2, album = 1, sendState = SendState.Sending), message(1, album = 1, sendState = SendState.Sent)),
        ).single()
        assertEquals(SendState.Sending, row.display.sendState)
    }

    @Test
    fun rozlozeniMrizky() {
        assertEquals(listOf(2), albumRowSizes(2))
        assertEquals(listOf(1, 2), albumRowSizes(3))
        assertEquals(listOf(2, 2, 3), albumRowSizes(7))
        (1..10).forEach { count -> assertEquals(count, albumRowSizes(count).sum()) }
    }
}
