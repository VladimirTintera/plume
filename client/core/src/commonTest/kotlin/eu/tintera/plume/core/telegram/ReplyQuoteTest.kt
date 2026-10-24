package eu.tintera.plume.core.telegram

import dev.g000sha256.tdl.dto.FormattedText
import dev.g000sha256.tdl.dto.TextEntity
import dev.g000sha256.tdl.dto.TextEntityTypeBold
import dev.g000sha256.tdl.dto.TextEntityTypeSpoiler
import dev.g000sha256.tdl.dto.TextEntityTypeTextUrl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/** Citace úryvku pro TDLib: kde úryvek doopravdy je a které formátování si nese. */
class ReplyQuoteTest {

    private fun text(value: String, vararg entities: TextEntity) = FormattedText(text = value, entities = arrayOf(*entities))

    @Test
    fun opakovaneSlovoBereVyskytUOdhadnutehoMista() {
        val quote = quoteInput(text("ahoj a zase ahoj"), ReplyQuote("ahoj", position = 10))!!
        assertEquals(12, quote.position)
    }

    @Test
    fun spatnyOdhadSpadneNaPrvniVyskyt() {
        val quote = quoteInput(text("ahoj světe"), ReplyQuote("ahoj", position = 8))!!
        assertEquals(0, quote.position)
    }

    @Test
    fun uryvekKteryVTextuNeniNeniCitace() {
        assertNull(quoteInput(text("ahoj světe"), ReplyQuote("nazdar", position = 0)))
        assertNull(quoteInput(text("ahoj"), ReplyQuote("", position = 0)))
    }

    @Test
    fun formatovaniSeOrizneNaUryvekAPosune() {
        // „tučné slovo" je tučné, úryvek začíná uprostřed a končí za ním.
        val original = text(
            "řekl tučné slovo tady",
            TextEntity(offset = 5, length = 11, type = TextEntityTypeBold()),
        )
        val quote = quoteInput(original, ReplyQuote("slovo tady", position = 11))!!
        val bold = quote.text.entities.single()
        assertIs<TextEntityTypeBold>(bold.type)
        assertEquals(0, bold.offset)
        assertEquals(5, bold.length)
    }

    @Test
    fun odkazyVCitaciNezustavajiSpoilerAno() {
        val original = text(
            "tajné na webu",
            TextEntity(offset = 0, length = 5, type = TextEntityTypeSpoiler()),
            TextEntity(offset = 9, length = 4, type = TextEntityTypeTextUrl(url = "https://example.com")),
        )
        val quote = quoteInput(original, ReplyQuote("tajné na webu", position = 0))!!
        assertIs<TextEntityTypeSpoiler>(quote.text.entities.single().type)
    }

    @Test
    fun entitaMimoUryvekOdpadne() {
        val original = text("tučně a pak nic", TextEntity(offset = 0, length = 5, type = TextEntityTypeBold()))
        val quote = quoteInput(original, ReplyQuote("pak nic", position = 8))!!
        assertEquals(0, quote.text.entities.size)
    }
}
