package eu.tintera.plume.app.shell

import androidx.navigation3.runtime.NavKey
import eu.tintera.plume.core.navigation.ArchiveChatProfileRoute
import eu.tintera.plume.core.navigation.ArchiveConversationRoute
import eu.tintera.plume.core.navigation.ChatProfileRoute
import eu.tintera.plume.core.navigation.ChatsRoute
import eu.tintera.plume.core.navigation.ConversationRoute
import eu.tintera.plume.core.navigation.MediaRoute
import eu.tintera.plume.core.telegram.SharedMediaKind
import eu.tintera.plume.features.profile.ProfileTabRequests
import kotlin.test.Test
import kotlin.test.assertEquals

/** „Všechna média" z prohlížeče ([showAllMedia]): kde se profil ukáže a na které záložce. */
class ShowAllMediaTest {

    private val tabs = ProfileTabRequests()

    /** Z konverzace: prohlížeč se zavře a nad konverzaci přibude profil na záložce Média. */
    @Test
    fun zKonverzacePridaProfil() {
        val media = MediaRoute(chatId = 7, messageId = 10)
        val stack = mutableListOf<NavKey>(ChatsRoute, ConversationRoute(7), media)

        stack.showAllMedia(media, tabs)

        assertEquals(listOf<NavKey>(ChatsRoute, ConversationRoute(7), ChatProfileRoute(7)), stack)
        assertEquals(7L to SharedMediaKind.Media, tabs.target.value)
    }

    /** Z mřížky v profilu: profil pod prohlížečem už je, žádný druhý nepřibude. */
    @Test
    fun zProfiluSeJenVrati() {
        val media = MediaRoute(chatId = 7, messageId = 10, fromSharedMedia = true)
        val stack = mutableListOf<NavKey>(ChatsRoute, ConversationRoute(7), ChatProfileRoute(7), media)

        stack.showAllMedia(media, tabs)

        assertEquals(listOf<NavKey>(ChatsRoute, ConversationRoute(7), ChatProfileRoute(7)), stack)
        assertEquals(7L to SharedMediaKind.Media, tabs.target.value)
    }

    @Test
    fun archivDostaneArchivniProfil() {
        val media = MediaRoute(chatId = 7, messageId = 10)
        val stack = mutableListOf<NavKey>(ChatsRoute, ArchiveConversationRoute(7), media)

        stack.showAllMedia(media, tabs)

        assertEquals(listOf<NavKey>(ChatsRoute, ArchiveConversationRoute(7), ArchiveChatProfileRoute(7)), stack)
    }

    /** Prohlížeč, který už v zásobníku není, nic nezmění ani nic nevyžádá. */
    @Test
    fun zavrenyProhlizecNicNemeni() {
        val media = MediaRoute(chatId = 7, messageId = 10)
        val stack = mutableListOf<NavKey>(ChatsRoute, ConversationRoute(7))

        stack.showAllMedia(media, tabs)

        assertEquals(listOf<NavKey>(ChatsRoute, ConversationRoute(7)), stack)
        assertEquals(null, tabs.target.value)
    }

    /** Požadavek vyřídí jen profil téhož chatu. */
    @Test
    fun pozadavekPatriJednomuChatu() {
        tabs.request(7, SharedMediaKind.Media)
        tabs.consume(3)
        assertEquals(7L to SharedMediaKind.Media, tabs.target.value)
        tabs.consume(7)
        assertEquals(null, tabs.target.value)
    }
}
