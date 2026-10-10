package eu.tintera.plume.core.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Prompt a čtení verdiktu kontroly tónu. */
class TonePromptTest {
    @Test
    fun promptNeseZpravu() {
        val prompt = tonePrompt("Kde jsou ty peníze?!")
        assertTrue("Kde jsou ty peníze?!" in prompt)
        assertTrue("UNSURE" in prompt)
    }

    @Test
    fun cteVerdiktIVObalu() {
        assertEquals(ToneVerdict.Fine, parseTone("FINE"))
        assertEquals(ToneVerdict.Issue(ToneIssue.Irritated), parseTone("irritated."))
        assertEquals(ToneVerdict.Issue(ToneIssue.Rude), parseTone("Answer: RUDE"))
    }

    @Test
    fun veSpornemPripadeMlci() {
        assertNull(parseTone("UNSURE"))
        assertNull(parseTone(""))
        assertNull(parseTone("RUDE or IRRITATED"))
        assertNull(parseTone("FINE, but UNSURE"))
        assertNull(parseTone("it is hard to say"))
    }
}
