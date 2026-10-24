package eu.tintera.plume.core.telegram

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Kdy se emoji status za jménem ukáže. */
class EmojiStatusTest {

    private val now = 1_800_000_000L

    @Test
    fun statusBezKonceplatiVzdycky() {
        assertTrue(emojiStatusActive(expirationDate = 0, now = now))
    }

    @Test
    fun prosleStatusSeNeukaze() {
        assertFalse(emojiStatusActive(expirationDate = (now - 60).toInt(), now = now))
        // Přesně v okamžiku konce už neplatí.
        assertFalse(emojiStatusActive(expirationDate = now.toInt(), now = now))
    }

    @Test
    fun budouciKonecPlati() {
        assertTrue(emojiStatusActive(expirationDate = (now + 60).toInt(), now = now))
    }
}
