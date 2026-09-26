package eu.tintera.plume.core.ui

import eu.tintera.plume.core.telegram.MessageEntity
import eu.tintera.plume.core.telegram.MessageEntityType
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Které úseky textu se nahradí vlastním emoji.
 *
 * Offsety jsou v UTF-16 jednotkách, takže emoji mimo základní rovinu zabere dvě — a přesně na
 * ta dvě místa musí inline obsah sednout, jinak by půlka náhradního znaku zůstala v textu.
 */
class CustomEmojiRangesTest {

    private val text = "Ahoj 😀 světe 🔥"

    private fun emoji(offset: Int, id: Long) = MessageEntity(offset, 2, MessageEntityType.CustomEmoji(id))

    @Test
    fun vlastniEmojiNahradiCelyNahradniZnak() {
        val ranges = customEmojiRanges(text, listOf(emoji(5, 1L), emoji(14, 2L)), spoilersRevealed = false)
        assertEquals(listOf(5..6 to 1L, 14..15 to 2L), ranges)
    }

    @Test
    fun vZakrytemSpoileruSeNenahrazuje() {
        val entities = listOf(emoji(5, 1L), MessageEntity(5, 2, MessageEntityType.Spoiler))
        assertEquals(emptyList(), customEmojiRanges(text, entities, spoilersRevealed = false))
        assertEquals(listOf(5..6 to 1L), customEmojiRanges(text, entities, spoilersRevealed = true))
    }

    @Test
    fun rozsahZaKoncemTextuSeZahodi() {
        assertEquals(emptyList(), customEmojiRanges(text, listOf(emoji(40, 1L)), spoilersRevealed = false))
    }

    @Test
    fun ostatniEntitySeNenahrazuji() {
        val entities = listOf(MessageEntity(0, 4, MessageEntityType.Bold))
        assertEquals(emptyList(), customEmojiRanges(text, entities, spoilersRevealed = false))
    }
}
