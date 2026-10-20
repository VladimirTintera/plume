package eu.tintera.plume.features.channellimit

import eu.tintera.plume.core.navigation.ChannelLimitRequests
import eu.tintera.plume.core.telegram.ChatKind
import eu.tintera.plume.core.telegram.ChatSummary
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.features.chats.FakeChatRepository
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.channel_limit_failed
import eu.tintera.plume.resources.channel_limit_leave_failed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ChannelLimitViewModelTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun group(id: Long) = ChatSummary(
        id = id,
        title = "Skupina $id",
        unreadCount = 0,
        lastMessagePreview = null,
        isPinned = false,
        order = id,
        avatarFileId = null,
        isMuted = false,
        isMarkedAsUnread = false,
        kind = ChatKind.Group,
        lastMessageTimestamp = null,
        presence = null,
        activity = null,
        draft = null,
    )

    /** Server s neaktivními skupinami; opuštění chatu z [failing] selže. */
    private class Server(
        private val inactive: Result<List<ChatSummary>>,
        private val failing: Set<Long> = emptySet(),
    ) : FakeChatRepository() {
        val left = mutableListOf<Long>()
        override suspend fun inactiveGroups() = inactive
        override suspend fun deleteOrLeave(chatId: Long): Result<Unit> {
            left += chatId
            return if (chatId in failing) Result.failure(IllegalStateException("CHANNEL_PRIVATE")) else Result.success(Unit)
        }
    }

    private val requests = ChannelLimitRequests().apply { request() }

    @Test
    fun naprvniNicVybraneAOpusteniVybranychSheetZavre() = runTest {
        val server = Server(Result.success(listOf(group(1), group(2), group(3))))
        val viewModel = ChannelLimitViewModel(server, requests)
        viewModel.load()
        advanceUntilIdle()
        assertEquals(listOf(1L, 2L, 3L), viewModel.state.value.chats?.map { it.id })
        assertTrue(viewModel.state.value.selected.isEmpty(), "opouští se chaty — rozhoduje uživatel")

        viewModel.leaveSelected()
        advanceUntilIdle()
        assertTrue(server.left.isEmpty(), "bez výběru se nic neopouští")

        viewModel.toggle(1)
        viewModel.toggle(3)
        viewModel.leaveSelected()
        advanceUntilIdle()

        assertEquals(setOf(1L, 3L), server.left.toSet())
        assertFalse(requests.shown.value, "místo se uvolnilo, sheet odejde")
    }

    @Test
    fun coNeprosloZustaneVybraneSHlaskou() = runTest {
        val server = Server(Result.success(listOf(group(1), group(2), group(3))), failing = setOf(3))
        val viewModel = ChannelLimitViewModel(server, requests)
        viewModel.load()
        advanceUntilIdle()

        viewModel.toggle(1)
        viewModel.toggle(3)
        viewModel.leaveSelected()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(listOf(2L, 3L), state.chats?.map { it.id }, "opuštěný zmizí, neopuštěný zůstane")
        assertEquals(setOf(3L), state.selected)
        assertFalse(state.leaving)
        assertEquals(UiText(Res.string.channel_limit_leave_failed), state.error)
        assertTrue(requests.shown.value)
    }

    @Test
    fun nenactenySeznamJePrazdnySHlaskou() = runTest {
        val viewModel = ChannelLimitViewModel(Server(Result.failure(IllegalStateException("x"))), requests)
        viewModel.load()
        advanceUntilIdle()
        assertEquals(emptyList(), viewModel.state.value.chats)
        assertEquals(UiText(Res.string.channel_limit_failed), viewModel.state.value.error)
    }
}
