package eu.tintera.plume.core.ai

import eu.tintera.ai.core.AiFailureReason
import eu.tintera.ai.core.AiProvider
import eu.tintera.ai.core.Generated
import eu.tintera.ai.core.ModelVariant
import eu.tintera.ai.core.OnDeviceAiException
import eu.tintera.ai.core.StructuredOutputException
import eu.tintera.ai.core.generateAnswer
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime

/** Volba cesty (typy / text) a to, že typovaná odpověď projde týmiž pravidly jako text. */
class StructuredAnswersTest {

    private class FakeAi(
        private val structured: (() -> Any)?,
        private val text: String = "",
    ) : UnimplementedOnDeviceAi() {
        val prompts = mutableListOf<String>()
        var textCalls = 0

        override suspend fun supportsStructuredOutput(variant: ModelVariant) = structured != null

        @Suppress("UNCHECKED_CAST")
        override suspend fun <T : Any> generateStructured(variant: ModelVariant, prompt: String, type: KClass<T>): T {
            prompts += prompt
            return structured!!.invoke() as T
        }

        override fun generate(variant: ModelVariant, prompt: String): Flow<Generated> {
            textCalls++
            prompts += prompt
            return flowOf(Generated(text))
        }
    }

    private suspend fun FakeAi.scam(): ScamKind? = generateAnswer(
        ModelVariant(),
        ScamAnswer::class,
        prompt = { structured -> scamPrompt("Výhra! Klikni a přihlas se", structured) },
        fromStructured = ::scamVerdictOf,
        fromText = ::parseScamVerdict,
    )

    @Test
    fun typyMajiPrednost() = runTest {
        val ai = FakeAi(structured = { ScamAnswer("PHISHING") }, text = "SAFE")
        assertEquals(ScamKind.Phishing, ai.scam())
        assertEquals(0, ai.textCalls)
        // Strukturovaný prompt o formátu mlčí.
        assertFalse(ai.prompts.single().contains("exactly one word"))
        assertFalse(ai.prompts.single().endsWith("Answer:"))
    }

    @Test
    fun bezTypuJdeText() = runTest {
        val ai = FakeAi(structured = null, text = "PRIZE")
        assertEquals(ScamKind.Prize, ai.scam())
        assertTrue(ai.prompts.single().endsWith("Answer:"))
    }

    @Test
    fun spatnyTvarSpadneNaText() = runTest {
        val ai = FakeAi(structured = { throw StructuredOutputException(code = 17) }, text = "MONEY")
        assertEquals(ScamKind.Money, ai.scam())
        assertEquals(1, ai.textCalls)
    }

    @Test
    fun nevimNeniDuvodZkouseText() = runTest {
        // „UNSURE" je platná odpověď; druhý pokus textem by přebil opatrnost klasifikace.
        val ai = FakeAi(structured = { ScamAnswer("UNSURE") }, text = "PHISHING")
        assertNull(ai.scam())
        assertEquals(0, ai.textCalls)
    }

    @Test
    fun rozbityModelLetiVen() = runTest {
        val ai = FakeAi(structured = { throw OnDeviceAiException(AiFailureReason.ModelError, AiProvider.GeminiNano, 5) })
        assertFailsWith<OnDeviceAiException> { ai.scam() }
        assertEquals(0, ai.textCalls)
    }

    @Test
    fun verdiktyZTypuJakoZTextu() {
        assertNull(scamVerdictOf(ScamAnswer(null)))
        assertNull(scamVerdictOf(ScamAnswer("SAFE")))
        assertEquals(ToneVerdict.Issue(ToneIssue.Rude), toneVerdictOf(ToneAnswer("RUDE")))
        assertEquals(ToneVerdict.Fine, toneVerdictOf(ToneAnswer("FINE")))
        assertNull(toneVerdictOf(ToneAnswer("UNSURE")))
    }

    @Test
    fun pripominkaZTypu() {
        val sentAt = LocalDateTime(2026, 10, 15, 12, 0)
        assertEquals(LocalDateTime(2026, 10, 16, 15, 0), reminderTimeOf(ReminderAnswer("2026-10-16 15:00"), sentAt))
        assertNull(reminderTimeOf(ReminderAnswer("NONE"), sentAt))
        // Minulost odmítne stejné pravidlo jako u textu.
        assertNull(reminderTimeOf(ReminderAnswer("2026-10-14 15:00"), sentAt))
    }

    @Test
    fun terminZTypuProjdeOverenimMista() {
        val text = "Sraz v 18:00 u kina Aero"
        val details = eventDetailsOf(EventAnswer("FUTURE", "YES", "Sraz u kina", "kino Aero"), text)
        assertEquals(EventDetails(isEvent = true, title = "Sraz u kina", place = "kino Aero"), details)
        // Místo, které ve zprávě není, se zahodí i ze strukturované odpovědi.
        assertNull(eventDetailsOf(EventAnswer("FUTURE", "YES", "Sraz", "Národní divadlo"), text)?.place)
        assertEquals(false, eventDetailsOf(EventAnswer("PAST", "YES", null, null), text)?.isEvent)
        assertNull(eventDetailsOf(EventAnswer("FUTURE", null, null, null), text))
    }

    @Test
    fun hledaniZTypuDaTataPoleJakoText() {
        val fields = searchFieldsOf(SearchAnswer(text = " dovolené ", chats = "GROUPS", content = "PHOTOS", from = "2025-06-01", to = ""))
        assertEquals(mapOf("TEXT" to "dovolené", "CHATS" to "GROUPS", "CONTENT" to "PHOTOS", "FROM" to "2025-06-01"), fields)
        assertEquals(
            searchFields("TEXT: dovolené\nCHATS: GROUPS\nCONTENT: PHOTOS\nFROM: 2025-06-01"),
            fields,
        )
    }
}
