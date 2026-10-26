package eu.tintera.plume.core.telegram

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Jméno nové sady samolepek z psaní a klíčová slova samolepky. */
class StickerSetNameTest {

    @Test
    fun mezeraJePodtrzitkoACiziZnakyVypadnou() {
        assertEquals("moje_sada", stickerSetNameInput("moje sada"))
        assertEquals("Ptk2", stickerSetNameInput("Pták-2!"))
        assertEquals("a".repeat(STICKER_SET_NAME_MAX), stickerSetNameInput("a".repeat(80)))
    }

    @Test
    fun klicovaSlovaPoCarkach() {
        assertEquals(listOf("pes", "štěně", "haf"), stickerKeywords(" pes, štěně ,, haf, Pes "))
        assertEquals(emptyList(), stickerKeywords("  , "))
    }

    @Test
    fun limityKlicovychSlov() {
        assertTrue(stickerKeywordsFit(List(STICKER_KEYWORDS_MAX) { "a" }))
        assertFalse(stickerKeywordsFit(List(STICKER_KEYWORDS_MAX + 1) { "a" }))
        // Délka se počítá dohromady, ne po slovech.
        assertTrue(stickerKeywordsFit(listOf("a".repeat(32), "b".repeat(32))))
        assertFalse(stickerKeywordsFit(listOf("a".repeat(32), "b".repeat(33))))
    }
}
