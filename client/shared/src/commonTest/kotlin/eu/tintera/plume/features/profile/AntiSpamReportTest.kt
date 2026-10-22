package eu.tintera.plume.features.profile

import eu.tintera.plume.core.telegram.ChatLogEvent
import eu.tintera.plume.core.telegram.ChatLogFilter
import eu.tintera.plume.core.telegram.ChatLogPage
import eu.tintera.plume.core.telegram.FakeProfileRepository
import eu.tintera.plume.core.telegram.SenderInfo
import eu.tintera.plume.core.telegram.withAntiSpamReported
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.event_log_antispam_failed
import eu.tintera.plume.resources.event_log_antispam_reported
import eu.tintera.plume.resources.event_message_deleted
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Nahlášení omylu antispamu z Posledních akcí (`reportSupergroupAntiSpamFalsePositive`).
 * Na zařízení ověřit nešlo: událost vzniká jen tam, kde agresivní antispam zprávu opravdu smazal.
 */
class AntiSpamReportTest {

    private val main = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(main)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private val chatId = -100777L
    private val antiSpam = SenderInfo(id = 5434988373, title = "Telegram Anti-Spam", avatarFileId = null)

    private fun deleted(id: Long, messageId: Long?) = ChatLogEvent(
        id = id,
        date = 1_760_000_000,
        actor = antiSpam,
        description = UiText(Res.string.event_message_deleted),
        antiSpamMessageId = messageId,
    )

    private class Repository(
        val events: List<ChatLogEvent>,
        val result: CompletableDeferred<Result<Unit>> = CompletableDeferred(Result.success(Unit)),
    ) : FakeProfileRepository() {
        val reports = mutableListOf<Pair<Long, Long>>()

        override suspend fun eventLog(chatId: Long, fromEventId: Long, filter: ChatLogFilter) =
            Result.success(ChatLogPage(events, nextFromEventId = null))

        override suspend fun administrators(chatId: Long) = Result.success(emptyList<SenderInfo>())

        override suspend fun reportAntiSpamFalsePositive(chatId: Long, messageId: Long): Result<Unit> {
            reports += chatId to messageId
            return result.await()
        }
    }

    @Test
    fun nahlaseniJdeSIdSmazaneZpravyATlacitkoZmizi() = runTest {
        val repository = Repository(listOf(deleted(1, messageId = 501), deleted(2, messageId = 502)))
        val viewModel = EventLogViewModel(chatId, repository)
        advanceUntilIdle()

        viewModel.reportAntiSpamMistake(viewModel.events!!.first())
        advanceUntilIdle()

        assertEquals(listOf(chatId to 501L), repository.reports)
        assertEquals(listOf(null, 502L), viewModel.events!!.map { it.antiSpamMessageId })
        assertEquals(UiText(Res.string.event_log_antispam_reported), viewModel.notice)
        assertTrue(viewModel.reportingAntiSpam.isEmpty())
    }

    /** Dvojí ťuknutí, než server odpoví, nesmí odeslat dvě hlášení. */
    @Test
    fun druheTuknutiBehemHlaseniNicNeodesle() = runTest {
        val pending = CompletableDeferred<Result<Unit>>()
        val repository = Repository(listOf(deleted(1, messageId = 501)), pending)
        val viewModel = EventLogViewModel(chatId, repository)
        advanceUntilIdle()
        val event = viewModel.events!!.single()

        viewModel.reportAntiSpamMistake(event)
        advanceUntilIdle()
        assertEquals(setOf(1L), viewModel.reportingAntiSpam)
        viewModel.reportAntiSpamMistake(event)
        pending.complete(Result.success(Unit))
        advanceUntilIdle()

        assertEquals(1, repository.reports.size)
    }

    /** Po chybě tlačítko zůstává — uživatel to může zkusit znovu. */
    @Test
    fun chybaNechaTlacitkoAOhlasiSe() = runTest {
        val failure = CompletableDeferred(Result.failure<Unit>(IllegalStateException("Unexpected failure")))
        val repository = Repository(listOf(deleted(1, messageId = 501)), failure)
        val viewModel = EventLogViewModel(chatId, repository)
        advanceUntilIdle()

        viewModel.reportAntiSpamMistake(viewModel.events!!.single())
        advanceUntilIdle()

        assertEquals(501L, viewModel.events!!.single().antiSpamMessageId)
        assertEquals(UiText(Res.string.event_log_antispam_failed), viewModel.notice)
        assertTrue(viewModel.reportingAntiSpam.isEmpty())
    }

    /** Smazání správcem (bez příznaku od TDLib) se nahlásit nedá. */
    @Test
    fun udalostBezPriznakuSeNehlasi() = runTest {
        val repository = Repository(listOf(deleted(1, messageId = null)))
        val viewModel = EventLogViewModel(chatId, repository)
        advanceUntilIdle()

        viewModel.reportAntiSpamMistake(viewModel.events!!.single())
        advanceUntilIdle()

        assertEquals(emptyList(), repository.reports)
        assertNull(viewModel.notice)
    }

    @Test
    fun nahlasenaUdalostZtratiJenSvujPriznak() {
        val events = listOf(deleted(1, messageId = 501), deleted(2, messageId = 502), deleted(3, messageId = null))
        assertEquals(listOf(501L, null, null), events.withAntiSpamReported(2).map { it.antiSpamMessageId })
        assertEquals(events, events.withAntiSpamReported(99))
    }
}
