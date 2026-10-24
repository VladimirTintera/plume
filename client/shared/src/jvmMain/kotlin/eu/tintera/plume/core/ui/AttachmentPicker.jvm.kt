package eu.tintera.plume.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.OutgoingAttachment
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.attachment_prepare_failed
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.imageio.ImageIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data

/**
 * Desktop: systémový dialog souborů (AWT `FileDialog`, na macOS nativní panel). Soubor se
 * nekopíruje — na rozdíl od mobilů tu žádný dočasný adresář výběru není a TDLib ho při odeslání
 * přečte z původního místa.
 *
 * Fotka (JPEG, PNG, WebP, BMP, kterou umí ImageIO přečíst) jde jako fotka, všechno ostatní
 * včetně videa jako soubor: rozměry a délku videa by musel přečíst dekodér, který desktop zatím
 * nemá.
 */
@Composable
actual fun rememberAttachmentPicker(
    onPicked: (List<OutgoingAttachment>) -> Unit,
    onError: (UiText) -> Unit,
): AttachmentPicker {
    val scope = rememberCoroutineScope()
    val picked by rememberUpdatedState(onPicked)
    val failed by rememberUpdatedState(onError)

    return remember {
        object : AttachmentPicker {
            override fun pickMedia() = pick(media = true)

            override fun pickDocument() = pick(media = false)

            private fun pick(media: Boolean) {
                val files = chooseFiles(media)
                if (files.isEmpty()) return
                scope.launch {
                    val results = withContext(Dispatchers.IO) {
                        files.map { file -> async { runCatching { prepareAttachment(file, media) } } }.awaitAll()
                    }
                    results.forEach { result ->
                        result.exceptionOrNull()?.let { Logger.withTag("AttachmentPicker").w(it) { "Příloha nejde připravit." } }
                    }
                    val prepared = results.mapNotNull { it.getOrNull() }
                    if (prepared.isNotEmpty()) picked(prepared)
                    if (prepared.size < results.size) failed(UiText(Res.string.attachment_prepare_failed))
                }
            }
        }
    }
}

private fun chooseFiles(media: Boolean): List<File> {
    val dialog = FileDialog(null as Frame?).apply {
        isMultipleMode = true
        if (media) {
            // Filtr jde jen přes jméno; macOS FileDialog ho respektuje, Windows ne (tam projde vše).
            setFilenameFilter { _, name -> name.substringAfterLast('.', "").lowercase() in MEDIA_EXTENSIONS }
        }
    }
    dialog.isVisible = true
    return dialog.files.orEmpty().toList().take(PICK_LIMIT)
}

internal fun prepareAttachment(file: File, media: Boolean): OutgoingAttachment {
    require(file.isFile) { "Není soubor: ${file.path}" }
    if (media && file.extension.lowercase() in PHOTO_EXTENSIONS) {
        val image = ImageIO.read(file)
        if (image != null) {
            val (width, height) = uprightSize(file, image.width, image.height)
            return OutgoingAttachment.Photo(path = file.path, width = width, height = height)
        }
    }
    return OutgoingAttachment.Document(path = file.path)
}

/**
 * Rozměry fotky tak, jak ji ukazuje aplikace: Skia (Coil, editor fotky) ji otočí podle EXIF,
 * ImageIO ne. Bez otočení by snímek z fotoaparátu na výšku měl prohozené rozměry a editor
 * i bublina by ho kreslily do špatného poměru stran. `Codec` dekóduje jen hlavičku.
 */
internal fun uprightSize(file: File, width: Int, height: Int): Pair<Int, Int> = runCatching {
    Codec.makeFromData(Data.makeFromBytes(file.readBytes())).encodedOrigin.swapsWidthHeight()
}.getOrDefault(false).let { swaps -> if (swaps) height to width else width to height }

internal val PHOTO_EXTENSIONS = setOf("jpg", "jpeg", "png", "bmp", "webp")
private val MEDIA_EXTENSIONS = PHOTO_EXTENSIONS + setOf("gif", "mp4", "mov", "m4v", "webm", "mkv")
