package eu.tintera.plume.features.settings

import eu.tintera.plume.core.telegram.BlockedSender
import eu.tintera.plume.core.telegram.BlockedSendersPage
import eu.tintera.plume.core.telegram.ChatKind
import eu.tintera.plume.features.chats.FakeChatRepository
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

@OptIn(ExperimentalCoroutinesApi::class)
class BlockedSendersViewModelTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun sender(id: Long) = BlockedSender(id, "Odesílatel $id", null, ChatKind.Private, null, false)

    /** Server, který po odblokování ještě chvíli vrací starý seznam. */
    private class StaleServer(private val senders: List<BlockedSender>) : FakeChatRepository() {
        val changes = mutableListOf<Pair<Long, Boolean>>()
        override suspend fun blockedSenders(offset: Int, limit: Int) =
            Result.success(BlockedSendersPage(senders, senders.size))
        override suspend fun setSenderBlocked(senderId: Long, blocked: Boolean): Result<Unit> {
            changes += senderId to blocked
            return Result.success(Unit)
        }
    }

    /** Ověřeno na emulátoru: odblokovaný bot po přečtení seznamu v něm zůstal. */
    @Test
    fun odblokovanySeNevratiAniSeZastaralouOdpovedi() = runTest {
        val server = StaleServer(listOf(sender(1), sender(2)))
        val viewModel = BlockedSendersViewModel(server)
        viewModel.reload()
        advanceUntilIdle()

        viewModel.unblock(sender(1))
        advanceUntilIdle()

        assertEquals(listOf(2L), viewModel.state.value.senders?.map { it.id })
        assertEquals(listOf(1L to false), server.changes)
        assertEquals(1L, viewModel.state.value.unblocked?.id)
    }

    @Test
    fun vratitOdesilateleVratiDoSeznamu() = runTest {
        val server = StaleServer(listOf(sender(1), sender(2)))
        val viewModel = BlockedSendersViewModel(server)
        viewModel.reload()
        advanceUntilIdle()

        viewModel.unblock(sender(1))
        advanceUntilIdle()
        viewModel.undoUnblock(sender(1))
        advanceUntilIdle()

        assertEquals(listOf(1L, 2L), viewModel.state.value.senders?.map { it.id })
        assertEquals(listOf(1L to false, 1L to true), server.changes)
    }
}
