package eu.tintera.plume.core.telegram

import dev.g000sha256.tdl.dto.MessageContent
import dev.g000sha256.tdl.dto.MessageTonConnectRequest
import dev.g000sha256.tdl.dto.MessageTonWalletTransfer
import dev.g000sha256.tdl.dto.PageBlock
import dev.g000sha256.tdl.dto.PageBlockParagraph
import dev.g000sha256.tdl.dto.PushMessageContentTonConnectRequest
import dev.g000sha256.tdl.dto.PushMessageContentTonWalletTransfer
import dev.g000sha256.tdl.dto.RichText
import dev.g000sha256.tdl.dto.RichTextPlain
import dev.g000sha256.tdl.dto.RichTextTonAddress
import dev.g000sha256.tdl.dto.RichTexts
import dev.g000sha256.tdl.dto.TonConnectRequestStatePending
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.content_ton_connect_request
import eu.tintera.plume.resources.content_ton_connect_request_named
import eu.tintera.plume.resources.content_ton_received
import eu.tintera.plume.resources.content_ton_received_comment
import eu.tintera.plume.resources.content_ton_sent
import eu.tintera.plume.resources.ton_amount
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Obsah z peněženky TON (TDLib 1.8.68): Plume peněženku nemá, ale převod a adresu musí ukázat
 * slovy, ne „neumíme zobrazit" nebo prázdným místem.
 */
class TonContentTextTest {

    private fun transfer(amount: Long, comment: String = "", encrypted: Boolean = false) = MessageTonWalletTransfer(
        senderUserId = 0,
        transactionId = "",
        peerAddress = "UQ…",
        amount = amount,
        comment = comment,
        isCommentEncrypted = encrypted,
    )

    private fun preview(content: MessageContent) =
        content.previewTextOrNull(isOutgoing = false, isChannelPost = false)

    private val oneAndHalf = UiText(Res.string.ton_amount, tonAmount(1_500_000_000))

    @Test
    fun zapornaCastkaJeOdchoziPrevod() {
        assertEquals(UiText(Res.string.content_ton_received, oneAndHalf), preview(transfer(1_500_000_000)))
        assertEquals(UiText(Res.string.content_ton_sent, oneAndHalf), preview(transfer(-1_500_000_000)))
    }

    @Test
    fun zasifrovanyKomentarSeNevypisuje() {
        assertEquals(
            UiText(Res.string.content_ton_received_comment, oneAndHalf, "Za pizzu"),
            preview(transfer(1_500_000_000, comment = " Za pizzu ")),
        )
        assertEquals(
            UiText(Res.string.content_ton_received, oneAndHalf),
            preview(transfer(1_500_000_000, comment = "a8f3…", encrypted = true)),
        )
    }

    @Test
    fun zadostDAppBezJmenaMaObecnouVetu() {
        fun request(name: String) = MessageTonConnectRequest(
            sessionId = 1,
            state = TonConnectRequestStatePending(expirationDate = 0),
            dappName = name,
            topic = "",
            traceId = "",
        )
        assertEquals(UiText(Res.string.content_ton_connect_request_named, "Fragment"), preview(request("Fragment")))
        assertEquals(UiText(Res.string.content_ton_connect_request), preview(request(" ")))
        assertEquals(UiText(Res.string.content_ton_connect_request), PushMessageContentTonConnectRequest(dappName = "").previewText())
    }

    @Test
    fun jednotkaVPushiSeNezdvoji() {
        // Částku posílá server jako text; jestli s jednotkou, z TDLib poznat nejde.
        assertEquals(
            UiText(Res.string.content_ton_received, UiText(Res.string.ton_amount, "1.5")),
            PushMessageContentTonWalletTransfer(senderUserId = 0, amount = "1.5", comment = "").previewText(),
        )
        assertEquals(
            UiText(Res.string.content_ton_received_comment, UiText.Raw("1.5 TON"), "Díky"),
            PushMessageContentTonWalletTransfer(senderUserId = 0, amount = "1.5 TON", comment = "Díky").previewText(),
        )
    }

    @Test
    fun adresaVClankuZustane() {
        val address = RichTextTonAddress(text = RichTextPlain("UQBx…3k"), address = "UQBx3k")
        val text = RichTexts(arrayOf<RichText>(RichTextPlain("Pošli na "), address))
        assertEquals("Pošli na UQBx…3k", text.plainText())
        val labels = RichMediaLabels(photo = "", video = "", gif = "", audio = "", voice = "", file = "")
        assertEquals("Pošli na UQBx…3k", arrayOf<PageBlock>(PageBlockParagraph(text)).toRichBody(labels).text)
    }
}
