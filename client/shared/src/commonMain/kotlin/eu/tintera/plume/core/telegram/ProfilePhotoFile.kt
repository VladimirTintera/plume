package eu.tintera.plume.core.telegram

import dev.g000sha256.tdl.dto.BackgroundFillGradient
import dev.g000sha256.tdl.dto.ChatPhotoSticker
import dev.g000sha256.tdl.dto.ChatPhotoStickerTypeCustomEmoji
import dev.g000sha256.tdl.dto.InputChatPhoto
import dev.g000sha256.tdl.dto.InputChatPhotoAnimation
import dev.g000sha256.tdl.dto.InputChatPhotoSticker
import dev.g000sha256.tdl.dto.InputChatPhotoStatic
import dev.g000sha256.tdl.dto.InputFileLocal

/**
 * Nová profilová fotka (účtu nebo chatu). Soubor jde přes CESTU (`InputFileLocal`), ne přes
 * systémové URI — TDLib jiné nepřijme, stejně jako u příloh. Fotku z emoji ([Emoji]) vyrobí
 * až server, na disku nic není.
 */
sealed interface ProfilePhotoFile {

    /** Čtvercový JPEG. */
    data class Still(val path: String) : ProfilePhotoFile

    /**
     * Animovaná profilovka: čtvercové MP4 bez zvuku, nejvýš 10 s, strana 160–1280 px a do 2 MB
     * (podmínky `InputChatPhotoAnimation`). Statickou fotku z něj vyrobí server ze snímku
     * v čase [mainFrameSeconds] od začátku animace — toho, který si uživatel vybral v ořezu.
     */
    data class Animated(val path: String, val mainFrameSeconds: Double = 0.0) : ProfilePhotoFile

    /**
     * Vlastní emoji na barevném pozadí (`InputChatPhotoSticker`). Obrázek i animaci vyrobí
     * server; aplikace posílá jen id emoji a přechod pozadí.
     */
    data class Emoji(val customEmojiId: Long, val background: EmojiPhotoBackground) : ProfilePhotoFile
}

/**
 * Svislý přechod pozadí fotky z emoji, barvy jako RGB bez alfy (tak je chce `BackgroundFill`).
 * Jsou to DATA fotky, která uvidí všichni v každém klientovi, ne barvy rozhraní — proto pevné
 * hodnoty, ne role z motivu.
 */
data class EmojiPhotoBackground(val top: Int, val bottom: Int)

/** Nabídka pozadí: syté dvojice, na kterých je emoji vidět ve světlém i tmavém klientovi. */
val EMOJI_PHOTO_BACKGROUNDS: List<EmojiPhotoBackground> = listOf(
    EmojiPhotoBackground(0x5A7FFF, 0x2CA0F2),
    EmojiPhotoBackground(0x4DD08A, 0x1E9E8C),
    EmojiPhotoBackground(0xFFB547, 0xFF6A3D),
    EmojiPhotoBackground(0xFF6F91, 0xD6336C),
    EmojiPhotoBackground(0xB07CFF, 0x6C4BE0),
    EmojiPhotoBackground(0x5ED3F3, 0x3A7BD5),
    EmojiPhotoBackground(0xF8D84A, 0xF29E2E),
    EmojiPhotoBackground(0x8E9AAF, 0x4A5568),
)

internal fun ProfilePhotoFile.toInputChatPhoto(): InputChatPhoto = when (this) {
    is ProfilePhotoFile.Still -> InputChatPhotoStatic(photo = InputFileLocal(path = path))
    is ProfilePhotoFile.Animated -> InputChatPhotoAnimation(
        animation = InputFileLocal(path = path),
        mainFrameTimestamp = mainFrameSeconds,
    )
    is ProfilePhotoFile.Emoji -> InputChatPhotoSticker(
        sticker = ChatPhotoSticker(
            type = ChatPhotoStickerTypeCustomEmoji(customEmojiId = customEmojiId),
            backgroundFill = BackgroundFillGradient(
                topColor = background.top,
                bottomColor = background.bottom,
                rotationAngle = 0,
            ),
        ),
    )
}
