package eu.tintera.plume.app.shell

import androidx.navigation3.runtime.NavKey
import eu.tintera.plume.core.accounts.AccountSlot
import eu.tintera.plume.core.adaptive.PaneInfo
import eu.tintera.plume.core.adaptive.PaneRole
import eu.tintera.plume.core.navigation.ArchiveChatProfileRoute
import eu.tintera.plume.core.navigation.ArchiveConversationRoute
import eu.tintera.plume.core.navigation.ArchiveRoute
import eu.tintera.plume.core.navigation.ChatCalendarRoute
import eu.tintera.plume.core.navigation.ChatProfileRoute
import eu.tintera.plume.core.navigation.ChatsRoute
import eu.tintera.plume.core.navigation.ConversationRoute
import eu.tintera.plume.features.conversation.ChatTarget
import kotlin.test.Test
import kotlin.test.assertEquals

private val slot = AccountSlot.Primary

/**
 * Vlákno komentářů v bočním sloupci vedle kanálu ([openCommentsBeside], `ConversationRoute.beside`).
 * Role panelů zrcadlí metadata v `AppShell`: vlákno ve sloupci je Extra, jinak konverzace Detail.
 */
class CommentsBesideTest {

    private val archive = "archive"

    private val paneOf: (NavKey) -> PaneInfo? = { key ->
        when (key) {
            ChatsRoute -> PaneInfo(PaneRole.List, Unit)
            ArchiveRoute -> PaneInfo(PaneRole.List, archive)
            is ConversationRoute -> PaneInfo(if (key.beside) PaneRole.Extra else PaneRole.Detail, Unit)
            is ArchiveConversationRoute -> PaneInfo(if (key.beside) PaneRole.Extra else PaneRole.Detail, archive)
            is ChatProfileRoute, is ChatCalendarRoute -> PaneInfo(PaneRole.Extra, Unit)
            is ArchiveChatProfileRoute -> PaneInfo(PaneRole.Extra, archive)
            else -> null
        }
    }

    private val channel = ConversationRoute(slot, chatId = 1)
    private fun thread(id: Long) = ConversationRoute(slot, chatId = 100, threadId = id, beside = true)
    private fun comments(id: Long) = ChatTarget(chatId = 100, threadId = id)

    @Test
    fun cipOtevreKomentareVedleKanalu() {
        val stack = mutableListOf<NavKey>(ChatsRoute, channel)
        stack.openTarget(slot, comments(5), archive = false, from = channel, beside = true)
        assertEquals(listOf<NavKey>(ChatsRoute, channel, thread(5)), stack)
    }

    @Test
    fun naTelefonuKomentareNahradiKanal() {
        val stack = mutableListOf<NavKey>(ChatsRoute, channel)
        stack.openTarget(slot, comments(5), archive = false, from = channel, beside = false)
        assertEquals(listOf<NavKey>(ChatsRoute, channel, ConversationRoute(slot, 100, threadId = 5)), stack)
    }

    @Test
    fun komentareVystridajiProfilVeSloupci() {
        val stack = mutableListOf<NavKey>(ChatsRoute, channel, ChatProfileRoute(slot, 1), ChatProfileRoute(slot, 9))
        stack.openTarget(slot, comments(5), archive = false, from = channel, beside = true)
        assertEquals(listOf<NavKey>(ChatsRoute, channel, thread(5)), stack)
    }

    @Test
    fun jinyPrispevekVymeniVlakno() {
        val stack = mutableListOf<NavKey>(ChatsRoute, channel, thread(5))
        stack.openTarget(slot, comments(6), archive = false, from = channel, beside = true)
        assertEquals(listOf<NavKey>(ChatsRoute, channel, thread(6)), stack)
    }

    @Test
    fun totezVlaknoPodruheNicNedela() {
        val stack = mutableListOf<NavKey>(ChatsRoute, channel, thread(5))
        stack.openTarget(slot, comments(5), archive = false, from = channel, beside = true)
        assertEquals(listOf<NavKey>(ChatsRoute, channel, thread(5)), stack)
    }

    @Test
    fun odkazZVlaknaVeSloupciZustaneUKanalu() {
        val stack = mutableListOf<NavKey>(ChatsRoute, channel, thread(5), ChatProfileRoute(slot, 9))
        stack.openTarget(slot, comments(6), archive = false, from = thread(5), beside = true)
        assertEquals(listOf<NavKey>(ChatsRoute, channel, thread(6)), stack)
    }

    @Test
    fun archivMaVlaknoVeSvescene() {
        val archived = ArchiveConversationRoute(slot, chatId = 1)
        val stack = mutableListOf<NavKey>(ChatsRoute, ArchiveRoute, archived)
        stack.openTarget(slot, comments(5), archive = true, from = archived, beside = true)
        assertEquals(
            listOf<NavKey>(ChatsRoute, ArchiveRoute, archived, ArchiveConversationRoute(slot, 100, threadId = 5, beside = true)),
            stack,
        )
    }

    @Test
    fun zminkaZKomentaruZavreSloupecBezProfilu() {
        // Zmínka ve vlákně otevře jiný chat nad ním: sloupec patřil kanálu, profil se nepřenáší.
        val other = ConversationRoute(slot, chatId = 2)
        val stack = mutableListOf<NavKey>(ChatsRoute, channel, thread(5), ChatProfileRoute(slot, 9), other)
        stack.followProfile(paneOf)
        assertEquals(listOf<NavKey>(ChatsRoute, channel, other), stack)
    }

    @Test
    fun zakrytyProfilSeDalPrenasi() {
        val other = ConversationRoute(slot, chatId = 2)
        val stack = mutableListOf<NavKey>(ChatsRoute, channel, ChatProfileRoute(slot, 1), other)
        stack.followProfile(paneOf)
        assertEquals(listOf<NavKey>(ChatsRoute, channel, other, ChatProfileRoute(slot, 2)), stack)
    }

    @Test
    fun sipkaVeSloupciZavreJenVlakno() {
        val stack = mutableListOf<NavKey>(ChatsRoute, channel, thread(5))
        stack.closeConversation(thread(5), list = ChatsRoute, sideBySide = true)
        assertEquals(listOf<NavKey>(ChatsRoute, channel), stack)
    }

    @Test
    fun prepinacPaneluVKanaluZavreKomentare() {
        val stack = mutableListOf<NavKey>(ChatsRoute, channel, thread(5))
        stack.toggleProfile(channel, ChatProfileRoute(slot, 1), sideBySide = true)
        assertEquals(listOf<NavKey>(ChatsRoute, channel), stack)
    }

    @Test
    fun profilClenaNadVlaknemNeniKorenSloupce() {
        val stack = listOf<NavKey>(ChatsRoute, channel, thread(5), ChatProfileRoute(slot, 9))
        assertEquals(false, stack.standsOnDetail(ChatProfileRoute(slot, 9)))
    }
}
