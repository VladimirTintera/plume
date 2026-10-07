package eu.tintera.plume.core.telegram

import dev.g000sha256.tdl.dto.InputChatPhoto
import dev.g000sha256.tdl.dto.InputChatPhotoAnimation
import dev.g000sha256.tdl.dto.InputChatPhotoStatic
import dev.g000sha256.tdl.dto.InputFileLocal

/**
 * Nová profilová fotka (účtu nebo chatu) připravená na disku. Fotka jde přes CESTU
 * (`InputFileLocal`), ne přes systémové URI — TDLib jiné nepřijme, stejně jako u příloh.
 */
sealed interface ProfilePhotoFile {
    val path: String

    /** Čtvercový JPEG. */
    data class Still(override val path: String) : ProfilePhotoFile

    /**
     * Animovaná profilovka: čtvercové MP4 bez zvuku, nejvýš 10 s, strana 160–1280 px a do 2 MB
     * (podmínky `InputChatPhotoAnimation`). Statickou fotku z něj vyrobí server ze snímku
     * v čase [mainFrameSeconds] od začátku animace — toho, který si uživatel vybral v ořezu.
     */
    data class Animated(override val path: String, val mainFrameSeconds: Double = 0.0) : ProfilePhotoFile
}

internal fun ProfilePhotoFile.toInputChatPhoto(): InputChatPhoto = when (this) {
    is ProfilePhotoFile.Still -> InputChatPhotoStatic(photo = InputFileLocal(path = path))
    is ProfilePhotoFile.Animated -> InputChatPhotoAnimation(
        animation = InputFileLocal(path = path),
        mainFrameTimestamp = mainFrameSeconds,
    )
}
