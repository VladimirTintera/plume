package eu.tintera.plume.app.shell

import androidx.navigation3.runtime.NavKey
import eu.tintera.plume.core.navigation.ArchiveChatProfileRoute
import eu.tintera.plume.core.navigation.ArchiveConversationRoute
import eu.tintera.plume.core.navigation.ArchiveRoute
import eu.tintera.plume.core.navigation.ChatProfileRoute
import eu.tintera.plume.core.navigation.ChatsRoute
import eu.tintera.plume.core.navigation.ConversationRoute
import eu.tintera.plume.core.telegram.ChatFolder
import eu.tintera.plume.features.chats.ArchiveChange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Pravidla nad zásobníkem Chatů — viz `ChatsStack.kt`. */
class ChatsStackTest {

    @Test
    fun bezArchivuJeVidetHlavniSeznam() {
        val stack = listOf<NavKey>(ChatsRoute, ConversationRoute(chatId = 1))

        assertEquals(ChatFolder.Main, stack.shownChatFolder())
    }

    @Test
    fun sArchivemNahoreJeVidetArchiv() {
        val stack = listOf<NavKey>(ChatsRoute, ConversationRoute(chatId = 1), ArchiveRoute)

        assertEquals(ChatFolder.Archive, stack.shownChatFolder())
    }

    @Test
    fun rozhodujePosledniListPanelNeJenPritomnostArchivu() {
        // Konverzace ani profil nad archivem seznam vlevo nemění — pořád je to archiv.
        val stack = listOf<NavKey>(
            ChatsRoute,
            ArchiveRoute,
            ArchiveConversationRoute(chatId = 2),
            ArchiveChatProfileRoute(chatId = 2),
        )

        assertEquals(ChatFolder.Archive, stack.shownChatFolder())
    }

    @Test
    fun otevrenaKonverzaceHlavnihoSeznamuNeniOtevrenaKonverzaceArchivu() {
        // Regrese: prázdný archiv nad otevřenou konverzací hlavního seznamu musí dostat
        // celou šířku, jinak vedle prázdného stavu stojí placeholder „vyber chat vlevo".
        val stack = listOf<NavKey>(ChatsRoute, ConversationRoute(chatId = 1), ArchiveRoute)

        assertFalse(stack.hasOpenConversation(ChatFolder.Archive))
        assertTrue(stack.hasOpenConversation(ChatFolder.Main))
    }

    @Test
    fun otevrenaKonverzaceArchivuNeniOtevrenaKonverzaceHlavnihoSeznamu() {
        val stack = listOf<NavKey>(ChatsRoute, ArchiveRoute, ArchiveConversationRoute(chatId = 2))

        assertTrue(stack.hasOpenConversation(ChatFolder.Archive))
        assertFalse(stack.hasOpenConversation(ChatFolder.Main))
    }

    @Test
    fun zpetZArchivuOdebereCelouJehoVetevAZanechaOtevrenyChat() {
        val mainChat = ConversationRoute(chatId = 1)
        val stack = mutableListOf<NavKey>(
            ChatsRoute,
            mainChat,
            ArchiveRoute,
            ArchiveConversationRoute(chatId = 2),
            ArchiveChatProfileRoute(chatId = 2),
        )

        stack.closeArchive()

        assertEquals(listOf<NavKey>(ChatsRoute, mainChat), stack)
    }

    @Test
    fun archivaceZavreKonverzaciVHlavnimSeznamu() {
        val stack = mutableListOf<NavKey>(ChatsRoute, ConversationRoute(chatId = 1))

        stack.closeConversationOf(ArchiveChange(chatId = 1, archived = true))

        assertEquals(listOf<NavKey>(ChatsRoute), stack)
    }

    @Test
    fun vraceniZArchivuZavreKonverzaciVArchivu() {
        val stack = mutableListOf<NavKey>(ChatsRoute, ArchiveRoute, ArchiveConversationRoute(chatId = 2))

        stack.closeConversationOf(ArchiveChange(chatId = 2, archived = false))

        assertEquals(listOf<NavKey>(ChatsRoute, ArchiveRoute), stack)
    }

    @Test
    fun zaviraSeJenVetevZeKtereChatOdesel() {
        val mainChat = ConversationRoute(chatId = 2)
        val archiveChat = ArchiveConversationRoute(chatId = 2)

        // Archivace se týká hlavního seznamu, archivní konverzace téhož chatu zůstane.
        val archiving = mutableListOf<NavKey>(ChatsRoute, mainChat, ArchiveRoute, archiveChat)
        archiving.closeConversationOf(ArchiveChange(chatId = 2, archived = true))
        assertEquals(listOf<NavKey>(ChatsRoute, ArchiveRoute, archiveChat), archiving)

        // A naopak.
        val unarchiving = mutableListOf<NavKey>(ChatsRoute, mainChat, ArchiveRoute, archiveChat)
        unarchiving.closeConversationOf(ArchiveChange(chatId = 2, archived = false))
        assertEquals(listOf<NavKey>(ChatsRoute, mainChat, ArchiveRoute), unarchiving)
    }

    @Test
    fun jinyChatSeNezavira() {
        val mainChat = ConversationRoute(chatId = 1)
        val profile = ChatProfileRoute(chatId = 1)
        val stack = mutableListOf<NavKey>(ChatsRoute, mainChat, profile)

        stack.closeConversationOf(ArchiveChange(chatId = 99, archived = true))

        assertEquals(listOf<NavKey>(ChatsRoute, mainChat, profile), stack)
    }
}
