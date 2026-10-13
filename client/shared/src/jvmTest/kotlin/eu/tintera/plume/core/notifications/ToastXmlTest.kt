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
    )

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
        assertEquals(42L to 7, parseActivation(toast.getAttribute("launch")))
        assertEquals(42L to null, parseActivation(activationArguments(42, null)))
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
}
