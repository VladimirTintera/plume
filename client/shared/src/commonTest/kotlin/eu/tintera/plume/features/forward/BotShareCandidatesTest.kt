package eu.tintera.plume.features.forward

import eu.tintera.plume.core.telegram.BotChatRequest
import eu.tintera.plume.core.telegram.BotUsersRequest
import eu.tintera.plume.core.telegram.ChatKind
import eu.tintera.plume.core.telegram.ChatSummary
import kotlin.test.Test
import kotlin.test.assertEquals

/** Koho nabídne výběr pro tlačítko bota „vyber lidi" / „vyber chat". */
class BotShareCandidatesTest {

    private fun chat(id: Long, kind: ChatKind, forum: Boolean = false) = ChatSummary(
        id = id,
        title = "Chat $id",
        unreadCount = 0,
        lastMessagePreview = null,
        isPinned = false,
        order = id,
        avatarFileId = null,
        isMuted = false,
        isMarkedAsUnread = false,
        kind = kind,
        lastMessageTimestamp = null,
        presence = null,
        activity = null,
        draft = null,
        isForum = forum,
    )

    private val jana = chat(1, ChatKind.Private)
    private val me = chat(9, ChatKind.Private)
    private val group = chat(-2, ChatKind.Group)
    private val forum = chat(-3, ChatKind.Group, forum = true)
    private val channel = chat(-4, ChatKind.Channel)
    private val all = listOf(jana, me, group, forum, channel)

    private val users = BotUsersRequest(buttonId = 1, bots = null, maxQuantity = 3)

    private fun chatRequest(channel: Boolean = false, forum: Boolean? = null) = BotChatRequest(
        buttonId = 2,
        channel = channel,
        forum = forum,
        botIsMember = false,
        botAdministrator = false,
    )

    @Test
    fun lideJsouSoukromeChatyBezUlozenychZprav() {
        assertEquals(listOf(jana), botShareCandidates(all, users, null, savedMessagesChatId = 9))
    }

    @Test
    fun skupinaBezOhleduNaTemata() {
        assertEquals(listOf(group, forum), botShareCandidates(all, null, chatRequest(), savedMessagesChatId = 9))
    }

    @Test
    fun skupinaSTematyNeboBezNich() {
        assertEquals(listOf(forum), botShareCandidates(all, null, chatRequest(forum = true), savedMessagesChatId = 9))
        assertEquals(listOf(group), botShareCandidates(all, null, chatRequest(forum = false), savedMessagesChatId = 9))
    }

    @Test
    fun kanal() {
        assertEquals(listOf(channel), botShareCandidates(all, null, chatRequest(channel = true), savedMessagesChatId = 9))
    }
}
