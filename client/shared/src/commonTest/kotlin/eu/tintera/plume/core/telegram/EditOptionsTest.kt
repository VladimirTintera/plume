package eu.tintera.plume.core.telegram

import dev.g000sha256.tdl.dto.LinkPreviewOptions
import dev.g000sha256.tdl.dto.MessageText
import dev.g000sha256.tdl.dto.FormattedText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class EditOptionsTest {

    private fun options(url: String = "", disabled: Boolean = false, small: Boolean = false) =
        LinkPreviewOptions(isDisabled = disabled, url = url, forceSmallMedia = small, forceLargeMedia = false, showAboveText = true)

    @Test
    fun volbaZpravyPrejdeDoDomenyIsAdresou() {
        assertEquals(
            eu.tintera.plume.core.telegram.LinkPreviewOptions(disabled = true, aboveText = true),
            options(disabled = true).toDomain(),
        )
        assertEquals(
            eu.tintera.plume.core.telegram.LinkPreviewOptions(largeMedia = false, aboveText = true, url = "https://example.com"),
            options(url = "https://example.com", small = true).toDomain(),
        )
        // Výchozí volba = nic nevybráno.
        assertNull(LinkPreviewOptions(false, "", false, false, false).toDomain())
    }

    @Test
    fun textNemaPopisekNadMediem() {
        assertFalse(MessageText(FormattedText("x", emptyArray()), null, null).showsCaptionAboveMedia())
        assertFalse(null.showsCaptionAboveMedia())
    }
}
