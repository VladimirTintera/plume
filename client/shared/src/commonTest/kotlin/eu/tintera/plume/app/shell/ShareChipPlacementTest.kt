package eu.tintera.plume.app.shell

import eu.tintera.plume.core.accounts.AccountSlot
import androidx.navigation3.runtime.NavKey
import eu.tintera.plume.core.adaptive.PaneInfo
import eu.tintera.plume.core.adaptive.PaneRole
import eu.tintera.plume.core.navigation.ArchiveConversationRoute
import eu.tintera.plume.core.navigation.ArchiveRoute
import eu.tintera.plume.core.navigation.ChatProfileRoute
import eu.tintera.plume.core.navigation.ChatsRoute
import eu.tintera.plume.core.navigation.ConversationRoute
import eu.tintera.plume.core.navigation.ForumTopicsRoute
import eu.tintera.plume.core.navigation.MediaRoute
import eu.tintera.plume.core.navigation.ShareToChatRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val slot = AccountSlot.Primary

/**
 * Kde visí čip sdíleného obsahu ([showsShareChip]) a do které konverzace z něj jde vložit
 * ([shareInsertChat]).
 */
class ShareChipPlacementTest {

    private val archive = "archive"

    private val paneOf: (NavKey) -> PaneInfo? = { key ->
        when (key) {
            ChatsRoute -> PaneInfo(PaneRole.List, Unit)
            ArchiveRoute -> PaneInfo(PaneRole.List, archive)
            is ConversationRoute, is ForumTopicsRoute -> PaneInfo(PaneRole.Detail, Unit)
            is ArchiveConversationRoute -> PaneInfo(PaneRole.Detail, archive)
            is ChatProfileRoute -> PaneInfo(PaneRole.Extra, Unit)
            else -> null
        }
    }

    @Test
    fun cipVisiNadSeznamemKonverzaciIProfilem() {
        assertTrue(listOf<NavKey>(ChatsRoute).showsShareChip(paneOf))
        assertTrue(listOf(ChatsRoute, ConversationRoute(slot, 1)).showsShareChip(paneOf))
        assertTrue(listOf(ChatsRoute, ConversationRoute(slot, 1), ChatProfileRoute(slot, 1)).showsShareChip(paneOf))
    }

    @Test
    fun nadObrazovkouPresCelouPlochuCipNeni() {
        assertFalse(listOf(ChatsRoute, ConversationRoute(slot, 1), MediaRoute(slot, 1, 5)).showsShareChip(paneOf))
        assertFalse(listOf(ChatsRoute, ShareToChatRoute(slot, 1)).showsShareChip(paneOf))
    }

    @Test
    fun naTelefonuJenKonverzaceNavrchu() {
        assertEquals(1, listOf(ChatsRoute, ConversationRoute(slot, 1)).shareInsertChat(paneOf, sideBySide = false))
        assertEquals(2, listOf(ChatsRoute, ArchiveRoute, ArchiveConversationRoute(slot, 2)).shareInsertChat(paneOf, sideBySide = false))
        // Profil konverzaci na telefonu zakrývá.
        assertNull(listOf(ChatsRoute, ConversationRoute(slot, 1), ChatProfileRoute(slot, 1)).shareInsertChat(paneOf, sideBySide = false))
        assertNull(listOf<NavKey>(ChatsRoute).shareInsertChat(paneOf, sideBySide = false))
        assertNull(listOf(ChatsRoute, ForumTopicsRoute(slot, 3)).shareInsertChat(paneOf, sideBySide = false))
    }

    @Test
    fun vedleSeznamuIPodProfilem() {
        val stack = listOf(ChatsRoute, ConversationRoute(slot, 1), ChatProfileRoute(slot, 1))
        assertEquals(1, stack.shareInsertChat(paneOf, sideBySide = true))
        assertEquals(1, listOf(ChatsRoute, ConversationRoute(slot, 1)).shareInsertChat(paneOf, sideBySide = true))
    }

    @Test
    fun vedleSeznamuBezKonverzaceNicNebo() {
        // Placeholder vpravo.
        assertNull(listOf<NavKey>(ChatsRoute).shareInsertChat(paneOf, sideBySide = true))
        // Archiv nad konverzací hlavního seznamu: ta vidět není, vpravo je placeholder archivu.
        assertNull(listOf(ChatsRoute, ConversationRoute(slot, 1), ArchiveRoute).shareInsertChat(paneOf, sideBySide = true))
        // Prohlížeč přes celou plochu konverzaci zakrývá.
        assertNull(listOf(ChatsRoute, ConversationRoute(slot, 1), MediaRoute(slot, 1, 5)).shareInsertChat(paneOf, sideBySide = true))
    }
}
