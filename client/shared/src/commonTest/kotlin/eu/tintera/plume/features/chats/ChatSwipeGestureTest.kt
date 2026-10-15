package eu.tintera.plume.features.chats

import eu.tintera.plume.core.telegram.TELEGRAM_SERVICE_NOTIFICATIONS_ID
import eu.tintera.plume.core.settings.ChatSwipeAction
import eu.tintera.plume.core.telegram.ChatFolder
import eu.tintera.plume.core.telegram.ChatKind
import eu.tintera.plume.core.telegram.ChatSponsor
import eu.tintera.plume.core.telegram.ChatSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChatSwipeGestureTest {

    @Test
    fun prahJePodilSirky() {
        assertEquals(400f, chatSwipeThreshold(width = 1000f, cap = 500f))
    }

    @Test
    fun sirokaPolozkaMaPrahZastropovany() {
        assertEquals(500f, chatSwipeThreshold(width = 3000f, cap = 500f))
    }

    /** Než je položka změřená, nesmí puštění udělat nic. */
    @Test
    fun nezmerenaPolozkaPrahNema() {
        assertTrue(chatSwipeThreshold(width = 0f, cap = 500f).isInfinite())
        assertFalse(9999f >= chatSwipeThreshold(width = 0f, cap = 500f))
    }

    @Test
    fun akceSeOtociPodleStavuChatu() {
        assertEquals(ChatSwipeEffect.Archive, chatSwipeEffect(ChatSwipeAction.Archive, chat(), SAVED))
        assertEquals(ChatSwipeEffect.Unarchive, chatSwipeEffect(ChatSwipeAction.Archive, chat(archived = true), SAVED))
        assertEquals(ChatSwipeEffect.MarkRead, chatSwipeEffect(ChatSwipeAction.Read, chat(unread = 3), SAVED))
        assertEquals(ChatSwipeEffect.MarkRead, chatSwipeEffect(ChatSwipeAction.Read, chat(markedUnread = true), SAVED))
        assertEquals(ChatSwipeEffect.MarkUnread, chatSwipeEffect(ChatSwipeAction.Read, chat(), SAVED))
        assertEquals(ChatSwipeEffect.Mute, chatSwipeEffect(ChatSwipeAction.Mute, chat(), SAVED))
        assertEquals(ChatSwipeEffect.Unmute, chatSwipeEffect(ChatSwipeAction.Mute, chat(muted = true), SAVED))
        assertEquals(ChatSwipeEffect.Pin, chatSwipeEffect(ChatSwipeAction.Pin, chat(), SAVED))
        assertEquals(ChatSwipeEffect.Unpin, chatSwipeEffect(ChatSwipeAction.Pin, chat(pinned = true), SAVED))
        assertEquals(ChatSwipeEffect.Snooze, chatSwipeEffect(ChatSwipeAction.Snooze, chat(), SAVED))
        assertEquals(ChatSwipeEffect.Unsnooze, chatSwipeEffect(ChatSwipeAction.Snooze, chat(), SAVED, snoozed = true))
    }

    @Test
    fun nicNeudelaNic() {
        assertNull(chatSwipeEffect(ChatSwipeAction.None, chat(), SAVED))
    }

    /** Uložené zprávy archivovat nejdou, ostatní akce u nich platí. */
    @Test
    fun ulozeneZpravySeNearchivuji() {
        val saved = chat(id = SAVED)
        assertNull(chatSwipeEffect(ChatSwipeAction.Archive, saved, SAVED))
        assertEquals(ChatSwipeEffect.Pin, chatSwipeEffect(ChatSwipeAction.Pin, saved, SAVED))
    }

    /** Chat služby Telegramu TDLib do archivu nepustí; z archivu ho ale vrátit jde. */
    @Test
    fun chatTelegramuSeNearchivuje() {
        assertNull(chatSwipeEffect(ChatSwipeAction.Archive, chat(id = TELEGRAM_SERVICE_NOTIFICATIONS_ID), SAVED))
        assertEquals(
            ChatSwipeEffect.Unarchive,
            chatSwipeEffect(ChatSwipeAction.Archive, chat(id = TELEGRAM_SERVICE_NOTIFICATIONS_ID, archived = true), SAVED),
        )
    }

    @Test
    fun sponzorovanyChatTahNema() {
        val sponsored = chat().copy(sponsor = ChatSponsor.Proxy)
        ChatSwipeAction.entries.forEach { assertNull(chatSwipeEffect(it, sponsored, SAVED)) }
    }

    /** Ze seznamu odjíždí jen archivace — ostatní akce nechají chat na místě. */
    @Test
    fun zeSeznamuOdejdeJenArchivace() {
        assertEquals(
            setOf(ChatSwipeEffect.Archive, ChatSwipeEffect.Unarchive),
            ChatSwipeEffect.entries.filter { it.leavesList }.toSet(),
        )
    }

    private fun chat(
        id: Long = 1,
        archived: Boolean = false,
        muted: Boolean = false,
        pinned: Boolean = false,
        unread: Int = 0,
        markedUnread: Boolean = false,
    ) = ChatSummary(
        id = id,
        title = "Chat $id",
        unreadCount = unread,
        lastMessagePreview = null,
        isPinned = pinned,
        order = id,
        folder = if (archived) ChatFolder.Archive else ChatFolder.Main,
        avatarFileId = null,
        isMuted = muted,
        isMarkedAsUnread = markedUnread,
        kind = ChatKind.Private,
        lastMessageTimestamp = null,
        presence = null,
        activity = null,
        draft = null,
    )

    private companion object {
        const val SAVED = 99L
    }
}
