package eu.tintera.plume.core.ui

import androidx.compose.ui.text.buildAnnotatedString
import eu.tintera.plume.core.telegram.MessageEntity
import eu.tintera.plume.core.telegram.MessageEntityType
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Vlastní emoji v obyčejném `Text` (náhled v seznamu chatů, citace). Offsety entit platí pro
 * připojovaný text, ne pro celý řetězec — před náhledem konceptu stojí „Koncept: ".
 */
class AppendWithCustomEmojiTest {

    private val text = "Ahoj 😀"
    private val emoji = MessageEntity(5, 2, MessageEntityType.CustomEmoji(42L))

    @Test
    fun offsetySePosunouZaPrefix() {
        val prefix = "Koncept: "
        var ids = emptySet<Long>()
        val built = buildAnnotatedString {
            append(prefix)
            ids = appendWithCustomEmoji(text, listOf(emoji))
        }

        assertEquals(setOf(42L), ids)
        val annotation = built.getStringAnnotations(INLINE_CONTENT_TAG, 0, built.length).single()
        assertEquals(customEmojiKey(42L), annotation.item)
        assertEquals("😀", built.text.substring(annotation.start, annotation.end))
    }

    @Test
    fun emojiVeSpoileruZustaneZnakem() {
        val spoiler = MessageEntity(0, text.length, MessageEntityType.Spoiler)
        var ids = emptySet<Long>()
        val built = buildAnnotatedString { ids = appendWithCustomEmoji(text, listOf(spoiler, emoji)) }

        assertEquals(emptySet(), ids)
        assertEquals(emptyList(), built.getStringAnnotations(INLINE_CONTENT_TAG, 0, built.length))
    }
}
