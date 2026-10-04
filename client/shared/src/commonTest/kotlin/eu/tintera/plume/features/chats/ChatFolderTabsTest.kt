package eu.tintera.plume.features.chats

import eu.tintera.plume.core.telegram.ArchiveUnread
import eu.tintera.plume.core.telegram.ChatFolderTab
import eu.tintera.plume.core.telegram.ChatFolders
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChatFolderTabsTest {

    private val folders = ChatFolders(
        folders = listOf(ChatFolderTab(id = 2, title = "Práce"), ChatFolderTab(id = 5, title = "Rodina")),
    )

    @Test
    fun bezSlozekSeRadekNekresli() {
        assertTrue(folderTabs(ChatFolders(), emptyMap(), selected = null).isEmpty())
    }

    @Test
    fun vsechnyChatyJsouTamKamJePresunulUzivatel() {
        assertEquals(listOf(null, 2, 5), folderTabs(folders, emptyMap(), null).map { it.id })
        assertEquals(listOf(2, null, 5), folderTabs(folders.copy(mainPosition = 1), emptyMap(), null).map { it.id })
        // Pozice za koncem (složka mezitím zmizela) neshodí nic, celý seznam jde na konec.
        assertEquals(listOf(2, 5, null), folderTabs(folders.copy(mainPosition = 9), emptyMap(), null).map { it.id })
    }

    @Test
    fun vybranaJePravdeJedna() {
        assertEquals(listOf(false, false, true), folderTabs(folders, emptyMap(), 5).map { it.selected })
        assertEquals(listOf(true, false, false), folderTabs(folders, emptyMap(), null).map { it.selected })
    }

    @Test
    fun cisloJeZNeztlumenychChatu() {
        val tabs = folderTabs(folders, mapOf(2 to ArchiveUnread(chats = 7, unmutedChats = 3)), null)
        assertEquals(listOf(0, 3, 0), tabs.map { it.unread })
    }

    @Test
    fun smazanaSlozkaSpadneNaCelySeznam() {
        assertEquals(5, selectedFolderId(5, folders))
        assertNull(selectedFolderId(7, folders))
        assertNull(selectedFolderId(null, folders))
    }
}
