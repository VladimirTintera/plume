package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.ChatReplyMarkup
import eu.tintera.plume.core.telegram.ReplyKeyboardButton
import eu.tintera.plume.core.telegram.ReplyKeyboardButtonKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReplyMarkupTest {
    private fun message(id: Long) = ChatMessage(
        id = id,
        isOutgoing = false,
        senderName = null,
        senderAvatarFileId = null,
        senderId = 7L,
        text = "Jak se jmenuje projekt?",
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

    private val keyboard = ChatReplyMarkup.Keyboard(
        messageId = 10,
        placeholder = "Vyber akci",
        rows = listOf(listOf(ReplyKeyboardButton("Nápověda", ReplyKeyboardButtonKind.Text))),
        oneTime = false,
    )

    @Test
    fun napovedaKlavesniceJeVzdycky() {
        assertEquals("Vyber akci", ConversationState(replyMarkup = keyboard).inputPlaceholder)
    }

    @Test
    fun prazdnaNapovedaZnamenaVychozi() {
        assertNull(ConversationState(replyMarkup = keyboard.copy(placeholder = "")).inputPlaceholder)
        assertNull(ConversationState().inputPlaceholder)
    }

    @Test
    fun vynucenaOdpovedJenKdyzSeOdpovidaNaTuZpravu() {
        val force = ChatReplyMarkup.ForceReply(messageId = 20, placeholder = "Název projektu")
        assertEquals(
            "Název projektu",
            ConversationState(replyMarkup = force, replyingTo = message(20)).inputPlaceholder,
        )
        // Uživatel odpověď zrušil nebo odpovídá na něco jiného — nápověda by mluvila o něčem jiném.
        assertNull(ConversationState(replyMarkup = force).inputPlaceholder)
        assertNull(ConversationState(replyMarkup = force, replyingTo = message(21)).inputPlaceholder)
    }
}
