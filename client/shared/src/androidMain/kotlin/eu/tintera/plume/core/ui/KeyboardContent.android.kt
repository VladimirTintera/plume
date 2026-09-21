package eu.tintera.plume.core.ui

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.PlatformTextInputInterceptor
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import co.touchlab.kermit.Logger
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.attachment_prepare_failed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val logger = Logger.withTag("KeyboardContent")

private const val GIF = "image/gif"
private const val MP4 = "video/mp4"

/** Statické obrázky, které umí `preparePhoto` dekódovat. WebP bývá samolepka z klávesnice. */
private val IMAGE_TYPES = listOf("image/png", "image/jpeg", "image/webp")

/**
 * Obalí vstupní spojení pole: do `EditorInfo` doplní, co pole přijme, a `commitContent`
 * od klávesnice převezme. Obojí musí jít až PO původním `createInputConnection` — ten `EditorInfo`
 * teprve vyplňuje a naše typy by přepsal.
 */
@Composable
actual fun KeyboardContentReceiver(
    acceptAnimations: Boolean,
    acceptImages: Boolean,
    onContent: (KeyboardContent) -> Unit,
    onError: (UiText) -> Unit,
    content: @Composable () -> Unit,
) {
    val mimeTypes = buildList {
        if (acceptAnimations) addAll(listOf(GIF, MP4))
        if (acceptImages) addAll(IMAGE_TYPES)
    }
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val received by rememberUpdatedState(onContent)
    val failed by rememberUpdatedState(onError)

    val interceptor = remember(mimeTypes) {
        PlatformTextInputInterceptor { request, nextHandler ->
            // Obal zůstává i bez typů: jiná větev kompozice by pole složila znovu a vzala mu fokus.
            if (mimeTypes.isEmpty()) nextHandler.startInputMethod(request)
            nextHandler.startInputMethod(
                PlatformTextInputMethodRequest { outAttributes ->
                    val connection = request.createInputConnection(outAttributes)
                    EditorInfoCompat.setContentMimeTypes(outAttributes, mimeTypes.toTypedArray())
                    InputConnectionCompat.createWrapper(connection, outAttributes) { info, flags, _ ->
                        receive(context, scope, info, flags, mimeTypes, { received(it) }, { failed(it) })
                    }
                },
            )
        }
    }
    InterceptPlatformTextInput(interceptor = interceptor, content = content)
}

/**
 * Vrací, jestli obsah převzalo. `false` pro nepřijatý typ — klávesnice pak může zkusit jiný
 * formát (Gboard nabízí GIF i jako MP4).
 */
private fun receive(
    context: Context,
    scope: CoroutineScope,
    info: InputContentInfoCompat,
    flags: Int,
    accepted: List<String>,
    onContent: (KeyboardContent) -> Unit,
    onError: (UiText) -> Unit,
): Boolean {
    val description = info.description
    val type = accepted.firstOrNull { description.hasMimeType(it) } ?: return false

    // Od Androidu 7.1 dává klávesnice oprávnění ke čtení URI až na vyžádání, a jen na chvíli.
    val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1 &&
        flags and InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION != 0
    if (needsPermission) {
        runCatching { info.requestPermission() }.onFailure {
            logger.w(it) { "Klávesnice nedala oprávnění k obsahu." }
            return false
        }
    }

    scope.launch {
        val prepared = withContext(Dispatchers.IO) {
            try {
                runCatching {
                    when (type) {
                        GIF, MP4 -> KeyboardContent.Animation(
                            prepareAnimation(context, info.contentUri, isVideo = type == MP4),
                        )
                        else -> KeyboardContent.Image(preparePhoto(context, info.contentUri))
                    }
                }
            } finally {
                // Kopie je hotová (nebo nebude), oprávnění už netřeba.
                if (needsPermission) runCatching { info.releasePermission() }
            }
        }
        prepared
            .onSuccess(onContent)
            .onFailure {
                logger.w(it) { "Obsah z klávesnice ($type) se nepodařilo připravit." }
                onError(UiText(Res.string.attachment_prepare_failed))
            }
    }
    return true
}
