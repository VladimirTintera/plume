package eu.tintera.plume.core.ai

import kotlin.test.Test
import kotlin.test.assertTrue

/** Prompt pro shrnutí přepisu hlasovky. */
class VoiceSummaryPromptTest {
    @Test
    fun nesePrepisMluvcihoAJazykAplikace() {
        val prompt = voiceSummaryPrompt("tak v pátek v šest u Kotvy, vezmi stan", "Jana", "cs")
        assertTrue("tak v pátek v šest u Kotvy, vezmi stan" in prompt)
        assertTrue("what Jana said" in prompt)
        assertTrue("write in Czech, even if the voice message is in another language" in prompt)
    }

    @Test
    fun dlouhyPrepisOrizne() {
        val prompt = voiceSummaryPrompt("x".repeat(MAX_VOICE_SUMMARY_CHARS + 500), "Jana", "en")
        assertTrue("x".repeat(MAX_VOICE_SUMMARY_CHARS) in prompt)
        assertTrue("x".repeat(MAX_VOICE_SUMMARY_CHARS + 1) !in prompt)
    }
}
