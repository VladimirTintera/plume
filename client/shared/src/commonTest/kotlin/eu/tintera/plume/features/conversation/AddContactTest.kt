package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.MessageMedia
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Přidání kontaktu z karty: tentýž člověk bývá sdílený víckrát a po přidání nemá žádná jeho
 * karta dál nabízet „Přidat do kontaktů" — ani ta, na kterou se neťuklo.
 */
class AddContactTest {

    private fun message(id: Long, media: MessageMedia?) = ChatMessage(
        id = id,
        isOutgoing = false,
        senderName = null,
        senderAvatarFileId = null,
        senderId = 1L,
        text = "",
        entities = emptyList(),
        media = media,
        linkPreview = null,
        reactions = emptyList(),
        sendState = null,
        replyToMessageId = null,
        isService = false,
        timestamp = 1,
        isEdited = false,
    )

    private fun contact(userId: Long?) =
        MessageMedia.Contact(name = "Jana", phone = "+420111222333", userId = userId, canAddToContacts = userId != null)

    private fun ConversationState.addable() =
        messages.map { (it.media as? MessageMedia.Contact)?.canAddToContacts }

    @Test
    fun pridaniZhasneVsechnyKartyTehozCloveka() {
        val state = ConversationState(
            messages = listOf(
                message(3, contact(7)),
                message(2, contact(8)),
                message(1, contact(7)),
                message(0, null),
            ),
        )

        val added = state.withContactAddable(userId = 7, addable = false)
        assertEquals(listOf(false, true, false, null), added.addable())

        // „Vrátit" je zase rozsvítí, jinou kartu nechá být.
        assertEquals(listOf(true, true, true, null), added.withContactAddable(userId = 7, addable = true).addable())
    }

    @Test
    fun kontaktBezTelegramuSeNemeni() {
        val state = ConversationState(messages = listOf(message(1, contact(null))))
        assertEquals(listOf(false), state.withContactAddable(userId = 7, addable = true).addable())
    }
}
