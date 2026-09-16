package eu.tintera.plume.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.OutgoingAttachment
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Foundation.writeToFile
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIGraphicsImageRendererFormat
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIViewController
import platform.UniformTypeIdentifiers.UTTypeImage
import platform.UniformTypeIdentifiers.UTTypeItem
import platform.darwin.NSObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * iOS: PHPicker na fotky, UIDocumentPicker na soubory. Ani jeden nepotřebuje oprávnění ke knihovně
 * fotek — běží mimo proces aplikace a vydají jen to, co uživatel vybral.
 *
 * **Delegáty se drží v `remember`.** Kontrolery je drží jen slabě; bez silné reference by je
 * uklidil garbage collector dřív, než uživatel něco vybere, a výběr by tiše nic neudělal.
 */
@Composable
actual fun rememberAttachmentPicker(
    onPicked: (OutgoingAttachment) -> Unit,
    onError: (String) -> Unit,
): AttachmentPicker {
    val scope = rememberCoroutineScope()
    val picked by rememberUpdatedState(onPicked)
    val failed by rememberUpdatedState(onError)

    return remember {
        IosAttachmentPicker(
            scope = scope,
            onPicked = { picked(it) },
            onError = { failed(it) },
        )
    }
}

private class IosAttachmentPicker(
    private val scope: CoroutineScope,
    private val onPicked: (OutgoingAttachment) -> Unit,
    private val onError: (String) -> Unit,
) : AttachmentPicker {

    private val logger = Logger.withTag("Attachments")

    private fun prepare(block: suspend () -> OutgoingAttachment) {
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { block() } }
                .onSuccess(onPicked)
                .onFailure { failure ->
                    logger.w(failure) { "Přílohu se nepodařilo připravit." }
                    onError("Soubor se nepodařilo načíst.")
                }
        }
    }

    private val photoDelegate = object : NSObject(), PHPickerViewControllerDelegateProtocol {
        override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
            picker.dismissViewControllerAnimated(true, null)
            val result = didFinishPicking.firstOrNull() as? PHPickerResult ?: return
            prepare { preparePhoto(result) }
        }
    }

    private val documentDelegate = object : NSObject(), UIDocumentPickerDelegateProtocol {
        override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
            val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL ?: return
            prepare { prepareDocument(url) }
        }
    }

    override fun pickPhoto() {
        val configuration = PHPickerConfiguration().apply {
            filter = PHPickerFilter.imagesFilter
            selectionLimit = 1
        }
        val picker = PHPickerViewController(configuration = configuration)
        picker.delegate = photoDelegate
        present(picker)
    }

    override fun pickDocument() {
        // `asCopy`: soubor dostaneme jako kopii v prostoru aplikace, bez security-scoped přístupu.
        val picker = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeItem), asCopy = true)
        picker.delegate = documentDelegate
        present(picker)
    }

    /** Nad nejvýš položený kontroler — Compose obrazovka sama může něco prezentovat (sheet). */
    private fun present(controller: UIViewController) {
        var top = UIApplication.sharedApplication.keyWindow?.rootViewController ?: return
        while (true) top = top.presentedViewController ?: break
        top.presentViewController(controller, animated = true, completion = null)
    }
}

/**
 * Načte fotku jako data obrázku, zmenší na [PHOTO_MAX_EDGE] a uloží jako JPEG.
 *
 * Kreslení přes `UIGraphicsImageRenderer` použije orientaci obrázku, takže výsledný JPEG je
 * natočený správně i bez EXIF. HEIC z iPhonu se tím zároveň převede na JPEG, který přečte každý.
 */
@OptIn(ExperimentalForeignApi::class)
private suspend fun preparePhoto(result: PHPickerResult): OutgoingAttachment.Photo {
    val data = suspendCancellableCoroutine<NSData> { continuation ->
        result.itemProvider.loadDataRepresentationForTypeIdentifier(UTTypeImage.identifier) { data, error ->
            if (data != null) {
                continuation.resume(data)
            } else {
                continuation.resumeWithException(IllegalStateException(error?.localizedDescription ?: "Fotka nejde načíst."))
            }
        }
    }

    val image = UIImage.imageWithData(data) ?: error("Obrázek nejde dekódovat.")
    // `size` je v bodech a už po započtení orientace; skutečné pixely jsou body × scale.
    val (sourceWidth, sourceHeight) = image.size.useContents { width * image.scale to height * image.scale }
    val longest = max(sourceWidth, sourceHeight)
    val scale = if (longest > PHOTO_MAX_EDGE) PHOTO_MAX_EDGE / longest else 1.0
    val width = (sourceWidth * scale).roundToInt()
    val height = (sourceHeight * scale).roundToInt()

    val format = UIGraphicsImageRendererFormat.defaultFormat().apply { this.scale = 1.0 }
    val renderer = UIGraphicsImageRenderer(size = CGSizeMake(width.toDouble(), height.toDouble()), format = format)
    val scaled = renderer.imageWithActions { _ ->
        image.drawInRect(CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble()))
    }
    val jpeg = UIImageJPEGRepresentation(scaled, PHOTO_JPEG_QUALITY / 100.0) ?: error("JPEG nejde vytvořit.")

    val path = outgoingPath("photo.jpg")
    check(jpeg.writeToFile(path, atomically = true)) { "Fotku nejde uložit." }
    return OutgoingAttachment.Photo(path = path, width = width, height = height)
}

/**
 * Soubor z výběru je kopie v dočasném adresáři, který systém maže, jakmile delegát doběhne.
 * Proto se přesune k nám, se stejným jménem — to jméno TDLib pošle.
 */
@OptIn(ExperimentalForeignApi::class)
private fun prepareDocument(url: NSURL): OutgoingAttachment.Document {
    val name = url.lastPathComponent?.takeIf { it.isNotBlank() } ?: "soubor"
    val path = outgoingPath(name)
    val copied = NSFileManager.defaultManager.copyItemAtURL(url, toURL = NSURL.fileURLWithPath(path), error = null)
    check(copied) { "Soubor nejde zkopírovat." }
    return OutgoingAttachment.Document(path = path)
}

/** Cesta v dočasném adresáři aplikace, každá příloha ve vlastním podadresáři (jména se nepřepíšou). */
@OptIn(ExperimentalForeignApi::class)
private fun outgoingPath(name: String): String {
    val directory = NSTemporaryDirectory() + OUTGOING_DIRECTORY + "/" + NSUUID().UUIDString
    NSFileManager.defaultManager.createDirectoryAtPath(
        directory,
        withIntermediateDirectories = true,
        attributes = null,
        error = null,
    )
    return directory + "/" + name.replace('/', '_')
}
