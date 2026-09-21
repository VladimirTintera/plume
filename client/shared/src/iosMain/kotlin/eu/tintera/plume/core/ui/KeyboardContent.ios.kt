package eu.tintera.plume.core.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.contextmenu.builder.item
import androidx.compose.foundation.text.contextmenu.modifier.appendTextContextMenuComponents
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import co.touchlab.kermit.Logger
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.attachment_prepare_failed
import eu.tintera.plume.resources.paste_gif
import eu.tintera.plume.resources.paste_image
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import platform.UIKit.UIPasteboard

private val logger = Logger.withTag("KeyboardContent")

// Typy schránky (UTI). GIF z webu i z GIF klávesnic je `com.compuserve.gif`.
private const val GIF = "com.compuserve.gif"
private const val MP4 = "public.mpeg-4"

/** Statické obrázky, které umí `UIImage` dekódovat. WebP bývá samolepka. */
private val IMAGE_TYPES = listOf("public.png", "public.jpeg", "public.heic", "org.webmproject.webp")

/**
 * iOS klávesnice GIFy do pole nevkládají (`commitContent` tu nemá obdobu) — GIF klávesnice
 * i samolepky jdou přes SCHRÁNKU. Pole proto dostane v kontextové nabídce položku „Vložit GIF /
 * obrázek", když schránka nese typ, který chat přijme; systémové „Vložit" umí jen text.
 *
 * - **Typ se zjišťuje `containsPasteboardTypes`**, které iOS nehlásí jako čtení schránky. Data
 *   se čtou až po ťuknutí — a protože to není systémové „Vložit", iOS se zeptá „Povolit vložení?".
 *   Jinak než přes `UIPasteControl` (tlačítko kreslí systém) se tomu vyhnout nedá.
 * - **Nabídka je nová kontextová nabídka Compose**, zapnutá v `MainViewController`; položka se
 *   v ní objeví jako vlastní akce nativní nabídky úprav.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
actual fun KeyboardContentReceiver(
    acceptAnimations: Boolean,
    acceptImages: Boolean,
    onContent: (KeyboardContent) -> Unit,
    onError: (UiText) -> Unit,
    content: @Composable () -> Unit,
) {
    val types = buildList {
        if (acceptAnimations) addAll(listOf(GIF, MP4))
        if (acceptImages) addAll(IMAGE_TYPES)
    }
    val scope = rememberCoroutineScope()
    val received by rememberUpdatedState(onContent)
    val failed by rememberUpdatedState(onError)
    val pasteGif = stringResource(Res.string.paste_gif)
    val pasteImage = stringResource(Res.string.paste_image)

    Box(
        Modifier.appendTextContextMenuComponents {
            val type = pasteboardType(types) ?: return@appendTextContextMenuComponents
            item(
                key = PasteMediaKey,
                label = if (type == GIF || type == MP4) pasteGif else pasteImage,
            ) {
                close()
                paste(scope, types, { received(it) }, { failed(it) })
            }
        },
        propagateMinConstraints = true,
    ) {
        content()
    }
}

/** Klíč položky „Vložit GIF / obrázek" v kontextové nabídce pole. */
private object PasteMediaKey

/** Přijatelný typ ve schránce, nebo `null`. GIF má přednost — obrázek ze stejné položky by byl jen první snímek. */
private fun pasteboardType(accepted: List<String>): String? {
    if (accepted.isEmpty()) return null
    val pasteboard = UIPasteboard.generalPasteboard
    return accepted.firstOrNull { pasteboard.containsPasteboardTypes(listOf(it)) }
}

private fun paste(
    scope: CoroutineScope,
    accepted: List<String>,
    onContent: (KeyboardContent) -> Unit,
    onError: (UiText) -> Unit,
) {
    val type = pasteboardType(accepted) ?: return
    // UIKit: schránka se čte na hlavním vlákně, příprava souboru už mimo něj.
    val data = UIPasteboard.generalPasteboard.dataForPasteboardType(type)
    if (data == null) {
        // Uživatel v dotazu „Povolit vložení?" odmítl, nebo se schránka mezitím změnila.
        logger.i { "Schránka nevydala $type." }
        return
    }
    scope.launch {
        val prepared = withContext(Dispatchers.IO) {
            runCatching {
                when (type) {
                    GIF -> KeyboardContent.Animation(prepareGifData(data))
                    MP4 -> KeyboardContent.Animation(prepareMp4AnimationData(data))
                    else -> KeyboardContent.Image(preparePhotoData(data))
                }
            }
        }
        prepared
            .onSuccess(onContent)
            .onFailure {
                logger.w(it) { "Obsah ze schránky ($type) se nepodařilo připravit." }
                onError(UiText(Res.string.attachment_prepare_failed))
            }
    }
}
