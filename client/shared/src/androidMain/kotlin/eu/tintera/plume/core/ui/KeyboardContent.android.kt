package eu.tintera.plume.core.ui

import android.content.ClipDescription
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.activity.compose.LocalActivity
import android.view.DragEvent
import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.contextmenu.builder.item
import androidx.compose.foundation.text.contextmenu.modifier.appendTextContextMenuComponents
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
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
import eu.tintera.plume.resources.paste_gif
import eu.tintera.plume.resources.paste_image
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

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
    acceptFiles: Boolean,
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
    val pasteGif = stringResource(Res.string.paste_gif)
    val pasteImage = stringResource(Res.string.paste_image)
    InterceptPlatformTextInput(interceptor = interceptor) {
        /*
         * Vložení ze schránky. Systémové „Vložit" v poli umí jen TEXT — obrázek ve schránce ho ani
         * neukáže. Proto vlastní položka v kontextové nabídce pole, jen když schránka nese typ, který
         * chat přijme. Builder běží při každém otevření nabídky, schránka se čte až tehdy — popis
         * (`primaryClipDescription`) systém nehlásí jako čtení schránky, obsah až po ťuknutí.
         */
        val activity = LocalActivity.current
        var dragOver by remember { mutableStateOf(false) }
        val dropTarget = remember(mimeTypes, acceptFiles, activity) {
            object : DragAndDropTarget {
                override fun onEntered(event: DragAndDropEvent) { dragOver = true }
                override fun onExited(event: DragAndDropEvent) { dragOver = false }
                override fun onEnded(event: DragAndDropEvent) { dragOver = false }
                override fun onDrop(event: DragAndDropEvent): Boolean {
                    dragOver = false
                    return drop(context, activity, scope, event.toAndroidDragEvent(), mimeTypes, acceptFiles, { received(it) }, { failed(it) })
                }
            }
        }
        val dropHighlight = MaterialTheme.colorScheme.primary.copy(alpha = DROP_HIGHLIGHT_ALPHA)
        Box(
            Modifier
                .dragAndDropTarget(
                    shouldStartDragAndDrop = { event -> event.mimeTypes().any { accepts(it, mimeTypes, acceptFiles) } },
                    target = dropTarget,
                )
                // Pole nad sebou drží přetahovaný obsah — tónem, ne rámečkem, který by měnil rozměr.
                .drawBehind { if (dragOver) drawRect(dropHighlight) }
                .appendTextContextMenuComponents {
                val type = clipboardType(context, mimeTypes) ?: return@appendTextContextMenuComponents
                item(
                    key = PasteMediaKey,
                    label = if (type == GIF || type == MP4) pasteGif else pasteImage,
                ) {
                    close()
                    pasteFromClipboard(context, scope, mimeTypes, { received(it) }, { failed(it) })
                }
            },
            propagateMinConstraints = true,
        ) {
            content()
        }
    }
}

/** Android se na vložení ze schránky neptá; položka v kontextové nabídce stačí. */
@Composable
actual fun ClipboardPasteSuggestion(
    active: Boolean,
    acceptAnimations: Boolean,
    acceptImages: Boolean,
    onContent: (KeyboardContent) -> Unit,
    onError: (UiText) -> Unit,
    container: @Composable (kind: ClipboardMediaKind, pasteButton: @Composable () -> Unit) -> Unit,
) = Unit

/**
 * Přijme přetahovaný typ? Obrázky a GIFy podle [accepted], s [acceptFiles] jakýkoli soubor — jen
 * ne typy, které nese přetahovaný TEXT ([TEXT_DRAG_TYPES]): ten patří do pole jako text, a to umí
 * pole samo. Celou rodinu `text/…` vyřadit nejde, vyřadilo by i soubory `.xml` nebo `.csv`.
 */
private fun accepts(type: String, accepted: List<String>, acceptFiles: Boolean): Boolean =
    type in accepted || (acceptFiles && type !in TEXT_DRAG_TYPES)

/** Typy přetahovaného textu: prostý text, HTML z prohlížeče, odkaz a intent (zástupce aplikace). */
private val TEXT_DRAG_TYPES = setOf(
    ClipDescription.MIMETYPE_TEXT_PLAIN,
    ClipDescription.MIMETYPE_TEXT_HTML,
    ClipDescription.MIMETYPE_TEXT_URILIST,
    ClipDescription.MIMETYPE_TEXT_INTENT,
)

/**
 * Obsah přetažený z jiné aplikace. Oprávnění ke čtení jejích URI dá systém až na vyžádání
 * (`requestDragAndDropPermissions`), a jen do jejich uvolnění — to proto čeká, až se všechno
 * zkopíruje. Každá položka podle SVÉHO typu: obrázek a GIF jako ze schránky, jiný soubor jako
 * příloha. Víc položek naráz (víc fotek) přibude nad polem všechny, jako z výběru.
 */
private fun drop(
    context: Context,
    activity: Activity?,
    scope: CoroutineScope,
    event: DragEvent,
    accepted: List<String>,
    acceptFiles: Boolean,
    onContent: (KeyboardContent) -> Unit,
    onError: (UiText) -> Unit,
): Boolean {
    val clip = event.clipData ?: return false
    val uris = (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri }
    if (uris.isEmpty()) return false
    val permissions = activity?.requestDragAndDropPermissions(event)
    scope.launch {
        uris.forEach { uri ->
            val type = context.contentResolver.getType(uri)
                ?: accepted.firstOrNull { clip.description.hasMimeType(it) }
                ?: "application/octet-stream"
            if (!accepts(type, accepted, acceptFiles)) return@forEach
            val prepared = withContext(Dispatchers.IO) {
                runCatching {
                    when (type) {
                        GIF, MP4 -> KeyboardContent.Animation(prepareAnimation(context, uri, isVideo = type == MP4))
                        in accepted -> KeyboardContent.Image(preparePhoto(context, uri))
                        else -> KeyboardContent.File(prepareDocument(context, uri))
                    }
                }
            }
            prepared
                .onSuccess(onContent)
                .onFailure {
                    logger.w(it) { "Přetažený obsah ($type) se nepodařilo připravit." }
                    onError(UiText(Res.string.attachment_prepare_failed))
                }
        }
        permissions?.release()
    }
    return true
}

/** Tón přes pole, nad kterým se drží přetahovaný obsah. */
private const val DROP_HIGHLIGHT_ALPHA = 0.12f

/** Klíč položky „Vložit GIF / obrázek" v kontextové nabídce pole. */
private object PasteMediaKey

private fun clipboardManager(context: Context): ClipboardManager? =
    context.getSystemService(ClipboardManager::class.java)

/** Přijatelný typ obsahu ve schránce, nebo `null`. Text ve schránce vkládá systémové „Vložit". */
private fun clipboardType(context: Context, accepted: List<String>): String? {
    if (accepted.isEmpty()) return null
    val description = clipboardManager(context)?.primaryClipDescription ?: return null
    return accepted.firstOrNull { description.hasMimeType(it) }
}

/**
 * Vezme ze schránky první položku s adresou obsahu. Oprávnění ke čtení dává systém aplikaci
 * v popředí sám, na rozdíl od klávesnice se o ně nežádá.
 */
private fun pasteFromClipboard(
    context: Context,
    scope: CoroutineScope,
    accepted: List<String>,
    onContent: (KeyboardContent) -> Unit,
    onError: (UiText) -> Unit,
) {
    val type = clipboardType(context, accepted) ?: return
    val clip = clipboardManager(context)?.primaryClip ?: return
    val uri = (0 until clip.itemCount).firstNotNullOfOrNull { clip.getItemAt(it).uri } ?: run {
        logger.w { "Schránka hlásí $type, ale nenese adresu obsahu." }
        onError(UiText(Res.string.attachment_prepare_failed))
        return
    }
    prepare(context, scope, uri, type, onContent, onError, release = {})
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

    prepare(context, scope, info.contentUri, type, onContent, onError) {
        // Kopie je hotová (nebo nebude), oprávnění už netřeba.
        if (needsPermission) runCatching { info.releasePermission() }
    }
    return true
}

/**
 * Zkopíruje obsah do cache mimo hlavní vlákno a předá ho dál. [release] běží po kopii, ať se
 * povedla, nebo ne. Sdílí to klávesnice i schránka — obojí dává `content://` adresu s typem.
 */
private fun prepare(
    context: Context,
    scope: CoroutineScope,
    uri: Uri,
    type: String,
    onContent: (KeyboardContent) -> Unit,
    onError: (UiText) -> Unit,
    release: () -> Unit,
) {
    scope.launch {
        val prepared = withContext(Dispatchers.IO) {
            try {
                runCatching {
                    when (type) {
                        GIF, MP4 -> KeyboardContent.Animation(prepareAnimation(context, uri, isVideo = type == MP4))
                        else -> KeyboardContent.Image(preparePhoto(context, uri))
                    }
                }
            } finally {
                release()
            }
        }
        prepared
            .onSuccess(onContent)
            .onFailure {
                logger.w(it) { "Vložený obsah ($type) se nepodařilo připravit." }
                onError(UiText(Res.string.attachment_prepare_failed))
            }
    }
}
