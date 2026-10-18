package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.telegram.DraftChange
import eu.tintera.plume.core.telegram.DraftDiff
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DraftDiffingTest {

    /** Vyznačené kusy textu v pořadí: „-" smazané, „+" vložené. */
    private fun DraftDiff.marks(): List<String> = changes.map { change ->
        val sign = if (change.kind == DraftChange.Kind.Delete) "-" else "+"
        sign + text.substring(change.offset, change.offset + change.length)
    }

    /** Text bez smazaných kusů — to, co se po „Použít" vloží do pole. */
    private fun DraftDiff.revised(): String {
        val builder = StringBuilder()
        var last = 0
        changes.filter { it.kind == DraftChange.Kind.Delete }.forEach {
            builder.append(text, last, it.offset)
            last = it.offset + it.length
        }
        return builder.append(text, last, text.length).toString()
    }

    @Test
    fun opravaDiakritikyJeCeleSlovo() {
        val diff = draftDiff("Ahoj jak se mas", "Ahoj, jak se máš")
        assertEquals(listOf("+,", "-mas", "+máš"), diff.marks())
        assertEquals("Ahoj, jak se máš", diff.revised())
    }

    @Test
    fun smazaneSlovo() {
        val diff = draftDiff("Přijdu přijdu zítra", "Přijdu zítra")
        assertEquals("Přijdu zítra", diff.revised())
        assertEquals(1, diff.changes.size)
        assertTrue(diff.marks().single().startsWith("-"))
    }

    @Test
    fun bezZmenyNicNevyznaci() {
        val diff = draftDiff("Všechno v pořádku.", "Všechno v pořádku.")
        assertEquals(emptyList(), diff.changes)
        assertEquals("Všechno v pořádku.", diff.text)
    }

    @Test
    fun emojiZustavaCele() {
        assertEquals(listOf("Ahoj", " ", "👋"), diffTokens("Ahoj 👋"))
        val diff = draftDiff("Ahoj 👋 mas", "Ahoj 👋 máš")
        assertEquals(listOf("-mas", "+máš"), diff.marks())
    }

    @Test
    fun uplnePrepsanyTextSeDaPrecist() {
        val diff = draftDiff("abc", "xyz")
        assertEquals(listOf("-abc", "+xyz"), diff.marks())
        assertEquals("xyz", diff.revised())
    }
}
