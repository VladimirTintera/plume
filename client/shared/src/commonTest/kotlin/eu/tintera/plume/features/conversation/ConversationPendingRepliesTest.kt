package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.telegram.PendingBotMessage
import eu.tintera.plume.core.telegram.UnimplementedConversationRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationPendingRepliesTest {

    private class FakeRepository(
        private val stopResult: Result<Unit> = Result.success(Unit),
    ) : UnimplementedConversationRepository() {
        val pending = MutableSharedFlow<PendingBotMessage>()
        val stoppedElsewhere = MutableSharedFlow<Long>()
        val stopCalls = mutableListOf<Long>()

        override fun pendingMessages(chatId: Long): Flow<PendingBotMessage> = pending
        override fun stoppedPendingMessages(chatId: Long): Flow<Long> = stoppedElsewhere
        override suspend fun pendingMessagePeriodSeconds(): Int = PERIOD_SECONDS

        override suspend fun stopPendingMessage(chatId: Long, draftId: Long): Result<Unit> {
            stopCalls += draftId
            return stopResult
        }
    }

    private val state = testState()

    private fun TestScope.replies(repository: FakeRepository): ConversationPendingReplies =
        ConversationPendingReplies(TEST_CHAT, state, detachedScope(), repository, testLogger).also {
            it.observe()
            runCurrent()
        }

    private fun pending(draftId: Long, text: String, canStop: Boolean = true, keepOnStop: Boolean = false) =
        PendingBotMessage(
            draftId = draftId,
            forumTopicId = 0,
            text = text,
            entities = emptyList(),
            canStop = canStop,
            keepOnStop = keepOnStop,
        )

    private suspend fun TestScope.emit(repository: FakeRepository, message: PendingBotMessage) {
        repository.pending.emit(message)
        runCurrent()
    }

    @Test
    fun odpovedSeStejnymDraftemNahradiPredchozi() = runTest {
        val repository = FakeRepository()
        replies(repository)

        emit(repository, pending(1, "Ahoj"))
        emit(repository, pending(1, "Ahoj, jak"))

        assertEquals("Ahoj, jak", state.value.pendingReply?.message?.text)
    }

    @Test
    fun odpovedSJinymDraftemPredchoziSmaze() = runTest {
        val repository = FakeRepository()
        replies(repository)

        emit(repository, pending(1, "první"))
        emit(repository, pending(2, "druhá"))

        assertEquals(2L, state.value.pendingReply?.message?.draftId)
    }

    @Test
    fun odpocetBeziOdPoslednihoUpdatu() = runTest {
        val repository = FakeRepository()
        replies(repository)

        emit(repository, pending(1, "a"))
        advanceTimeBy(PERIOD_MS - 1_000)
        emit(repository, pending(1, "ab"))
        advanceTimeBy(PERIOD_MS - 1_000)
        runCurrent()
        assertEquals("ab", state.value.pendingReply?.message?.text)

        advanceTimeBy(2_000)
        runCurrent()
        assertNull(state.value.pendingReply)
    }

    @Test
    fun prichoziZpravaOdpovedSmazeVlastniNe() {
        val reply = PendingReply(pending(1, "píšu"))

        assertNull(reply.afterNewMessage(testMessage(10, outgoing = false)))
        assertSame(reply, reply.afterNewMessage(testMessage(10, outgoing = true)))
    }

    @Test
    fun vyprseniNesmazeOdpovedKteraUzZmizelaZpravou() = runTest {
        val repository = FakeRepository()
        replies(repository)

        emit(repository, pending(1, "a"))
        // ViewModel ji smazal příchozí zprávou a hned přišla další odpověď.
        state.value = state.value.copy(pendingReply = null)
        advanceTimeBy(PERIOD_MS / 2)
        emit(repository, pending(2, "b"))
        advanceTimeBy(PERIOD_MS / 2 + 1)
        runCurrent()

        assertEquals(2L, state.value.pendingReply?.message?.draftId)
    }

    @Test
    fun zastaveniBezKeepOnStopTextSmazeAPozdniUpdateHoNevrati() = runTest {
        val repository = FakeRepository()
        val replies = replies(repository)

        emit(repository, pending(1, "dlouhá odpověď"))
        replies.stop()
        runCurrent()

        assertEquals(listOf(1L), repository.stopCalls)
        assertNull(state.value.pendingReply)

        emit(repository, pending(1, "dlouhá odpověď, ještě"))
        assertNull(state.value.pendingReply)
    }

    @Test
    fun zastaveniSKeepOnStopTextNechaATlacitkoSchova() = runTest {
        val repository = FakeRepository()
        val replies = replies(repository)

        emit(repository, pending(1, "zůstanu", keepOnStop = true))
        replies.stop()
        runCurrent()

        val reply = assertNotNull(state.value.pendingReply)
        assertEquals("zůstanu", reply.message.text)
        assertFalse(reply.canStop)

        // Pozdní update téže odpovědi tlačítko nevrátí.
        emit(repository, pending(1, "zůstanu.", keepOnStop = true))
        assertFalse(assertNotNull(state.value.pendingReply).canStop)
    }

    @Test
    fun bezCanStopSeNezastavuje() = runTest {
        val repository = FakeRepository()
        val replies = replies(repository)

        emit(repository, pending(1, "nezastavitelná", canStop = false))
        replies.stop()
        runCurrent()

        assertTrue(repository.stopCalls.isEmpty())
        assertNotNull(state.value.pendingReply)
    }

    @Test
    fun nepovedeneZastaveniOhlasiChybuADalsiUpdateOdpovedUkaze() = runTest {
        val repository = FakeRepository(stopResult = Result.failure(IllegalStateException("MESSAGE_ID_INVALID")))
        val replies = replies(repository)

        emit(repository, pending(1, "a"))
        replies.stop()
        runCurrent()
        assertNotNull(state.value.error)

        emit(repository, pending(1, "ab"))
        assertEquals("ab", state.value.pendingReply?.message?.text)
        assertTrue(assertNotNull(state.value.pendingReply).canStop)
    }

    @Test
    fun zastaveniJindeSeProjeviTady() = runTest {
        val repository = FakeRepository()
        replies(repository)

        emit(repository, pending(1, "a"))
        repository.stoppedElsewhere.emit(1)
        runCurrent()

        assertNull(state.value.pendingReply)
    }

    private companion object {
        const val PERIOD_SECONDS = 30
        const val PERIOD_MS = PERIOD_SECONDS * 1_000L
    }
}
