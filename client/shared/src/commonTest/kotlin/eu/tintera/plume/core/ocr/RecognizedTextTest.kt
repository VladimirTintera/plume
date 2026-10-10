package eu.tintera.plume.core.ocr

import kotlin.test.Test
import kotlin.test.assertEquals

/** Pořadí čtení rozpoznaných řádků (Vision je vrací v libovolném pořadí) a text výběru. */
class RecognizedTextTest {

    /** Shora dolů; řádky vedle sebe s mírně posunutou horní hranou zleva doprava. */
    @Test
    fun poRadcichZlevaDoprava() {
        val lines = listOf(
            line("dole", 0.1f, 0.80f),
            line("vpravo", 0.6f, 0.10f),
            line("vlevo", 0.1f, 0.12f),
            line("uprostred", 0.1f, 0.45f),
        )
        assertEquals(listOf("vlevo", "vpravo", "uprostred", "dole"), inReadingOrder(lines).map { it.text })
    }

    /** Text pro schránku: řádky vedle sebe mezerou (položka a cena), pod sebou zalomením. */
    @Test
    fun celyText() {
        assertEquals("Káva 89 Kč\nCelkem", receipt.text)
    }

    /** Nic vybraného = celý text, jako před zavedením výběru. */
    @Test
    fun bezVyberuCelyText() {
        assertEquals(receipt.text, receipt.text(emptySet()))
    }

    /** Vybrané řádky v pořadí čtení, ne v pořadí ťuknutí; zbytek řádku na fotce se nepřidá. */
    @Test
    fun jenVybraneRadky() {
        assertEquals("89 Kč\nCelkem", receipt.text(setOf(2, 1)))
        assertEquals("Káva", receipt.text(setOf(0)))
    }

    private val receipt = RecognizedText(
        inReadingOrder(listOf(line("Káva", 0f, 0.1f), line("89 Kč", 0.5f, 0.11f), line("Celkem", 0f, 0.5f))),
    )

    private fun line(text: String, left: Float, top: Float) =
        RecognizedLine(text, left = left, top = top, right = left + 0.3f, bottom = top + 0.1f)
}
