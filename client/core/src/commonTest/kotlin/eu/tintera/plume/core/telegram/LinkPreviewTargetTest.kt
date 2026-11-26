package eu.tintera.plume.core.telegram

import dev.g000sha256.tdl.dto.Animation
import dev.g000sha256.tdl.dto.ChatPhoto
import dev.g000sha256.tdl.dto.File
import dev.g000sha256.tdl.dto.LinkPreviewAlbumMediaVideo
import dev.g000sha256.tdl.dto.LinkPreviewTypeAlbum
import dev.g000sha256.tdl.dto.LinkPreviewTypeAnimation
import dev.g000sha256.tdl.dto.LocalFile
import dev.g000sha256.tdl.dto.RemoteFile
import dev.g000sha256.tdl.dto.Thumbnail
import dev.g000sha256.tdl.dto.ThumbnailFormatJpeg
import dev.g000sha256.tdl.dto.ThumbnailFormatMpeg4
import dev.g000sha256.tdl.dto.Video
import dev.g000sha256.tdl.dto.InviteLinkChatTypeChannel
import dev.g000sha256.tdl.dto.InviteLinkChatTypeSupergroup
import dev.g000sha256.tdl.dto.LinkPreviewTypeChat
import dev.g000sha256.tdl.dto.LinkPreviewTypeMessage
import dev.g000sha256.tdl.dto.LinkPreviewTypeStickerSet
import dev.g000sha256.tdl.dto.LinkPreviewTypeUser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Náhled odkazu: kam vede odkaz na Telegram, profilovka a obrázek z média (`LinkPreviewType*`). */
class LinkPreviewTargetTest {

    private val noPhoto = ChatPhoto(1, 0, null, emptyArray(), null, null, null)

    @Test
    fun kanalASkupinaSeRozlisi() {
        assertEquals(LinkPreviewTarget.Channel, LinkPreviewTypeChat(InviteLinkChatTypeChannel(), noPhoto, false).target())
        assertEquals(LinkPreviewTarget.Group, LinkPreviewTypeChat(InviteLinkChatTypeSupergroup(), noPhoto, true).target())
    }

    @Test
    fun clovekABotSeRozlisi() {
        assertEquals(LinkPreviewTarget.User, LinkPreviewTypeUser(noPhoto, false).target())
        assertEquals(LinkPreviewTarget.Bot, LinkPreviewTypeUser(noPhoto, true).target())
    }

    @Test
    fun profilovkaSeKresliVKruhu() {
        val preview = LinkPreview(url = "https://t.me/x", siteName = "Telegram", title = "X", description = null, photo = null, largePhoto = true, showAboveText = false)
        assertFalse(preview.roundPhoto)
        assertTrue(preview.copy(target = LinkPreviewTarget.Channel).roundPhoto)
        assertFalse(preview.copy(target = LinkPreviewTarget.Stickers).roundPhoto)
    }

    @Test
    fun profilovkaBezVelikostiNeniObrazek() {
        // Chat bez fotky: náhled zůstane textový, ne prázdný rámeček.
        assertNull(LinkPreviewTypeChat(InviteLinkChatTypeChannel(), noPhoto, false).previewPhoto())
    }

    @Test
    fun zpravaASadaMajiCilBezObrazku() {
        assertEquals(LinkPreviewTarget.Message, LinkPreviewTypeMessage().target())
        assertEquals(LinkPreviewTarget.Stickers, LinkPreviewTypeStickerSet(emptyArray()).target())
        assertNull(LinkPreviewTypeMessage().previewPhoto())
    }

    private fun file(id: Int) = File(id, 0, 0, LocalFile("", false, false, false, false, 0, 0, 0), RemoteFile("", "", false, false, 0))

    private fun animation(thumbnail: Thumbnail?) = Animation(3, 320, 240, "a.mp4", "video/mp4", false, null, thumbnail, file(1))

    @Test
    fun gifMaObrazekZRastrovehoNahledu() {
        val photo = LinkPreviewTypeAnimation(animation(Thumbnail(ThumbnailFormatJpeg(), 320, 240, file(7)))).previewPhoto()
        assertEquals(7, photo?.fileId)
        assertEquals(320, photo?.width)
        // Náhled jako MPEG4 v kartě nakreslit neumíme — obrázek žádný, ne rozbitý.
        assertNull(LinkPreviewTypeAnimation(animation(Thumbnail(ThumbnailFormatMpeg4(), 320, 240, file(8)))).previewPhoto())
    }

    @Test
    fun albumBerePrvniSnimek() {
        val video = Video(5, 640, 480, "v.mp4", "video/mp4", false, false, null, Thumbnail(ThumbnailFormatJpeg(), 320, 240, file(9)), file(2))
        assertEquals(9, LinkPreviewTypeAlbum(arrayOf(LinkPreviewAlbumMediaVideo(video)), "Výlet").previewPhoto()?.fileId)
    }
}
