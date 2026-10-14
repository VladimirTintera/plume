package eu.tintera.plume.core.notifications

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.w3c.dom.Document
import org.w3c.dom.Element

/**
 * XML toastu musí být platné za všech okolností: Windows ho čte `LoadXml` a nevalidní dokument
 * odmítne celý — oznámení by se pak neukázalo vůbec, jen s HRESULT v logu.
 */
class ToastXmlTest {

    private fun notification(
        title: String = "Chat",
        subtitle: String? = null,
        body: String = "Ahoj",
        topicId: Int? = null,
        silent: Boolean = false,
        imagePath: String? = null,
        avatarPath: String? = null,
        timestamp: Long? = null,
        buttons: NotificationButtons? = null,
        call: CallButtons? = null,
    ) = DesktopNotification(
        id = "g1-n2",
        thread = "chat-42",
        title = title,
        subtitle = subtitle,
        body = body,
        chatId = 42,
        topicId = topicId,
        silent = silent,
        imagePath = imagePath,
        avatarPath = avatarPath,
        timestamp = timestamp,
        buttons = buttons,
        call = call,
    )

    private val labels = NotificationButtons.Labels(reply = "Odpovědět", send = "Odeslat", markRead = "Přečteno", mute = "Ztlumit & dost")

    private fun buttons(canReply: Boolean = true, canMute: Boolean = true, quote: Long? = null) =
        NotificationButtons(messageId = 99, quoteMessageId = quote, canReply = canReply, canMute = canMute, labels = labels)

    private fun Document.actions(): List<Element> {
        val nodes = getElementsByTagName("action")
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    private fun parse(xml: String): Document =
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xml.byteInputStream())

    private fun Document.texts(): List<String> {
        val nodes = getElementsByTagName("text")
        return (0 until nodes.length).map { nodes.item(it).textContent }
    }

    @Test
    fun specialCharactersStayValidXml() {
        val document = parse(toastXml(notification(title = "Tom & Jerry <3", subtitle = "\"Q\" 'P'", body = "a < b && c > d")))
        assertEquals(listOf("Tom & Jerry <3", "\"Q\" 'P'", "a < b && c > d"), document.texts())
        assertEquals("Tom & Jerry <3", (document.getElementsByTagName("header").item(0) as Element).getAttribute("title"))
    }

    @Test
    fun controlCharactersAreDropped() {
        val document = parse(toastXml(notification(body = "a\u0000b\u0007c\nd")))
        assertEquals("abc\nd", document.texts().last())
    }

    @Test
    fun privateChatHasNoSenderLine() {
        assertEquals(listOf("Chat", "Ahoj"), parse(toastXml(notification())).texts())
        assertEquals(listOf("Chat", "Alice", "Ahoj"), parse(toastXml(notification(subtitle = "Alice"))).texts())
    }

    @Test
    fun clickOpensChatAndTopic() {
        val toast = parse(toastXml(notification(topicId = 7))).documentElement
        assertEquals(ToastActivation.Open(42, 7), parseActivation(toast.getAttribute("launch")))
        assertEquals(ToastActivation.Open(42, null), parseActivation(activationArguments(42, null)))
        assertNull(parseActivation(""))
    }

    @Test
    fun silentOnlyWhenAsked() {
        assertTrue(toastXml(notification(silent = true)).contains("<audio silent=\"true\"/>"))
        assertFalse(toastXml(notification()).contains("<audio"))
    }

    @Test
    fun timestampIsIso() {
        val toast = parse(toastXml(notification(timestamp = 1_760_000_000))).documentElement
        assertEquals("2025-10-09T08:53:20Z", toast.getAttribute("displayTimestamp"))
    }

    @Test
    fun imagesAreFileUrisAndMissingFilesSkipped() {
        val file = File.createTempFile("plume toast", ".jpg").apply { deleteOnExit() }
        val document = parse(toastXml(notification(avatarPath = file.path, imagePath = file.path + ".missing")))
        val images = document.getElementsByTagName("image")
        assertEquals(1, images.length)
        val image = images.item(0) as Element
        assertEquals("appLogoOverride", image.getAttribute("placement"))
        assertTrue(image.getAttribute("src").startsWith("file:///"), image.getAttribute("src"))
        assertTrue(image.getAttribute("src").contains("%20"))
    }

    @Test
    fun noButtonsWithoutActions() {
        assertEquals(0, parse(toastXml(notification())).getElementsByTagName("actions").length)
    }

    @Test
    fun replyFieldSendReadAndMute() {
        val document = parse(toastXml(notification(topicId = 7, buttons = buttons(quote = 55))))
        val input = document.getElementsByTagName("input").item(0) as Element
        assertEquals("reply", input.getAttribute("id"))
        assertEquals("Odpovědět", input.getAttribute("placeHolderContent"))
        val actions = document.actions()
        assertEquals(listOf("Odeslat", "Přečteno", "Ztlumit & dost"), actions.map { it.getAttribute("content") })
        assertEquals("reply", actions[0].getAttribute("hint-inputId"))
        // Všechna na pozadí: okno se kvůli nim nevytahuje.
        assertTrue(actions.all { it.getAttribute("activationType") == "background" })

        assertEquals(
            ToastActivation.Reply(chatId = 42, topicId = 7, quoteMessageId = 55, text = "ahoj"),
            parseActivation(actions[0].getAttribute("arguments"), reply = "  ahoj "),
        )
        assertEquals(ToastActivation.MarkRead(chatId = 42, messageId = 99), parseActivation(actions[1].getAttribute("arguments")))
        assertEquals(ToastActivation.Mute(chatId = 42), parseActivation(actions[2].getAttribute("arguments")))
    }

    @Test
    fun emptyReplyIsIgnored() {
        val send = parse(toastXml(notification(buttons = buttons()))).actions()[0].getAttribute("arguments")
        assertNull(parseActivation(send, reply = "   "))
        assertNull(parseActivation(send, reply = null))
    }

    @Test
    fun privateChatReplyQuotesNothing() {
        val send = parse(toastXml(notification(buttons = buttons(quote = null)))).actions()[0].getAttribute("arguments")
        assertEquals(ToastActivation.Reply(42, null, null, "x"), parseActivation(send, reply = "x"))
    }

    @Test
    fun buttonsFollowPermissions() {
        val readOnly = parse(toastXml(notification(buttons = buttons(canReply = false, canMute = false))))
        assertEquals(0, readOnly.getElementsByTagName("input").length)
        assertEquals(listOf("Přečteno"), readOnly.actions().map { it.getAttribute("content") })
    }

    @Test
    fun incomingCallStaysUntilAnsweredOrDeclined() {
        val document = parse(toastXml(notification(silent = true, call = CallButtons(callId = 314, answer = "Přijmout", decline = "Odmítnout"))))
        assertEquals("incomingCall", document.documentElement.getAttribute("scenario"))
        val (answer, decline) = document.actions()
        assertEquals("Přijmout", answer.getAttribute("content"))
        // Přijmout vytahuje okno, Odmítnout ne.
        assertEquals("foreground", answer.getAttribute("activationType"))
        assertEquals("background", decline.getAttribute("activationType"))
        assertEquals(ToastActivation.AnswerCall(chatId = 42, callId = 314), parseActivation(answer.getAttribute("arguments")))
        assertEquals(ToastActivation.DeclineCall(chatId = 42, callId = 314), parseActivation(decline.getAttribute("arguments")))
        assertEquals(1, document.getElementsByTagName("audio").length)
    }

    @Test
    fun messageIsNotCall() {
        assertFalse(parse(toastXml(notification())).documentElement.hasAttribute("scenario"))
        assertNull(parseActivation("action=answer&chat=42"))
    }
}
