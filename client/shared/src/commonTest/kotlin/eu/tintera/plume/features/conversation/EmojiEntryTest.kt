package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.telegram.CustomEmojiItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class EmojiEntryTest {

    @Test
    fun vlastniEmojiProjdeKlicemTamIZpet() {
        val item = CustomEmojiItem(id = 5_368_324_170_671_202_286L, emoji = "🦆")
        assertEquals(EmojiEntry.Custom(item), parseRecentEmoji(item.recentKey()))
    }

    @Test
    fun klicNemaMezeru() {
        // Mezera dělí položky uloženého seznamu nedávných.
        assertFalse(' ' in CustomEmojiItem(id = 1L, emoji = "❤️").recentKey())
    }

    @Test
    fun dvojteckaVNahradnimZnakuNevadi() {
        assertEquals(EmojiEntry.Custom(CustomEmojiItem(id = 7L, emoji = "a:b")), parseRecentEmoji("custom:7:a:b"))
    }

    @Test
    fun obycejneEmojiJenZKatalogu() {
        assertEquals(EmojiEntry.Plain("😀"), parseRecentEmoji("😀"))
        assertNull(parseRecentEmoji("abc"))
    }

    @Test
    fun poskozenyKlicSeZahodi() {
        assertNull(parseRecentEmoji("custom:"))
        assertNull(parseRecentEmoji("custom:abc:🦆"))
        assertNull(parseRecentEmoji("custom:12:"))
        assertNull(parseRecentEmoji("custom::🦆"))
    }
}
