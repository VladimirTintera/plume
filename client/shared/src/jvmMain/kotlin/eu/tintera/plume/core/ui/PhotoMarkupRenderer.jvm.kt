package eu.tintera.plume.core.ui

import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Paint
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.PaintStrokeCap
import org.jetbrains.skia.PaintStrokeJoin
import org.jetbrains.skia.PathBuilder
import org.jetbrains.skia.Surface

/**
 * [PhotoMarkupRenderer] na desktopu ve Skie — totéž pořadí jako iOS a Android: tahy
 * v souřadnicích originálu, pak otočení a ořez jednou transformací plátna.
 *
 * - **Originál se čte přes `Image.makeFromEncoded`, které otočí fotku podle EXIF.** Tak ji
 *   ukazuje i editor (Coil na desktopu dekóduje týmž voláním) a tahy jsou podíly TÉHLE podoby.
 *   Výběr příloh na desktopu fotku nepřevádí, takže snímek z fotoaparátu má orientaci jen v EXIF.
 * - **Výstup je JPEG**, jako na mobilech; průhledná místa PNG zčernají stejně jako na Androidu.
 */
class DesktopPhotoMarkupRenderer(
    private val directory: File = File(System.getProperty("java.io.tmpdir"), "plume-$OUTGOING_DIRECTORY"),
) : PhotoMarkupRenderer {

    override suspend fun render(path: String, markup: PhotoMarkup): MarkedPhoto = withContext(Dispatchers.Default) {
        val photo = Image.makeFromEncoded(File(path).readBytes())
        val width = photo.width
        val height = photo.height
        val turns = normalizedTurns(markup.quarterTurns)
        val (rotatedWidth, rotatedHeight) = rotatedSize(width, height, turns)
        val (resultWidth, resultHeight) = resultSize(width, height, markup)
        val surface = Surface.makeRaster(ImageInfo.makeN32Premul(resultWidth, resultHeight))
        val canvas = surface.canvas
        // Kreslí se v souřadnicích ORIGINÁLU, otočení a ořez nese transformace plátna: posun
        // o výřez, pak otočení (osa y dolů, kladný úhel po směru hodin) s posunem, který
        // otočenou fotku vrátí do kladných souřadnic.
        canvas.translate(-markup.crop.left * rotatedWidth, -markup.crop.top * rotatedHeight)
        when (turns) {
            1 -> canvas.translate(rotatedWidth.toFloat(), 0f)
            2 -> canvas.translate(rotatedWidth.toFloat(), rotatedHeight.toFloat())
            3 -> canvas.translate(0f, rotatedHeight.toFloat())
        }
        canvas.rotate(90f * turns)
        canvas.drawImage(photo, 0f, 0f)
        markup.strokes.forEach { stroke ->
            val paint = Paint().apply {
                isAntiAlias = true
                mode = PaintMode.STROKE
                strokeCap = PaintStrokeCap.ROUND
                strokeJoin = PaintStrokeJoin.ROUND
                strokeWidth = stroke.tool.strokeWidth(width, height)
                color = stroke.color.toInt()
            }
            val points = stroke.points
            // Jedna cesta na tah, jako na mobilech: průsvitný zvýrazňovač se na spojích nesmí
            // překrývat. Ťuknutí je úsečka nulové délky — s kulatým koncem z ní je tečka.
            val line = PathBuilder().apply {
                moveTo(points[0] * width, points[1] * height)
                if (points.size == 2) lineTo(points[0] * width, points[1] * height)
                for (i in 2 until points.size step 2) lineTo(points[i] * width, points[i + 1] * height)
            }
            canvas.drawPath(line.detach(), paint)
        }
        val jpeg = surface.makeImageSnapshot().encodeToData(EncodedImageFormat.JPEG, PHOTO_JPEG_QUALITY)?.bytes
            ?: error("JPEG nejde vytvořit.")
        val output = File(directory, UUID.randomUUID().toString()).apply { mkdirs() }.resolve("photo.jpg")
        output.writeBytes(jpeg)
        MarkedPhoto(output.absolutePath, resultWidth, resultHeight)
    }
}
