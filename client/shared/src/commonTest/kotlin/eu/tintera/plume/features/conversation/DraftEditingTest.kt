package eu.tintera.plume.features.conversation

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlin.test.Test
import kotlin.test.assertEquals

/** Vkládání emoji do rozepsaného textu a mazání z něj. */
class DraftEditingTest {

    @Test
    fun vlozeniNaKurzor() {
        val result = TextFieldValue("ahoj světe", selection = TextRange(4)).inserting("👋")
        assertEquals("ahoj👋 světe", result.text)
        assertEquals(TextRange(6), result.selection)
    }

    @Test
    fun vlozeniMistoOznaceneho() {
        val result = TextFieldValue("ahoj světe", selection = TextRange(5, 10)).inserting("🌍")
        assertEquals("ahoj 🌍", result.text)
        assertEquals(TextRange(7), result.selection)
    }

    @Test
    fun mazaniCelehoEmoji() {
        val result = TextFieldValue("ok👍🏽", selection = TextRange(6)).deletingBackward()
        assertEquals("ok", result.text)
        assertEquals(TextRange(2), result.selection)
    }

    @Test
    fun mazaniOznaceneho() {
        val result = TextFieldValue("ahoj světe", selection = TextRange(4, 10)).deletingBackward()
        assertEquals("ahoj", result.text)
    }

    @Test
    fun mazaniNaZacatkuNicNedela() {
        val value = TextFieldValue("ahoj", selection = TextRange(0))
        assertEquals(value, value.deletingBackward())
    }

    @Test
    fun emojiBezVyberuVariantySeDohledaVKatalogu() {
        assertEquals("❤️", catalogEmoji("❤"))
    }

    @Test
    fun emojiMimoKatalogNeprojde() {
        assertEquals(null, catalogEmoji("x"))
    }

    @Test
    fun palecMaPetOdstinu() {
        assertEquals(listOf("👍🏻", "👍🏼", "👍🏽", "👍🏾", "👍🏿"), skinToneVariants("👍"))
    }

    @Test
    fun viceOsobJenSeStejnymOdstinem() {
        assertEquals("🧑🏽\u200D🤝\u200D🧑🏽", skinToneVariants("🧑\u200D🤝\u200D🧑")?.get(2))
    }

    @Test
    fun emojiBezOdstinu() {
        assertEquals(null, skinToneVariants("😀"))
    }

    @Test
    fun zakladVariantyAKatalog() {
        assertEquals("👍", baseEmoji("👍🏽"))
        assertEquals("😀", baseEmoji("😀"))
        // Nedávná ukládají varianty; při čtení nesmí vypadnout z katalogu.
        assertEquals("👍🏽", catalogEmoji("👍🏽"))
    }
}
