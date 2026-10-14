package eu.tintera.plume.core.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.text.contextmenu.builder.item
import androidx.compose.foundation.text.contextmenu.modifier.appendTextContextMenuComponents
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitInteropInteractionMode
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
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
import platform.Foundation.NSData
import platform.Foundation.NSItemProvider
import platform.Foundation.NSNotification
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIButtonConfigurationCornerStyleCapsule
import platform.UIKit.UIColor
import platform.CoreGraphics.CGSizeMake
import platform.UIKit.UIPasteConfiguration
import platform.UIKit.UIPasteConfigurationSupportingProtocol
import platform.UIKit.UIPasteControl
import platform.UIKit.UIPasteControlConfiguration
import platform.UIKit.UIPasteControlDisplayMode
import platform.UIKit.UIPasteboard
import platform.UIKit.UIPasteboardChangedNotification
import platform.UIKit.UIResponder
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

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
    acceptFiles: Boolean,
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
    prepare(scope, type, data, onContent, onError)
}

/** Uloží data ze schránky do cache mimo hlavní vlákno a předá je dál. Sdílí to nabídka i tlačítko. */
private fun prepare(
    scope: CoroutineScope,
    type: String,
    data: NSData,
    onContent: (KeyboardContent) -> Unit,
    onError: (UiText) -> Unit,
) {
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

private fun acceptedTypes(acceptAnimations: Boolean, acceptImages: Boolean) = buildList {
    if (acceptAnimations) addAll(listOf(GIF, MP4))
    if (acceptImages) addAll(IMAGE_TYPES)
}

/**
 * `changeCount` schránky, ze které se naposledy vkládalo. Pro celý proces, ne pro obrazovku —
 * po návratu do chatu (nebo v jiném) by návrh visel znovu nad obsahem, který už odešel.
 */
private val pastedChangeCount = mutableStateOf<Long?>(null)

/**
 * Návrh vložení s `UIPasteControl`.
 *
 * - **Co je ve schránce, se zjišťuje `containsPasteboardTypes`** (bez dotazu) při zobrazení,
 *   při změně schránky (`UIPasteboardChangedNotification`) a po návratu do aplikace — schránku
 *   mezitím mohla změnit jiná aplikace a tu změnu notifikace nehlásí.
 * - **Vložený obsah se podruhé nenabízí**: pamatuje se `changeCount` schránky, ze které se
 *   vkládalo. Návrh by jinak visel nad polem, dokud uživatel schránku nepřepíše.
 * - **Tlačítko potřebuje cíl** (`UIPasteConfigurationSupporting`): obyčejný `UIResponder`
 *   s `pasteConfiguration` na přijímané typy. Systém podle ní tlačítko povolí a po ťuknutí
 *   zavolá `pasteItemProviders` — data se pak čtou z `NSItemProvider`, bez dotazu.
 */
@OptIn(ExperimentalForeignApi::class, ExperimentalComposeUiApi::class)
@Composable
actual fun ClipboardPasteSuggestion(
    active: Boolean,
    acceptAnimations: Boolean,
    acceptImages: Boolean,
    onContent: (KeyboardContent) -> Unit,
    onError: (UiText) -> Unit,
    container: @Composable (kind: ClipboardMediaKind, pasteButton: @Composable () -> Unit) -> Unit,
) {
    val types = acceptedTypes(acceptAnimations, acceptImages)
    if (!active || types.isEmpty()) return

    val scope = rememberCoroutineScope()
    val received by rememberUpdatedState(onContent)
    val failed by rememberUpdatedState(onError)
    var available by remember(types) { mutableStateOf(pasteboardType(types)) }
    var changeCount by remember { mutableStateOf(UIPasteboard.generalPasteboard.changeCount) }

    DisposableEffect(types) {
        val center = NSNotificationCenter.defaultCenter
        val refresh: (NSNotification?) -> Unit = {
            available = pasteboardType(types)
            changeCount = UIPasteboard.generalPasteboard.changeCount
        }
        val observers = listOf(UIPasteboardChangedNotification, UIApplicationDidBecomeActiveNotification).map { name ->
            center.addObserverForName(name, `object` = null, queue = NSOperationQueue.mainQueue, usingBlock = refresh)
        }
        onDispose { observers.forEach(center::removeObserver) }
    }

    val type = available ?: return
    if (changeCount == pastedChangeCount.value) return

    val kind = if (type == GIF || type == MP4) ClipboardMediaKind.Animation else ClipboardMediaKind.Image
    val colors = MaterialTheme.colorScheme
    val target = remember(types) {
        PasteTarget(types) { providerType, data ->
            /*
             * Obě hodnoty z ŽIVÉ schránky. `changeCount` se mění i bez notifikace (jiná aplikace,
             * na simulátoru synchronizace se schránkou Macu) — s číslem z doby zobrazení by se
             * vložený obsah nepoznal jako vložený a návrh by zůstal viset.
             */
            val current = UIPasteboard.generalPasteboard.changeCount
            changeCount = current
            pastedChangeCount.value = current
            prepare(scope, providerType, data, { received(it) }, { failed(it) })
        }
    }
    val control = remember(target) {
        val configuration = UIPasteControlConfiguration().apply {
            displayMode = UIPasteControlDisplayMode.UIPasteControlDisplayModeIconAndLabel
            cornerStyle = UIButtonConfigurationCornerStyleCapsule
            baseBackgroundColor = colors.primary.toUIColor()
            baseForegroundColor = colors.onPrimary.toUIColor()
        }
        UIPasteControl(configuration = configuration).apply {
            this.target = target
            backgroundColor = UIColor.clearColor
        }
    }
    /*
     * `intrinsicContentSize` u `UIPasteControl` rozměr NEHLÁSÍ (−1, „bez rozměru") a Compose by
     * mu dal nulovou plochu — tlačítko by nebylo vidět. Měří se Auto Layoutem; kdyby ani ten nic
     * nevrátil, platí minimum, pod které systém tlačítko stejně nevykreslí (musí být celé vidět).
     */
    val (width, height) = remember(control) {
        control.systemLayoutSizeFittingSize(CGSizeMake(0.0, 0.0)).useContents {
            width.coerceAtLeast(PASTE_BUTTON_MIN_WIDTH) to height.coerceAtLeast(PASTE_BUTTON_MIN_HEIGHT)
        }
    }

    container(kind) {
        UIKitView(
            factory = { control },
            // Body UIKitu jsou v Compose na iOS totéž co dp.
            modifier = Modifier.size(width.dp, height.dp),
            /*
             * NAD kreslicí vrstvou Compose, ne pod ní. Výchozí interop view leží pod plátnem
             * Metalu a prosvítá průhledným otvorem — pro iOS je tím tlačítko ZAKRYTÉ, ťuknutí
             * neprojde ověřením „uživatel opravdu viděl systémové tlačítko" a vložení se zeptá
             * na povolení stejně jako naše položka v nabídce. Nativní přístupnost: VoiceOver
             * má číst systémové „Vložit", ne nic.
             */
            properties = UIKitInteropProperties(
                interactionMode = UIKitInteropInteractionMode.NonCooperative,
                isNativeAccessibilityEnabled = true,
                placedAsOverlay = true,
            ),
        )
    }
}

/**
 * Cíl `UIPasteControl`. Vezme první poskytovatele, který nese přijímaný typ, a data předá
 * [onData] — `loadDataRepresentation` volá zpět na vlákně na pozadí, příprava souboru tam může
 * zůstat, výsledek se předá přes korutinu na hlavním vlákně.
 */
@OptIn(ExperimentalForeignApi::class)
private class PasteTarget(
    private val types: List<String>,
    private val onData: (type: String, data: NSData) -> Unit,
) : UIResponder(), UIPasteConfigurationSupportingProtocol {

    private val configuration = UIPasteConfiguration(acceptableTypeIdentifiers = types)

    override fun pasteConfiguration(): UIPasteConfiguration = configuration

    override fun setPasteConfiguration(pasteConfiguration: UIPasteConfiguration?) = Unit

    override fun pasteItemProviders(itemProviders: List<*>) {
        val provider = itemProviders.filterIsInstance<NSItemProvider>().firstOrNull() ?: return
        val type = types.firstOrNull { provider.hasItemConformingToTypeIdentifier(it) } ?: return
        provider.loadDataRepresentationForTypeIdentifier(type) { data, error ->
            if (data != null) {
                dispatch_async(dispatch_get_main_queue()) { onData(type, data) }
            } else {
                logger.w { "Schránka nevydala $type: ${error?.localizedDescription}" }
            }
        }
    }
}

private const val PASTE_BUTTON_MIN_WIDTH = 96.0
private const val PASTE_BUTTON_MIN_HEIGHT = 36.0

private fun Color.toUIColor(): UIColor =
    UIColor.colorWithRed(red.toDouble(), green = green.toDouble(), blue = blue.toDouble(), alpha = alpha.toDouble())

/** Přetažení iOS zatím nepřijímá (chtělo by to `UIDropInteraction`); plocha jen skládá obsah. */
@Composable
actual fun ContentDropArea(modifier: Modifier, content: @Composable BoxScope.() -> Unit) =
    Box(modifier, content = content)
