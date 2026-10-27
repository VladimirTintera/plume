package eu.tintera.plume.core.telegram

import dev.g000sha256.tdl.dto.Error
import dev.g000sha256.tdl.dto.MessageSendingStateFailed
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.conversation_send_failed_unknown
import eu.tintera.plume.resources.error_slowmode_wait_minutes
import eu.tintera.plume.resources.error_slowmode_wait_seconds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Co se z neodeslané zprávy od TDLib dá poslat znovu a jakou větou se řekne proč. */
class SendFailureTest {

    private fun failed(
        message: String = "SLOWMODE_WAIT_30",
        canRetry: Boolean = true,
        needAnotherSender: Boolean = false,
        needAnotherReplyQuote: Boolean = false,
        needDropReply: Boolean = false,
        stars: Long = 0,
    ) = MessageSendingStateFailed(
        error = Error(code = 400, message = message),
        canRetry = canRetry,
        needAnotherSender = needAnotherSender,
        needAnotherReplyQuote = needAnotherReplyQuote,
        needDropReply = needDropReply,
        requiredPaidMessageStarCount = stars,
        retryAfter = 0.0,
    )

    @Test
    fun pomalyRezimJdeZopakovatSVetouOCekani() {
        val failure = failed().toSendFailure()
        assertTrue(failure.canRetry)
        assertEquals(UiText(Res.string.error_slowmode_wait_seconds, 30), failure.reason)
        // Pod dvě minuty sekundy (`slowModeWaitText`), nad nimi minuty nahoru.
        assertEquals(UiText(Res.string.error_slowmode_wait_seconds, 61), failed("SLOWMODE_WAIT_61").toSendFailure().reason)
        assertEquals(UiText(Res.string.error_slowmode_wait_minutes, 3), failed("SLOWMODE_WAIT_121").toSendFailure().reason)
    }

    @Test
    fun jinyOdesilatelAZahozenaOdpovedOpakovaniNebrani() {
        // Obojí udělá TDLib při opakování sám.
        assertTrue(failed(needAnotherSender = true).toSendFailure().canRetry)
        assertTrue(failed(needDropReply = true).toSendFailure().canRetry)
    }

    @Test
    fun jinaCitaceAHvezdyOpakovaniBrani() {
        // Výběr citace ani placené zprávy Plume nemá, server by zprávu odmítl znovu.
        assertFalse(failed(needAnotherReplyQuote = true).toSendFailure().canRetry)
        assertFalse(failed(stars = 10).toSendFailure().canRetry)
        assertFalse(failed(canRetry = false).toSendFailure().canRetry)
    }

    @Test
    fun neznamaHlaskaSeUzivateliNeukaze() {
        assertEquals(UiText(Res.string.conversation_send_failed_unknown), failed("SOMETHING_NEW").toSendFailure().reason)
    }
}
