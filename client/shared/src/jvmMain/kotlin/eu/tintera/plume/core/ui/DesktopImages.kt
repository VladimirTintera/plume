package eu.tintera.plume.core.ui

import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Profilová fotka přes ImageIO: výřez, zmenšení na [PROFILE_PHOTO_EDGE] a JPEG. */
class DesktopSquarePhoto : SquarePhoto {

    override suspend fun crop(path: String, region: CropRegion?): String = withContext(Dispatchers.IO) {
        val image = ImageIO.read(File(path)) ?: error("Obrázek nejde dekódovat.")
        val (left, top, side) = squarePixels(region, image.width, image.height)
        val edge = minOf(side, PROFILE_PHOTO_EDGE)
        val square = BufferedImage(edge, edge, BufferedImage.TYPE_INT_RGB)
        square.createGraphics().apply {
            setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            drawImage(image, 0, 0, edge, edge, left, top, left + side, top + side, null)
            dispose()
        }
        val output = File.createTempFile("profile", ".jpg")
        writeJpeg(square, output)
        output.path
    }
}

internal fun writeJpeg(image: BufferedImage, file: File) {
    val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
    val parameters = writer.defaultWriteParam.apply {
        compressionMode = ImageWriteParam.MODE_EXPLICIT
        compressionQuality = PHOTO_JPEG_QUALITY / 100f
    }
    ImageIO.createImageOutputStream(file).use { stream ->
        writer.output = stream
        writer.write(null, IIOImage(image, null, null), parameters)
    }
    writer.dispose()
}
