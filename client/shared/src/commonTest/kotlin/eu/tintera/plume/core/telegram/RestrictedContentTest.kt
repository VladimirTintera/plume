package eu.tintera.plume.core.telegram

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RestrictedContentTest {

    private val message = ChatMessage(
        id = 5,
        isOutgoing = false,
        senderName = "Kanál",
        senderAvatarFileId = null,
        senderId = 1L,
        text = "Tajný obsah https://example.com",
        entities = listOf(MessageEntity(offset = 11, length = 19, type = MessageEntityType.Link("https://example.com"))),
        media = MessageMedia.Photo(fileId = 1, thumbnailFileId = 2, fullFileId = 3, width = 10, height = 10),
        linkPreview = LinkPreview(
            url = "https://example.com",
            siteName = null,
            title = "Titulek",
            description = null,
            photo = null,
            largePhoto = false,
            showAboveText = false,
        ),
        reactions = listOf(MessageReaction(emoji = "👍", count = 3, isChosen = false)),
        sendState = null,
        replyToMessageId = null,
        isService = false,
        timestamp = 100,
        isEdited = false,
    )

    @Test
    fun obsahNahradiDuvodBezPrilohAEntit() {
        val restricted = message.restricted("Tahle zpráva tu není dostupná.")
        assertEquals("Tahle zpráva tu není dostupná.", restricted.text)
        assertTrue(restricted.entities.isEmpty())
        assertNull(restricted.media)
        assertNull(restricted.linkPreview)
        assertTrue(restricted.isRestricted)
    }

    @Test
    fun odesilatelCasAReakceZustavaji() {
        val restricted = message.restricted("Nedostupné.")
        assertEquals(message.senderName, restricted.senderName)
        assertEquals(message.timestamp, restricted.timestamp)
        assertEquals(message.reactions, restricted.reactions)
        assertFalse(message.isRestricted)
    }
}
