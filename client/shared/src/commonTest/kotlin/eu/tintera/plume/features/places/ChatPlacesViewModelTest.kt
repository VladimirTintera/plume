package eu.tintera.plume.features.places

import eu.tintera.plume.core.telegram.ChatLocationsPage
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.telegram.UnimplementedConversationRepository
import eu.tintera.plume.core.telegram.testMessage
import eu.tintera.plume.features.conversation.ConversationJumpRequests
import eu.tintera.plume.features.conversation.JumpTarget
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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ChatPlacesViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()


    /** Lokálně dvě místa (a konec lokální databáze), ze serveru jedno starší a pak začátek chatu. */
    private class FakeRepository(private val localEmpty: Boolean = false) : UnimplementedConversationRepository() {
        val calls = mutableListOf<Pair<Long, Boolean>>()
        override suspend fun chatLocations(chatId: Long, fromMessageId: Long, onlyLocal: Boolean, maxMessages: Int): Result<ChatLocationsPage> {
            calls += fromMessageId to onlyLocal
            return Result.success(
                when {
                    onlyLocal && localEmpty -> ChatLocationsPage(emptyList(), null, reachedEnd = true)
                    onlyLocal -> ChatLocationsPage(listOf(testMessage(30).copy(text = "", media = MessageMedia.Location(50.0, 14.0)), testMessage(20).copy(text = "", media = MessageMedia.Location(50.01, 14.0))), oldestMessageId = 15, reachedEnd = true)
                    else -> ChatLocationsPage(listOf(testMessage(5).copy(text = "", media = MessageMedia.Location(50.02, 14.0))), oldestMessageId = 1, reachedEnd = true)
                },
            )
        }
    }

    @Test
    fun lokalnePakStarsiAzKZacatku() = runTest(dispatcher) {
        val repository = FakeRepository()
        val model = ChatPlacesViewModel(1, repository, ConversationJumpRequests())
        advanceUntilIdle()
        assertEquals(listOf(30L, 20L), model.state.value.places?.map { it.id })
        assertTrue(model.state.value.canLoadOlder)

        model.loadOlder()
        advanceUntilIdle()
        assertEquals(listOf(30L, 20L, 5L), model.state.value.places?.map { it.id })
        assertTrue(model.state.value.reachedStart)
        assertFalse(model.state.value.canLoadOlder)
        assertEquals(listOf(0L to true, 15L to false), repository.calls)
    }

    @Test
    fun prazdneZarizeniSiRovnouReknePoServer() = runTest(dispatcher) {
        val repository = FakeRepository(localEmpty = true)
        val model = ChatPlacesViewModel(1, repository, ConversationJumpRequests())
        advanceUntilIdle()
        assertEquals(listOf(0L to true, 0L to false), repository.calls)
        assertEquals(listOf(5L), model.state.value.places?.map { it.id })
    }

    @Test
    fun skokNaZpravuJdeDoKonverzace() = runTest(dispatcher) {
        val jumps = ConversationJumpRequests()
        val model = ChatPlacesViewModel(7, FakeRepository(), jumps)
        model.jumpToMessage(20)
        assertEquals(7L to JumpTarget.Message(20), assertNotNull(jumps.target.value))
    }
}
