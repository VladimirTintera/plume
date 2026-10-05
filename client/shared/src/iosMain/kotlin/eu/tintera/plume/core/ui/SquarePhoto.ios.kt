package eu.tintera.plume.core.ui

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIGraphicsImageRendererFormat
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.Foundation.writeToFile
import kotlin.math.min

class IosSquarePhoto : SquarePhoto {

    /** Kreslí se do čtverce posunutá o levý horní roh výřezu — tím vznikne ořez. */
    @OptIn(ExperimentalForeignApi::class)
    override suspend fun crop(path: String, region: CropRegion?): String = withContext(Dispatchers.Default) {
        val image = UIImage.imageWithContentsOfFile(path) ?: error("Obrázek nejde dekódovat.")
        val (width, height) = image.size.useContents { width * image.scale to height * image.scale }
        val (left, top, sidePixels) = squarePixels(region, width.toInt(), height.toInt())
        val side = sidePixels.toDouble()
        val edge = min(side, PROFILE_PHOTO_EDGE.toDouble())
        val factor = edge / side
        val format = UIGraphicsImageRendererFormat.defaultFormat().apply {
            scale = 1.0
            opaque = true
        }
        val renderer = UIGraphicsImageRenderer(size = CGSizeMake(edge, edge), format = format)
        val square = renderer.imageWithActions { _ ->
            image.drawInRect(
                CGRectMake(-left * factor, -top * factor, width * factor, height * factor),
            )
        }
        val jpeg = UIImageJPEGRepresentation(square, PHOTO_JPEG_QUALITY / 100.0) ?: error("JPEG nejde vytvořit.")
        val output = outgoingPath("profile.jpg")
        check(jpeg.writeToFile(output, atomically = true)) { "Fotku nejde uložit." }
        output
    }
}
