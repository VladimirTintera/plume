package eu.tintera.plume.app.shell

import androidx.navigation3.runtime.NavKey
import eu.tintera.plume.core.navigation.ArchiveChatProfileRoute
import eu.tintera.plume.core.navigation.ArchiveConversationRoute
import eu.tintera.plume.core.navigation.ArchiveEditChatRoute
import eu.tintera.plume.core.navigation.ArchiveRoute
import eu.tintera.plume.core.navigation.ChatProfileRoute
import eu.tintera.plume.core.navigation.ChatsRoute
import eu.tintera.plume.core.navigation.ConversationRoute
import eu.tintera.plume.core.navigation.ArchiveForumTopicsRoute
import eu.tintera.plume.core.navigation.ForumTopicsRoute
import eu.tintera.plume.core.navigation.MediaRoute
import eu.tintera.plume.core.telegram.ChatFolder
import eu.tintera.plume.features.chats.ArchiveChange
import eu.tintera.plume.features.conversation.ChatTarget
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

    /** Vrácení z pruhu stěhuje konverzaci z archivu do hlavního seznamu, ne ji zavírá. */
    @Test
    fun vraceniZPruhuPrestehujeKonverzaciDoHlavnihoSeznamu() {
        val stack = mutableListOf<NavKey>(
            ChatsRoute,
            ConversationRoute(chatId = 1),
            ChatProfileRoute(chatId = 1),
            ArchiveRoute,
            ArchiveChatProfileRoute(chatId = 3),
            ArchiveEditChatRoute(chatId = 3),
            ArchiveConversationRoute(chatId = 2),
        )

        stack.moveConversationToMain(chatId = 2)

        // Z archivu nezbylo nic a konverzace hlavního seznamu (i s profilem) je nahrazená.
        assertEquals(listOf<NavKey>(ChatsRoute, ConversationRoute(chatId = 2)), stack)
    }

    @Test
    fun vraceniZPruhuMimoArchivNicNemeni() {
        // Chat z archivu otevřený z hledání nebo notifikace stojí v hlavní větvi; tam není kam stěhovat.
        val stack = mutableListOf<NavKey>(ChatsRoute, ConversationRoute(chatId = 2))

        stack.moveConversationToMain(chatId = 2)

        assertEquals(listOf<NavKey>(ChatsRoute, ConversationRoute(chatId = 2)), stack)
    }

    @Test
    fun jinyChatSeNezavira() {
        val mainChat = ConversationRoute(chatId = 1)
        val profile = ChatProfileRoute(chatId = 1)
        val stack = mutableListOf<NavKey>(ChatsRoute, mainChat, profile)

        stack.closeConversationOf(ArchiveChange(chatId = 99, archived = true))

        assertEquals(listOf<NavKey>(ChatsRoute, mainChat, profile), stack)
    }

    /** Po smazání chatu z profilu nesmí zůstat jeho profil ani konverzace — ostatní ano. */
    @Test
    fun zavreniChatuOdeberJenJehoRouty() {
        val stack = mutableListOf<NavKey>(
            ChatsRoute,
            ConversationRoute(chatId = 1),
            ChatProfileRoute(chatId = 2),
            ConversationRoute(chatId = 2),
            ChatProfileRoute(chatId = 1),
            MediaRoute(chatId = 1, messageId = 5),
            ArchiveRoute,
            ArchiveConversationRoute(chatId = 1),
            ArchiveChatProfileRoute(chatId = 1),
        )

        stack.closeChat(1)

        assertEquals(listOf<NavKey>(ChatsRoute, ChatProfileRoute(chatId = 2), ConversationRoute(chatId = 2), ArchiveRoute), stack)
    }

    @Test
    fun seznamTematJeOtevrenaKonverzaceSveVetve() {
        // Nad prázdným archivem se podle toho rozhoduje, jestli zůstane dvoupanel.
        val main = listOf<NavKey>(ChatsRoute, ForumTopicsRoute(chatId = 1), ArchiveRoute)
        assertTrue(main.hasOpenConversation(ChatFolder.Main))
        assertFalse(main.hasOpenConversation(ChatFolder.Archive))

        val archive = listOf<NavKey>(ChatsRoute, ArchiveRoute, ArchiveForumTopicsRoute(chatId = 1))
        assertTrue(archive.hasOpenConversation(ChatFolder.Archive))
    }

    @Test
    fun zavreniArchivuOdeberTemaIKonverzaciTematu() {
        val stack = mutableListOf<NavKey>(
            ChatsRoute,
            ArchiveRoute,
            ArchiveForumTopicsRoute(chatId = 2),
            ArchiveConversationRoute(chatId = 2, topicId = 7),
        )

        stack.closeArchive()

        assertEquals(listOf<NavKey>(ChatsRoute), stack)
    }

    @Test
    fun zavreniChatuOdeberITemata() {
        val stack = mutableListOf<NavKey>(ChatsRoute, ForumTopicsRoute(chatId = 1), ConversationRoute(chatId = 1, topicId = 3))

        stack.closeChat(1)

        assertEquals(listOf<NavKey>(ChatsRoute), stack)
    }

    @Test
    fun archivaceForaZavreJehoTemata() {
        val stack = mutableListOf<NavKey>(ChatsRoute, ForumTopicsRoute(chatId = 1), ConversationRoute(chatId = 1, topicId = 3))

        stack.closeConversationOf(ArchiveChange(chatId = 1, archived = true))

        assertEquals(listOf<NavKey>(ChatsRoute), stack)
    }

    @Test
    fun odkazNaTemaOtevreTemaNadKonverzaci() {
        val stack = mutableListOf<NavKey>(ChatsRoute, ConversationRoute(chatId = 1))

        stack.openTarget(ChatTarget(chatId = 2, messageId = 50, topicId = 7), archive = false)

        assertEquals(
            listOf<NavKey>(ChatsRoute, ConversationRoute(chatId = 1), ConversationRoute(chatId = 2, messageId = 50, topicId = 7)),
            stack,
        )
    }

    @Test
    fun odkazDoOtevrenehoTematuHoNezdvoji() {
        val stack = mutableListOf<NavKey>(ChatsRoute, ForumTopicsRoute(chatId = 2), ConversationRoute(chatId = 2, topicId = 7))

        stack.openTarget(ChatTarget(chatId = 2, messageId = 50, topicId = 7), archive = false)

        assertEquals(3, stack.size)
    }

    @Test
    fun odkazDoJinehoTematuTeheSkupinyHoPrida() {
        val stack = mutableListOf<NavKey>(ChatsRoute, ForumTopicsRoute(chatId = 2), ConversationRoute(chatId = 2, topicId = 7))

        stack.openTarget(ChatTarget(chatId = 2, topicId = 8), archive = false)

        assertEquals(ConversationRoute(chatId = 2, topicId = 8), stack.last())
    }

    @Test
    fun skupinaSTematyBezTematuOtevreSeznamTemat() {
        val stack = mutableListOf<NavKey>(ChatsRoute, ConversationRoute(chatId = 1))

        stack.openTarget(ChatTarget(chatId = 2, forum = true), archive = false)
        stack.openTarget(ChatTarget(chatId = 2, forum = true), archive = false)

        assertEquals(listOf<NavKey>(ChatsRoute, ConversationRoute(chatId = 1), ForumTopicsRoute(chatId = 2)), stack)
    }

    @Test
    fun obycejnyChatSeNezdvoji() {
        val stack = mutableListOf<NavKey>(ChatsRoute, ConversationRoute(chatId = 1))

        stack.openTarget(ChatTarget(chatId = 1), archive = false)

        assertEquals(listOf<NavKey>(ChatsRoute, ConversationRoute(chatId = 1)), stack)
    }

    @Test
    fun zTematuVedeOdkazDoCeleSkupiny() {
        // Téma téže skupiny není „otevřený chat": zpráva mimo téma se musí otevřít.
        val stack = mutableListOf<NavKey>(ChatsRoute, ConversationRoute(chatId = 2, topicId = 7))

        stack.openTarget(ChatTarget(chatId = 2, messageId = 50), archive = false)

        assertEquals(ConversationRoute(chatId = 2, messageId = 50), stack.last())
    }

    @Test
    fun vlaknoKomentaruSeOteviraVzdy() {
        val stack = mutableListOf<NavKey>(ChatsRoute, ConversationRoute(chatId = 1))

        stack.openTarget(ChatTarget(chatId = 3, threadId = 9), archive = false)

        assertEquals(ConversationRoute(chatId = 3, threadId = 9), stack.last())
    }

    @Test
    fun vArchivuSeOteviraArchivniVetev() {
        val stack = mutableListOf<NavKey>(ChatsRoute, ArchiveRoute, ArchiveConversationRoute(chatId = 1))

        stack.openTarget(ChatTarget(chatId = 2, topicId = 7), archive = true)
        stack.openTarget(ChatTarget(chatId = 4, forum = true), archive = true)

        assertEquals(
            listOf<NavKey>(
                ChatsRoute,
                ArchiveRoute,
                ArchiveConversationRoute(chatId = 1),
                ArchiveConversationRoute(chatId = 2, topicId = 7),
                ArchiveForumTopicsRoute(chatId = 4),
            ),
            stack,
        )
    }

    @Test
    fun nalezenaZpravaVTematuMaPodSebouSeznamTemat() {
        val stack = mutableListOf<NavKey>(ChatsRoute, ForumTopicsRoute(chatId = 1), ConversationRoute(chatId = 1, topicId = 3))

        stack.openFoundMessage(chatId = 2, messageId = 50, topicId = 7)

        assertEquals(
            listOf<NavKey>(ChatsRoute, ForumTopicsRoute(chatId = 2), ConversationRoute(chatId = 2, messageId = 50, topicId = 7)),
            stack,
        )
    }

    @Test
    fun nalezenaZpravaMimoTemaNahradiKonverzaci() {
        val stack = mutableListOf<NavKey>(ChatsRoute, ConversationRoute(chatId = 1))

        stack.openFoundMessage(chatId = 2, messageId = 50, topicId = null)

        assertEquals(listOf<NavKey>(ChatsRoute, ConversationRoute(chatId = 2, messageId = 50)), stack)
    }
}
