package eu.tintera.plume.features.chats

import eu.tintera.plume.core.accounts.AccountSlot
import eu.tintera.plume.core.telegram.ChatKind
import eu.tintera.plume.core.telegram.ChatSummary
import eu.tintera.plume.core.telegram.FoundMessage
import eu.tintera.plume.core.ui.UiText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Hledání přes víc účtů: komu výsledek patří, co se slučuje a co zůstává z účtu obrazovky. */
class MergedSearchStateTest {

    private val first = AccountSlot.Primary
    private val second = AccountSlot("bbbbbb")

    @Test
    fun vlastniChatyUcetPoUctuSeSlotem() {
        val merged = mergedSearchState(
            ChatSearchState(),
            listOf(first to ChatSearchState(query = "a", results = listOf(chat(1))), second to ChatSearchState(query = "a", results = listOf(chat(1), chat(2)))),
        )

        assertEquals(listOf(first to 1L, second to 1L, second to 2L), merged.results.map { it.slot to it.id })
        assertEquals("a", merged.query)
    }

    /** Veřejný kanál najdou oba účty; otevřít ho jde kterýmkoli, bere se první. */
    @Test
    fun verejnyChatJenJednou() {
        val merged = mergedSearchState(
            ChatSearchState(),
            listOf(first to ChatSearchState(global = listOf(chat(9))), second to ChatSearchState(global = listOf(chat(9), chat(8)))),
        )

        assertEquals(listOf(first to 9L, second to 8L), merged.global.map { it.slot to it.id })
    }

    @Test
    fun zpravyPodleDataNapricUcty() {
        val merged = mergedSearchState(
            ChatSearchState(),
            listOf(
                first to ChatSearchState(messages = listOf(message(1, date = 10), message(1, date = 30))),
                second to ChatSearchState(messages = listOf(message(2, date = 20))),
            ),
        )

        assertEquals(listOf(30, 20, 10), merged.messages.map { it.date })
        assertEquals(listOf(first, second, first), merged.messages.map { it.chat.slot })
    }

    /** Návrhy a reklamy patří účtu obrazovky. */
    @Test
    fun navrhyZUctuObrazovky() {
        val screen = ChatSearchState(recent = listOf(chat(5)), top = listOf(chat(6)), tags = listOf("#a"))
        val merged = mergedSearchState(screen, listOf(first to ChatSearchState(recent = listOf(chat(7))), second to ChatSearchState()))

        assertEquals(listOf(5L), merged.recent.map { it.id })
        assertEquals(listOf(6L), merged.top.map { it.id })
        assertEquals(listOf("#a"), merged.tags)
    }

    @Test
    fun nacitaniDokudHledaKteryKoliUcet() {
        val merged = mergedSearchState(
            ChatSearchState(),
            listOf(first to ChatSearchState(), second to ChatSearchState(searching = true, messagesNextOffset = "x")),
        )

        assertTrue(merged.searching)
        assertEquals("x", merged.messagesNextOffset)
        assertFalse(mergedSearchState(ChatSearchState(), listOf(first to ChatSearchState())).searching)
    }

    private fun chat(id: Long) = ChatSummary(
        id = id,
        title = "Chat $id",
        unreadCount = 0,
        lastMessagePreview = null,
        isPinned = false,
        order = id,
        avatarFileId = null,
        isMuted = false,
        isMarkedAsUnread = false,
        kind = ChatKind.Private,
        lastMessageTimestamp = null,
        presence = null,
        activity = null,
        draft = null,
    )

    private fun message(chatId: Long, date: Int) =
        FoundMessage(chat = chat(chatId), messageId = date.toLong(), text = UiText.Raw("x"), date = date, isOutgoing = false)
}
