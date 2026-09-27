package eu.tintera.plume.core.telegram

import dev.g000sha256.tdl.dto.FormattedText
import dev.g000sha256.tdl.dto.MessagePaidMedia
import dev.g000sha256.tdl.dto.PaidMediaPreview
import dev.g000sha256.tdl.dto.PaidMediaUnsupported
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PaidMediaTest {

    private fun paid(vararg items: dev.g000sha256.tdl.dto.PaidMedia) = MessagePaidMedia(
        starCount = 150,
        media = arrayOf(*items),
        caption = FormattedText(text = "", entities = emptyArray()),
        showCaptionAboveMedia = false,
    )

    @Test
    fun nezaplaceneNeseCenuPocetARozmeryPrvni() {
        val media = paid(
            PaidMediaPreview(width = 800, height = 600, duration = 0, minithumbnail = null),
            PaidMediaPreview(width = 100, height = 100, duration = 12, minithumbnail = null),
        ).toPaidMedia(hideSensitive = false)
        assertEquals(
            MessageMedia.PaidMedia(starCount = 150, itemCount = 2, width = 800, height = 600, durationSeconds = 0),
            media,
        )
    }

    @Test
    fun neznamyDruhJeZamceneBezNahledu() {
        val media = paid(PaidMediaUnsupported()).toPaidMedia(hideSensitive = false)
        assertEquals(MessageMedia.PaidMedia(starCount = 150, itemCount = 1, width = 0, height = 0, durationSeconds = 0), media)
    }

    @Test
    fun prazdneNicNekresli() {
        assertNull(paid().toPaidMedia(hideSensitive = false))
    }
}
