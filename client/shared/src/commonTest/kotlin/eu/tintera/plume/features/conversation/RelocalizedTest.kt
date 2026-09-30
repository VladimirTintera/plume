package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.telegram.ChatMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/** Dosazení zpráv převedených znovu po změně jazyka ([withRelocalized]). */
class RelocalizedTest {

    private fun message(id: Long, text: String) = ChatMessage(
        id = id,
        isOutgoing = false,
        senderName = null,
        senderAvatarFileId = null,
        senderId = 1L,
        text = text,
        entities = emptyList(),
        media = null,
        linkPreview = null,
        reactions = emptyList(),
        sendState = null,
        replyToMessageId = null,
        isService = true,
        timestamp = 1,
        isEdited = false,
        mediaAlbumId = 0L,
    )

    @Test
    fun nezmeneneZpravySeNahradi() {
        val created = message(2, "Group created")
        val photo = message(1, "Photo")
        val snapshot = ConversationState(messages = listOf(created, photo), quotes = mapOf(9L to message(9, "Photo")))

        val result = snapshot.withRelocalized(
            snapshot,
            reloaded = listOf(message(2, "Skupina vytvořena"), message(1, "Fotka"), message(9, "Fotka")).associateBy { it.id },
        )

        assertEquals(listOf("Skupina vytvořena", "Fotka"), result.messages.map { it.text })
        assertEquals("Fotka", result.quotes.getValue(9).text)
    }

    @Test
    fun zmenaBehemPrevoduVyhrava() {
        val original = message(1, "Photo")
        val snapshot = ConversationState(messages = listOf(original))
        // Během převodu dorazila úprava — ta je novější než převod.
        val edited = message(1, "Photo").copy(isEdited = true)
        val current = snapshot.copy(messages = listOf(edited))

        val result = current.withRelocalized(snapshot, reloaded = mapOf(1L to message(1, "Fotka")))

        assertSame(edited, result.messages.single())
    }

    @Test
    fun smazanaZpravaSeNevrati() {
        val snapshot = ConversationState(messages = listOf(message(2, "a"), message(1, "b")))
        val current = snapshot.copy(messages = snapshot.messages.take(1))

        val result = current.withRelocalized(snapshot, reloaded = mapOf(1L to message(1, "B"), 2L to message(2, "A")))

        assertEquals(listOf(2L), result.messages.map { it.id })
        assertEquals("A", result.messages.single().text)
    }
}
