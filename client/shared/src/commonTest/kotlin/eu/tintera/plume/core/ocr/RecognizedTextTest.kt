package eu.tintera.plume.core.ocr

import kotlin.test.Test
import kotlin.test.assertEquals

/** Pořadí čtení rozpoznaných bloků (Vision je vrací v libovolném pořadí). */
class RecognizedTextTest {

    /** Shora dolů; bloky vedle sebe s mírně posunutou horní hranou zleva doprava. */
    @Test
    fun poRadcichZlevaDoprava() {
        val blocks = listOf(
            block("dole", 0.1f, 0.80f),
            block("vpravo", 0.6f, 0.10f),
            block("vlevo", 0.1f, 0.12f),
            block("uprostred", 0.1f, 0.45f),
        )
        assertEquals(listOf("vlevo", "vpravo", "uprostred", "dole"), inReadingOrder(blocks).map { it.text })
    }

    /** Text pro schránku: bloky na řádku mezerou (položka a cena), řádky zalomením. */
    @Test
    fun celyText() {
        val text = RecognizedText(listOf(block("Káva", 0f, 0.1f), block("89 Kč", 0.5f, 0.11f), block("Celkem", 0f, 0.5f)))
        assertEquals("Káva 89 Kč\nCelkem", text.text)
    }

    private fun block(text: String, left: Float, top: Float) =
        RecognizedBlock(text, left = left, top = top, right = left + 0.3f, bottom = top + 0.1f)
}
