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
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
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
 * od iOS 17 — aplikace cílí na 18.2, takže je vždycky), zbytek ve Skie, kterou na iOS kreslí
 * Compose: zmenšení, bílý obrys a **WebP**.
 *
 * - **WebP umí zakódovat jen Skia.** ImageIO WebP jen čte (`CGImageDestinationCopyTypeIdentifiers`
 *   nabízí PNG a HEIC); PNG fotky v 512 px snadno přeleze 512 kB, které Telegram u statické
 *   samolepky snese.
 * - **Obrázek se napřed překreslí přes UIKit** (`UIGraphicsImageRenderer`): to srovná otočení
 *   podle EXIF, které by holý `CGImage` z fotky z fotoaparátu ignoroval, a rovnou zmenší.
 * - Rozměry, obrys a obálka jsou tytéž jako na Androidu (`StickerGeometry.kt`).
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
            val sticker = if (removeBackground && outline) withOutline(content) else scaled(content, stickerSize(content.width, content.height))
            val bytes = sticker.encodeToData(EncodedImageFormat.WEBP, WEBP_QUALITY)?.bytes ?: error("WebP nejde zakódovat.")
            MadeSticker(save(bytes, "sticker.webp"), sticker.width, sticker.height)
        }
    }.onFailure { logger.w(it) { "Samolepku z $path nejde vyrobit." } }

    override suspend fun makeCover(path: String): Result<MadeSticker> = runCatching {
        withContext(Dispatchers.Default) {
            val bytes = NSFileManager.defaultManager.contentsAtPath(path)?.toByteArray() ?: error("Samolepku $path nejde přečíst.")
            val sticker = Image.makeFromEncoded(bytes)
            val (width, height) = fitInSquare(sticker.width, sticker.height, COVER_SIDE)
            val surface = Surface.makeRaster(rgbaInfo(COVER_SIDE, COVER_SIDE))
            val left = (COVER_SIDE - width) / 2f
            val top = (COVER_SIDE - height) / 2f
            surface.canvas.drawImageRect(
                sticker,
                Rect.makeWH(sticker.width.toFloat(), sticker.height.toFloat()),
                Rect.makeXYWH(left, top, width.toFloat(), height.toFloat()),
                SMOOTH,
                null,
                true,
            )
            val cover = surface.makeImageSnapshot()
            // Strop 32 kB: v 100 px ho WebP s kvalitou 90 přeleze jen výjimečně, pak se ubírá.
            var quality = WEBP_QUALITY
            var encoded: ByteArray
            do {
                encoded = cover.encodeToData(EncodedImageFormat.WEBP, quality)?.bytes ?: error("WebP nejde zakódovat.")
                quality -= COVER_QUALITY_STEP
            } while (encoded.size > COVER_MAX_BYTES && quality > 0)
            MadeSticker(save(encoded, "cover.webp"), COVER_SIDE, COVER_SIDE)
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

/**
 * Vyříznutý objekt s bílým obrysem po svém tvaru — totéž jako na Androidu: objekt se zmenší,
 * aby se s obrysem vešel do [STICKER_SIDE], bílá maska (jen pixely nad [ALPHA_THRESHOLD]) se
 * nakreslí na každém posunu z [outlineOffsets] a objekt přijde navrch.
 */
internal fun withOutline(content: Image): Image {
    val (width, height) = fitInSquare(content.width, content.height, STICKER_SIDE - 2 * OUTLINE_WIDTH)
    val scaled = scaled(content, width to height)
    val mask = whiteMask(scaled)
    val surface = Surface.makeRaster(rgbaInfo(width + 2 * OUTLINE_WIDTH, height + 2 * OUTLINE_WIDTH))
    val source = Rect.makeWH(width.toFloat(), height.toFloat())
    for ((dx, dy) in outlineOffsets()) {
        surface.canvas.drawImageRect(mask, source, Rect.makeXYWH(dx, dy, width.toFloat(), height.toFloat()), SMOOTH, null, true)
    }
    surface.canvas.drawImage(scaled, OUTLINE_WIDTH.toFloat(), OUTLINE_WIDTH.toFloat())
    return surface.makeImageSnapshot()
}

/** Bílá silueta (premultiplied: bílá s alfou `a` je `a,a,a,a`) jen z pixelů, které jsou vidět. */
private fun whiteMask(image: Image): Image {
    val info = rgbaInfo(image.width, image.height)
    val pixels = org.jetbrains.skia.Bitmap().apply {
        allocPixels(info)
        check(image.readPixels(this)) { "Pixely nejde přečíst." }
    }.readPixels(info, image.width * 4, 0, 0) ?: error("Pixely nejde přečíst.")
    for (index in pixels.indices step 4) {
        val alpha = pixels[index + 3].toInt() and 0xFF
        val value = (if (alpha > ALPHA_THRESHOLD) alpha else 0).toByte()
        pixels[index] = value
        pixels[index + 1] = value
        pixels[index + 2] = value
        pixels[index + 3] = value
    }
    return Image.makeRaster(info, pixels, image.width * 4)
}

private fun scaled(image: Image, size: Pair<Int, Int>): Image {
    val (width, height) = size
    val surface = Surface.makeRaster(rgbaInfo(width, height))
    surface.canvas.drawImageRect(
        image,
        Rect.makeWH(image.width.toFloat(), image.height.toFloat()),
        Rect.makeWH(width.toFloat(), height.toFloat()),
        SMOOTH,
        null,
        true,
    )
    return surface.makeImageSnapshot()
}

private fun rgbaInfo(width: Int, height: Int) = ImageInfo(width, height, ColorType.RGBA_8888, ColorAlphaType.PREMUL)

/** Zmenšení přes mipmapy: z 1024 px na 512 by holé lineární vzorkování zubatělo. */
private val SMOOTH = FilterMipmap(FilterMode.LINEAR, MipmapMode.LINEAR)

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
