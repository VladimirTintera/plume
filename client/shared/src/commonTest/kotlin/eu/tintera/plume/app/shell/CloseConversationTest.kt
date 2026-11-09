package eu.tintera.plume.app.shell

import eu.tintera.plume.core.accounts.AccountSlot
import androidx.navigation3.runtime.NavKey
import eu.tintera.plume.core.navigation.ArchiveConversationRoute
import eu.tintera.plume.core.navigation.ArchiveRoute
import eu.tintera.plume.core.navigation.ChatProfileRoute
import eu.tintera.plume.core.navigation.ChatsRoute
import eu.tintera.plume.core.navigation.ConversationRoute
import eu.tintera.plume.core.navigation.ForumTopicsRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

private val slot = AccountSlot.Primary

/**
 * Šipka zpět v konverzaci ([closeConversation]): vedle seznamu zavře celou větev nad ním,
 * na telefonu vede o krok zpět.
 */
class CloseConversationTest {

    private val a = ConversationRoute(slot, chatId = 1)
    private val b = ConversationRoute(slot, chatId = 2)

    @Test
    fun vedleSeznamuZavreKonverzaceOtevreneZminkou() {
        val stack = mutableListOf<NavKey>(ChatsRoute, a, b)
        stack.closeConversation(b, list = ChatsRoute, sideBySide = true)
        assertEquals(listOf<NavKey>(ChatsRoute), stack)
    }

    @Test
    fun vedleSeznamuZavreIProfil() {
        val stack = mutableListOf<NavKey>(ChatsRoute, a, ChatProfileRoute(slot, chatId = 1))
        stack.closeConversation(a, list = ChatsRoute, sideBySide = true)
        assertEquals(listOf<NavKey>(ChatsRoute), stack)
    }

    @Test
    fun naTelefonuJdeOKrokZpet() {
        val stack = mutableListOf<NavKey>(ChatsRoute, a, b)
        stack.closeConversation(b, list = ChatsRoute, sideBySide = false)
        assertEquals(listOf<NavKey>(ChatsRoute, a), stack)
    }

    @Test
    fun temaSeVraciDoSeznamuTemat() {
        val topic = ConversationRoute(slot, chatId = 3, topicId = 7)
        val stack = mutableListOf<NavKey>(ChatsRoute, ForumTopicsRoute(slot, chatId = 3), topic)
        stack.closeConversation(topic, list = ChatsRoute, sideBySide = true)
        assertEquals(listOf<NavKey>(ChatsRoute, ForumTopicsRoute(slot, chatId = 3)), stack)
    }

    @Test
    fun archivNechaHlavniKonverzaciPodSebou() {
        val y = ArchiveConversationRoute(slot, chatId = 9)
        val stack = mutableListOf<NavKey>(ChatsRoute, a, ArchiveRoute, y)
        stack.closeConversation(y, list = ArchiveRoute, sideBySide = true)
        assertEquals(listOf<NavKey>(ChatsRoute, a, ArchiveRoute), stack)
    }

    @Test
    fun druheZavreniOdchazejiciKonverzaceNicNeodebere() {
        val stack = mutableListOf<NavKey>(ChatsRoute, a)
        stack.closeConversation(a, list = ChatsRoute, sideBySide = true)
        assertFalse(stack.closeConversation(a, list = ChatsRoute, sideBySide = true))
        assertEquals(listOf<NavKey>(ChatsRoute), stack)
    }
}
