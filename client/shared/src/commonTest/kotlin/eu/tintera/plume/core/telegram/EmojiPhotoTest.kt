package eu.tintera.plume.core.telegram

import dev.g000sha256.tdl.dto.BackgroundFillGradient
import dev.g000sha256.tdl.dto.ChatPhotoStickerTypeCustomEmoji
import dev.g000sha256.tdl.dto.InputChatPhotoSticker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Fotka z emoji: co jde do `setProfilePhoto` / `setChatPhoto`. */
class EmojiPhotoTest {

    @Test
    fun emojiJdeJakoSamolepkaSPrechodem() {
        val photo = ProfilePhotoFile.Emoji(customEmojiId = 5368324170671202286, background = EmojiPhotoBackground(0x5A7FFF, 0x2CA0F2))

        val input = assertIs<InputChatPhotoSticker>(photo.toInputChatPhoto())
        assertEquals(5368324170671202286, assertIs<ChatPhotoStickerTypeCustomEmoji>(input.sticker.type).customEmojiId)
        val fill = assertIs<BackgroundFillGradient>(input.sticker.backgroundFill)
        assertEquals(0x5A7FFF, fill.topColor)
        assertEquals(0x2CA0F2, fill.bottomColor)
        assertEquals(0, fill.rotationAngle)
    }

    /** TDLib bere barvy pozadí jako RGB — alfa navíc by server odmítl. */
    @Test
    fun pozadiJsouRgbBezAlfy() {
        assertTrue(EMOJI_PHOTO_BACKGROUNDS.isNotEmpty())
        EMOJI_PHOTO_BACKGROUNDS.forEach { background ->
            assertTrue(background.top in 0..0xFFFFFF && background.bottom in 0..0xFFFFFF, "$background")
        }
    }
}
