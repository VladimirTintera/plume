package eu.tintera.plume.app.shell

import androidx.navigation3.runtime.NavKey
import eu.tintera.plume.core.adaptive.PaneInfo
import eu.tintera.plume.core.adaptive.PaneRole
import eu.tintera.plume.core.navigation.ChatCalendarRoute
import eu.tintera.plume.core.navigation.ChatProfileRoute
import eu.tintera.plume.core.navigation.ChatsRoute
import eu.tintera.plume.core.navigation.ConversationRoute
import eu.tintera.plume.core.navigation.ForumTopicsRoute
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Kalendář zpráv jako třetí panel: otevírá se a zavírá z ⋮ ([toggleCalendar]) a při přepnutí
 * chatu přejde na kalendář nového ([followProfile]), jako profil. Role zrcadlí `AppShell`.
 */
class CalendarPaneTest {

    private val paneOf: (NavKey) -> PaneInfo? = { key ->
        when (key) {
            ChatsRoute -> PaneInfo(PaneRole.List, Unit)
            is ConversationRoute, is ForumTopicsRoute -> PaneInfo(PaneRole.Detail, Unit)
            is ChatProfileRoute, is ChatCalendarRoute -> PaneInfo(PaneRole.Extra, Unit)
            else -> null
        }
    }

    @Test
    fun jinyChatPrevezmeKalendarNeProfil() {
        val stack = mutableListOf<NavKey>(ChatsRoute, ChatCalendarRoute(1, anchor = 40), ConversationRoute(2))
        stack.followProfile(paneOf)
        assertEquals(listOf<NavKey>(ChatsRoute, ConversationRoute(2), ChatCalendarRoute(2)), stack)
    }

    @Test
    fun tematuSkupinyKalendarNepatriPanelSeZavre() {
        val stack = mutableListOf<NavKey>(ChatsRoute, ChatCalendarRoute(1), ConversationRoute(2, topicId = 7))
        stack.followProfile(paneOf)
        assertEquals(listOf<NavKey>(ChatsRoute, ConversationRoute(2, topicId = 7)), stack)
    }

    @Test
    fun kalendarNahradiOtevrenyProfil() {
        val stack = mutableListOf<NavKey>(ChatsRoute, ConversationRoute(1), ChatProfileRoute(1))
        stack.toggleCalendar(ConversationRoute(1), ChatCalendarRoute(1, anchor = 9))
        assertEquals(listOf<NavKey>(ChatsRoute, ConversationRoute(1), ChatCalendarRoute(1, anchor = 9)), stack)
    }

    @Test
    fun druheOtevreniKalendarZavre() {
        val stack = mutableListOf<NavKey>(ChatsRoute, ConversationRoute(1), ChatCalendarRoute(1, anchor = 9))
        stack.toggleCalendar(ConversationRoute(1), ChatCalendarRoute(1, anchor = 12))
        assertEquals(listOf<NavKey>(ChatsRoute, ConversationRoute(1)), stack)
    }
}
