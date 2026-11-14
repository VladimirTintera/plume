package eu.tintera.plume.app.shell

import eu.tintera.plume.features.chats.ArchiveChange
import androidx.navigation3.runtime.NavKey
import eu.tintera.plume.core.accounts.AccountSlot
import eu.tintera.plume.core.adaptive.PaneInfo
import eu.tintera.plume.core.adaptive.PaneRole
import eu.tintera.plume.core.navigation.AccountRoute
import eu.tintera.plume.core.navigation.ArchiveChatProfileRoute
import eu.tintera.plume.core.navigation.ArchiveConversationRoute
import eu.tintera.plume.core.navigation.ArchiveRoute
import eu.tintera.plume.core.navigation.ChatProfileRoute
import eu.tintera.plume.core.navigation.ChatsRoute
import eu.tintera.plume.core.navigation.ConversationRoute
import eu.tintera.plume.core.navigation.MediaRoute
import eu.tintera.plume.core.navigation.NavSavedStateConfiguration
import eu.tintera.plume.features.conversation.ChatTarget
import eu.tintera.plume.features.profile.ProfileTabRequests
import eu.tintera.plume.features.conversation.ConversationJumpRequests
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Každá cesta v zásobníku drží účet, ze kterého vyšla. S jedním účtem by se špatný slot nikde
 * neprojevil (všude by byl primární), proto se tu navigují routy jiného účtu.
 */
class RouteSlotTest {

    private val other = AccountSlot("a1b2")

    private val paneOf: (NavKey) -> PaneInfo? = { key ->
        when (key) {
            ChatsRoute -> PaneInfo(PaneRole.List, Unit)
            is ConversationRoute -> PaneInfo(PaneRole.Detail, Unit)
            is ChatProfileRoute -> PaneInfo(PaneRole.Extra, Unit)
            else -> null
        }
    }

    private fun List<NavKey>.slots() = filterIsInstance<AccountRoute>().map { it.slot }.distinct()

    @Test
    fun slotPrezijeUlozeniZasobniku() {
        val json = Json { serializersModule = NavSavedStateConfiguration.serializersModule }
        val route = ConversationRoute(other, 7)
        val restored = json.decodeFromString(
            ConversationRoute.serializer(),
            json.encodeToString(ConversationRoute.serializer(), route),
        )
        assertEquals(other, restored.slot)
    }

    @Test
    fun cilZKonverzaceJeVTemzeUctu() {
        val stack = mutableListOf<NavKey>(ChatsRoute, ConversationRoute(other, 7))
        stack.openTarget(other, ChatTarget(8), archive = false)
        stack.openFoundMessage(other, 9, messageId = 1, topicId = null)
        stack.openCommentThread(other, 10, threadId = 2)
        assertEquals(listOf(other), stack.slots())
    }

    @Test
    fun povysenaSkupinaZustavaVUctu() {
        val stack = mutableListOf<NavKey>(ChatsRoute, ConversationRoute(other, 7), ChatProfileRoute(other, 7))
        stack.replaceUpgradedChat(other, 7, 8)
        assertEquals(listOf<NavKey>(ChatsRoute, ConversationRoute(other, 8), ChatProfileRoute(other, 8)), stack)
    }

    @Test
    fun presunZArchivuZustavaVUctu() {
        val stack = mutableListOf<NavKey>(ChatsRoute, ArchiveRoute, ArchiveConversationRoute(other, 7))
        stack.moveConversationToMain(other, 7)
        assertEquals(listOf<NavKey>(ChatsRoute, ConversationRoute(other, 7)), stack)
    }

    /** Soukromý chat má v obou účtech id protějšku: zavřít se smí jen ten z účtu, kterého se to týká. */
    @Test
    fun zavreniChatuNechaStejneIdJinehoUctu() {
        val stack = mutableListOf<NavKey>(ChatsRoute, ConversationRoute(AccountSlot.Primary, 7), ConversationRoute(other, 7))
        stack.closeChat(other, 7)
        assertEquals(listOf<NavKey>(ChatsRoute, ConversationRoute(AccountSlot.Primary, 7)), stack)
    }

    @Test
    fun archivaceZaviraJenKonverzaciSvehoUctu() {
        val stack = mutableListOf<NavKey>(ChatsRoute, ConversationRoute(AccountSlot.Primary, 7), ConversationRoute(other, 7))
        stack.closeConversationOf(other, ArchiveChange(chatId = 7, archived = true))
        assertEquals(listOf<NavKey>(ChatsRoute, ConversationRoute(AccountSlot.Primary, 7)), stack)
    }

    @Test
    fun profilJdeZaKonverzaciStejnehoUctu() {
        val stack = mutableListOf<NavKey>(ChatsRoute, ConversationRoute(other, 7), ChatProfileRoute(other, 7), ConversationRoute(other, 8))
        stack.followProfile(paneOf)
        assertEquals(listOf(other), stack.slots())
    }

    @Test
    fun prohlizecVraciKonverzaciSvehoUctu() {
        val media = MediaRoute(other, chatId = 7, messageId = 10)
        val stack = mutableListOf<NavKey>(ChatsRoute, media)
        stack.showInChat(media, messageId = 10, jumps = ConversationJumpRequests())
        assertEquals(listOf<NavKey>(ChatsRoute, ConversationRoute(other, 7, 10)), stack)
    }

    @Test
    fun vsechnaMediaVeProfiluSvehoUctu() {
        val media = MediaRoute(other, chatId = 7, messageId = 10)
        val stack = mutableListOf<NavKey>(ChatsRoute, ArchiveConversationRoute(other, 7), media)
        stack.showAllMedia(media, ProfileTabRequests())
        assertEquals(listOf<NavKey>(ChatsRoute, ArchiveConversationRoute(other, 7), ArchiveChatProfileRoute(other, 7)), stack)
    }
}
