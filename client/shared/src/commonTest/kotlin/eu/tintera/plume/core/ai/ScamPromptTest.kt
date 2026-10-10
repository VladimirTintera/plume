package eu.tintera.plume.core.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Prompt a čtení verdiktu modelu u podvodné zprávy. */
class ScamPromptTest {
    @Test
    fun promptNeseZpravu() {
        val prompt = scamPrompt("Vyhrál jsi iPhone, klikni na odkaz")
        assertTrue("Vyhrál jsi iPhone, klikni na odkaz" in prompt)
        assertTrue("UNSURE" in prompt)
    }

    @Test
    fun cteDruhIVObalu() {
        assertEquals(ScamKind.Phishing, parseScamVerdict("PHISHING"))
        assertEquals(ScamKind.Prize, parseScamVerdict("prize."))
        assertEquals(ScamKind.Money, parseScamVerdict("Answer: \"MONEY\""))
    }

    @Test
    fun veSpornemPripadeMlci() {
        assertNull(parseScamVerdict("SAFE"))
        assertNull(parseScamVerdict("UNSURE"))
        assertNull(parseScamVerdict(""))
        assertNull(parseScamVerdict("I think it is fine"))
        // Dva druhy naráz, nebo druh s pochybností: model si jistý není.
        assertNull(parseScamVerdict("PHISHING or PRIZE"))
        assertNull(parseScamVerdict("PHISHING, but UNSURE"))
    }
}
