package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.telegram.MessageReaction
import eu.tintera.plume.core.telegram.QuickReaction
import eu.tintera.plume.core.telegram.UnimplementedConversationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationReactionsTest {

    private data class Sent(val messageId: Long, val emoji: String, val add: Boolean)

    private class FakeRepository(private val result: Result<Unit> = Result.success(Unit)) : UnimplementedConversationRepository() {
        val sent = mutableListOf<Sent>()

        override suspend fun setReaction(chatId: Long, messageId: Long, emoji: String, add: Boolean): Result<Unit> {
            sent += Sent(messageId, emoji, add)
            return result
        }
    }

    private var menusClosed = 0

    private fun reactions(scope: CoroutineScope, state: MutableStateFlow<ConversationState>, repository: FakeRepository) =
        ConversationReactions(
            chatId = TEST_CHAT,
            state = state,
            scope = scope,
            repository = repository,
            quickReaction = NoQuickReaction,
            closeMenus = { menusClosed++ },
            logger = testLogger,
        )

    private fun reactionsOf(state: MutableStateFlow<ConversationState>, id: Long) =
        state.value.messages.first { it.id == id }.reactions

    @Test
    fun reakceSeUkazeHnedAOdejdeNaServer() = runTest {
        val state = testState(testMessage(1))
        val repository = FakeRepository()
        val reactions = reactions(this, state, repository)

        reactions.toggleReaction(1, "❤")
        assertEquals(listOf(MessageReaction("❤", count = 1, isChosen = true)), reactionsOf(state, 1))
        advanceUntilIdle()

        assertEquals(listOf(Sent(1, "❤", add = true)), repository.sent)
        assertEquals(1, menusClosed)
    }

    @Test
    fun vlastniReakceSeOdebere() = runTest {
        val state = testState(testMessage(1, reactions = listOf(MessageReaction("❤", count = 2, isChosen = true))))
        val repository = FakeRepository()
        val reactions = reactions(this, state, repository)

        reactions.toggleReaction(1, "❤")
        advanceUntilIdle()

        assertEquals(listOf(Sent(1, "❤", add = false)), repository.sent)
        assertEquals(listOf(MessageReaction("❤", count = 1, isChosen = false)), reactionsOf(state, 1))
    }

    @Test
    fun odmitnutaReakceSeVratiAOhlasiSe() = runTest {
        val state = testState(testMessage(1))
        val reactions = reactions(this, state, FakeRepository(Result.failure(IllegalStateException("REACTION_INVALID"))))

        reactions.toggleReaction(1, "❤")
        advanceUntilIdle()

        assertEquals(emptyList(), reactionsOf(state, 1))
        assertNotNull(state.value.error)
    }

    @Test
    fun reakceNaNeznamouZpravuNicNeudela() = runTest {
        val state = testState(testMessage(1))
        val repository = FakeRepository()
        val reactions = reactions(this, state, repository)

        reactions.toggleReaction(99, "❤")
        advanceUntilIdle()

        assertEquals(emptyList(), repository.sent)
        assertNull(state.value.error)
    }

    private object NoQuickReaction : QuickReaction {
        override val reaction = MutableStateFlow<String?>(null)
        override val choices = MutableStateFlow(emptyList<String>())
        override suspend fun set(reaction: String): Result<Unit> = error("v testu se nenastavuje")
    }
}
