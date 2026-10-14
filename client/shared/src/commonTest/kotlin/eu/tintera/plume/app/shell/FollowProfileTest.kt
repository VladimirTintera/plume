package eu.tintera.plume.app.shell

import androidx.navigation3.runtime.NavKey
import eu.tintera.plume.core.adaptive.PaneInfo
import eu.tintera.plume.core.adaptive.PaneRole
import eu.tintera.plume.core.navigation.ArchiveChatProfileRoute
import eu.tintera.plume.core.navigation.ArchiveConversationRoute
import eu.tintera.plume.core.navigation.ArchiveRoute
import eu.tintera.plume.core.navigation.ChatProfileRoute
import eu.tintera.plume.core.navigation.ChatsRoute
import eu.tintera.plume.core.navigation.ConversationRoute
import eu.tintera.plume.core.navigation.EditChatRoute
import eu.tintera.plume.core.navigation.ForumTopicsRoute
import eu.tintera.plume.core.navigation.InstantViewRoute
import eu.tintera.plume.core.navigation.MediaRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Panel s profilem zůstává otevřený při přepnutí konverzace ([followProfile]): zakrytý profil
 * vystřídá profil nové konverzace. Role panelů tu zrcadlí metadata v `AppShell`.
 */
class FollowProfileTest {

    private val archive = "archive"

    private val paneOf: (NavKey) -> PaneInfo? = { key ->
        when (key) {
            ChatsRoute -> PaneInfo(PaneRole.List, Unit)
            ArchiveRoute -> PaneInfo(PaneRole.List, archive)
            is ConversationRoute, is ForumTopicsRoute, is InstantViewRoute -> PaneInfo(PaneRole.Detail, Unit)
            is ArchiveConversationRoute -> PaneInfo(PaneRole.Detail, archive)
            is ChatProfileRoute, is EditChatRoute -> PaneInfo(PaneRole.Extra, Unit)
            is ArchiveChatProfileRoute -> PaneInfo(PaneRole.Extra, archive)
            else -> null
        }
    }

    private fun MutableList<NavKey>.follow() = apply { followProfile(paneOf) }

    @Test
    fun jinyChatZeSeznamuPrevezmeProfil() {
        // openConversation odebere konverzaci A a přidá B; profil A zůstal mezi nimi.
        val stack = mutableListOf<NavKey>(ChatsRoute, ChatProfileRoute(1), ConversationRoute(2)).follow()
        assertEquals(listOf<NavKey>(ChatsRoute, ConversationRoute(2), ChatProfileRoute(2)), stack)
    }

    @Test
    fun zminkaNadProfilemPrevezmeProfilIsUpravou() {
        val stack = mutableListOf<NavKey>(
            ChatsRoute, ConversationRoute(1), ChatProfileRoute(1), EditChatRoute(1), ConversationRoute(2),
        ).follow()
        assertEquals(listOf<NavKey>(ChatsRoute, ConversationRoute(1), ConversationRoute(2), ChatProfileRoute(2)), stack)
    }

    @Test
    fun skupinaSTematyMaProfilSkupiny() {
        val stack = mutableListOf<NavKey>(ChatsRoute, ChatProfileRoute(1), ForumTopicsRoute(5)).follow()
        assertEquals(listOf<NavKey>(ChatsRoute, ForumTopicsRoute(5), ChatProfileRoute(5)), stack)
    }

    @Test
    fun clanekBezProfiluPanelZavre() {
        val stack = mutableListOf<NavKey>(ChatsRoute, ConversationRoute(1), ChatProfileRoute(1), InstantViewRoute("https://t.me/x")).follow()
        assertEquals(listOf<NavKey>(ChatsRoute, ConversationRoute(1), InstantViewRoute("https://t.me/x")), stack)
    }

    @Test
    fun prohlizecNadNovouKonverzaciZustaneNavrchu() {
        val media = MediaRoute(2, 10)
        val stack = mutableListOf<NavKey>(ChatsRoute, ChatProfileRoute(1), ConversationRoute(2), media).follow()
        assertEquals(listOf<NavKey>(ChatsRoute, ConversationRoute(2), ChatProfileRoute(2), media), stack)
    }

    @Test
    fun archivMaArchivniProfil() {
        val stack = mutableListOf<NavKey>(
            ChatsRoute, ArchiveRoute, ArchiveChatProfileRoute(7), ArchiveConversationRoute(8),
        ).follow()
        assertEquals(listOf<NavKey>(ChatsRoute, ArchiveRoute, ArchiveConversationRoute(8), ArchiveChatProfileRoute(8)), stack)
    }

    @Test
    fun zavrenyPanelSeNeotevre() {
        val stack = mutableListOf<NavKey>(ChatsRoute, ConversationRoute(2))
        assertFalse(stack.followProfile(paneOf))
        assertEquals(listOf<NavKey>(ChatsRoute, ConversationRoute(2)), stack)
    }

    @Test
    fun nahledOdpovidaOpravenemuZasobniku() {
        val stack = listOf<NavKey>(ChatsRoute, ChatProfileRoute(1), ConversationRoute(2))
        assertEquals(stack.toMutableList().follow(), stack.withProfileFollowing(paneOf))
    }
}
