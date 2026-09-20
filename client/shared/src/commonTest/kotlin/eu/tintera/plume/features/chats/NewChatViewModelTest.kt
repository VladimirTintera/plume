package eu.tintera.plume.features.chats

import eu.tintera.plume.core.telegram.ChatActivity
import eu.tintera.plume.core.telegram.ChatFolder
import eu.tintera.plume.core.telegram.ChatListState
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.core.telegram.ChatSummary
import eu.tintera.plume.core.telegram.FoundMessagesPage
import eu.tintera.plume.core.telegram.InternalLink
import eu.tintera.plume.core.telegram.InviteJoinResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NewChatViewModelTest {

    @Test
    fun nacteniKontaktuPoStartu() = runTest {
        val contact1 = testChat(1, "Alice")
        val contact2 = testChat(2, "Bob")
        val fakeRepo = FakeChatRepository(contactsResult = Result.success(listOf(contact1, contact2)))

        val viewModel = NewChatViewModel(fakeRepo)

        val state = viewModel.state.first { !it.loading }
        assertEquals(2, state.contacts.size)
        assertEquals("Alice", state.contacts[0].title)
        assertEquals("Bob", state.contacts[1].title)
        assertNull(state.error)
    }

    @Test
    fun selhaniNacteniKontaktuUkazeChybu() = runTest {
        val fakeRepo = FakeChatRepository(contactsResult = Result.failure(RuntimeException("Network error")))

        val viewModel = NewChatViewModel(fakeRepo)

        val state = viewModel.state.first { !it.loading }
        assertTrue(state.contacts.isEmpty())
        assertNotNull(state.error)

        viewModel.consumeError()
        val errorClearedState = viewModel.state.first { it.error == null }
        assertNull(errorClearedState.error)
    }

    @Test
    fun filtraceKontaktuPodleDotazu() = runTest {
        val contact1 = testChat(1, "Alice Smith")
        val contact2 = testChat(2, "Bob Jones")
        val fakeRepo = FakeChatRepository(contactsResult = Result.success(listOf(contact1, contact2)))

        val viewModel = NewChatViewModel(fakeRepo)
        viewModel.state.first { !it.loading }

        viewModel.onQueryChange("alice")

        val filteredState = viewModel.state.first { it.query == "alice" }
        assertEquals(1, filteredState.contacts.size)
        assertEquals("Alice Smith", filteredState.contacts[0].title)
    }

    private fun testChat(id: Long, title: String) = ChatSummary(
        id = id,
        title = title,
        unreadCount = 0,
        lastMessagePreview = null,
        isPinned = false,
        order = id,
        avatarFileId = null,
        isMuted = false,
        isMarkedAsUnread = false,
        isPrivate = true,
        lastMessageTimestamp = null,
        presence = null,
        activity = null,
        draft = null,
    )

    private class FakeChatRepository(
        private val contactsResult: Result<List<ChatSummary>> = Result.success(emptyList()),
    ) : ChatRepository {
        override val savedMessagesChatId: StateFlow<Long?> = MutableStateFlow(999L)

        override fun chats(folder: ChatFolder): StateFlow<ChatListState> =
            MutableStateFlow(ChatListState.Ready(emptyList(), canLoadMore = false))

        override fun activity(chatId: Long): Flow<ChatActivity?> = emptyFlow()

        override suspend fun openSavedMessages(): Result<Long> = Result.success(999L)

        override suspend fun resolveLink(url: String): Result<InternalLink?> = Result.success(null)

        override suspend fun joinByInvite(link: String): Result<InviteJoinResult> =
            Result.failure(UnsupportedOperationException())

        override suspend fun chatByUsername(username: String): Result<Long> =
            Result.failure(UnsupportedOperationException())

        override suspend fun privateChat(userId: Long): Result<Long> = Result.success(userId)

        override suspend fun contacts(): Result<List<ChatSummary>> = contactsResult

        override suspend fun searchChats(query: String, onServer: Boolean): Result<List<ChatSummary>> =
            Result.success(emptyList())

        override suspend fun searchPublicChats(query: String): Result<List<ChatSummary>> =
            Result.success(emptyList())

        override suspend fun searchMessages(query: String, offset: String): Result<FoundMessagesPage> =
            Result.success(FoundMessagesPage(emptyList(), null))

        override suspend fun recentlyFoundChats(): Result<List<ChatSummary>> = Result.success(emptyList())

        override suspend fun addRecentlyFound(chatId: Long): Result<Unit> = Result.success(Unit)

        override suspend fun clearRecentlyFound(): Result<Unit> = Result.success(Unit)

        override suspend fun topChats(): Result<List<ChatSummary>> = Result.success(emptyList())

        override suspend fun loadMore(folder: ChatFolder) {}

        override suspend fun setPinned(chatId: Long, folder: ChatFolder, pinned: Boolean): Result<Unit> =
            Result.success(Unit)

        override suspend fun setArchived(chatId: Long, archived: Boolean): Result<Unit> =
            Result.success(Unit)

        override fun muted(chatId: Long): Flow<Boolean?> = emptyFlow()

        override suspend fun setMuted(chatId: Long, muted: Boolean): Result<Unit> = Result.success(Unit)

        override suspend fun setMarkedAsUnread(chatId: Long, unread: Boolean): Result<Unit> =
            Result.success(Unit)

        override suspend fun deleteOrLeave(chatId: Long): Result<Unit> = Result.success(Unit)
    }
}
