package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.MessageSchedule
import eu.tintera.plume.core.telegram.UnimplementedConversationRepository
import eu.tintera.plume.core.telegram.testMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationScheduledTest {

    private class FakeRepository(
        var scheduled: List<ChatMessage>,
        private val rescheduleResult: Result<Unit> = Result.success(Unit),
    ) : UnimplementedConversationRepository() {
        val rescheduled = mutableListOf<Pair<Long, MessageSchedule?>>()
        val deleted = mutableListOf<List<Long>>()

        override suspend fun scheduledMessages(chatId: Long): Result<List<ChatMessage>> = Result.success(scheduled)

        override suspend fun reschedule(chatId: Long, messageId: Long, schedule: MessageSchedule?): Result<Unit> {
            rescheduled += messageId to schedule
            if (rescheduleResult.isSuccess) scheduled = scheduled.filterNot { it.id == messageId }
            return rescheduleResult
        }

        override suspend fun deleteMessages(chatId: Long, messageIds: List<Long>, revoke: Boolean): Result<Unit> {
            deleted += messageIds
            scheduled = scheduled.filterNot { it.id in messageIds }
            return Result.success(Unit)
        }
    }

    private val state = testState()

    private fun scheduled(scope: CoroutineScope, repository: FakeRepository) =
        ConversationScheduled(TEST_CHAT, state, scope, repository, testLogger)

    @Test
    fun seznamSeOtevreHnedANaplniAzPoNacteni() = runTest {
        val scheduled = scheduled(this, FakeRepository(listOf(testMessage(5), testMessage(6))))

        scheduled.openScheduled()
        assertEquals(emptyList(), state.value.scheduledMessages)
        advanceUntilIdle()

        assertEquals(listOf(5L, 6L), state.value.scheduledMessages?.map { it.id })
    }

    @Test
    fun prazdnySeznamSeZavreSam() = runTest {
        val scheduled = scheduled(this, FakeRepository(emptyList()))

        scheduled.openScheduled()
        advanceUntilIdle()

        assertNull(state.value.scheduledMessages)
    }

    @Test
    fun zavrenyMezitimZustaneZavreny() = runTest {
        val scheduled = scheduled(this, FakeRepository(listOf(testMessage(5))))

        scheduled.openScheduled()
        scheduled.closeScheduled()
        advanceUntilIdle()

        assertNull(state.value.scheduledMessages)
    }

    @Test
    fun odeslaniHnedSeznamObnovi() = runTest {
        val repository = FakeRepository(listOf(testMessage(5), testMessage(6)))
        val scheduled = scheduled(this, repository)
        scheduled.openScheduled()
        advanceUntilIdle()

        scheduled.reschedule(5, null)
        advanceUntilIdle()

        assertEquals(listOf<Pair<Long, MessageSchedule?>>(5L to null), repository.rescheduled)
        assertEquals(listOf(6L), state.value.scheduledMessages?.map { it.id })
    }

    @Test
    fun smazaniPosledniNaplanovaneSeznamZavre() = runTest {
        val repository = FakeRepository(listOf(testMessage(5)))
        val scheduled = scheduled(this, repository)
        scheduled.openScheduled()
        advanceUntilIdle()

        scheduled.deleteScheduled(5)
        advanceUntilIdle()

        assertEquals(listOf(listOf(5L)), repository.deleted)
        assertNull(state.value.scheduledMessages)
    }

    @Test
    fun nepovedenePreplanovaniSeOhlasi() = runTest {
        val repository = FakeRepository(listOf(testMessage(5)), rescheduleResult = Result.failure(IllegalStateException("SCHEDULE_DATE_INVALID")))
        val scheduled = scheduled(this, repository)
        scheduled.openScheduled()
        advanceUntilIdle()

        scheduled.reschedule(5, MessageSchedule.WhenOnline)
        advanceUntilIdle()

        assertNotNull(state.value.error)
        assertEquals(listOf(5L), state.value.scheduledMessages?.map { it.id })
    }
}
