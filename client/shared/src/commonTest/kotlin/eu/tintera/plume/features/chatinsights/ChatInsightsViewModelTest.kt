package eu.tintera.plume.features.chatinsights

import eu.tintera.plume.core.telegram.MessageStat
import eu.tintera.plume.core.telegram.MessageStatKind
import eu.tintera.plume.core.telegram.MessageStatsPage
import eu.tintera.plume.core.telegram.SenderInfo
import eu.tintera.plume.core.telegram.SharedMediaCounts
import eu.tintera.plume.core.telegram.UnimplementedConversationRepository
import eu.tintera.plume.features.conversation.testChatInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ChatInsightsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    /**
     * Historie [count] zpráv (id 1 = nejstarší); lokálně jsou jen ty s id > [localFrom].
     * Stránkuje jako TDLib: od `fromMessageId` (bez ní) ke starším.
     */
    private class FakeRepository(count: Int, private val localFrom: Long = 0, var failLocal: Boolean = false) : UnimplementedConversationRepository() {
        private val all = (1L..count).map { MessageStat(it, 1_700_000_000 + it.toInt() * 60, senderId = if (it % 2 == 0L) 7 else 8, isOutgoing = it % 2 == 0L, kind = MessageStatKind.Text, characters = 3) }
        val calls = mutableListOf<Pair<Long, Boolean>>()

        override suspend fun messageStats(chatId: Long, fromMessageId: Long, onlyLocal: Boolean, maxMessages: Int): Result<MessageStatsPage> {
            calls += fromMessageId to onlyLocal
            if (onlyLocal && failLocal) return Result.failure(IllegalStateException("db"))
            val older = all.filter { (fromMessageId == 0L || it.id < fromMessageId) && (!onlyLocal || it.id > localFrom) }
                .sortedByDescending { it.id }
            val page = older.take(maxMessages)
            return Result.success(MessageStatsPage(page, page.lastOrNull()?.id, reachedEnd = page.size == older.size))
        }

        override suspend fun sharedMediaCounts(chatId: Long) = Result.success(SharedMediaCounts(media = 3, files = 0, links = 1))

        override suspend fun sender(senderId: Long) = Result.success(SenderInfo(senderId, "Člověk $senderId", null))

        override suspend fun chatInfo(chatId: Long) = Result.success(testChatInfo())
    }

    private fun viewModel(repository: FakeRepository) = ChatInsightsViewModel(
        chatId = 1,
        repository = repository,
        zone = { TimeZone.UTC },
        today = { LocalDate(2026, 11, 27) },
        computeContext = dispatcher,
    )

    @Test
    fun lokalniPrubehAPakNacistStarsiAzKZacatku() = runTest(dispatcher) {
        // 2 500 zpráv, v zařízení posledních 2 200 → lokálně dva úseky (2 000 + 200).
        val repository = FakeRepository(count = 2_500, localFrom = 300)
        val model = viewModel(repository)
        advanceUntilIdle()

        val local = model.state.value
        assertEquals(2_200, local.insights?.messageCount)
        assertFalse(local.scanning)
        assertTrue(local.canLoadOlder)
        assertEquals(listOf(0L to true, 501L to true), repository.calls)
        assertEquals(3, local.mediaCounts?.media)
        assertEquals("Člověk 8", local.senders[8]?.title)
        // Soukromý chat: odpovědi se počítají.
        assertNotNull(local.insights?.replies)

        model.loadOlder()
        advanceUntilIdle()
        val all = model.state.value
        assertEquals(2_500, all.insights?.messageCount)
        assertTrue(all.reachedStart)
        assertFalse(all.canLoadOlder)
        assertEquals(301L to false, repository.calls.last())
    }

    @Test
    fun prazdneZarizeniSiRovnouReknePoServer() = runTest(dispatcher) {
        val repository = FakeRepository(count = 50, localFrom = 50)
        val model = viewModel(repository)
        advanceUntilIdle()

        assertEquals(50, model.state.value.insights?.messageCount)
        assertTrue(model.state.value.reachedStart)
        assertEquals(listOf(0L to true, 0L to false), repository.calls)
    }

    @Test
    fun chybaLokalnihoPruchoduUkazeChybuASeZkusiZnovu() = runTest(dispatcher) {
        val repository = FakeRepository(count = 10, failLocal = true)
        val model = viewModel(repository)
        advanceUntilIdle()
        assertNotNull(model.state.value.failure)

        repository.failLocal = false
        model.load()
        advanceUntilIdle()
        assertEquals(10, model.state.value.insights?.messageCount)
    }
}
