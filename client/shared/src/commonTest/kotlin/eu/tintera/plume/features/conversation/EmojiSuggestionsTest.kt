package eu.tintera.plume.features.conversation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EmojiSuggestionsTest {

    @Test
    fun slovoNaKonciTextu() {
        assertEquals(EmojiWord(4, 9, "srdce"), emojiWordAt("mám srdce", 9))
    }

    @Test
    fun slovoSDiakritikou() {
        assertEquals("kočka", emojiWordAt("kočka", 5)?.word)
    }

    @Test
    fun slovoUprostredTextuPredMezerou() {
        assertEquals(EmojiWord(0, 5, "heart"), emojiWordAt("heart and soul", 5))
    }

    @Test
    fun kurzorUprostredSlovaNicNenabidne() {
        assertNull(emojiWordAt("srdce", 3))
    }

    @Test
    fun jednoPismenoNestaci() {
        assertNull(emojiWordAt("a", 1))
    }

    @Test
    fun zminkaHashtagPrikazAOdkazNejsouSlova() {
        assertNull(emojiWordAt("@jana", 5))
        assertNull(emojiWordAt("#plume", 6))
        assertNull(emojiWordAt("/start", 6))
        assertNull(emojiWordAt("example.com", 11))
    }

    @Test
    fun slovoSeCislemNeniSlovo() {
        assertNull(emojiWordAt("3dny", 4))
        assertNull(emojiWordAt("srdce2", 5))
    }

    @Test
    fun zaMezerouNicNeni() {
        assertNull(emojiWordAt("srdce ", 6))
    }

    @Test
    fun emojiNahradiSlovoAKurzorJeZaNim() {
        val text = "mám srdce a víc"
        val word = emojiWordAt(text, 9)!!
        assertEquals("mám ❤️ a víc" to 6, emojiReplacement(text, word, "❤️"))
    }

    @Test
    fun nedavnaEmojiJdouDopreduOdNejnovejsiho() {
        val found = listOf("♥️", "❣️", "❤️", "🔥")
        assertEquals(listOf("🔥", "❤️", "♥️", "❣️"), found.recentFirst(listOf("🔥", "😀", "❤️")))
    }

    @Test
    fun bezNedavnychZustavaPoradiOdTdlib() {
        val found = listOf("♥️", "❣️", "❤️")
        assertEquals(found, found.recentFirst(emptyList()))
    }

    @Test
    fun nedavneSOdstinemPletiPosuneZakladniEmoji() {
        assertEquals(listOf("👍", "👋"), listOf("👋", "👍").recentFirst(listOf("👍🏽")))
    }
}
