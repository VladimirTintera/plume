package eu.tintera.plume.features.calls

import eu.tintera.plume.core.calls.CallAudioOutput
import eu.tintera.plume.core.telegram.ActiveCall
import eu.tintera.plume.core.telegram.CallHistoryEntry
import eu.tintera.plume.core.telegram.CallHistoryPage
import eu.tintera.plume.core.telegram.CallOutcome
import eu.tintera.plume.core.telegram.CallPhase
import eu.tintera.plume.core.telegram.CallRepository
import eu.tintera.plume.core.telegram.ChatKind
import eu.tintera.plume.core.telegram.ChatSummary
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.features.chats.FakeChatRepository
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.calls_load_failed
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CallsViewModelTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun chat(id: Long) = ChatSummary(
        id = id,
        title = "Člověk $id",
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

    private fun entry(chatId: Long, messageId: Long, outcome: CallOutcome = CallOutcome.Outgoing) = CallHistoryEntry(
        chat = chat(chatId),
        messageId = messageId,
        outcome = outcome,
        isVideo = false,
        durationSeconds = 60,
        isGroup = false,
        date = 0,
        callUserId = chatId,
    )

    /** Server s historií po stránkách: offset je číslo stránky, zmeškané zvlášť. */
    private class Server(
        val pages: List<List<CallHistoryEntry>>,
        val missed: List<CallHistoryEntry> = emptyList(),
    ) : FakeChatRepository() {
        val requests = mutableListOf<Pair<String, Boolean>>()
        var gate: CompletableDeferred<Unit>? = null
        var fail = false
        val deleted = mutableListOf<Boolean>()

        override suspend fun callHistory(offset: String, onlyMissed: Boolean): Result<CallHistoryPage> {
            requests += offset to onlyMissed
            gate?.await()
            if (fail) return Result.failure(IllegalStateException("x"))
            if (onlyMissed) return Result.success(CallHistoryPage(missed, ""))
            val index = offset.toIntOrNull() ?: 0
            val next = if (index + 1 < pages.size) "${index + 1}" else ""
            return Result.success(CallHistoryPage(pages[index], next))
        }

        override suspend fun deleteCallHistory(revoke: Boolean): Result<Unit> {
            deleted += revoke
            return Result.success(Unit)
        }
    }

    /** Hovory jen tolik, kolik ViewModel potřebuje: stav probíhajícího hovoru. */
    private class Calls : CallRepository {
        val activeFlow = MutableStateFlow<ActiveCall?>(null)
        override val supported = true
        override val supportsVideo = false
        override val active: StateFlow<ActiveCall?> = activeFlow
        override suspend fun call(userId: Long, video: Boolean) = Result.success(Unit)
        override suspend fun accept(camera: Boolean) = Result.success(Unit)
        override suspend fun hangUp() = Unit
        override fun setMuted(muted: Boolean) = Unit
        override fun setHeld(held: Boolean) = Unit
        override suspend fun resume() = Unit
        override fun setResumeHandler(handler: (suspend () -> Boolean)?) = Unit
        override fun setOutput(output: CallAudioOutput) = Unit
        override fun setCamera(on: Boolean) = Unit
        override fun previewCamera(on: Boolean) = Unit
        override fun switchCamera() = Unit
        override fun reportFailure(userId: Long, video: Boolean, reason: UiText) = Unit
    }

    @Test
    fun strankujeOdNejnovejsihoATenSamyHovorJenJednou() = runTest {
        val server = Server(listOf(listOf(entry(1, 10), entry(2, 9)), listOf(entry(2, 9), entry(3, 8))))
        val viewModel = CallsViewModel(server, Calls())
        advanceUntilIdle()
        assertEquals(listOf(10L, 9L), viewModel.state.value.entries?.map { it.messageId })

        viewModel.loadMore()
        advanceUntilIdle()
        assertEquals(listOf(10L, 9L, 8L), viewModel.state.value.entries?.map { it.messageId })
        assertTrue(viewModel.state.value.endReached)

        viewModel.loadMore()
        advanceUntilIdle()
        assertEquals(listOf("" to false, "1" to false), server.requests, "po konci se už nečte")
    }

    @Test
    fun zmeskaneCtouOdZacatkuAStaraStrankaSeNeprilepi() = runTest {
        val server = Server(listOf(listOf(entry(1, 10))), missed = listOf(entry(4, 5, CallOutcome.Missed)))
        val viewModel = CallsViewModel(server, Calls())
        server.gate = CompletableDeferred()
        // Přepnutí, zatímco první stránka všech hovorů ještě nedorazila.
        viewModel.setOnlyMissed(true)
        server.gate?.complete(Unit)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.onlyMissed)
        assertEquals(listOf(5L), viewModel.state.value.entries?.map { it.messageId })
    }

    @Test
    fun poSkoncenemHovoruSeHistorieCteZnovu() = runTest {
        val server = Server(listOf(listOf(entry(1, 10))))
        val calls = Calls()
        CallsViewModel(server, calls)
        advanceUntilIdle()
        val call = ActiveCall(id = 1, userId = 1, isOutgoing = true, phase = CallPhase.Active)
        calls.activeFlow.value = call
        advanceUntilIdle()
        calls.activeFlow.value = call.copy(phase = CallPhase.Ended)
        advanceUntilIdle()

        assertEquals(2, server.requests.size)
    }

    @Test
    fun chybaNacteniNechaPrazdnySeznamSHlaskou() = runTest {
        val server = Server(listOf(emptyList())).apply { fail = true }
        val viewModel = CallsViewModel(server, Calls())
        advanceUntilIdle()
        assertEquals(emptyList(), viewModel.state.value.entries)
        assertEquals(UiText(Res.string.calls_load_failed), viewModel.state.value.error)
    }

    @Test
    fun smazaniPredaVolbuIProtejsky() = runTest {
        val server = Server(listOf(listOf(entry(1, 10))))
        val viewModel = CallsViewModel(server, Calls())
        advanceUntilIdle()
        viewModel.deleteAll(revoke = true)
        advanceUntilIdle()
        assertEquals(listOf(true), server.deleted)
    }
}
