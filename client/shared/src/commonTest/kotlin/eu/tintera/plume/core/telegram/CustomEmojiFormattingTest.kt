package eu.tintera.plume.core.telegram

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Vlastní emoji v rozepsaném textu: nedělitelné, neprodlužuje se, nespojuje se. */
class CustomEmojiFormattingTest {

    private fun emoji(start: Int, id: Long = 7, length: Int = 2) =
        FormatSpan(TextFormat.CustomEmoji, start, start + length, customEmojiId = id)

    @Test
    fun vlozeniNaKurzor() {
        val result = insertCustomEmoji("ahoj ", emptyList(), 5, 5, "😀", 42)
        assertEquals("ahoj 😀", result.text)
        assertEquals(listOf(emoji(5, 42)), result.formats)
    }

    @Test
    fun vlozeniMistoVyberuPosuneOstatni() {
        val bold = FormatSpan(TextFormat.Bold, 6, 10)
        val result = insertCustomEmoji("ab cd efgh", listOf(bold), 0, 2, "😀", 1)
        assertEquals("😀 cd efgh", result.text)
        assertEquals(listOf(emoji(0, 1), FormatSpan(TextFormat.Bold, 6, 10)), result.formats.sortedBy { it.start })
    }

    @Test
    fun dveStejnaVedleSebeSeNespoji() {
        val first = insertCustomEmoji("", emptyList(), 0, 0, "😀", 9)
        val second = insertCustomEmoji(first.text, first.formats, 2, 2, "😀", 9)
        assertEquals(listOf(emoji(0, 9), emoji(2, 9)), second.formats)
    }

    @Test
    fun psaniZaEmojiHoNeprodlouzi() {
        val spans = listOf(emoji(0))
        assertEquals(spans, spans.afterEdit("😀", "😀x", cursor = 3))
    }

    @Test
    fun psaniPredEmojiHoPosune() {
        assertEquals(listOf(emoji(1)), listOf(emoji(0)).afterEdit("😀", "x😀", cursor = 1))
    }

    @Test
    fun smazaniPulkyEmojiHoZrusi() {
        // Backspace u emoji ze zástupných párů smaže jednu jednotku — zbytek už není emoji.
        val after = listOf(emoji(0)).afterEdit("😀", "\uD83D", cursor = 1)
        assertEquals(emptyList(), after)
    }

    @Test
    fun vlozeniDovnitrHoZrusi() {
        assertEquals(emptyList(), listOf(emoji(0)).afterEdit("😀", "\uD83Dx\uDE00", cursor = 2))
    }

    @Test
    fun bezFormatovaniEmojiNechaANeprostupujeDoPsani() {
        val spans = listOf(emoji(0), FormatSpan(TextFormat.Bold, 0, 2))
        assertEquals(listOf(emoji(0)), spans.cleared(0, 2))
        assertEquals(emptySet(), listOf(emoji(0)).continuingAt(2))
    }

    @Test
    fun konceptVlastniEmojiVratiUpravaNe() {
        val entities = listOf(MessageEntity(offset = 0, length = 2, type = MessageEntityType.CustomEmoji(5)))
        assertEquals(listOf(emoji(0, 5)), draftFormatsOf("😀", entities))
        assertNull(composedFormatsOf("😀", entities))
    }
}
