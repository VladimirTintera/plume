package eu.tintera.plume.core.telegram

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DiceEmojiTest {
    private val server = listOf("🎲", "🎯", "🏀", "⚽", "🎳", "🎰")

    @Test
    fun samotneEmojiZeSeznamuJeHod() {
        assertEquals("🎲", diceEmojiFor("🎲", server))
        assertEquals("🎰", diceEmojiFor(" 🎰 ", server))
    }

    @Test
    fun vyberVariantyNerozhoduje() {
        // Klávesnice pošle „⚽️", server má „⚽" — odejít musí podoba od serveru.
        assertEquals("⚽", diceEmojiFor("⚽️", server))
        assertEquals("⚽️", diceEmojiFor("⚽", listOf("⚽️")))
    }

    @Test
    fun cokoliJinehoZustaneTextem() {
        assertNull(diceEmojiFor("🎲🎲", server))
        assertNull(diceEmojiFor("🎲 hodím", server))
        assertNull(diceEmojiFor("😀", server))
        assertNull(diceEmojiFor("", server))
    }

    @Test
    fun bezSeznamuOdServeruSeNehazi() {
        assertNull(diceEmojiFor("🎲", emptyList()))
    }
}
