package eu.tintera.plume.core.telegram

import kotlin.test.Test
import kotlin.test.assertEquals

class LanguagePackTest {

    private val packs = listOf("en", "cs", "de", "pt-br", "zh-hans", "zh-hant", "nb", "sk")

    @Test
    fun presnaShoda() {
        assertEquals("cs", languagePackFor("cs", packs))
        assertEquals("pt-br", languagePackFor("pt-BR", packs))
    }

    @Test
    fun regionPadneNaZakladJazyka() {
        assertEquals("de", languagePackFor("de-AT", packs))
    }

    @Test
    fun jazykBezRegionuVezmeBalicekSRegionem() {
        assertEquals("pt-br", languagePackFor("pt", packs))
        assertEquals("zh-hans", languagePackFor("zh", packs))
    }

    /** Portugalská portugalština není brazilská — radši angličtina než cizí varianta. */
    @Test
    fun jinyRegionTehozJazykaSeNebere() {
        assertEquals("", languagePackFor("pt-PT", packs))
    }

    @Test
    fun norstinaJeNb() {
        assertEquals("nb", languagePackFor("no", packs))
    }

    @Test
    fun jazykBezBalickuJeVychozi() {
        assertEquals("", languagePackFor("eo", packs))
    }
}
