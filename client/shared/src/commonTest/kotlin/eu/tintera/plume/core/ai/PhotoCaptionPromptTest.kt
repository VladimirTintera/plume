package eu.tintera.plume.core.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Návrh popisku fotky: pokyn a úprava toho, co model napíše ([cleanCaption]). */
class PhotoCaptionPromptTest {
    @Test
    fun pokynJeVJazyceAplikace() {
        assertTrue("write in Czech" in photoCaptionPrompt("cs"))
        assertTrue("one short, plain sentence" in photoCaptionPrompt("en"))
    }

    @Test
    fun uvozovkyOdrazkaADalsiRadkyPryc() {
        assertEquals("Západ slunce nad Vltavou", cleanCaption("„Západ slunce nad Vltavou“"))
        assertEquals("Sunset over the river", cleanCaption("\n- \"Sunset over the river\"\nAlternative: Evening view"))
    }

    @Test
    fun prazdneNicDlouheZkrati() {
        assertNull(cleanCaption("  \n \n"))
        assertNull(cleanCaption("\"\""))
        assertEquals(MAX_CAPTION_CHARS, cleanCaption("a".repeat(MAX_CAPTION_CHARS + 50))?.length)
    }
}
