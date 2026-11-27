package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.UnimplementedConversationRepository
import eu.tintera.plume.core.telegram.testMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationReplyThreadTest {

    /** Celá historie chatu; `history` s záporným offsetem vrací zprávy od `fromMessageId` k přítomnosti. */
    private class FakeRepository(history: List<ChatMessage>) : UnimplementedConversationRepository() {
        private val all = history.sortedBy { it.id }
        val pages = mutableListOf<Long>()
        val fetched = mutableListOf<Long>()
        var failHistory = false

        override suspend fun history(chatId: Long, fromMessageId: Long, limit: Int, offset: Int): Result<List<ChatMessage>> {
            pages += fromMessageId
            // Meze `getChatHistory` v TDLib: jinak odpoví chybou (na emulátoru se to stalo).
            require(limit in 1..100 && offset > -100 && limit >= -offset) { "limit $limit, offset $offset" }
            if (failHistory) return Result.failure(IllegalStateException("network"))
            // Jako TDLib: od nejnovější, `fromMessageId` (a -offset novějších) ve výsledku.
            return Result.success(all.filter { it.id >= fromMessageId }.take(limit).reversed())
        }

        override suspend fun message(chatId: Long, messageId: Long): Result<ChatMessage> {
            fetched += messageId
            return all.firstOrNull { it.id == messageId }?.let { Result.success(it) }
                ?: Result.failure(IllegalStateException("not found"))
        }
    }

    /** Skoky jako dvojice (cíl, bod návratu). */
    private val jumps = mutableListOf<Pair<Long, Long?>>()

    private fun reply(id: Long, to: Long?, album: Long = 0L) = testMessage(id, album = album).copy(replyToMessageId = to)

    private fun thread(
        scope: CoroutineScope,
        repository: FakeRepository,
        state: kotlinx.coroutines.flow.MutableStateFlow<ConversationState>,
    ) = ConversationReplyThread(TEST_CHAT, state, scope, repository, { id, back -> jumps += id to back }, testLogger)

    @Test
    fun vlaknoNeseKorenOdpovediIOdbockyAleNicCizího() = runTest {
        // 1 ← 3 ← 5, 1 ← 4 (odbočka), 2 a 6 jsou jiný rozhovor, 7 odpovídá na 5.
        val history = listOf(
            reply(1, null), reply(2, null), reply(3, 1), reply(4, 1),
            reply(5, 3), reply(6, 2), reply(7, 5),
        )
        // V načteném okně je jen konec historie; kořen se musí dočíst.
        val state = testState(*history.filter { it.id >= 5 }.toTypedArray())
        val repository = FakeRepository(history)

        thread(this, repository, state).openThread(5)
        advanceUntilIdle()

        val sheet = assertNotNull(state.value.sheet<ReplyThreadSheet>())
        assertEquals(listOf(1L, 3L, 4L, 5L, 7L), sheet.messages.map { it.id })
        assertFalse(sheet.scanning)
        assertNull(sheet.pausedAt)
        assertFalse(sheet.rootMissing)
        // Zpráva 3 nebyla v okně — dočetla se, 5 se nečetla.
        assertEquals(listOf(3L, 1L), repository.fetched)
        // Historie se prochází od kořene.
        assertEquals(1L, repository.pages.first())
    }

    @Test
    fun otevreSeHnedSVychoziZpravou() = runTest {
        val state = testState(reply(1, null), reply(2, 1))
        thread(this, FakeRepository(listOf(reply(1, null), reply(2, 1))), state).openThread(2)

        val sheet = assertNotNull(state.value.sheet<ReplyThreadSheet>())
        assertEquals(listOf(2L), sheet.messages.map { it.id })
        assertTrue(sheet.scanning)
        assertNull(state.value.messageMenu)
    }

    @Test
    fun citaceSmazaneZpravyVlaknoUzavre() = runTest {
        // 10 odpovídá na zprávu 9, která už neexistuje.
        val history = listOf(reply(10, 9), reply(11, 10))
        val state = testState(*history.toTypedArray())

        thread(this, FakeRepository(history), state).openThread(11)
        advanceUntilIdle()

        val sheet = assertNotNull(state.value.sheet<ReplyThreadSheet>())
        assertEquals(listOf(10L, 11L), sheet.messages.map { it.id })
        assertTrue(sheet.rootMissing)
    }

    @Test
    fun naStropuSeZastaviAHledatDalPokracuje() = runTest {
        // Kořen, pak víc než jedno kolo cizích zpráv, pak odpověď.
        val filler = (2L..SCAN_BUDGET + 150L).map { reply(it, null) }
        val late = reply(SCAN_BUDGET + 200L, 1)
        val history = listOf(reply(1, null)) + filler + late
        val state = testState(reply(1, null))
        val replies = thread(this, FakeRepository(history), state)

        replies.openThread(1)
        advanceUntilIdle()
        val paused = assertNotNull(state.value.sheet<ReplyThreadSheet>())
        assertEquals(listOf(1L), paused.messages.map { it.id })
        assertNotNull(paused.pausedAt)

        replies.scanMore()
        advanceUntilIdle()
        val done = assertNotNull(state.value.sheet<ReplyThreadSheet>())
        assertEquals(listOf(1L, late.id), done.messages.map { it.id })
        assertNull(done.pausedAt)
    }

    @Test
    fun chybaHistorieNabidneZkusitZnovu() = runTest {
        val history = listOf(reply(1, null), reply(2, 1))
        val repository = FakeRepository(history).apply { failHistory = true }
        val state = testState(*history.toTypedArray())

        thread(this, repository, state).openThread(2)
        advanceUntilIdle()

        val sheet = assertNotNull(state.value.sheet<ReplyThreadSheet>())
        assertNotNull(sheet.pausedAt)
        assertNotNull(state.value.error)
    }

    @Test
    fun zavrenySheetZustaneZavreny() = runTest {
        val history = listOf(reply(1, null), reply(2, 1))
        val state = testState(*history.toTypedArray())
        val replies = thread(this, FakeRepository(history), state)

        replies.openThread(2)
        replies.closeThread()
        advanceUntilIdle()

        assertNull(state.value.sheet<ReplyThreadSheet>())
    }

    @Test
    fun tuknutiZavreSheetASkociSNavratemKVychoziZprave() = runTest {
        val history = listOf(reply(1, null), reply(2, 1))
        val state = testState(*history.toTypedArray())
        val replies = thread(this, FakeRepository(history), state)

        replies.openThread(2)
        advanceUntilIdle()
        replies.pickThreadMessage(1)
        assertNull(state.value.sheet<ReplyThreadSheet>())

        // Na výchozí zprávu se skáče bez návratu — není kam.
        replies.openThread(2)
        advanceUntilIdle()
        replies.pickThreadMessage(2)

        assertEquals(listOf<Pair<Long, Long?>>(1L to 2L, 2L to null), jumps)
    }

    @Test
    fun odpovedNaOdpovedVTezeStranceSeChyti() {
        val members = mutableMapOf(1L to reply(1, null))
        extendThread(members, listOf(reply(2, 1), reply(3, 2), reply(4, 99)))
        assertEquals(setOf(1L, 2L, 3L), members.keys)
    }

    @Test
    fun zAlbaJenPrvniFotka() {
        val rows = threadRows(listOf(reply(3, 1, album = 7), reply(1, null), reply(2, 1, album = 7)))
        assertEquals(listOf(1L, 2L), rows.map { it.id })
    }
}
