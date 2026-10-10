package eu.tintera.plume.core.translation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Kódy jazyků pro překlad v telefonu. */
class DeviceTranslationCodeTest {

    @Test
    fun jazykBezRegionu() {
        assertEquals("cs", deviceTranslationCode("cs"))
        assertEquals("pt", deviceTranslationCode("pt-BR"))
        assertEquals("en", deviceTranslationCode("en_US"))
    }

    /** ML Kit umí jen zjednodušenou čínštinu; tradiční by dala jiné písmo, než uživatel chce. */
    @Test
    fun cinstina() {
        assertEquals("zh", deviceTranslationCode("zh"))
        assertEquals("zh", deviceTranslationCode("zh-CN"))
        assertNull(deviceTranslationCode("zh-TW"))
    }

    @Test
    fun zastaraleKody() {
        assertEquals("he", deviceTranslationCode("iw"))
        assertEquals("id", deviceTranslationCode("in"))
    }
}
