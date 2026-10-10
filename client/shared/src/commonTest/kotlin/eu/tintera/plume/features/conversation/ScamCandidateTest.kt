package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.MessageEntity
import eu.tintera.plume.core.telegram.MessageEntityType
import eu.tintera.plume.core.telegram.SendState
import eu.tintera.plume.core.telegram.Verification
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Předfiltr, který rozhoduje, kdy se model ptá na podvod. */
class ScamCandidateTest {
    private fun message(
        text: String,
        outgoing: Boolean = false,
        entities: List<MessageEntity> = emptyList(),
        verification: Verification? = null,
    ) = ChatMessage(
        id = 1L,
        isOutgoing = outgoing,
        senderName = "Jana",
        senderAvatarFileId = null,
        senderId = 1L,
        text = text,
        entities = entities,
        media = null,
        linkPreview = null,
        reactions = emptyList(),
        sendState = SendState.Sent,
        replyToMessageId = null,
        isService = false,
        timestamp = 1,
        isEdited = false,
        senderVerification = verification,
    )

    @Test
    fun typickyPodvodJeKandidat() {
        assertTrue(isScamCandidate(message("Váš účet bude zablokován, ověřte se na https://telegrarn.org/login")))
        assertTrue(isScamCandidate(message("Gratulujeme, vyhrál jste dárek zdarma!")))
        assertTrue(isScamCandidate(message("Your parcel is waiting, pay the delivery fee: www.example.com")))
        // Odkaz jako entita (popisek bez adresy v textu) a jedno slovo.
        val link = MessageEntity(0, 6, MessageEntityType.Link("https://example.com"))
        assertTrue(isScamCandidate(message("Ověřit zde", entities = listOf(link))))
    }

    @Test
    fun beznaZpravaNeni() {
        assertFalse(isScamCandidate(message("V kolik přijdeš?")))
        assertFalse(isScamCandidate(message("Posílám článek https://example.com/clanek")))
        // Jedno slovo bez odkazu nestačí.
        assertFalse(isScamCandidate(message("Kód od garáže je na lednici")))
        // Celá slova: „won" nechytí „wonderful".
        assertFalse(isScamCandidate(message("What a wonderful day https://example.com")))
    }

    @Test
    fun vlastniAOverenaSeNeposuzuji() {
        val scam = "Váš účet bude zablokován, ověřte heslo"
        assertFalse(isScamCandidate(message(scam, outgoing = true)))
        assertFalse(isScamCandidate(message(scam, verification = Verification.Verified())))
        assertTrue(isScamCandidate(message(scam, verification = Verification.Scam)))
    }
}
