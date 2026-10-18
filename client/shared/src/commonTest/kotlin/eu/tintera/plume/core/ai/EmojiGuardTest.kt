package eu.tintera.plume.core.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Pojistka stylu „Přidat emoji": model smí jen vkládat emoji. */
class EmojiGuardTest {

    @Test
    fun vlozeneEmojiProjdou() {
        assertTrue(onlyEmojiAdded("Zítra jedeme na hory", "Zítra jedeme na hory 🏔️⛷️"))
        assertTrue(onlyEmojiAdded("Mám radost!", "Mám radost! 😊"))
    }

    /** Model kolem emoji přidá nebo ubere mezeru — to text nemění. */
    @Test
    fun mezeryKolemEmojiNevadi() {
        assertTrue(onlyEmojiAdded("Mám kufr.", "Mám kufr 🧳."))
    }

    @Test
    fun slozeneEmojiASkinToneSeOdeberou() {
        assertEquals("Ahoj ", withoutEmoji("Ahoj 👋🏽"))
        assertEquals("Rodina", withoutEmoji("Rodina👨‍👩‍👧"))
        assertEquals("Hodnocení ", withoutEmoji("Hodnocení ⭐️"))
    }

    @Test
    fun zmenenaSlovaNeprojdou() {
        assertFalse(onlyEmojiAdded("ahoj jak se mas", "Ahoj, jak se máš? 😊"))
        assertFalse(onlyEmojiAdded("Přijdu v osm", "Přijdu v osm, těším se 🎉"))
    }

    @Test
    fun emojiZPuvodnihoTextuSeSmiPresunout() {
        assertTrue(onlyEmojiAdded("Díky 🙏 moc", "Díky moc 🙏😊"))
    }

    @Test
    fun promptChceJenVlozitEmoji() {
        val prompt = rewritePrompt("Zítra jedeme na hory", RewriteStyle.Emoji)
        assertTrue("Only insert emoji" in prompt)
    }
}
