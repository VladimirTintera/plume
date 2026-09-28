package eu.tintera.plume.core.telegram

import eu.tintera.plume.core.telegram.TextFormat.Bold
import eu.tintera.plume.core.telegram.TextFormat.Code
import eu.tintera.plume.core.telegram.TextFormat.CodeBlock
import eu.tintera.plume.core.telegram.TextFormat.ExpandableQuote
import eu.tintera.plume.core.telegram.TextFormat.Italic
import eu.tintera.plume.core.telegram.TextFormat.Quote
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Formátování ve vstupním poli: posun za psaním, přepínání na výběru, ořez a převod ze zprávy. */
class TextFormattingTest {

    private val boldAhoj = listOf(FormatSpan(Bold, 0, 4))

    @Test
    fun psaniNaKonciRozsahuPisDal() {
        // „ahoj" tučně, kurzor za ním, píše se dál: tučné pokračuje.
        assertEquals(listOf(FormatSpan(Bold, 0, 5)), boldAhoj.afterEdit("ahoj", "ahojs", cursor = 5))
    }

    @Test
    fun psaniPredRozsahemHoPosune() {
        assertEquals(listOf(FormatSpan(Bold, 2, 6)), boldAhoj.afterEdit("ahoj", "xxahoj", cursor = 2))
        // Přesně na začátek: vložený text do rozsahu nepatří.
        assertEquals(listOf(FormatSpan(Bold, 1, 5)), boldAhoj.afterEdit("ahoj", " ahoj", cursor = 1))
    }

    @Test
    fun psaniUvnitrRozsahuHoRoztahne() {
        assertEquals(listOf(FormatSpan(Bold, 0, 5)), boldAhoj.afterEdit("ahoj", "ahXoj", cursor = 3))
    }

    @Test
    fun smazaniCelehoRozsahuHoZahodi() {
        val spans = listOf(FormatSpan(Bold, 5, 9))
        assertEquals(emptyList(), spans.afterEdit("toje ahoj", "toje ", cursor = 5))
        // Vyprázdněné pole po odeslání.
        assertEquals(emptyList(), spans.afterEdit("toje ahoj", "", cursor = 0))
    }

    @Test
    fun mazaniUvnitrRozsahuHoZkrati() {
        assertEquals(listOf(FormatSpan(Bold, 0, 3)), boldAhoj.afterEdit("ahoj", "ahj", cursor = 2))
    }

    @Test
    fun prepsaneSlovoSiFormatNecha() {
        // Klávesnice opraví „ahoj" na „Ahoj": nahrazený je jen první znak.
        assertEquals(boldAhoj, boldAhoj.afterEdit("ahoj", "Ahoj", cursor = 1))
        // Celý výběr přepsaný jiným textem.
        assertEquals(listOf(FormatSpan(Bold, 0, 2)), boldAhoj.afterEdit("ahoj", "no", cursor = 2))
    }

    @Test
    fun kurzorRozhodneNejednoznacnyRozdil() {
        // Tučné je první „a"; „a" napsané PŘED něj (kurzor 1) rozsah posune, ne roztáhne.
        val spans = listOf(FormatSpan(Bold, 0, 1))
        // Kurzor 1 po vložení jednoho znaku = vkládalo se na pozici 0.
        assertEquals(listOf(FormatSpan(Bold, 1, 2)), spans.afterEdit("a", "aa", cursor = 1))
        // Za něj (kurzor 2): roztáhne.
        assertEquals(listOf(FormatSpan(Bold, 0, 2)), spans.afterEdit("a", "aa", cursor = 2))
        // Smazané první „a" ze dvou (kurzor 0): tučné „a" za ním se posune.
        val second = listOf(FormatSpan(Bold, 1, 2))
        assertEquals(listOf(FormatSpan(Bold, 0, 1)), second.afterEdit("aa", "a", cursor = 0))
    }

    @Test
    fun prepnutiNaVyberu() {
        val text = "ahoj světe"
        val bold = emptyList<FormatSpan>().toggled(Bold, 5, 10, text)
        assertEquals(listOf(FormatSpan(Bold, 5, 10)), bold)
        assertTrue(bold.covers(Bold, 6, 9))
        // Znovu na témž výběru: sundá se.
        assertEquals(emptyList(), bold.toggled(Bold, 5, 10, text))
        // Vypnutí uprostřed rozsah rozdělí.
        assertEquals(
            listOf(FormatSpan(Bold, 5, 6), FormatSpan(Bold, 8, 10)),
            bold.toggled(Bold, 6, 8, text),
        )
    }

    @Test
    fun zcastiFormatovanyVyberSeDoformatuje() {
        val text = "ahoj světe"
        val merged = listOf(FormatSpan(Bold, 0, 4)).toggled(Bold, 2, 10, text)
        assertEquals(listOf(FormatSpan(Bold, 0, 10)), merged)
        assertFalse(listOf(FormatSpan(Bold, 0, 4)).covers(Bold, 2, 10))
    }

    @Test
    fun ruzneFormatySeNemichaji() {
        val text = "ahoj světe"
        val spans = listOf(FormatSpan(Bold, 0, 10)).toggled(Italic, 0, 4, text)
        assertEquals(listOf(FormatSpan(Bold, 0, 10), FormatSpan(Italic, 0, 4)), spans)
        assertEquals(listOf(FormatSpan(Italic, 0, 4)), spans.toggled(Bold, 0, 10, text))
    }

    @Test
    fun citaceJdePoCelychRadcich() {
        val text = "první\ndruhý řádek\ntřetí"
        // Výběr uprostřed druhého řádku = celý druhý řádek, bez zalomení.
        assertEquals(listOf(FormatSpan(Quote, 6, 17)), emptyList<FormatSpan>().toggled(Quote, 8, 10, text))
        // Přes dva řádky.
        assertEquals(listOf(FormatSpan(Quote, 0, 17)), emptyList<FormatSpan>().toggled(Quote, 2, 8, text))
        // Výběr končící hned za zalomením (obsahuje ho, ale nic z druhého řádku) druhý řádek nebere.
        assertEquals(listOf(FormatSpan(Quote, 0, 5)), emptyList<FormatSpan>().toggled(Quote, 0, 6, text))
    }

    @Test
    fun citaceARozbalovaciCitaceJsouJednaRodina() {
        val text = "citát"
        val expandable = listOf(FormatSpan(ExpandableQuote, 0, 5))
        assertTrue(expandable.covers(Quote, 0, 5))
        assertEquals(emptyList(), expandable.toggled(Quote, 0, 5, text))
    }

    @Test
    fun kodPresViceRadkuJeBlok() {
        assertEquals(listOf(FormatSpan(Code, 0, 3)), emptyList<FormatSpan>().toggled(Code, 0, 3, "val x"))
        assertEquals(listOf(FormatSpan(CodeBlock, 0, 7)), emptyList<FormatSpan>().toggled(Code, 0, 7, "a\nb = 1"))
        // Blok se „Kódem" zruší.
        assertEquals(emptyList(), listOf(FormatSpan(CodeBlock, 0, 7)).toggled(Code, 0, 7, "a\nb = 1"))
    }

    @Test
    fun bezFormatovaniSundaVse() {
        val spans = listOf(FormatSpan(Bold, 0, 10), FormatSpan(Italic, 2, 4), FormatSpan(Quote, 0, 10))
        assertEquals(listOf(FormatSpan(Bold, 0, 2), FormatSpan(Quote, 0, 2)), spans.cleared(2, 10))
    }

    @Test
    fun orezPosuneRozsahy() {
        val composed = ComposedText("  ahoj  ", listOf(FormatSpan(Bold, 2, 6)))
        assertEquals(ComposedText("ahoj", listOf(FormatSpan(Bold, 0, 4))), composed.trimmed())
        // Tučná jen mezera na konci zmizí i s ní.
        assertEquals(ComposedText("ahoj"), ComposedText("ahoj ", listOf(FormatSpan(Bold, 4, 5))).trimmed())
    }

    @Test
    fun formatovaniZeZpravy() {
        val text = "tučně kód example.com"
        val entities = listOf(
            MessageEntity(0, 5, MessageEntityType.Bold),
            MessageEntity(6, 3, MessageEntityType.Code()),
            // Odkaz psaný v textu se dohledá znovu, do pole nepatří.
            MessageEntity(10, 11, MessageEntityType.Link("https://example.com")),
        )
        assertEquals(
            listOf(FormatSpan(Bold, 0, 5), FormatSpan(Code, 6, 9)),
            composedFormatsOf(text, entities),
        )
    }

    @Test
    fun coPoleNeuneseToUpravuZakaze() {
        val text = "klikni sem"
        assertNull(composedFormatsOf(text, listOf(MessageEntity(0, 10, MessageEntityType.Link("https://x.cz")))))
        assertNull(composedFormatsOf(text, listOf(MessageEntity(0, 6, MessageEntityType.MentionUser(1)))))
        assertNull(composedFormatsOf(text, listOf(MessageEntity(0, 6, MessageEntityType.Code(true, "kotlin")))))
        // Blok bez jazyka pole unese.
        assertEquals(
            listOf(FormatSpan(CodeBlock, 0, 6)),
            composedFormatsOf(text, listOf(MessageEntity(0, 6, MessageEntityType.Code(block = true)))),
        )
    }
}
