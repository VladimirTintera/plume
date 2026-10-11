package eu.tintera.plume.features.conversation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StickerSuggestionsTest {

    @Test
    fun jedineEmoji() {
        assertEquals("😂", stickerSuggestionEmoji("😂"))
    }

    @Test
    fun mezeryKolemNevadi() {
        assertEquals("👍", stickerSuggestionEmoji(" 👍 "))
    }

    @Test
    fun emojiSVyberemVarianty() {
        assertEquals("❤️", stickerSuggestionEmoji("❤️"))
    }

    @Test
    fun slozeneEmojiJeJedno() {
        assertEquals("👨‍👩‍👧", stickerSuggestionEmoji("👨‍👩‍👧"))
        assertEquals("👍🏽", stickerSuggestionEmoji("👍🏽"))
        assertEquals("🇨🇿", stickerSuggestionEmoji("🇨🇿"))
    }

    @Test
    fun dveEmojiNe() {
        assertNull(stickerSuggestionEmoji("😂😂"))
    }

    @Test
    fun emojiVTextuNe() {
        assertNull(stickerSuggestionEmoji("ahoj 👋"))
    }

    @Test
    fun prazdnePoleNe() {
        assertNull(stickerSuggestionEmoji(""))
        assertNull(stickerSuggestionEmoji("  "))
    }

    @Test
    fun obycejnyTextNe() {
        assertNull(stickerSuggestionEmoji("a"))
        assertNull(stickerSuggestionEmoji("1"))
    }
}
