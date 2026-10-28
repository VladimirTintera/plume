package eu.tintera.plume.app.shell

import androidx.navigation3.runtime.NavKey
import eu.tintera.plume.core.navigation.ArchiveChatProfileRoute
import eu.tintera.plume.core.navigation.ArchiveConversationRoute
import eu.tintera.plume.core.navigation.ChatProfileRoute
import eu.tintera.plume.core.navigation.ChatsRoute
import eu.tintera.plume.core.navigation.ConversationRoute
import eu.tintera.plume.core.navigation.MediaRoute
import eu.tintera.plume.features.conversation.ConversationJumpRequests
import eu.tintera.plume.features.conversation.JumpTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** „Zobrazit v chatu" z prohlížeče médií ([showInChat]): co se zavře a kam se skočí. */
class ShowInChatTest {

    private val jumps = ConversationJumpRequests()

    /** Prohlížeč nad konverzací: zavře se a konverzace pod ním skočí na zprávu. */
    @Test
    fun nadKonverzaciSkociVNi() {
        val media = MediaRoute(chatId = 7, messageId = 10)
        val stack = mutableListOf<NavKey>(ChatsRoute, ConversationRoute(7), media)

        stack.showInChat(media, messageId = 42, jumps)

        assertEquals(listOf<NavKey>(ChatsRoute, ConversationRoute(7)), stack)
        assertEquals(7L to JumpTarget.Message(42L), jumps.target.value)
    }

    /** Z mřížky médií v profilu se zavře i profil — konverzace leží pod ním. */
    @Test
    fun zProfiluZavreIProfil() {
        val media = MediaRoute(chatId = 7, messageId = 10, fromSharedMedia = true)
        val stack = mutableListOf<NavKey>(ChatsRoute, ConversationRoute(7), ChatProfileRoute(7), media)

        stack.showInChat(media, messageId = 42, jumps)

        assertEquals(listOf<NavKey>(ChatsRoute, ConversationRoute(7)), stack)
        assertEquals(7L to JumpTarget.Message(42L), jumps.target.value)
    }

    /** Bez konverzace toho chatu (profil otevřený ze skupiny) se otevře rovnou na zprávě. */
    @Test
    fun bezKonverzaceOtevreNovou() {
        val media = MediaRoute(chatId = 7, messageId = 10, fromSharedMedia = true)
        val stack = mutableListOf<NavKey>(ChatsRoute, ConversationRoute(3), ChatProfileRoute(7), media)

        stack.showInChat(media, messageId = 42, jumps)

        assertEquals(listOf<NavKey>(ChatsRoute, ConversationRoute(3), ConversationRoute(7, messageId = 42)), stack)
    }

    /** Archivní větev zůstává archivní. */
    @Test
    fun archivZustavaArchivem() {
        val media = MediaRoute(chatId = 7, messageId = 10, fromSharedMedia = true)
        val stack = mutableListOf<NavKey>(ChatsRoute, ArchiveConversationRoute(7), ArchiveChatProfileRoute(7), media)

        stack.showInChat(media, messageId = 42, jumps)

        assertEquals(listOf<NavKey>(ChatsRoute, ArchiveConversationRoute(7)), stack)
    }

    /** Prohlížeč, který už v zásobníku není (dobíhá zavírání), nic nezmění. */
    @Test
    fun zavrenyProhlizecNicNemeni() {
        val media = MediaRoute(chatId = 7, messageId = 10)
        val stack = mutableListOf<NavKey>(ChatsRoute, ConversationRoute(7))

        stack.showInChat(media, messageId = 42, jumps)

        assertEquals(listOf<NavKey>(ChatsRoute, ConversationRoute(7)), stack)
        assertEquals(null, jumps.target.value)
    }

    @Test
    fun nabiziSeJenUZprav() {
        assertTrue(MediaRoute(chatId = 7, messageId = 10).showsChatMessages)
        assertTrue(MediaRoute(chatId = 7, messageId = 10, fromSharedMedia = true).showsChatMessages)
        assertFalse(MediaRoute(chatId = 7, messageId = 0, profilePhotos = true).showsChatMessages)
        assertFalse(MediaRoute(chatId = 7, messageId = 10, pollMedia = 0).showsChatMessages)
        assertFalse(MediaRoute(chatId = 7, messageId = 10, articleMedia = 0).showsChatMessages)
        assertFalse(MediaRoute(chatId = 7, messageId = 10, sponsored = true).showsChatMessages)
    }
}
