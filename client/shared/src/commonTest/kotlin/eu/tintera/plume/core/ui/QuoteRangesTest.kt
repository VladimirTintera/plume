package eu.tintera.plume.core.ui

import eu.tintera.plume.core.telegram.MessageEntity
import eu.tintera.plume.core.telegram.MessageEntityType
import kotlin.test.Test
import kotlin.test.assertEquals

class QuoteRangesTest {

    private fun quote(offset: Int, length: Int, expandable: Boolean = false) =
        MessageEntity(offset = offset, length = length, type = MessageEntityType.Quote(expandable))

    @Test
    fun citaceSeVezmouObeVarianty() {
        val text = "Napsal:\ncitace\na dodal"
        val entities = listOf(quote(8, 6), quote(15, 7, expandable = true))
        assertEquals(listOf(8 until 14, 15 until 22), quoteRanges(text, entities))
    }

    @Test
    fun jineEntityBlokNekresli() {
        val text = "tučné slovo"
        assertEquals(
            emptyList(),
            quoteRanges(text, listOf(MessageEntity(offset = 0, length = 5, type = MessageEntityType.Bold))),
        )
    }

    @Test
    fun useksePresahujiciTextOrizne() {
        // Server posílá platné hodnoty, ale kreslení podle nich nesmí spadnout kvůli jedné zprávě.
        assertEquals(listOf(2 until 4), quoteRanges("abcd", listOf(quote(2, 99))))
        assertEquals(emptyList(), quoteRanges("abcd", listOf(quote(9, 3))))
        assertEquals(emptyList(), quoteRanges("abcd", listOf(quote(1, 0))))
    }
}
