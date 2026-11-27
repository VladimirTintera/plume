package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.settings.UnimplementedAppSettings
import eu.tintera.plume.core.settings.onThisDayKey
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.UnimplementedConversationRepository
import eu.tintera.plume.core.telegram.testMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationOnThisDayTest {

    private val zone = TimeZone.UTC
    private val today = LocalDate(2026, 11, 27)

    /** Historie chatu; stránkuje jako TDLib: od `fromMessageId` (včetně) ke starším. */
    private class FakeRepository(private val all: List<ChatMessage>) : UnimplementedConversationRepository() {
        var calls = 0

        override suspend fun firstMessageFrom(chatId: Long, epochSeconds: Int): Result<Long?> {
            calls++
            return Result.success(all.filter { it.timestamp >= epochSeconds }.minByOrNull { it.id }?.id)
        }

        override suspend fun history(chatId: Long, fromMessageId: Long, limit: Int, offset: Int): Result<List<ChatMessage>> {
            calls++
            return Result.success(all.filter { fromMessageId == 0L || it.id <= fromMessageId }.sortedByDescending { it.id }.take(limit))
        }
    }

    private class Settings(enabled: Boolean = true, dismissed: Set<String> = emptySet()) : UnimplementedAppSettings() {
        val dismissedNow = mutableListOf<Pair<Long, Long>>()
        override val onThisDay: StateFlow<Boolean> = MutableStateFlow(enabled)
        override val onThisDayDismissed: StateFlow<Set<String>> = MutableStateFlow(dismissed)
        override fun dismissOnThisDay(epochDay: Long, chatId: Long) {
            dismissedNow += epochDay to chatId
        }
    }

    private var nextId = 1L

    private fun at(year: Int, month: Int, day: Int, hour: Int = 12): ChatMessage =
        testMessage(nextId++).copy(timestamp = LocalDateTime(year, month, day, hour, 0).toInstant(zone).epochSeconds.toInt())

    private val jumps = mutableListOf<Int>()

    private fun part(scope: CoroutineScope, repository: FakeRepository, settings: Settings, state: MutableStateFlow<ConversationState>) =
        ConversationOnThisDay(TEST_CHAT, state, scope, repository, settings, { jumps += it }, testLogger, zone = { zone }, today = { today })

    @Test
    fun najdeDenPredRokem() = runTest {
        val history = listOf(at(2025, 11, 26), at(2025, 11, 27, hour = 9), at(2025, 11, 27, hour = 20), at(2025, 11, 28), at(2026, 11, 27))
        val state = testState()
        part(this, FakeRepository(history), Settings(), state).check()
        advanceUntilIdle()

        val memory = assertNotNull(state.value.onThisDay)
        assertEquals(1, memory.yearsAgo)
        assertEquals(2, memory.count)
        assertEquals(false, memory.capped)
        assertEquals(history[1].id, memory.preview.id)
        assertEquals(LocalDate(2025, 11, 27).atStartOfDayIn(zone).epochSeconds.toInt(), memory.dayStart)
    }

    @Test
    fun prazdnyRokPreskociAHledaDal() = runTest {
        val history = listOf(at(2023, 11, 27), at(2024, 3, 1), at(2026, 11, 1))
        val state = testState()
        part(this, FakeRepository(history), Settings(), state).check()
        advanceUntilIdle()

        assertEquals(3, state.value.onThisDay?.yearsAgo)
    }

    @Test
    fun mladyChatSeDalNeprohledava() = runTest {
        // Chat začal v létě 2026: rok zpátky nic, a dál už se nehledá.
        val repository = FakeRepository(listOf(at(2026, 7, 1), at(2026, 11, 20)))
        val state = testState()
        part(this, repository, Settings(), state).check()
        advanceUntilIdle()

        assertNull(state.value.onThisDay)
        val oneYear = repository.calls
        assertTrue(oneYear <= 4, "dotazů jen na jeden rok, bylo $oneYear")
    }

    @Test
    fun vypnuteAZavreneNaDnesekNehleda() = runTest {
        val history = listOf(at(2025, 11, 27))
        val off = FakeRepository(history)
        part(this, off, Settings(enabled = false), testState()).check()
        val dismissed = FakeRepository(history)
        part(this, dismissed, Settings(dismissed = setOf(onThisDayKey(today.toEpochDays(), TEST_CHAT))), testState()).check()
        advanceUntilIdle()

        assertEquals(0, off.calls)
        assertEquals(0, dismissed.calls)
    }

    @Test
    fun tuknutiSkociNaZacatekDneAZavreNaDnesek() = runTest {
        val settings = Settings()
        val state = testState()
        val memories = part(this, FakeRepository(listOf(at(2025, 11, 27))), settings, state)
        memories.check()
        advanceUntilIdle()
        val dayStart = assertNotNull(state.value.onThisDay).dayStart

        memories.open()

        assertNull(state.value.onThisDay)
        assertEquals(listOf(dayStart), jumps)
        assertEquals(listOf(today.toEpochDays() to TEST_CHAT), settings.dismissedNow)
    }

    @Test
    fun rusnyDenMaPocetSeStropem() = runTest {
        val history = (0 until 250).map { at(2025, 11, 27, hour = 8) } + at(2025, 11, 28)
        val state = testState()
        part(this, FakeRepository(history), Settings(), state).check()
        advanceUntilIdle()

        val memory = assertNotNull(state.value.onThisDay)
        assertTrue(memory.capped)
        // Dvě stránky po stu; první zpráva každé je ta, od které se čte.
        assertTrue(memory.count in 190..200, "počet ${memory.count}")
    }
}
