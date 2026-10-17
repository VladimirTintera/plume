package eu.tintera.plume.core.ai

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Prompt a čtení verdiktu modelu u triáže chatu („čeká na odpověď"). */
class TriagePromptTest {
    @Test
    fun promptNeseZpravyOdNejstarsi() {
        val prompt = triagePrompt(listOf("Ahoj", "Přijdeš zítra?"))
        assertTrue("Ahoj\nPřijdeš zítra?" in prompt)
        assertTrue("UNSURE" in prompt)
    }

    @Test
    fun dlouhyVstupSeOriziOdZacatku() {
        val prompt = triagePrompt(listOf("x".repeat(MAX_TRIAGE_CHARS), "Přijdeš?"))
        // Rozhoduje konec rozhovoru, ten musí zůstat celý.
        assertTrue("Přijdeš?" in prompt)
        assertFalse("x".repeat(MAX_TRIAGE_CHARS) in prompt)
    }

    @Test
    fun odznakJenUJasnehoReply() {
        assertTrue(parseTriageVerdict("REPLY"))
        assertTrue(parseTriageVerdict("reply."))
        assertTrue(parseTriageVerdict("Answer: \"REPLY\""))
        assertTrue(triageVerdictOf(TriageAnswer("REPLY")))
    }

    @Test
    fun veSpornemPripadeMlci() {
        assertFalse(parseTriageVerdict("INFO"))
        assertFalse(parseTriageVerdict("UNSURE"))
        assertFalse(parseTriageVerdict(""))
        assertFalse(parseTriageVerdict("REPLY or INFO"))
        assertFalse(parseTriageVerdict("REPLY, but UNSURE"))
        assertFalse(triageVerdictOf(TriageAnswer(null)))
    }
}
