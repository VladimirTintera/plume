package eu.tintera.plume.features.forum

import eu.tintera.plume.core.telegram.NO_TOPIC_EMOJI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/** Co formulář tématu pošle jako změnu ikony (`editForumTopic`). */
class TopicEditorTest {

    private val edit = TopicEditor(topicId = 7, name = "Novinky", customEmojiId = 42L, originalEmojiId = 42L)

    @Test
    fun nezmenenaIkonaSeNeposila() {
        // Jen přejmenování nesmí ikonu přepsat — `null` = `editIconCustomEmoji = false`.
        assertNull(edit.copy(name = "Jinak").iconEdit)
    }

    @Test
    fun novaIkonaJdeSvymId() {
        assertEquals(99L, edit.copy(customEmojiId = 99L).iconEdit)
    }

    @Test
    fun odebraniEmojiJeNula() {
        // TDLib čte nulu jako „emoji pryč" — `null` by znamenalo „nech, jak je".
        assertEquals(NO_TOPIC_EMOJI, edit.copy(customEmojiId = null).iconEdit)
        assertEquals(5L, TopicEditor(topicId = 1, name = "x", customEmojiId = 5L, originalEmojiId = null).iconEdit)
    }

    @Test
    fun noveTemaPosilaIkonuPriZalozeni() {
        // U nového tématu jde ikona rovnou do `createForumTopic`, úprava se neposílá.
        assertNull(TopicEditor(name = "x", customEmojiId = 5L).iconEdit)
    }

    @Test
    fun obecneTemaIkonuMenitNemuze() {
        assertFalse(TopicEditor(topicId = 1, name = "General", isGeneral = true).canChangeIcon)
    }
}
