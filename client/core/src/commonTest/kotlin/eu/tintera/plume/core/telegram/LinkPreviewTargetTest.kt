package eu.tintera.plume.core.telegram

import dev.g000sha256.tdl.dto.ChatPhoto
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

/** Náhled odkazu na Telegram: kam vede a jestli je obrázek profilovka (`LinkPreviewType*`). */
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
}
