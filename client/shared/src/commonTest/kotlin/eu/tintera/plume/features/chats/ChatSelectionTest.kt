package eu.tintera.plume.features.chats

import eu.tintera.plume.core.telegram.ChatFolder
import eu.tintera.plume.core.telegram.ChatKind
import eu.tintera.plume.core.telegram.ChatSponsor
import eu.tintera.plume.core.telegram.ChatSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Výběr víc chatů v seznamu. Akce lišty jsou přepínače podle stavu VŠECH vybraných a týkají se
 * jen chatů, kterým něco změní — jinak by „Vrátit" po archivaci vracel i to, co se nehnulo.
 */
class ChatSelectionTest {

    @Test
    fun odebraniPoslednihoVyberUkonci() {
        val selection = ChatSelection(setOf(1L))
        assertEquals(setOf(1L, 2L), selection.toggled(2)?.ids)
        assertNull(selection.toggled(1))
    }

    @Test
    fun chatKteryZeSeznamuOdesel_zVyberuVypadne() {
        val selection = ChatSelection(setOf(1L, 2L))
        assertEquals(setOf(2L), selection.within(listOf(chat(2), chat(3)))?.ids)
        assertNull(selection.within(listOf(chat(3))))
    }

    @Test
    fun sponzorovanyChatVybratNejde() {
        assertTrue(chat(1).isSelectable())
        assertFalse(chat(1).copy(sponsor = ChatSponsor.Proxy).isSelectable())
    }

    @Test
    fun archivujeSeDokudNejsouVsechnyVArchivu() {
        val main = chat(1)
        val archived = chat(2, archived = true)
        val mixed = chatSelectionActions(listOf(main, archived), savedMessagesChatId = null)
        assertTrue(mixed.archive)
        // Do archivu jde jen ten, který v něm ještě není.
        assertEquals(listOf(1L), mixed.archiveTargets(listOf(main, archived)).map { it.id })

        val all = chatSelectionActions(listOf(archived, chat(3, archived = true)), savedMessagesChatId = null)
        assertFalse(all.archive)
    }

    @Test
    fun ulozeneZpravyArchivaciZasedi() {
        val actions = chatSelectionActions(listOf(chat(1), chat(99)), savedMessagesChatId = 99)
        assertFalse(actions.canArchive)
        assertTrue(chatSelectionActions(listOf(chat(1)), savedMessagesChatId = 99).canArchive)
    }

    @Test
    fun ztlumiSeDokudNejsouVsechnyZtlumene() {
        val muted = chat(1, muted = true)
        val loud = chat(2)
        val actions = chatSelectionActions(listOf(muted, loud), savedMessagesChatId = null)
        assertTrue(actions.mute)
        assertEquals(listOf(2L), actions.muteTargets(listOf(muted, loud)).map { it.id })
        assertFalse(chatSelectionActions(listOf(muted), savedMessagesChatId = null).mute)
    }

    @Test
    fun ctouSeNeprecteneJinakSeOznaciJakoNeprectene() {
        val unread = chat(1, unread = 3)
        val marked = chat(2).copy(isMarkedAsUnread = true)
        val read = chat(3)
        val actions = chatSelectionActions(listOf(unread, marked, read), savedMessagesChatId = null)
        assertTrue(actions.read)
        // Ruční příznak je taky „nepřečtené" — přečte se s ostatními.
        assertEquals(listOf(1L, 2L), actions.readTargets(listOf(unread, marked, read)).map { it.id })

        val allRead = chatSelectionActions(listOf(read, chat(4)), savedMessagesChatId = null)
        assertFalse(allRead.read)
        assertEquals(listOf(3L, 4L), allRead.readTargets(listOf(read, chat(4))).map { it.id })
    }

    private fun chat(id: Long, archived: Boolean = false, muted: Boolean = false, unread: Int = 0) = ChatSummary(
        id = id,
        title = "Chat $id",
        unreadCount = unread,
        lastMessagePreview = null,
        isPinned = false,
        order = id,
        folder = if (archived) ChatFolder.Archive else ChatFolder.Main,
        avatarFileId = null,
        isMuted = muted,
        isMarkedAsUnread = false,
        kind = ChatKind.Private,
        lastMessageTimestamp = null,
        presence = null,
        activity = null,
        draft = null,
    )
}
