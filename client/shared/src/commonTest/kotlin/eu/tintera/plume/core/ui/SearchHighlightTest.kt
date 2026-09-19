package eu.tintera.plume.core.ui

import eu.tintera.plume.core.telegram.MessageEntity
import eu.tintera.plume.core.telegram.MessageEntityType
import kotlin.test.Test
import kotlin.test.assertEquals

class SearchHighlightTest {

    @Test
    fun hledaSeBezDiakritikyAVelikostiPismen() {
        assertEquals(listOf(4..10), highlightRanges("Ten Obšlehl to", "obslehl"))
    }

    @Test
    fun zakrytySpoilerSeNezvyrazni() {
        // „tajné slovo" pod spoilerem, „slovo" venku zůstane.
        val text = "Je tu tajné slovo a slovo"
        val spoiler = MessageEntity(offset = 6, length = 11, type = MessageEntityType.Spoiler)
        val ranges = highlightRanges(text, "slovo")
        assertEquals(listOf(12..16, 20..24), ranges)
        assertEquals(listOf(20..24), visibleHighlights(text, listOf(spoiler), ranges, spoilersRevealed = false))
        assertEquals(ranges, visibleHighlights(text, listOf(spoiler), ranges, spoilersRevealed = true))
    }

    @Test
    fun useksePresahujiciTextOrizne() {
        assertEquals(listOf(2..3), visibleHighlights("abcd", emptyList(), listOf(2..9), spoilersRevealed = false))
        assertEquals(emptyList(), visibleHighlights("abcd", emptyList(), listOf(7..9), spoilersRevealed = false))
    }
}
