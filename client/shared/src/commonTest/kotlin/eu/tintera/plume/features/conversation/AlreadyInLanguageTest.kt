package eu.tintera.plume.features.conversation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AlreadyInLanguageTest {
    @Test
    fun stejnyJazykPrekladNenabidne() {
        assertTrue(alreadyInLanguage("cs", "cs"))
        assertTrue(alreadyInLanguage("en-US", "en"))
        assertTrue(alreadyInLanguage("pt_BR", "pt"))
    }

    @Test
    fun jinyJazykNeboNerozpoznanyPrekladNecha() {
        assertFalse(alreadyInLanguage("en", "cs"))
        assertFalse(alreadyInLanguage(null, "cs"))
    }

    @Test
    fun zastaraleKodyJsouTytez() {
        assertTrue(alreadyInLanguage("iw", "he"))
        assertTrue(alreadyInLanguage("in-ID", "id"))
    }

    @Test
    fun cinstinaSeNeschovava() {
        // Cíl je písmo (zjednodušená / tradiční) a rozpoznání ho nerozliší.
        assertFalse(alreadyInLanguage("zh", "zh-CN"))
        assertFalse(alreadyInLanguage("zh-TW", "zh-TW"))
    }

    @Test
    fun kratkyTextSeNerozpoznava() {
        assertNull(languageDetectionText("ok"))
        assertNull(languageDetectionText("👍 díky moc"))
        assertEquals("Ahoj, jak se máš dnes?", languageDetectionText("  Ahoj, jak se máš dnes?  "))
    }
}
