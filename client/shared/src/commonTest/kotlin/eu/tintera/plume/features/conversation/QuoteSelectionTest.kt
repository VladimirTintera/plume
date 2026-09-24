package eu.tintera.plume.features.conversation

import androidx.compose.ui.text.AnnotatedString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Co z označeného textu kopie v nabídce jde citovat. */
class QuoteSelectionTest {

    private fun selected(vararg texts: String) = texts.map(::AnnotatedString)

    @Test
    fun uryvekTextuZpravy() {
        assertEquals("citací", quoteSelection(selected("citací"), "Odpověď s citací slova"))
    }

    @Test
    fun nicOznaceneNeniCitace() {
        assertNull(quoteSelection(emptyList(), "text"))
        assertNull(quoteSelection(selected("   "), "text"))
    }

    @Test
    fun textMimoZpravuSeCitovatNeda() {
        // Hlavička citace nad odpovědí, jméno odesílatele — v textu zprávy nejsou.
        assertNull(quoteSelection(selected("citace"), "Odpověď s citací slova"))
    }

    @Test
    fun zVyberuPresViceTextuZustaneKusZeZpravy() {
        assertEquals(
            "Odpověď s",
            quoteSelection(selected("Notifikátor", "Odpověď s"), "Odpověď s citací slova"),
        )
    }

    @Test
    fun okrajoveMezerySeOrezou() {
        assertEquals("citací", quoteSelection(selected(" citací "), "s citací slova"))
    }

    @Test
    fun dlouhyVyberSeZkratiNaZacatek() {
        val long = "a".repeat(MAX_QUOTE_LENGTH + 50)
        assertEquals(MAX_QUOTE_LENGTH, quoteSelection(selected(long), long)!!.length)
    }
}
