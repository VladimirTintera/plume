@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package eu.tintera.plume.core.stickers

import co.touchlab.kermit.Logger
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGColorSpaceRelease
import platform.CoreGraphics.CGContextDrawImage
import platform.CoreGraphics.CGContextRelease
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGImageGetHeight
import platform.CoreGraphics.CGImageGetWidth
import platform.CoreGraphics.CGImageRef
import platform.CoreGraphics.CGImageRelease
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.CoreImage.CIContext
import platform.CoreImage.CIImage
import platform.CoreImage.createCGImage
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import platform.Foundation.create
import platform.Foundation.writeToFile
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIGraphicsImageRendererFormat
import platform.UIKit.UIImage
import platform.Vision.VNGenerateForegroundInstanceMaskRequest
import platform.Vision.VNImageRequestHandler
import platform.Vision.VNInstanceMaskObservation

/**
 * [StickerMaker] na iOS: odstranění pozadí přes Vision (`VNGenerateForegroundInstanceMaskRequest`,
 * od iOS 17 — aplikace cílí na 18.2, takže je vždycky), zbytek je společný s desktopem ve Skie
 * (`SkiaStickers.kt`): zmenšení, bílý obrys a **WebP**.
 *
 * - **Obrázek se napřed překreslí přes UIKit** (`UIGraphicsImageRenderer`): to srovná otočení
 *   podle EXIF, které by holý `CGImage` z fotky z fotoaparátu ignoroval, a rovnou zmenší.
 */
class IosStickerMaker : StickerMaker {
    private val logger = Logger.withTag("StickerMaker")

    /**
     * Vision masku v simulátoru neudělá („does not support …Revision1": maska potřebuje Neural
     * Engine, který simulátor nemá); každé iOS 18 zařízení ho má (A12 a novější).
     */
    override val canRemoveBackground: Boolean = NSProcessInfo.processInfo.environment["SIMULATOR_UDID"] == null

    override suspend fun make(path: String, removeBackground: Boolean, outline: Boolean): Result<MadeSticker> = runCatching {
        withContext(Dispatchers.Default) {
            val photo = decode(path, DECODE_SIDE) ?: error("Obrázek $path nejde přečíst.")
            val content = try {
                if (removeBackground) cutOut(photo) else photo.toSkiaImage()
            } finally {
                CGImageRelease(photo)
            }
            val sticker = encodeSticker(content, outline = removeBackground && outline)
            MadeSticker(save(sticker.bytes, "sticker.webp"), sticker.width, sticker.height)
        }
    }.onFailure { logger.w(it) { "Samolepku z $path nejde vyrobit." } }

    override suspend fun makeCover(path: String): Result<MadeSticker> = runCatching {
        withContext(Dispatchers.Default) {
            val bytes = NSFileManager.defaultManager.contentsAtPath(path)?.toByteArray() ?: error("Samolepku $path nejde přečíst.")
            val cover = encodeCover(bytes)
            MadeSticker(save(cover.bytes, "cover.webp"), cover.width, cover.height)
        }
    }.onFailure { logger.w(it) { "Obálku sady z $path nejde vyrobit." } }

    /**
     * Jen hlavní objekt, oříznutý na něj (`croppedToInstancesExtent`). Bez objektu Vision
     * nevrátí žádné pozorování — to je [NoStickerSubjectException], jako na Androidu.
     */
    private fun cutOut(photo: CGImageRef): Image = memScoped {
        val error = alloc<ObjCObjectVar<NSError?>>()
        val request = VNGenerateForegroundInstanceMaskRequest()
        val handler = VNImageRequestHandler(cGImage = photo, options = emptyMap<Any?, Any?>())
        check(handler.performRequests(listOf(request), error.ptr)) { "Vision: ${error.value?.localizedDescription}" }
        val observation = request.results?.firstOrNull() as? VNInstanceMaskObservation ?: throw NoStickerSubjectException()
        val masked = observation.generateMaskedImageOfInstances(
            instances = observation.allInstances,
            fromRequestHandler = handler,
            croppedToInstancesExtent = true,
            error = error.ptr,
        ) ?: error("Vision: ${error.value?.localizedDescription}")
        val image = CIImage.imageWithCVPixelBuffer(masked)
        val cg = CIContext.contextWithOptions(null).createCGImage(image, fromRect = image.extent) ?: error("Výřez nejde vykreslit.")
        try {
            cg.toSkiaImage()
        } finally {
            CGImageRelease(cg)
        }
    }

    /** Fotka překreslená přes UIKit (otočení podle EXIF), delší strana nejvýš [maxSide]. */
    private fun decode(path: String, maxSide: Int): CGImageRef? {
        val image = UIImage.imageWithContentsOfFile(path) ?: return null
        val (width, height) = image.size.useContents { width * image.scale to height * image.scale }
        if (width <= 0 || height <= 0) return null
        val scale = minOf(1.0, maxSide / maxOf(width, height))
        val format = UIGraphicsImageRendererFormat.defaultFormat().apply {
            this.scale = 1.0
            opaque = false
        }
        val size = CGSizeMake(width * scale, height * scale)
        val redrawn = UIGraphicsImageRenderer(size = size, format = format).imageWithActions {
            image.drawInRect(CGRectMake(0.0, 0.0, width * scale, height * scale))
        }
        return redrawn.CGImage?.also { platform.CoreGraphics.CGImageRetain(it) }
    }

    private fun save(bytes: ByteArray, name: String): String {
        val directory = NSTemporaryDirectory() + "stickers"
        NSFileManager.defaultManager.createDirectoryAtPath(directory, withIntermediateDirectories = true, attributes = null, error = null)
        val path = "$directory/${NSUUID().UUIDString}-$name"
        check(bytes.toNSData().writeToFile(path, atomically = true)) { "Soubor $path nejde uložit." }
        return path
    }

    private companion object {
        /** Dekóduje se zhruba na dvojnásobek samolepky: segmentaci víc nepomůže, jen ji zpomalí. */
        const val DECODE_SIDE = 1024
    }
}

/** `CGImage` do Skii přes bitmapový kontext RGBA (premultiplied), řádek 0 je horní okraj. */
private fun CGImageRef.toSkiaImage(): Image {
    val width = CGImageGetWidth(this).toInt()
    val height = CGImageGetHeight(this).toInt()
    val bytes = ByteArray(width * height * 4)
    val colorSpace = CGColorSpaceCreateDeviceRGB()
    bytes.usePinned { pinned ->
        val context = CGBitmapContextCreate(
            data = pinned.addressOf(0),
            width = width.toULong(),
            height = height.toULong(),
            bitsPerComponent = 8u,
            bytesPerRow = (width * 4).toULong(),
            space = colorSpace,
            bitmapInfo = CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value,
        )
        CGContextDrawImage(context, CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble()), this)
        CGContextRelease(context)
    }
    CGColorSpaceRelease(colorSpace)
    return Image.makeRaster(rgbaInfo(width, height), bytes, width * 4)
}

private fun NSData.toByteArray(): ByteArray = ByteArray(length.toInt()).apply {
    if (isNotEmpty()) usePinned { platform.posix.memcpy(it.addressOf(0), bytes, length) }
}

private fun ByteArray.toNSData(): NSData = usePinned { NSData.create(bytes = it.addressOf(0), length = size.toULong()) }
