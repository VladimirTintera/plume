package eu.tintera.plume.features.chats

import eu.tintera.plume.core.ui.UNDO_WINDOW_MS
import eu.tintera.plume.core.telegram.ChatKind
import eu.tintera.plume.core.telegram.ChatListState
import eu.tintera.plume.core.telegram.ChatSummary
import eu.tintera.plume.core.telegram.FakeChatRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Smazání chatu s „Vrátit": chat hned zmizí, smaže se až po lhůtě a vrácení ho zruší.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PendingChatDeletesTest {

    private class Chats(private val failing: Set<Long> = emptySet()) : FakeChatRepository() {
        val deleted = mutableListOf<Long>()
        override suspend fun deleteOrLeave(chatId: Long): Result<Unit> {
            if (chatId in failing) return Result.failure(IllegalStateException("CHANNEL_PRIVATE"))
            deleted += chatId
            return Result.success(Unit)
        }
    }

    @Test
    fun smazeSeAzPoLhute() = runTest {
        val chats = Chats()
        val pending = PendingChatDeletes(chats, backgroundScope)

        pending.schedule(listOf(1, 2))
        runCurrent()
        assertEquals(setOf(1L, 2L), pending.pending.value, "Ze seznamu zmizí hned")
        assertTrue(chats.deleted.isEmpty(), "Smazat se nesmí, dokud jde vrátit")

        advanceTimeBy(UNDO_WINDOW_MS + 1)
        assertEquals(listOf(1L, 2L), chats.deleted)
    }

    @Test
    fun vraceniSmazaniZrusi() = runTest {
        val chats = Chats()
        val pending = PendingChatDeletes(chats, backgroundScope)

        val batch = pending.schedule(listOf(1))
        advanceTimeBy(UNDO_WINDOW_MS - 1_000)
        pending.undo(batch, listOf(1))
        advanceTimeBy(UNDO_WINDOW_MS)

        assertTrue(chats.deleted.isEmpty())
        assertTrue(pending.pending.value.isEmpty(), "Vrácený chat je zpátky v seznamu")
    }

    @Test
    fun selhaniChatVratiAOhlasi() = runTest {
        val chats = Chats(failing = setOf(2))
        val pending = PendingChatDeletes(chats, backgroundScope)
        val failure = backgroundScope.launch { pending.failures.first() }

        pending.schedule(listOf(1, 2))
        advanceTimeBy(UNDO_WINDOW_MS + 1)

        assertEquals(listOf(1L), chats.deleted)
        assertEquals(setOf(1L), pending.pending.value, "Nesmazaný je zpátky, smazaný čeká na update")
        runCurrent()
        assertTrue(failure.isCompleted, "Selhání se ohlásí")
    }

    @Test
    fun seznamCekajiciSchova() {
        val list = ChatListState.Ready(listOf(chat(1), chat(2), chat(3)), canLoadMore = false)
        assertEquals(listOf(1L, 3L), (list.without(setOf(2)) as ChatListState.Ready).chats.map { it.id })
        assertEquals(list, list.without(emptySet()))
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
}
