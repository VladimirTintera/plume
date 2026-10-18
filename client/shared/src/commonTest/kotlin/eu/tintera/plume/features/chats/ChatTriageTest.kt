package eu.tintera.plume.features.chats

import eu.tintera.plume.core.telegram.ChatKind
import eu.tintera.plume.core.telegram.ChatSponsor
import eu.tintera.plume.core.telegram.ChatSummary
import eu.tintera.plume.core.telegram.Verification
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Které chaty triáž vůbec posuzuje ([isTriageCandidate]). */
class ChatTriageTest {

    private fun chat(
        id: Long = 1,
        kind: ChatKind = ChatKind.Private,
        unread: Int = 2,
        lastMessageId: Long? = 10,
        outgoing: Boolean = false,
        muted: Boolean = false,
    ) = ChatSummary(
        id = id,
        title = "Jana",
        unreadCount = unread,
        lastMessagePreview = null,
        isPinned = false,
        order = id,
        avatarFileId = null,
        isMuted = muted,
        isMarkedAsUnread = false,
        kind = kind,
        lastMessageTimestamp = 0,
        lastMessageId = lastMessageId,
        lastMessageOutgoing = outgoing,
        presence = null,
        activity = null,
        draft = null,
    )

    @Test
    fun kartaJenSeSouhlasnouPosledniZpravou() {
        val jana = chat(id = 1, lastMessageId = 10)
        val petr = chat(id = 2, lastMessageId = 20)
        val read = chat(id = 3, unread = 0, lastMessageId = 30)
        // Petr mezitím poslal další zprávu (21), posudek patří té staré.
        val awaiting = mapOf(1L to 10L, 2L to 21L, 3L to 30L)
        assertEquals(listOf(jana), awaitingReplyChats(listOf(jana, petr, read), awaiting, emptySet()))
    }

    @Test
    fun schovanaKartaSeVratiSNovymChatem() {
        val jana = chat(id = 1, lastMessageId = 10)
        val petr = chat(id = 2, lastMessageId = 20)
        val hidden = setOf(1L to 10L)
        assertEquals(emptyList(), awaitingReplyChats(listOf(jana), mapOf(1L to 10L), hidden))
        assertEquals(
            listOf(jana, petr),
            awaitingReplyChats(listOf(jana, petr), mapOf(1L to 10L, 2L to 20L), hidden),
        )
    }

    @Test
    fun nepreceteneSoukromeSPrichoziZpravou() {
        assertTrue(isTriageCandidate(chat(), savedMessagesChatId = 99))
    }

    @Test
    fun skupinaJenSeZminkouITlumena() {
        assertFalse(isTriageCandidate(chat(kind = ChatKind.Group), null))
        assertTrue(isTriageCandidate(chat(kind = ChatKind.Group).copy(unreadMentionCount = 1), null))
        // Zmínky Telegram hlásí i ze ztlumené skupiny; poslední zpráva ve skupině bývá vlastní
        // a na posudku zmínky to nic nemění.
        assertTrue(isTriageCandidate(chat(kind = ChatKind.Group, muted = true, outgoing = true).copy(unreadMentionCount = 2), null))
    }

    @Test
    fun kanalyAPrecteneNe() {
        assertFalse(isTriageCandidate(chat(kind = ChatKind.Channel).copy(unreadMentionCount = 1), null))
        assertFalse(isTriageCandidate(chat(unread = 0), null))
        assertFalse(isTriageCandidate(chat(lastMessageId = null), null))
    }

    @Test
    fun posledniVlastniZpravaUlozeneZtlumeneAOvereneNe() {
        assertFalse(isTriageCandidate(chat(outgoing = true), null))
        assertFalse(isTriageCandidate(chat(id = 99), savedMessagesChatId = 99))
        assertFalse(isTriageCandidate(chat(muted = true), null))
        assertFalse(isTriageCandidate(chat().copy(verification = Verification.Verified()), null))
        assertFalse(isTriageCandidate(chat().copy(sponsor = ChatSponsor.Proxy), null))
    }
}
