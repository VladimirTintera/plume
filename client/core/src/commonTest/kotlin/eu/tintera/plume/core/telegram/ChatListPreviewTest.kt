package eu.tintera.plume.core.telegram

import dev.g000sha256.tdl.dto.File
import dev.g000sha256.tdl.dto.FormattedText
import dev.g000sha256.tdl.dto.LocalFile
import dev.g000sha256.tdl.dto.MessagePhoto
import dev.g000sha256.tdl.dto.MessageText
import dev.g000sha256.tdl.dto.Photo
import dev.g000sha256.tdl.dto.PhotoSize
import dev.g000sha256.tdl.dto.RemoteFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import dev.g000sha256.tdl.dto.Minithumbnail as TdlMinithumbnail

class ChatListPreviewTest {

    private fun file(id: Int) = File(
        id = id,
        size = 0,
        expectedSize = 0,
        local = LocalFile("", false, false, false, false, 0, 0, 0),
        remote = RemoteFile("", "", false, false, 0),
    )

    private fun size(id: Int, width: Int, height: Int) = PhotoSize("s", file(id), width, height, intArrayOf())

    private val blur = TdlMinithumbnail(width = 40, height = 30, data = byteArrayOf(1, 2, 3))

    private fun photoMessage(
        sizes: Array<PhotoSize> = arrayOf(size(1, 100, 75), size(2, 320, 240), size(3, 1280, 960)),
        minithumbnail: TdlMinithumbnail? = blur,
        hasSpoiler: Boolean = false,
        isSecret: Boolean = false,
    ) = MessagePhoto(
        photo = Photo(hasStickers = false, minithumbnail = minithumbnail, sizes = sizes),
        video = null,
        caption = FormattedText("", arrayOf()),
        showCaptionAboveMedia = false,
        hasSpoiler = hasSpoiler,
        isSecret = isSecret,
    )

    @Test
    fun nejmensiVariantaKterouCtverecekJesteZaplni() {
        // `s` 100 × 75 má kratší stranu nad prahem, a je nejmenší.
        assertEquals(1, photoMessage().listThumbnail(hidden = false)?.fileId)
    }

    @Test
    fun sirokaFotkaBereVetsiVariantu() {
        // U 16 : 9 má `s` kratší stranu 56 px — oříznutá na čtverec by byla rozmazaná.
        val sizes = arrayOf(size(1, 100, 56), size(2, 320, 180), size(3, 1280, 720))
        assertEquals(2, photoMessage(sizes = sizes).listThumbnail(hidden = false)?.fileId)
    }

    @Test
    fun bezDostatecneVariantyTaNejvetsi() {
        val sizes = arrayOf(size(1, 90, 40), size(2, 60, 30))
        assertEquals(1, sizes.toList().listThumbnailSize()?.photo?.id)
    }

    @Test
    fun spoilerJenRozmazany() {
        val thumbnail = photoMessage(hasSpoiler = true).listThumbnail(hidden = false)
        assertTrue(thumbnail?.hidden == true)
        assertEquals(blur.data.toList(), thumbnail?.placeholder?.jpeg?.toList())
    }

    @Test
    fun skrytyBezRozmazanehoNahleduNemaNic() {
        // Prázdný čtvereček by nic neřekl — a ostrý obrázek se u skrytého nestahuje.
        assertNull(photoMessage(minithumbnail = null).listThumbnail(hidden = true))
    }

    @Test
    fun tajneMediumNahledNema() {
        assertNull(photoMessage(isSecret = true).listThumbnail(hidden = false))
    }

    @Test
    fun textNahledNema() {
        assertNull(MessageText(FormattedText("Ahoj", arrayOf()), null, null).listThumbnail(hidden = false))
    }

    private fun summary(
        outgoing: Boolean = true,
        sendState: SendState? = SendState.Sent,
        lastMessageId: Long? = 10,
        readUpTo: Long = 0,
    ) = ChatSummary(
        id = 1,
        title = "Jana",
        unreadCount = 0,
        lastMessagePreview = null,
        isPinned = false,
        order = 0,
        avatarFileId = null,
        isMuted = false,
        isMarkedAsUnread = false,
        kind = ChatKind.Private,
        lastMessageTimestamp = 0,
        lastMessageId = lastMessageId,
        lastMessageOutgoing = outgoing,
        lastMessageSendState = sendState,
        lastReadOutboxMessageId = readUpTo,
        presence = null,
        activity = null,
        draft = null,
    )

    @Test
    fun prectenoAzKdyzProtejsekDocetlKPosledniZprave() {
        assertFalse(summary(readUpTo = 9).lastMessageRead)
        assertTrue(summary(readUpTo = 10).lastMessageRead)
        assertTrue(summary(readUpTo = 12).lastMessageRead)
    }

    @Test
    fun odesilanaAniCiziZpravaPrectenaNeni() {
        assertFalse(summary(sendState = SendState.Sending, readUpTo = 99).lastMessageRead)
        assertFalse(summary(outgoing = false, sendState = null, readUpTo = 99).lastMessageRead)
    }
}
