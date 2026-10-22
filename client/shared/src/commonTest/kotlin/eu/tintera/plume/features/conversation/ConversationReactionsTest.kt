package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.telegram.MessageActions
import eu.tintera.plume.core.telegram.MessageReaction
import eu.tintera.plume.core.telegram.QuickReaction
import eu.tintera.plume.core.telegram.ReactionDetail
import eu.tintera.plume.core.telegram.ReactionModeration
import eu.tintera.plume.core.telegram.SenderInfo
import eu.tintera.plume.core.telegram.UnimplementedConversationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
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
        var details = emptyList<ReactionDetail>()
        val deletedFrom = mutableListOf<Long>()

        override suspend fun reactionDetails(chatId: Long, messageId: Long, reaction: String?) =
            Result.success(details.filter { reaction == null || it.emoji == reaction })

        override suspend fun reactionModeration(chatId: Long, messageId: Long) = ReactionModeration(canDelete = true)

        override suspend fun deleteReactionsFrom(chatId: Long, messageId: Long, senderId: Long): Result<Unit> {
            deletedFrom += senderId
            details = details.filter { it.sender.id != senderId }
            return result
        }

        override suspend fun setReaction(chatId: Long, messageId: Long, emoji: String, add: Boolean): Result<Unit> {
            sent += Sent(messageId, emoji, add)
            return result
        }

        var clearedRecent = 0

        override suspend fun clearRecentReactions(): Result<Unit> {
            clearedRecent++
            return result
        }
    }

    private fun withMenu(state: MutableStateFlow<ConversationState>) = state.update {
        it.copy(
            messageMenu = MessageMenu(
                messageId = 1,
                timestamp = 0,
                isOutgoing = false,
                emoji = listOf("❤", "🎃"),
                chosenEmoji = emptySet(),
                actions = MessageActions.Fallback,
                items = emptyList(),
                favoriteSticker = null,
                hasRecentReactions = true,
            ),
        )
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

    /** „Vymazat nedávné" zmizí hned a volání odejde; reakce samy zůstávají nabízené. */
    @Test
    fun vymazaniNedavnychSchovaTlacitko() = runTest {
        val state = testState(testMessage(1))
        withMenu(state)
        val repository = FakeRepository()
        val reactions = reactions(this, state, repository)

        reactions.clearRecentReactions()
        assertEquals(false, state.value.messageMenu?.hasRecentReactions)
        advanceUntilIdle()

        assertEquals(1, repository.clearedRecent)
        assertEquals(listOf("❤", "🎃"), state.value.messageMenu?.emoji)
        assertNull(state.value.error)
    }

    @Test
    fun nepovedeneVymazaniNedavnychTlacitkoVrati() = runTest {
        val state = testState(testMessage(1))
        withMenu(state)
        val reactions = reactions(this, state, FakeRepository(Result.failure(IllegalStateException("FLOOD_WAIT_5"))))

        reactions.clearRecentReactions()
        advanceUntilIdle()

        assertEquals(true, state.value.messageMenu?.hasRecentReactions)
        assertNotNull(state.value.error)
    }

    private fun detail(senderId: Long, emoji: String = "❤") =
        ReactionDetail(SenderInfo(senderId, "Člen $senderId", avatarFileId = null), emoji, isOwn = false)

    @Test
    fun spravceSmazeReakceClenaAPrehledZustane() = runTest {
        val state = testState(testMessage(1))
        val repository = FakeRepository().apply { details = listOf(detail(10), detail(20, "👍")) }
        val reactions = reactions(this, state, repository)

        reactions.showReactionDetails(1)
        advanceUntilIdle()
        assertEquals(ReactionModeration(canDelete = true), state.value.reactionDetails?.moderation)

        reactions.deleteReactionsFrom(10)
        advanceUntilIdle()

        assertEquals(listOf(10L), repository.deletedFrom)
        assertEquals(listOf(detail(20, "👍")), state.value.reactionDetails?.items)
        assertNotNull(state.value.notice)
    }

    @Test
    fun poSmazaniPosledniReakcePrehledZavre() = runTest {
        val state = testState(testMessage(1))
        val repository = FakeRepository().apply { details = listOf(detail(10)) }
        val reactions = reactions(this, state, repository)

        reactions.showReactionDetails(1)
        advanceUntilIdle()
        reactions.deleteReactionsFrom(10)
        advanceUntilIdle()

        assertNull(state.value.reactionDetails)
    }

    @Test
    fun podFiltremSeSmazaniVratiKeVsem() = runTest {
        val state = testState(testMessage(1))
        val repository = FakeRepository().apply { details = listOf(detail(10), detail(20, "👍")) }
        val reactions = reactions(this, state, repository)

        reactions.showReactionDetails(1)
        advanceUntilIdle()
        reactions.filterReactionDetails("❤")
        advanceUntilIdle()
        reactions.deleteReactionsFrom(10)
        advanceUntilIdle()

        val open = assertNotNull(state.value.reactionDetails)
        assertNull(open.filter)
        assertEquals(listOf(detail(20, "👍")), open.items)
    }

    private object NoQuickReaction : QuickReaction {
        override val reaction = MutableStateFlow<String?>(null)
        override val choices = MutableStateFlow(emptyList<String>())
        override suspend fun set(reaction: String): Result<Unit> = error("v testu se nenastavuje")
    }
}
