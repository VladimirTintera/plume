package eu.tintera.plume.features.report

import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.core.telegram.ReportOption
import eu.tintera.plume.core.telegram.ReportOptionId
import eu.tintera.plume.core.telegram.ReportRequest
import eu.tintera.plume.core.telegram.ReportStep
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.report_done
import eu.tintera.plume.resources.sponsored_ads_hidden
import eu.tintera.plume.resources.sponsored_hide_needs_premium
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReportFlowTest {

    private val spam = ReportOption(ReportOptionId(byteArrayOf(1)), "Spam")
    private val other = ReportOption(ReportOptionId(byteArrayOf(2)), "Jiné")

    /** Server jako fronta odpovědí; zaznamená, co dostal. */
    private class Server(answers: List<Result<ReportStep>> = emptyList()) {
        val queue = ArrayDeque(answers)
        val requests = mutableListOf<ReportRequest>()
        suspend fun report(chatId: Long, request: ReportRequest): Result<ReportStep> {
            requests += request
            return queue.removeFirst()
        }
    }

    private fun TestScope.flow(server: Server) = ReportFlow(this, server::report)

    @Test
    fun spamovyActionBarNahlasiHnedAPakPustiZbytek() = runTest {
        val server = Server(listOf(Result.success(ReportStep.Done)))
        val flow = flow(server)
        var after = false

        flow.start(chatId = 1, then = { after = true })
        advanceUntilIdle()

        assertTrue(after)
        assertNull(flow.wizard.value)
        assertEquals(UiText(Res.string.report_done), flow.outcome.value)
        // Úvodní požadavek je prázdný — tak ho server u spamového action baru bere jako spam.
        assertEquals(listOf(ReportRequest()), server.requests)
    }

    /** Reklamy skryté po volbě důvodu: hláška od serveru a reklama pryč (zbytek proběhne). */
    @Test
    fun vyrizenyKonecPustiZbytekSVlastniHlaskou() = runTest {
        val hidden = UiText(Res.string.sponsored_ads_hidden)
        val server = Server(listOf(
            Result.success(ReportStep.ChooseOption("Proč?", listOf(spam, other))),
            Result.success(ReportStep.Ended(hidden, done = true)),
        ))
        val flow = flow(server)
        var after = false

        flow.start(chatId = 1, messageIds = listOf(9), then = { after = true })
        advanceUntilIdle()
        flow.choose(other)
        advanceUntilIdle()

        assertTrue(after)
        assertNull(flow.wizard.value)
        assertEquals(hidden, flow.outcome.value)
        assertEquals(listOf(ReportRequest(messageIds = listOf(9)), ReportRequest(other.id, listOf(9))), server.requests)
    }

    /** Skrýt reklamy chce Premium: nic se nestalo, takže reklama zůstává. */
    @Test
    fun nevyrizenyKonecZbytekNepusti() = runTest {
        val premium = UiText(Res.string.sponsored_hide_needs_premium)
        val server = Server(listOf(Result.success(ReportStep.Ended(premium, done = false))))
        val flow = flow(server)
        var after = false

        flow.start(chatId = 1, messageIds = listOf(9), then = { after = true })
        advanceUntilIdle()

        assertFalse(after)
        assertNull(flow.wizard.value)
        assertEquals(premium, flow.outcome.value)
    }

    /** Mazání musí počkat, až je nahlášeno: nahlásit jde jen chat, který ještě existuje. */
    @Test
    fun zbytekCekaNaKonecPruvodce() = runTest {
        val textStep = ReportStep.EnterText(spam.id, optional = true)
        val server = Server(listOf(
            Result.success(ReportStep.ChooseOption("Proč?", listOf(spam, other))),
            Result.success(textStep),
            Result.success(ReportStep.Done),
        ))
        val flow = flow(server)
        var after = false

        flow.start(chatId = 1, then = { after = true })
        advanceUntilIdle()
        assertFalse(after)
        assertEquals(ReportStep.ChooseOption("Proč?", listOf(spam, other)), flow.wizard.value?.step)

        flow.choose(spam)
        advanceUntilIdle()
        assertFalse(after)
        assertEquals(textStep, flow.wizard.value?.step)

        flow.submitText("  reklama na kryptoměny  ")
        advanceUntilIdle()

        assertTrue(after)
        assertNull(flow.wizard.value)
        assertEquals(
            listOf(
                ReportRequest(),
                ReportRequest(optionId = spam.id),
                ReportRequest(optionId = spam.id, text = "reklama na kryptoměny"),
            ),
            server.requests,
        )
    }

    @Test
    fun hlaseneZpravyJdouVKazdemKroku() = runTest {
        val server = Server(listOf(
            Result.success(ReportStep.ChooseOption("Proč?", listOf(spam))),
            Result.success(ReportStep.Done),
        ))
        val flow = flow(server)

        flow.start(chatId = 1, messageIds = listOf(10, 11))
        advanceUntilIdle()
        flow.choose(spam)
        advanceUntilIdle()

        assertEquals(
            listOf(ReportRequest(messageIds = listOf(10, 11)), ReportRequest(optionId = spam.id, messageIds = listOf(10, 11))),
            server.requests,
        )
    }

    /** Zavřený průvodce nic nenahlásil — chat se proto nesmí ani smazat, ani zablokovat. */
    @Test
    fun zrusenyPruvodceNespustiZbytek() = runTest {
        val server = Server(listOf(Result.success(ReportStep.ChooseOption("Proč?", listOf(spam)))))
        val flow = flow(server)
        var after = false

        flow.start(chatId = 1, then = { after = true })
        advanceUntilIdle()
        flow.cancel()
        advanceUntilIdle()

        assertFalse(after)
        assertNull(flow.wizard.value)
    }

    @Test
    fun chybaNespustiZbytekAOhlasiSe() = runTest {
        val server = Server(listOf(Result.failure(RuntimeException("CHAT_INVALID"))))
        val flow = flow(server)
        var after = false

        flow.start(chatId = 1, then = { after = true })
        advanceUntilIdle()

        assertFalse(after)
        assertNull(flow.wizard.value)
        assertTrue(flow.outcome.value != null)
    }

    /** Odpověď na krok, který byl na cestě při zavření, nesmí průvodce otevřít znovu. */
    @Test
    fun odpovedPoZavreniPruvodceNeozije() = runTest {
        val answer = CompletableDeferred<Result<ReportStep>>()
        val flow = ReportFlow(this) { _, _ -> answer.await() }

        flow.start(chatId = 1)
        advanceUntilIdle()
        flow.cancel()
        answer.complete(Result.success(ReportStep.ChooseOption("Proč?", listOf(spam))))
        advanceUntilIdle()

        assertNull(flow.wizard.value)
    }

    @Test
    fun bezNahlaseniSeZbytekSpustiHnedANicSeNeposila() = runTest {
        val server = Server()
        val flow = flow(server)
        var after = false

        flow.thenAfterReport(report = false, chatId = 1) { after = true }
        advanceUntilIdle()

        assertTrue(after)
        assertTrue(server.requests.isEmpty())
        assertNull(flow.outcome.value)
    }
}
