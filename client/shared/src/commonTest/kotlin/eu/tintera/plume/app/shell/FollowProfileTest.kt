package eu.tintera.plume.app.shell

import eu.tintera.plume.core.accounts.AccountSlot
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

private val slot = AccountSlot.Primary

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
        val stack = mutableListOf<NavKey>(ChatsRoute, ChatProfileRoute(slot, 1), ConversationRoute(slot, 2)).follow()
        assertEquals(listOf<NavKey>(ChatsRoute, ConversationRoute(slot, 2), ChatProfileRoute(slot, 2)), stack)
    }

    @Test
    fun zminkaNadProfilemPrevezmeProfilIsUpravou() {
        val stack = mutableListOf<NavKey>(
            ChatsRoute, ConversationRoute(slot, 1), ChatProfileRoute(slot, 1), EditChatRoute(slot, 1), ConversationRoute(slot, 2),
        ).follow()
        assertEquals(listOf<NavKey>(ChatsRoute, ConversationRoute(slot, 1), ConversationRoute(slot, 2), ChatProfileRoute(slot, 2)), stack)
    }

    @Test
    fun skupinaSTematyMaProfilSkupiny() {
        val stack = mutableListOf<NavKey>(ChatsRoute, ChatProfileRoute(slot, 1), ForumTopicsRoute(slot, 5)).follow()
        assertEquals(listOf<NavKey>(ChatsRoute, ForumTopicsRoute(slot, 5), ChatProfileRoute(slot, 5)), stack)
    }

    @Test
    fun clanekBezProfiluPanelZavre() {
        val stack = mutableListOf<NavKey>(ChatsRoute, ConversationRoute(slot, 1), ChatProfileRoute(slot, 1), InstantViewRoute(slot, "https://t.me/x")).follow()
        assertEquals(listOf<NavKey>(ChatsRoute, ConversationRoute(slot, 1), InstantViewRoute(slot, "https://t.me/x")), stack)
    }

    @Test
    fun prohlizecNadNovouKonverzaciZustaneNavrchu() {
        val media = MediaRoute(slot, 2, 10)
        val stack = mutableListOf<NavKey>(ChatsRoute, ChatProfileRoute(slot, 1), ConversationRoute(slot, 2), media).follow()
        assertEquals(listOf<NavKey>(ChatsRoute, ConversationRoute(slot, 2), ChatProfileRoute(slot, 2), media), stack)
    }

    @Test
    fun archivMaArchivniProfil() {
        val stack = mutableListOf<NavKey>(
            ChatsRoute, ArchiveRoute, ArchiveChatProfileRoute(slot, 7), ArchiveConversationRoute(slot, 8),
        ).follow()
        assertEquals(listOf<NavKey>(ChatsRoute, ArchiveRoute, ArchiveConversationRoute(slot, 8), ArchiveChatProfileRoute(slot, 8)), stack)
    }

    @Test
    fun zavrenyPanelSeNeotevre() {
        val stack = mutableListOf<NavKey>(ChatsRoute, ConversationRoute(slot, 2))
        assertFalse(stack.followProfile(paneOf))
        assertEquals(listOf<NavKey>(ChatsRoute, ConversationRoute(slot, 2)), stack)
    }

    @Test
    fun nahledOdpovidaOpravenemuZasobniku() {
        val stack = listOf<NavKey>(ChatsRoute, ChatProfileRoute(slot, 1), ConversationRoute(slot, 2))
        assertEquals(stack.toMutableList().follow(), stack.withProfileFollowing(paneOf))
    }
}
