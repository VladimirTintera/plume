package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.telegram.HistoryCounts
import eu.tintera.plume.core.telegram.UnimplementedConversationRepository
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConversationCalendarTest {

    private val zone = TimeZone.of("Europe/Prague")

    private fun at(month: Int, day: Int): Int = LocalDateTime(2026, month, day, 12, 0).toInstant(zone).epochSeconds.toInt()

    /** Odpověď na `messageCountsByDay` podle toho, odkud a z jakého zdroje se čte. */
    private data class Call(val from: Long, val onlyLocal: Boolean)

    private class FakeRepository(private val answers: Map<Call, HistoryCounts>) : UnimplementedConversationRepository() {
        val calls = mutableListOf<Call>()

        override suspend fun messageCountsByDay(
            chatId: Long,
            fromMessageId: Long,
            onlyLocal: Boolean,
            maxMessages: Int,
            zone: TimeZone,
        ): Result<HistoryCounts> {
            val call = Call(fromMessageId, onlyLocal)
            calls += call
            return Result.success(answers.getValue(call))
        }
    }

    private fun TestScope.calendar(repository: FakeRepository) =
        ConversationCalendar(chatId = 1, repository = repository, scope = this, zone = zone)

    @Test
    fun lokalniDatabazeSeProchaziPoUsecichAzDoKonce() = runTest(StandardTestDispatcher()) {
        val repository = FakeRepository(
            mapOf(
                Call(0, onlyLocal = true) to HistoryCounts(mapOf(LocalDate(2026, 10, 27) to 5), 50, at(10, 27), reachedEnd = false),
                // Den na hranici úseků přišel v obou — sečte se.
                Call(50, onlyLocal = true) to HistoryCounts(mapOf(LocalDate(2026, 10, 27) to 2, LocalDate(2026, 9, 3) to 1), 40, at(9, 3), reachedEnd = false),
                Call(40, onlyLocal = true) to HistoryCounts(emptyMap(), null, null, reachedEnd = true),
            ),
        )
        val calendar = calendar(repository)

        calendar.open()
        advanceUntilIdle()

        val all = calendar.state.value!!.all
        assertEquals(mapOf(LocalDate(2026, 10, 27) to 7, LocalDate(2026, 9, 3) to 1), all.days)
        assertEquals(LocalDate(2026, 9, 3), all.coveredFrom)
        assertFalse(all.counting)
        // Lokální konec není začátek chatu — to ví až server.
        assertFalse(all.reachedStart)
    }

    @Test
    fun nacistStarsiPokracujeZaNejstarsiZpravouZeServeru() = runTest(StandardTestDispatcher()) {
        val repository = FakeRepository(
            mapOf(
                Call(0, onlyLocal = true) to HistoryCounts(mapOf(LocalDate(2026, 10, 27) to 5), 50, at(10, 27), reachedEnd = false),
                Call(50, onlyLocal = true) to HistoryCounts(emptyMap(), null, null, reachedEnd = true),
                Call(50, onlyLocal = false) to HistoryCounts(mapOf(LocalDate(2026, 8, 1) to 3), 10, at(8, 1), reachedEnd = true),
            ),
        )
        val calendar = calendar(repository)
        calendar.open()
        advanceUntilIdle()

        calendar.loadOlder()
        advanceUntilIdle()

        val all = calendar.state.value!!.all
        assertEquals(Call(50, onlyLocal = false), repository.calls.last())
        assertEquals(LocalDate(2026, 8, 1), all.coveredFrom)
        assertEquals(3, all.days[LocalDate(2026, 8, 1)])
        assertTrue(all.reachedStart)
    }

    @Test
    fun zavrenyKalendarNemaStav() = runTest(StandardTestDispatcher()) {
        val calendar = calendar(FakeRepository(mapOf(Call(0, onlyLocal = true) to HistoryCounts(emptyMap(), null, null, reachedEnd = true))))

        calendar.open()
        advanceUntilIdle()
        calendar.close()

        assertNull(calendar.state.value)
    }

    @Test
    fun sytostDneJePodilNaNejrusnejsimDni() {
        assertEquals(0, heatLevel(0, 40))
        assertEquals(1, heatLevel(1, 40))
        assertEquals(2, heatLevel(20, 40))
        assertEquals(4, heatLevel(40, 40))
        assertTrue((1..4).all { heatLevel(it, 4) == it })
    }
}
