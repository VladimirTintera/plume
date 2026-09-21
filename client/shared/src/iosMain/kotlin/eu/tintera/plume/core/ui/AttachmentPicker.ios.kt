package eu.tintera.plume.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.OutgoingAnimation
import eu.tintera.plume.core.telegram.OutgoingAttachment
import eu.tintera.plume.core.telegram.OutgoingThumbnail
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.attachment_prepare_failed
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AVFoundation.AVAssetExportPresetPassthrough
import platform.AVFoundation.AVAssetExportSession
import platform.AVFoundation.AVAssetExportSessionStatusCompleted
import platform.AVFoundation.AVAssetImageGenerator
import platform.AVFoundation.AVAssetTrack
import platform.AVFoundation.AVFileTypeMPEG4
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVURLAsset
import platform.AVFoundation.duration
import platform.AVFoundation.naturalSize
import platform.AVFoundation.preferredTransform
import platform.AVFoundation.tracksWithMediaType
import platform.CoreGraphics.CGImageRelease
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeApplyAffineTransform
import platform.CoreGraphics.CGSizeMake
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMake
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Foundation.writeToFile
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerConfigurationSelectionOrdered
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIApplication
import platform.UIKit.UIColor
import platform.UIKit.UIRectFill
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIGraphicsImageRendererFormat
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIViewController
import platform.UniformTypeIdentifiers.UTTypeImage
import platform.UniformTypeIdentifiers.UTTypeItem
import platform.UniformTypeIdentifiers.UTTypeMovie
import platform.darwin.NSObject

/**
 * iOS: PHPicker na fotky, UIDocumentPicker na soubory. Ani jeden nepotřebuje oprávnění ke knihovně
 * fotek — běží mimo proces aplikace a vydají jen to, co uživatel vybral.
 *
 * **Delegáty se drží v `remember`.** Kontrolery je drží jen slabě; bez silné reference by je
 * uklidil garbage collector dřív, než uživatel něco vybere, a výběr by tiše nic neudělal.
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
        IosAttachmentPicker(
            scope = scope,
            onPicked = { picked(it) },
            onError = { failed(it) },
        )
    }
}

private class IosAttachmentPicker(
    private val scope: CoroutineScope,
    private val onPicked: (List<OutgoingAttachment>) -> Unit,
    private val onError: (UiText) -> Unit,
) : AttachmentPicker {

    private val logger = Logger.withTag("Attachments")

    /** Připraví položky souběžně; `awaitAll` drží pořadí výběru. */
    private fun <T> prepare(items: List<T>, block: suspend (T) -> OutgoingAttachment) {
        if (items.isEmpty()) return
        scope.launch {
            val results = items
                .map { item -> async(Dispatchers.IO) { runCatching { block(item) } } }
                .awaitAll()
            results.forEach { result ->
                result.exceptionOrNull()?.let { failure -> logger.w(failure) { "Přílohu se nepodařilo připravit." } }
            }
            val prepared = results.mapNotNull { it.getOrNull() }
            if (prepared.isNotEmpty()) onPicked(prepared)
            if (prepared.size < results.size) onError(UiText(Res.string.attachment_prepare_failed))
        }
    }

    private val photoDelegate = object : NSObject(), PHPickerViewControllerDelegateProtocol {
        override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
            picker.dismissViewControllerAnimated(true, null)
            prepare(didFinishPicking.filterIsInstance<PHPickerResult>()) { result ->
                if (result.itemProvider.hasItemConformingToTypeIdentifier(UTTypeMovie.identifier)) {
                    prepareVideo(result)
                } else {
                    preparePhoto(result)
                }
            }
        }
    }

    private val documentDelegate = object : NSObject(), UIDocumentPickerDelegateProtocol {
        override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
            prepare(didPickDocumentsAtURLs.filterIsInstance<NSURL>().take(PICK_LIMIT)) { prepareDocument(it) }
        }
    }

    override fun pickMedia() {
        val configuration = PHPickerConfiguration().apply {
            filter = PHPickerFilter.anyFilterMatchingSubfilters(
                listOf(PHPickerFilter.imagesFilter, PHPickerFilter.videosFilter),
            )
            selectionLimit = PICK_LIMIT.toLong()
            // Pořadí ťuknutí, ne pořadí v knihovně — album odejde tak, jak ho uživatel skládal.
            selection = PHPickerConfigurationSelectionOrdered
        }
        val picker = PHPickerViewController(configuration = configuration)
        picker.delegate = photoDelegate
        present(picker)
    }

    override fun pickDocument() {
        // `asCopy`: soubor dostaneme jako kopii v prostoru aplikace, bez security-scoped přístupu.
        val picker = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeItem), asCopy = true)
        picker.allowsMultipleSelection = true
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

    return preparePhotoData(data)
}

/**
 * Fotka z dat obrázku — z výběru i ze sdílení (rozšíření předá soubor, viz `IosShareInbox`).
 * Zmenší se a uloží jako JPEG, viz [preparePhoto].
 */
@OptIn(ExperimentalForeignApi::class)
internal fun preparePhotoData(data: NSData): OutgoingAttachment.Photo {
    val image = UIImage.imageWithData(data) ?: error("Obrázek nejde dekódovat.")
    // `size` je v bodech a už po započtení orientace; skutečné pixely jsou body × scale.
    val (sourceWidth, sourceHeight) = image.size.useContents { width * image.scale to height * image.scale }
    val longest = max(sourceWidth, sourceHeight)
    val scale = if (longest > PHOTO_MAX_EDGE) PHOTO_MAX_EDGE / longest else 1.0
    val width = (sourceWidth * scale).roundToInt()
    val height = (sourceHeight * scale).roundToInt()

    /*
     * Neprůhledně a na bílou: JPEG průhlednost nemá a průhledné pixely z něj vyšly černé —
     * samolepka nebo PNG s průhledným pozadím by odešly na černém čtverci (stejně jako na Androidu).
     */
    val format = UIGraphicsImageRendererFormat.defaultFormat().apply {
        this.scale = 1.0
        this.opaque = true
    }
    val renderer = UIGraphicsImageRenderer(size = CGSizeMake(width.toDouble(), height.toDouble()), format = format)
    val scaled = renderer.imageWithActions { _ ->
        val rect = CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble())
        UIColor.whiteColor.setFill()
        UIRectFill(rect)
        image.drawInRect(rect)
    }
    val jpeg = UIImageJPEGRepresentation(scaled, PHOTO_JPEG_QUALITY / 100.0) ?: error("JPEG nejde vytvořit.")

    val path = outgoingPath("photo.jpg")
    check(jpeg.writeToFile(path, atomically = true)) { "Fotku nejde uložit." }
    return OutgoingAttachment.Photo(path = path, width = width, height = height)
}

/**
 * GIF ze schránky beze změny. Na MP4 ho převede server; rozměry stačí z první snímku
 * (`UIImage` z GIFu nese velikost animace), délku u GIFu nezná nikdo, dokud ho server nepřevede.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun prepareGifData(data: NSData): OutgoingAnimation {
    val image = UIImage.imageWithData(data) ?: error("GIF nejde přečíst.")
    val (width, height) = image.size.useContents { (width * image.scale).roundToInt() to (height * image.scale).roundToInt() }
    val path = outgoingPath("animation.gif")
    check(data.writeToFile(path, atomically = true)) { "GIF nejde uložit." }
    return OutgoingAnimation(path = path, width = width, height = height, durationSeconds = 0, thumbnail = null)
}

/** MP4 bez zvuku ze schránky jako GIF: tatáž příprava jako video, jen jiný druh zprávy. */
internal suspend fun prepareMp4AnimationData(data: NSData): OutgoingAnimation {
    val path = outgoingPath("animation.mp4")
    check(data.writeToFile(path, atomically = true)) { "Video nejde uložit." }
    val video = prepareVideoAt(path)
    return OutgoingAnimation(
        path = video.path,
        width = video.width,
        height = video.height,
        durationSeconds = video.durationSeconds,
        thumbnail = video.thumbnail,
    )
}

/**
 * Video z knihovny: zkopíruje se, přebalí do MP4 a přečtou se z něj rozměry, délka a první snímek.
 *
 * - **Soubor z `loadFileRepresentation` platí jen uvnitř callbacku** — systém ho hned potom maže,
 *   takže se kopíruje ještě v něm.
 * - **Přebalení, ne překódování** (`AVAssetExportPresetPassthrough`): iPhone ukládá `.mov`,
 *   ostatní klienti počítají s MP4. Stopy se jen přesunou do jiného kontejneru, trvá to zlomek
 *   délky videa. `shouldOptimizeForNetworkUse` dá index na začátek, takže video jde přehrávat
 *   během stahování (`streamable`). Když přebalení selže, jde původní soubor bez streamování.
 * - **Rozměry se berou po `preferredTransform`** — video na výšku je uložené naležato s otočením.
 */
@OptIn(ExperimentalForeignApi::class)
private suspend fun prepareVideo(result: PHPickerResult): OutgoingAttachment.Video {
    val original = suspendCancellableCoroutine<String> { continuation ->
        result.itemProvider.loadFileRepresentationForTypeIdentifier(UTTypeMovie.identifier) { url, error ->
            val copied = url?.let { source ->
                val path = outgoingPath(source.lastPathComponent?.takeIf { it.isNotBlank() } ?: "video.mov")
                path.takeIf {
                    NSFileManager.defaultManager.copyItemAtURL(source, toURL = NSURL.fileURLWithPath(it), error = null)
                }
            }
            if (copied != null) {
                continuation.resume(copied)
            } else {
                continuation.resumeWithException(IllegalStateException(error?.localizedDescription ?: "Video nejde načíst."))
            }
        }
    }

    return prepareVideoAt(original)
}

/**
 * Video, které už leží u nás ([original], v `outgoing`): přebalí se do MP4 a přečtou se z něj
 * rozměry, délka a první snímek. Z výběru i ze sdílení.
 */
@OptIn(ExperimentalForeignApi::class)
internal suspend fun prepareVideoAt(original: String): OutgoingAttachment.Video {
    val exported = exportMp4(original)
    val path = exported ?: original
    val asset = AVURLAsset(uRL = NSURL.fileURLWithPath(path), options = null)

    val track = asset.tracksWithMediaType(AVMediaTypeVideo).firstOrNull() as? AVAssetTrack
    val (width, height) = track?.let {
        CGSizeApplyAffineTransform(it.naturalSize, it.preferredTransform).useContents {
            abs(width).roundToInt() to abs(height).roundToInt()
        }
    } ?: (0 to 0)
    val duration = CMTimeGetSeconds(asset.duration).takeIf { !it.isNaN() } ?: 0.0

    return OutgoingAttachment.Video(
        path = path,
        width = width,
        height = height,
        durationSeconds = duration.roundToInt(),
        thumbnail = videoThumbnail(asset, directoryOf(path)),
        streamable = exported != null,
    )
}

/** Přebalí video do MP4 bez překódování; `null`, když to nejde (kodek, který MP4 nenese). */
@OptIn(ExperimentalForeignApi::class)
private suspend fun exportMp4(source: String): String? {
    val asset = AVURLAsset(uRL = NSURL.fileURLWithPath(source), options = null)
    val session = AVAssetExportSession(asset = asset, presetName = AVAssetExportPresetPassthrough)
    val target = directoryOf(source) + "/" + (source.substringAfterLast('/').substringBeforeLast('.') + ".mp4")
    if (target == source) return source
    session.outputURL = NSURL.fileURLWithPath(target)
    session.outputFileType = AVFileTypeMPEG4
    session.shouldOptimizeForNetworkUse = true

    val completed = suspendCancellableCoroutine<Boolean> { continuation ->
        session.exportAsynchronouslyWithCompletionHandler {
            continuation.resume(session.status == AVAssetExportSessionStatusCompleted)
        }
        continuation.invokeOnCancellation { session.cancelExport() }
    }
    if (!completed) return null
    NSFileManager.defaultManager.removeItemAtPath(source, error = null)
    return target
}

/** První snímek jako JPEG do [VIDEO_THUMBNAIL_EDGE]; bez náhledu video odejde taky. */
@OptIn(ExperimentalForeignApi::class)
private fun videoThumbnail(asset: AVURLAsset, directory: String): OutgoingThumbnail? {
    val generator = AVAssetImageGenerator(asset = asset).apply {
        appliesPreferredTrackTransform = true
        maximumSize = CGSizeMake(VIDEO_THUMBNAIL_EDGE.toDouble(), VIDEO_THUMBNAIL_EDGE.toDouble())
    }
    val cgImage = generator.copyCGImageAtTime(CMTimeMake(value = 0, timescale = 1), actualTime = null, error = null)
        ?: return null
    try {
        val image = UIImage.imageWithCGImage(cgImage)
        val jpeg = UIImageJPEGRepresentation(image, PHOTO_JPEG_QUALITY / 100.0) ?: return null
        val path = "$directory/thumbnail.jpg"
        if (!jpeg.writeToFile(path, atomically = true)) return null
        val (width, height) = image.size.useContents { width.roundToInt() to height.roundToInt() }
        return OutgoingThumbnail(path = path, width = width, height = height)
    } finally {
        CGImageRelease(cgImage)
    }
}

private fun directoryOf(path: String): String = path.substringBeforeLast('/')

/**
 * Soubor z výběru je kopie v dočasném adresáři, který systém maže, jakmile delegát doběhne.
 * Proto se přesune k nám, se stejným jménem — to jméno TDLib pošle.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun prepareDocument(url: NSURL): OutgoingAttachment.Document {
    val name = url.lastPathComponent?.takeIf { it.isNotBlank() } ?: "soubor"
    val path = outgoingPath(name)
    val copied = NSFileManager.defaultManager.copyItemAtURL(url, toURL = NSURL.fileURLWithPath(path), error = null)
    check(copied) { "Soubor nejde zkopírovat." }
    return OutgoingAttachment.Document(path = path)
}

/** Cesta v dočasném adresáři aplikace, každá příloha ve vlastním podadresáři (jména se nepřepíšou). */
@OptIn(ExperimentalForeignApi::class)
internal fun outgoingPath(name: String): String {
    val directory = NSTemporaryDirectory() + OUTGOING_DIRECTORY + "/" + NSUUID().UUIDString
    NSFileManager.defaultManager.createDirectoryAtPath(
        directory,
        withIntermediateDirectories = true,
        attributes = null,
        error = null,
    )
    return directory + "/" + name.replace('/', '_')
}
