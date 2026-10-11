package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.telegram.StickerQuery
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StickerSearchRequestTest {

    private val languages = listOf("cs", "en")

    @Test
    fun prazdnyTextNehleda() {
        assertNull(StickerSearchRequest.Typed("", languages).toQuery())
        assertNull(StickerSearchRequest.Typed("   ", languages).toQuery())
    }

    @Test
    fun slovoJeTextSJazyky() {
        assertEquals(
            StickerQuery.Text("kočka", languages),
            StickerSearchRequest.Typed(" kočka ", languages).toQuery(),
        )
    }

    @Test
    fun napsanaEmojiJsouEmoji() {
        assertEquals(StickerQuery.Emoji("😂 👍"), StickerSearchRequest.Typed("😂 👍", languages).toQuery())
    }

    @Test
    fun kategorieHledaSvaEmoji() {
        assertEquals(StickerQuery.Emoji("😍 🥰 ❤️"), StickerSearchRequest.Category("😍 🥰 ❤️").toQuery())
    }

    @Test
    fun kategorieBezEmojiNehleda() {
        assertNull(StickerSearchRequest.Category("").toQuery())
    }
}
