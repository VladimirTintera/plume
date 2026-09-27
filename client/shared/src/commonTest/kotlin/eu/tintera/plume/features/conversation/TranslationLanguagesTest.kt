package eu.tintera.plume.features.conversation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TranslationLanguagesTest {

    private val names = mapOf("cs" to "Čeština", "en" to "Angličtina", "de" to "Němčina", "he" to "Hebrejština")

    @Test
    fun jazykAplikaceJePrvniAOstatniAbecedne() {
        val options = translationLanguageOptions("cs", "") { names[it] }
        assertEquals("cs", options.first().code)
        assertEquals(1, options.count { it.code == "cs" })
        assertEquals(listOf("Angličtina", "Hebrejština", "Němčina"), options.map { it.name }.filter { it in names.values }.drop(1))
    }

    @Test
    fun hledaSeBezDiakritikyIPodleKodu() {
        assertEquals(listOf("cs"), translationLanguageOptions("en", "cest") { names[it] }.map { it.code })
        assertEquals("de", translationLanguageOptions("cs", "de") { names[it] }.first().code)
    }

    @Test
    fun zastaralyKodPlatformyNeniVNabidceDvakrat() {
        val options = translationLanguageOptions("iw", "") { names[it] }
        assertEquals("he", options.first().code)
        assertEquals(1, options.count { it.code == "he" })
    }

    @Test
    fun neznamyNazevSeUkazeKodem() {
        assertTrue(translationLanguageOptions("cs", "haw") { names[it] }.any { it.name == "haw" })
    }
}
