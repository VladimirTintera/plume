package eu.tintera.plume.core.ui

import androidx.compose.foundation.ContextMenuDataProvider
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragData
import androidx.compose.ui.draganddrop.dragData
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.type
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.platform.DesktopOs
import eu.tintera.plume.core.platform.desktopOs
import eu.tintera.plume.core.telegram.OutgoingAnimation
import eu.tintera.plume.core.telegram.OutgoingAttachment
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.attachment_prepare_failed
import eu.tintera.plume.resources.paste_as_attachment
import eu.tintera.plume.resources.paste_image
import java.awt.Color
import java.awt.Image
import java.awt.RenderingHints
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.image.BufferedImage
import java.awt.image.MultiResolutionImage
import java.io.File
import java.net.URI
import javax.imageio.ImageIO
import javax.swing.ImageIcon
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

private val logger = Logger.withTag("KeyboardContent")

/** Co pole právě přijme a komu to předat. Plocha ([ContentDropArea]) ho čte až při puštění. */
private class ContentTarget(
    val acceptAnimations: Boolean,
    val acceptImages: Boolean,
    val acceptFiles: Boolean,
    val onContent: (KeyboardContent) -> Unit,
    val onError: (UiText) -> Unit,
    /** Fokus pro pole — psaní mimo pole ho sem vrátí ([ContentDropArea]). */
    val focus: FocusRequester,
) {
    val acceptsAny: Boolean get() = acceptAnimations || acceptImages || acceptFiles
}

/** Pole přihlášené u plochy kolem; `null`, dokud žádné složené není. */
private class ContentTargetSlot {
    var target: State<ContentTarget>? by mutableStateOf(null)
}

private val LocalContentTargetSlot = staticCompositionLocalOf<ContentTargetSlot?> { null }

/**
 * Desktop: Cmd+V (macOS), Ctrl+V nebo Shift+Insert v poli vloží zkopírované SOUBORY (Finder,
 * Průzkumník) jako přílohy a OBRÁZEK ze schránky (snímek obrazovky, „Kopírovat obrázek"
 * v prohlížeči) jako fotku. Text ve schránce vkládá pole samo — klávesa se pak propustí dál.
 *
 * Totéž nabídne kontextová nabídka pole: „Vložit obrázek", u souborů „Vložit jako přílohu".
 *
 * Přetažení přijímá [ContentDropArea] kolem; pole se u ní jen přihlásí se svými právy.
 *
 * **Pole je výchozí cíl klávesnice**: po složení (otevření chatu, přepnutí Alt+↓, zavření hledání
 * nebo výběru) si vezme fokus, ať jde rovnou psát — jako Telegram Desktop nebo WhatsApp. Na
 * telefonu by fokus vysunul klávesnici přes půl konverzace, proto jen tady.
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
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    LaunchedEffect(focus) { focus.requestFocus() }
    val target = rememberUpdatedState(ContentTarget(acceptAnimations, acceptImages, acceptFiles, onContent, onError, focus))
    val slot = LocalContentTargetSlot.current
    DisposableEffect(slot, target) {
        slot?.target = target
        onDispose { if (slot != null && slot.target === target) slot.target = null }
    }
    val pasteImage = stringResource(Res.string.paste_image)
    val pasteAsAttachment = stringResource(Res.string.paste_as_attachment)
    /*
     * Položka v kontextové nabídce pole, jen když schránka nese, co pole přijme. Stará nabídka
     * Compose (desktop novou nemá zapnutou) bere položky z `ContextMenuDataProvider` nad polem
     * a seznam si skládá až při otevření — schránka se tak čte v tu chvíli, ne při skládání.
     */
    ContextMenuDataProvider(
        items = {
            val current = target.value
            when (val paste = clipboardPaste(current)) {
                null -> emptyList()
                is ClipboardPaste.Files -> listOf(ContextMenuItem(pasteAsAttachment) { paste(scope, paste, current) })
                is ClipboardPaste.Picture -> listOf(ContextMenuItem(pasteImage) { paste(scope, paste, current) })
            }
        },
    ) {
        Box(
            // Na obalu, ne na poli: požadavek fokusu jde na první zaostřitelný prvek uvnitř, a tím
            // je pole samo — ikony v jeho dekoraci leží až pod ním.
            Modifier.focusRequester(focus).onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || !event.isPaste()) return@onPreviewKeyEvent false
                val current = target.value
                val paste = clipboardPaste(current) ?: return@onPreviewKeyEvent false
                paste(scope, paste, current)
                true
            },
            propagateMinConstraints = true,
        ) {
            content()
        }
    }
}

/** Návrh nad polem nepotřebuje: vložení klávesou se systém neptá, jako na iOS. */
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
 * Přetažené soubory z Finderu nebo Průzkumníka. Přijímá jen seznam souborů — obrázek tažený
 * z prohlížeče přichází jako obraz bez souboru a ten zatím ne.
 *
 * **Psaní kamkoli do konverzace jde do pole.** Stisk znaku, který nespotřeboval nic jiného
 * (zaostřený je výběr textu v bublině, tlačítko…), vrátí fokus poli. Znak sám pole dostane
 * hned potom: AWT posílá stisk (`KEY_PRESSED`) a napsaný znak (`KEY_TYPED`) jako dvě události
 * a druhá jde už k nově zaostřenému poli — i s diakritikou a mrtvými klávesami, které by
 * vlastní vkládání znaku nezvládlo. Stisk se proto nespotřebuje.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
actual fun ContentDropArea(modifier: Modifier, content: @Composable BoxScope.() -> Unit) {
    val slot = remember { ContentTargetSlot() }
    val scope = rememberCoroutineScope()
    var dragOver by remember { mutableStateOf(false) }
    val dropTarget = remember(slot) {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) { dragOver = true }
            override fun onExited(event: DragAndDropEvent) { dragOver = false }
            override fun onEnded(event: DragAndDropEvent) { dragOver = false }
            override fun onDrop(event: DragAndDropEvent): Boolean {
                dragOver = false
                val target = slot.target?.value ?: return false
                val files = (event.dragData() as? DragData.FilesList)?.readFiles().orEmpty()
                    .mapNotNull { runCatching { File(URI(it)) }.getOrNull() }
                if (files.isEmpty()) return false
                receiveFiles(scope, files, target)
                return true
            }
        }
    }
    CompositionLocalProvider(LocalContentTargetSlot provides slot) {
        Box(
            modifier.onKeyEvent { event ->
                if (event.startsTyping()) slot.target?.value?.focus?.requestFocus()
                false
            }.dragAndDropTarget(
                shouldStartDragAndDrop = { event ->
                    // Tah přílohy z vlastního okna ven (`fileDragSource`) se sem přiložit nemá.
                    !OutgoingFileDrag.active && slot.target?.value?.acceptsAny == true && event.dragData() is DragData.FilesList
                },
                target = dropTarget,
            ),
        ) {
            content()
            ContentDropOverlay(visible = dragOver, modifier = Modifier.matchParentSize())
        }
    }
}

/**
 * Stisk, ze kterého bude napsaný znak: bez Ctrl, Alt a Meta (zkratky) a s tisknutelným znakem.
 * Šipky, Tab, Esc ani F-klávesy znak nemají (`CHAR_UNDEFINED`), Enter a Backspace jsou řídicí.
 */
private fun KeyEvent.startsTyping(): Boolean {
    if (type != KeyEventType.KeyDown || isCtrlPressed || isAltPressed || isMetaPressed) return false
    val char = utf16CodePoint
    return char >= ' '.code && char != 0x7F && char != java.awt.event.KeyEvent.CHAR_UNDEFINED.code
}

private fun KeyEvent.isPaste(): Boolean = when {
    isAltPressed -> false
    desktopOs == DesktopOs.MacOs -> isMetaPressed && key == Key.V
    else -> (isCtrlPressed && key == Key.V) || (isShiftPressed && key == Key.Insert)
}

/** Co ze schránky vloží pole místo textu. */
private sealed interface ClipboardPaste {
    data class Files(val files: List<File>) : ClipboardPaste

    /** Obrázek se ze schránky čte až při vložení — pro nabídku stačí vědět, že tam je. */
    data class Picture(val clip: Transferable) : ClipboardPaste
}

/**
 * Co ze schránky vložit místo textu, nebo `null` — pak vkládá pole samo. Pořadí: soubory
 * (Finder k nim přidává i jméno jako text), pak obrázek — ale jen BEZ textu vedle. Excel
 * a Word dávají k buňkám a odstavcům i jejich obrázek a vložit se má text, kdežto snímek
 * obrazovky nebo „Kopírovat obrázek" text nenesou.
 */
private fun clipboardPaste(target: ContentTarget): ClipboardPaste? {
    val clip = runCatching { Toolkit.getDefaultToolkit().systemClipboard.getContents(null) }
        .onFailure { logger.w(it) { "Schránka nejde přečíst." } }
        .getOrNull() ?: return null
    clip.files()?.let { files -> return if (target.acceptsAny) ClipboardPaste.Files(files) else null }
    if (!target.acceptImages || clip.isDataFlavorSupported(DataFlavor.stringFlavor)) return null
    if (!clip.isDataFlavorSupported(DataFlavor.imageFlavor)) return null
    return ClipboardPaste.Picture(clip)
}

private fun paste(scope: CoroutineScope, paste: ClipboardPaste, target: ContentTarget) {
    when (paste) {
        is ClipboardPaste.Files -> receiveFiles(scope, paste.files, target)
        is ClipboardPaste.Picture -> scope.launch {
            // Ze schránky se čte na vlákně UI, jako vždy v AWT; mimo něj jde jen zmenšení a zápis.
            val image = runCatching { paste.clip.getTransferData(DataFlavor.imageFlavor) as Image }
            image.mapCatching { withContext(Dispatchers.IO) { preparePastedImage(it) } }
                .onSuccess { target.onContent(KeyboardContent.Image(it)) }
                .onFailure {
                    logger.w(it) { "Obrázek ze schránky se nepodařilo připravit." }
                    target.onError(UiText(Res.string.attachment_prepare_failed))
                }
        }
    }
}

private fun Transferable.files(): List<File>? {
    if (!isDataFlavorSupported(DataFlavor.javaFileListFlavor)) return null
    return runCatching { (getTransferData(DataFlavor.javaFileListFlavor) as List<*>).filterIsInstance<File>() }
        .getOrNull()
        ?.takeIf { it.isNotEmpty() }
}

private fun receiveFiles(scope: CoroutineScope, files: List<File>, target: ContentTarget) {
    scope.launch {
        withContext(Dispatchers.IO) {
            runCatching { prepareFiles(files, acceptAnimations = target.acceptAnimations, acceptImages = target.acceptImages) }
        }
            .onSuccess(target.onContent)
            .onFailure {
                logger.w(it) { "Soubory (${files.size}) se nepodařilo připravit." }
                target.onError(UiText(Res.string.attachment_prepare_failed))
            }
    }
}

/**
 * Přetažené nebo zkopírované soubory jako obsah pole. Jeden GIF jde jako animace (hned, jako na
 * Androidu); jsou-li VŠECHNY fotky, jdou jako fotky (album), jinak všechno jako soubory — fotky
 * a soubory se v jedné zprávě nemíchají a po jednom by se navzájem nahradily. Co chat nepřijme,
 * odfiltruje a ohlásí až `addAttachments`. Adresáře se přeskočí.
 */
internal fun prepareFiles(files: List<File>, acceptAnimations: Boolean, acceptImages: Boolean): KeyboardContent {
    val regular = files.filter { it.isFile }
    require(regular.isNotEmpty()) { "Žádný z přetažených objektů není soubor." }
    val single = regular.singleOrNull()
    if (single != null && acceptAnimations && single.extension.equals("gif", ignoreCase = true)) {
        val frame = ImageIO.read(single)
        if (frame != null) {
            return KeyboardContent.Animation(
                OutgoingAnimation(path = single.path, width = frame.width, height = frame.height, durationSeconds = 0, thumbnail = null),
            )
        }
    }
    val photos = acceptImages && regular.all { it.extension.lowercase() in PHOTO_EXTENSIONS }
    return KeyboardContent.Attachments(regular.map { prepareAttachment(it, media = photos) })
}

/**
 * Obrázek ze schránky jako JPEG zmenšený na [PHOTO_MAX_EDGE] — jako fotka z výběru na mobilu.
 * Snímek obrazovky z Retiny má přes 5 000 px a PNG desítky MB, TDLib přijme fotku do 10 MB.
 * Průhlednost se podloží bílou (JPEG ji neunese, černé pozadí by z ní udělalo kaňku).
 */
private fun preparePastedImage(image: Image): OutgoingAttachment.Photo {
    val largest = (image as? MultiResolutionImage)?.resolutionVariants
        ?.maxByOrNull { it.getWidth(null).toLong() * it.getHeight(null) }
        ?: image
    // Obrázek z Toolkitu se načítá líně a bez načtení má rozměry -1; ImageIcon na načtení počká.
    val source = if (largest is BufferedImage) largest else ImageIcon(largest).image
    val width = source.getWidth(null)
    val height = source.getHeight(null)
    require(width > 0 && height > 0) { "Obrázek ze schránky nemá rozměry." }
    val scale = minOf(1.0, PHOTO_MAX_EDGE.toDouble() / maxOf(width, height))
    val targetWidth = (width * scale).roundToInt().coerceAtLeast(1)
    val targetHeight = (height * scale).roundToInt().coerceAtLeast(1)
    val rgb = BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB)
    rgb.createGraphics().apply {
        color = Color.WHITE
        fillRect(0, 0, targetWidth, targetHeight)
        setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        drawImage(source, 0, 0, targetWidth, targetHeight, null)
        dispose()
    }
    val file = File.createTempFile("plume-paste-", ".jpg")
    writeJpeg(rgb, file)
    return OutgoingAttachment.Photo(path = file.path, width = targetWidth, height = targetHeight)
}
