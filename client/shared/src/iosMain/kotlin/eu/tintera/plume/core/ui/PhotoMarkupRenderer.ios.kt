package eu.tintera.plume.core.ui

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.CoreGraphics.CGPointMake
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.CoreGraphics.CGLineCap
import platform.CoreGraphics.CGLineJoin
import platform.Foundation.writeToFile
import platform.UIKit.UIBezierPath
import platform.UIKit.UIColor
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIGraphicsImageRendererFormat
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation

class IosPhotoMarkupRenderer : PhotoMarkupRenderer {

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun render(path: String, strokes: List<MarkupStroke>): MarkedPhoto = withContext(Dispatchers.Default) {
        val image = UIImage.imageWithContentsOfFile(path) ?: error("Obrázek nejde dekódovat.")
        val (width, height) = image.size.useContents { width * image.scale to height * image.scale }
        val format = UIGraphicsImageRendererFormat.defaultFormat().apply {
            scale = 1.0
            opaque = true
        }
        val renderer = UIGraphicsImageRenderer(size = CGSizeMake(width, height), format = format)
        val pixelWidth = width.toInt()
        val pixelHeight = height.toInt()
        val marked = renderer.imageWithActions { _ ->
            image.drawInRect(CGRectMake(0.0, 0.0, width, height))
            strokes.forEach { stroke ->
                val points = stroke.points
                val x0 = points[0] * width
                val y0 = points[1] * height
                // Jedna cesta na tah, jako na Androidu: průsvitný zvýrazňovač se na spojích nesmí
                // překrývat. Ťuknutí je úsečka nulové délky — s kulatým koncem z ní je tečka.
                val line = UIBezierPath().apply {
                    moveToPoint(CGPointMake(x0, y0))
                    if (points.size == 2) addLineToPoint(CGPointMake(x0, y0))
                    for (i in 2 until points.size step 2) addLineToPoint(CGPointMake(points[i] * width, points[i + 1] * height))
                    lineWidth = stroke.tool.strokeWidth(pixelWidth, pixelHeight).toDouble()
                    lineCapStyle = CGLineCap.kCGLineCapRound
                    lineJoinStyle = CGLineJoin.kCGLineJoinRound
                }
                stroke.color.toUiColor().setStroke()
                line.stroke()
            }
        }
        val jpeg = UIImageJPEGRepresentation(marked, PHOTO_JPEG_QUALITY / 100.0) ?: error("JPEG nejde vytvořit.")
        val output = outgoingPath("photo.jpg")
        check(jpeg.writeToFile(output, atomically = true)) { "Fotku nejde uložit." }
        MarkedPhoto(output, pixelWidth, pixelHeight)
    }
}

private fun Long.toUiColor(): UIColor = UIColor.colorWithRed(
    red = ((this shr 16) and 0xFF) / 255.0,
    green = ((this shr 8) and 0xFF) / 255.0,
    blue = (this and 0xFF) / 255.0,
    alpha = ((this shr 24) and 0xFF) / 255.0,
)
