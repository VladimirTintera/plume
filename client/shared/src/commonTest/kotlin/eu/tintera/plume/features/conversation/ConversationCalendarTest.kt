package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.HistoryCounts
import eu.tintera.plume.core.telegram.testMessage
import eu.tintera.plume.core.telegram.UnimplementedConversationRepository
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
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

    private class FakeRepository(
        private val answers: Map<Call, HistoryCounts>,
        private val firstMessage: Pair<Long, Int>? = null,
    ) : UnimplementedConversationRepository() {
        val calls = mutableListOf<Call>()

        override suspend fun messageCountsByDay(
            chatId: Long,
            fromMessageId: Long,
            onlyLocal: Boolean,
            maxMessages: Int,
            zone: TimeZone,
            includeFrom: Boolean,
        ): Result<HistoryCounts> {
            val call = Call(fromMessageId, onlyLocal)
            calls += call
            return Result.success(answers.getValue(call))
        }

        override suspend fun firstMessageFrom(chatId: Long, epochSeconds: Int): Result<Long?> = Result.success(firstMessage?.first)

        override suspend fun message(chatId: Long, messageId: Long): Result<ChatMessage> =
            Result.success(testMessage(messageId).copy(timestamp = firstMessage!!.second))
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
    fun ostruvekSeSpocitaOdZpravyNaKtereKonverzaceStala() = runTest(StandardTestDispatcher()) {
        val repository = FakeRepository(
            answers = mapOf(
                Call(0, onlyLocal = true) to HistoryCounts(mapOf(LocalDate(2026, 10, 27) to 5), 50, at(10, 27), reachedEnd = false),
                Call(50, onlyLocal = true) to HistoryCounts(emptyMap(), null, null, reachedEnd = true),
                // Okno kolem 1. 9., kam uživatel skočil: od 3. 9. po 31. 8.
                Call(30, onlyLocal = true) to HistoryCounts(
                    days = mapOf(LocalDate(2026, 9, 3) to 2, LocalDate(2026, 9, 1) to 4, LocalDate(2026, 8, 31) to 1),
                    oldestMessageId = 20,
                    oldestTimestamp = at(8, 31),
                    newestTimestamp = at(9, 3),
                    reachedEnd = true,
                ),
            ),
            firstMessage = 1L to at(6, 10),
        )
        val calendar = calendar(repository)

        calendar.open(anchor = 30)
        advanceUntilIdle()

        val all = calendar.state.value!!.all
        assertEquals(YearMonth(2026, 6), all.firstMonth)
        val island = all.islands.single()
        assertEquals(LocalDate(2026, 8, 31), island.oldestDay)
        assertEquals(LocalDate(2026, 9, 3), island.newestDay)
        assertEquals(4, island.days[LocalDate(2026, 9, 1)])
    }

    @Test
    fun kotvaVSouvislemUsekuOstruvekNedela() = runTest(StandardTestDispatcher()) {
        val repository = FakeRepository(
            mapOf(
                Call(0, onlyLocal = true) to HistoryCounts(mapOf(LocalDate(2026, 10, 27) to 5), 50, at(10, 27), reachedEnd = false),
                Call(50, onlyLocal = true) to HistoryCounts(emptyMap(), null, null, reachedEnd = true),
            ),
        )
        val calendar = calendar(repository)

        // Konverzace stojí u nejnovějších zpráv — ty počítá souvislý úsek.
        calendar.open(anchor = 70)
        advanceUntilIdle()

        assertTrue(calendar.state.value!!.all.islands.isEmpty())
        assertEquals(listOf(Call(0, true), Call(50, true)), repository.calls)
    }

    @Test
    fun nacistStarsiZahodiOstruvekKamUsekDosahl() = runTest(StandardTestDispatcher()) {
        val repository = FakeRepository(
            answers = mapOf(
                Call(0, onlyLocal = true) to HistoryCounts(mapOf(LocalDate(2026, 10, 27) to 5), 50, at(10, 27), reachedEnd = false),
                Call(50, onlyLocal = true) to HistoryCounts(emptyMap(), null, null, reachedEnd = true),
                Call(45, onlyLocal = true) to HistoryCounts(mapOf(LocalDate(2026, 10, 20) to 4), 41, at(10, 20), newestTimestamp = at(10, 20), reachedEnd = true),
                Call(50, onlyLocal = false) to HistoryCounts(mapOf(LocalDate(2026, 10, 20) to 9), 40, at(10, 20), reachedEnd = false),
            ),
        )
        val calendar = calendar(repository)
        calendar.open(anchor = 45)
        advanceUntilIdle()
        assertEquals(1, calendar.state.value!!.all.islands.size)

        calendar.loadOlder()
        advanceUntilIdle()

        // Souvislý úsek prošel přes ostrůvek; jeho zprávy by se jinak přičetly podruhé.
        assertTrue(calendar.state.value!!.all.islands.isEmpty())
    }

    @Test
    fun denOstruvkuJeZnamyJenMeziKrajnimiDny() {
        val island = Island(
            days = mapOf(LocalDate(2026, 9, 10) to 3, LocalDate(2026, 9, 12) to 1, LocalDate(2026, 9, 14) to 2),
            oldestDay = LocalDate(2026, 9, 10),
            newestDay = LocalDate(2026, 9, 14),
            oldestMessageId = 10,
            newestMessageId = 20,
        )

        assertEquals(DayStatus.Unknown, islandDayStatus(LocalDate(2026, 9, 9), listOf(island)))
        assertEquals(DayStatus.Partial(3), islandDayStatus(LocalDate(2026, 9, 10), listOf(island)))
        assertEquals(DayStatus.Known(0), islandDayStatus(LocalDate(2026, 9, 11), listOf(island)))
        assertEquals(DayStatus.Known(1), islandDayStatus(LocalDate(2026, 9, 12), listOf(island)))
        assertEquals(DayStatus.Partial(2), islandDayStatus(LocalDate(2026, 9, 14), listOf(island)))
        assertEquals(DayStatus.Unknown, islandDayStatus(LocalDate(2026, 9, 20), listOf(island)))
        assertEquals(DayStatus.Unknown, islandDayStatus(LocalDate(2026, 9, 1), emptyList()))
    }

    @Test
    fun denPredSouvislymUsekemJeNeznamy() {
        val counts = mapOf(LocalDate(2026, 10, 20) to 4, LocalDate(2026, 10, 22) to 1)

        assertEquals(DayStatus.Unknown, coveredDayStatus(LocalDate(2026, 10, 19), counts, LocalDate(2026, 10, 20)))
        assertEquals(DayStatus.Partial(4), coveredDayStatus(LocalDate(2026, 10, 20), counts, LocalDate(2026, 10, 20)))
        assertEquals(DayStatus.Known(0), coveredDayStatus(LocalDate(2026, 10, 21), counts, LocalDate(2026, 10, 20)))
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
