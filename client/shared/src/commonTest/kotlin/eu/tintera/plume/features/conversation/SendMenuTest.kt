package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.telegram.ChatInfo
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.Presence
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SendMenuTest {

    private fun info(isGroup: Boolean = false, isChannel: Boolean = false, isSavedMessages: Boolean = false) = ChatInfo(
        id = 1,
        title = "Chat",
        lastReadOutboxMessageId = 0L,
        lastReadInboxMessageId = 0L,
        unreadCount = 0,
        avatarFileId = null,
        isGroup = isGroup,
        isChannel = isChannel,
        isSavedMessages = isSavedMessages,
        draftText = null,
        draftReplyToMessageId = null,
    )

    @Test
    fun soukromyChatSeZnamouDostupnostiNabidneVse() {
        val offer = sendMenuOffer(ConversationState(info = info(), presence = Presence.Offline(wasOnline = 1)))
        assertEquals(SendMenuOffer(silent = true, schedule = true, whenOnline = true, effects = true), offer)
    }

    @Test
    fun azBudeOnlineJenSeZnamouDostupnosti() {
        // Bot dostupnost nemá (`null`), skrytou server nepřijme; online protějšek ji nepotřebuje.
        assertFalse(sendMenuOffer(ConversationState(info = info(), presence = null)).whenOnline)
        assertFalse(sendMenuOffer(ConversationState(info = info(), presence = Presence.Recently)).whenOnline)
        assertFalse(sendMenuOffer(ConversationState(info = info(), presence = Presence.Online)).whenOnline)
        // Ve skupině a kanálu nemá kdo být online.
        val offline = Presence.Offline(wasOnline = 1)
        assertFalse(sendMenuOffer(ConversationState(info = info(isGroup = true), presence = offline)).whenOnline)
        assertFalse(sendMenuOffer(ConversationState(info = info(isChannel = true), presence = offline)).whenOnline)
    }

    @Test
    fun efektJenVSoukromemChatu() {
        // Chat s botem je soukromý chat — efekt v něm projde (tudy se i ověřuje).
        assertTrue(sendMenuOffer(ConversationState(info = info())).effects)
        // TDLib efekt jinde nepřijme, v Uložených zprávách ho nemá kdo vidět.
        assertFalse(sendMenuOffer(ConversationState(info = info(isGroup = true))).effects)
        assertFalse(sendMenuOffer(ConversationState(info = info(isChannel = true))).effects)
        assertFalse(sendMenuOffer(ConversationState(info = info(isSavedMessages = true))).effects)
        assertFalse(sendMenuOffer(ConversationState(info = info(isGroup = true), threadRootId = 5)).effects)
    }

    @Test
    fun ulozeneZpravyNemajiBezZvuku() {
        val offer = sendMenuOffer(ConversationState(info = info(isSavedMessages = true)))
        assertFalse(offer.silent)
        // Naplánovat jde i sobě — připomínka.
        assertTrue(offer.schedule)
    }

    @Test
    fun veVlaknuSeNeplanuje() {
        val offer = sendMenuOffer(ConversationState(info = info(isGroup = true), threadRootId = 5))
        assertFalse(offer.schedule)
        assertTrue(offer.silent)
    }

    @Test
    fun priUpraveNicNenabizi() {
        val editing = ChatMessage(
            id = 1,
            isOutgoing = true,
            senderName = null,
            senderAvatarFileId = null,
            senderId = 1,
            text = "x",
            entities = emptyList(),
            media = null,
            linkPreview = null,
            reactions = emptyList(),
            sendState = null,
            replyToMessageId = null,
            isService = false,
            timestamp = 0,
            isEdited = false,
            mediaAlbumId = 0,
        )
        assertFalse(sendMenuOffer(ConversationState(info = info(), editing = editing)).any)
    }

    @Test
    fun casPlanovaniMusiBytAspomMinutuDopredu() {
        val now = 1_000_000L
        assertFalse(scheduleValid(now + 30, now))
        assertTrue(scheduleValid(now + 60, now))
        assertTrue(scheduleValid(now + MAX_SCHEDULE_AHEAD_SECONDS, now))
        assertFalse(scheduleValid(now + MAX_SCHEDULE_AHEAD_SECONDS + 1, now))
    }
}
