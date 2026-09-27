package eu.tintera.plume.core.telegram

import dev.g000sha256.tdl.dto.LinkPreviewOptions
import dev.g000sha256.tdl.dto.MessageText
import dev.g000sha256.tdl.dto.FormattedText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class EditOptionsTest {

    private fun options(url: String = "", disabled: Boolean = false, small: Boolean = false) =
        LinkPreviewOptions(isDisabled = disabled, url = url, forceSmallMedia = small, forceLargeMedia = false, showAboveText = true)

    @Test
    fun vypnutyNahledZustaneVypnuty() {
        assertEquals(options(disabled = true), options(disabled = true).keptFor("Viz example.com, opraveno"))
    }

    @Test
    fun vynucenaAdresaZustaneJenSOdkazemVTextu() {
        val forced = options(url = "https://example.com", small = true)
        assertEquals(forced, forced.keptFor("Viz https://example.com"))
        // Odkaz z textu zmizel: velikost a poloha zůstanou, adresa ne.
        assertEquals(options(small = true), forced.keptFor("Viz https://jinde.cz"))
    }

    @Test
    fun textNemaPopisekNadMediem() {
        assertFalse(MessageText(FormattedText("x", emptyArray()), null, null).showsCaptionAboveMedia())
        assertFalse(null.showsCaptionAboveMedia())
    }
}
