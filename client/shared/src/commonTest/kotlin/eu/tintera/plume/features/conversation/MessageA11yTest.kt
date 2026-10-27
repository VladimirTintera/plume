package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.features.conversation.items.a11ySender
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MessageA11yTest {

    private fun message(senderName: String?, isOutgoing: Boolean = false) = ChatMessage(
        id = 1,
        isOutgoing = isOutgoing,
        senderName = senderName,
        senderAvatarFileId = null,
        senderId = 2L,
        text = "Ahoj",
        entities = emptyList(),
        media = null,
        linkPreview = null,
        reactions = emptyList(),
        sendState = null,
        replyToMessageId = null,
        isService = false,
        timestamp = 0,
        isEdited = false,
    )

    @Test
    fun navazujiciZpravaVeSkupineRekneJmeno() {
        // Jméno v bublině nese jen první zpráva bloku; čtečka ho u dalších jinak nemá odkud vzít.
        assertEquals("Jana", a11ySender(message("Jana"), continuesAbove = true))
    }

    @Test
    fun prvniZpravaBlokuJmenoNeopakuje() {
        // To je v bublině vidět a čte se samo — podruhé by zaznělo „Jana, Jana".
        assertNull(a11ySender(message("Jana"), continuesAbove = false))
    }

    @Test
    fun vlastniZpravaASoukromyChatBezJmena() {
        assertNull(a11ySender(message("Já", isOutgoing = true), continuesAbove = true))
        assertNull(a11ySender(message(null), continuesAbove = true))
    }
}
