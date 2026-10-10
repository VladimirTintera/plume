package eu.tintera.plume.core.ai

import kotlin.test.Test
import kotlin.test.assertTrue

/** Prompt pro vysvětlení jedné zprávy. */
class ExplainPromptTest {
    @Test
    fun neseZpravuAJazykAplikace() {
        val prompt = explainPrompt("Dear residents, the water will be off on Thursday.", "cs")
        assertTrue("Dear residents, the water will be off on Thursday." in prompt)
        assertTrue("write in Czech, even if the message is in another language" in prompt)
    }

    @Test
    fun dlouhouZpravuOrizne() {
        val prompt = explainPrompt("x".repeat(MAX_EXPLAIN_CHARS + 500), "en")
        assertTrue("x".repeat(MAX_EXPLAIN_CHARS) in prompt)
        assertTrue("x".repeat(MAX_EXPLAIN_CHARS + 1) !in prompt)
    }
}
