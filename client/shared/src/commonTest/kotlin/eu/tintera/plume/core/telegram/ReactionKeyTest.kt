package eu.tintera.plume.core.telegram

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Klíč reakce vlastním emoji. Obyčejné emoji se za vlastní vydávat nesmí — reakce by se pak
 * poslala jako `ReactionTypeCustomEmoji` s nesmyslným id a server ji odmítl.
 */
class ReactionKeyTest {

    @Test
    fun vlastniEmojiSeVratiZKlice() {
        assertEquals(5372954454653933911L, customEmojiIdOf(customEmojiReaction(5372954454653933911L)))
    }

    @Test
    fun obycejneEmojiNeniVlastni() {
        listOf("❤", "👍", "🔥", "👨‍👩‍👧", "🇨🇿").forEach { assertNull(customEmojiIdOf(it), it) }
    }

    @Test
    fun poskozenyKlicNeniVlastniEmoji() {
        assertNull(customEmojiIdOf(customEmojiReaction(1L).dropLast(1)))
        assertNull(customEmojiIdOf(customEmojiReaction(1L) + "x"))
    }
}
