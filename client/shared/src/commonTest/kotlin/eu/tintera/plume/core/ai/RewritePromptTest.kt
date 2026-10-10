package eu.tintera.plume.core.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Prompt přepisu a čištění výstupu modelu. */
class RewritePromptTest {
    @Test
    fun promptNeseZpravuAPokyn() {
        val prompt = rewritePrompt("ahoj, prijdu pozdejc", RewriteStyle.Fix)
        assertTrue("ahoj, prijdu pozdejc" in prompt)
        assertTrue("diacritics" in prompt)
        assertTrue("Do not translate" in prompt)
    }

    @Test
    fun loupeObal() {
        assertEquals("Ahoj, přijdu později.", cleanRewrite("  Ahoj, přijdu později.  "))
        assertEquals("Ahoj, přijdu později.", cleanRewrite("\"Ahoj, přijdu později.\""))
        assertEquals("Ahoj, přijdu později.", cleanRewrite("„Ahoj, přijdu později.“"))
        assertEquals("Ahoj, přijdu později.", cleanRewrite("Rewritten message: Ahoj, přijdu později."))
        assertEquals("Ahoj, přijdu později.", cleanRewrite("```\nAhoj, přijdu později.\n```"))
    }

    @Test
    fun uvozovkyUvnitrNechava() {
        // Dvě citace na začátku a na konci nejsou obal celého textu.
        assertEquals("\"Ano\" řekl a pak \"ne\"", cleanRewrite("\"Ano\" řekl a pak \"ne\""))
        assertEquals("Říkal \"zítra\".", cleanRewrite("Říkal \"zítra\"."))
    }
}
