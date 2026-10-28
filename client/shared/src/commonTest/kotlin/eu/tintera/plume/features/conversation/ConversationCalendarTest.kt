package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.ChatSearchHit
import eu.tintera.plume.core.telegram.ChatSearchPage
import eu.tintera.plume.core.telegram.SenderInfo
import eu.tintera.plume.core.ui.UiText
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
    fun shrnutiDneBereJenZpravyToHoDneBezServisnich() = runTest(StandardTestDispatcher()) {
        // Zprávy 14.–16. 10.: id 100 je první zpráva 16. 10., od ní se čte ke starším.
        val messages = listOf(
            testMessage(100).copy(timestamp = at(10, 16)),
            testMessage(99).copy(timestamp = at(10, 15)),
            testMessage(98).copy(timestamp = at(10, 15), isService = true),
            testMessage(97).copy(timestamp = at(10, 15)),
            testMessage(96).copy(timestamp = at(10, 14)),
        )
        val repository = object : UnimplementedConversationRepository() {
            override suspend fun firstMessageFrom(chatId: Long, epochSeconds: Int): Result<Long?> = Result.success(100)

            override suspend fun history(chatId: Long, fromMessageId: Long, limit: Int, offset: Int): Result<List<ChatMessage>> =
                Result.success(messages.filter { it.id <= fromMessageId }.take(limit))
        }
        val calendar = ConversationCalendar(chatId = 1, repository = repository, scope = this, zone = zone)

        val day = calendar.messagesOfDay(LocalDate(2026, 10, 15))

        assertEquals(listOf(97L, 99L), day.map { it.id })
    }

    @Test
    fun hledaniPodleClenaSecteVysledkyPoDnech() = runTest(StandardTestDispatcher()) {
        val petr = SenderInfo(id = 42, title = "Petr", avatarFileId = null)
        var asked: Pair<String, Long?>? = null
        val repository = object : UnimplementedConversationRepository() {
            override suspend fun messageCountsByDay(
                chatId: Long, fromMessageId: Long, onlyLocal: Boolean, maxMessages: Int, zone: TimeZone, includeFrom: Boolean,
            ): Result<HistoryCounts> = Result.success(HistoryCounts(emptyMap(), null, null, reachedEnd = true))

            override suspend fun searchInChat(chatId: Long, query: String, fromMessageId: Long, senderUserId: Long?): Result<ChatSearchPage> {
                asked = query to senderUserId
                val hits = listOf(
                    ChatSearchHit(messageId = 30, text = UiText.Raw(""), date = at(10, 14), isOutgoing = false),
                    ChatSearchHit(messageId = 20, text = UiText.Raw(""), date = at(10, 14), isOutgoing = false),
                    ChatSearchHit(messageId = 10, text = UiText.Raw(""), date = at(10, 2), isOutgoing = false),
                )
                return Result.success(ChatSearchPage(hits.map { it.messageId }, total = 3, nextFromMessageId = null, hits = hits))
            }
        }
        val calendar = ConversationCalendar(chatId = 1, repository = repository, scope = this, zone = zone)
        calendar.open()
        advanceUntilIdle()

        calendar.setSearchSender(petr)
        advanceUntilIdle()

        val state = calendar.state.value!!
        assertEquals(CalendarFilter.Search(sender = petr), state.filter)
        assertEquals("" to 42L, asked)
        val server = state.server!!
        assertTrue(server.reachedEnd)
        // Den vede na nejstarší výsledek, ať ťuknutí začne od začátku dne.
        assertEquals(ServerDay(count = 2, firstMessageId = 20), server.days[LocalDate(2026, 10, 14)])
        assertEquals(1, server.days.getValue(LocalDate(2026, 10, 2)).count)
    }

    @Test
    fun denZeDvouStranekSeSecteAVedeNaStarsiZpravu() {
        val day = LocalDate(2026, 10, 14)
        val merged = mergeServerDays(mapOf(day to ServerDay(3, firstMessageId = 50)), listOf(day to ServerDay(2, firstMessageId = 40)))

        assertEquals(ServerDay(5, firstMessageId = 40), merged[day])
    }

    @Test
    fun souhrnMesice() {
        val counts = mapOf(
            LocalDate(2026, 9, 1) to 4,
            LocalDate(2026, 9, 2) to 10,
            LocalDate(2026, 9, 3) to 10,
            LocalDate(2026, 9, 10) to 6,
        )

        val stats = monthStats(YearMonth(2026, 9), counts, today = LocalDate(2026, 10, 28))!!

        // Při shodě vyhraje dřívější den; průměr 30 zpráv za 30 dnů; série 1.–3. září.
        assertEquals(LocalDate(2026, 9, 2), stats.busiest)
        assertEquals(10, stats.perDayTenths)
        assertEquals(3, stats.longestStreak)
    }

    @Test
    fun beznyMesicSePrumerujeJenPoDnesek() {
        val stats = monthStats(YearMonth(2026, 10), mapOf(LocalDate(2026, 10, 1) to 10), today = LocalDate(2026, 10, 4))!!

        assertEquals(25, stats.perDayTenths)
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
