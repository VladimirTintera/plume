package eu.tintera.plume.core.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.roundToInt

class AndroidPhotoMarkupRenderer(private val context: Context) : PhotoMarkupRenderer {

    override suspend fun render(path: String, markup: PhotoMarkup): MarkedPhoto = withContext(Dispatchers.Default) {
        val options = BitmapFactory.Options().apply { inMutable = true }
        val bitmap = BitmapFactory.decodeFile(path, options) ?: error("Obrázek nejde dekódovat.")
        val canvas = Canvas(bitmap)
        val width = bitmap.width
        val height = bitmap.height
        markup.strokes.forEach { stroke ->
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                strokeWidth = stroke.tool.strokeWidth(width, height)
                color = stroke.color.toInt()
            }
            val points = stroke.points
            if (points.size == 2) {
                // Ťuknutí bez tahu: tečka velká jako šířka čáry, jako v náhledu.
                canvas.drawPoint(points[0] * width, points[1] * height, paint)
            } else {
                // Jedna cesta, ne úsečky po kouskách: průsvitný zvýrazňovač by se na spojích
                // překrýval a ztmavoval.
                val line = Path().apply {
                    moveTo(points[0] * width, points[1] * height)
                    for (i in 2 until points.size step 2) lineTo(points[i] * width, points[i + 1] * height)
                }
                canvas.drawPath(line, paint)
            }
        }
        // Tahy jsou v souřadnicích originálu, takže se otáčí a ořezává až nakreslená fotka.
        val turns = normalizedTurns(markup.quarterTurns)
        val rotated = if (turns == 0) bitmap else {
            Bitmap.createBitmap(bitmap, 0, 0, width, height, Matrix().apply { postRotate(90f * turns) }, true)
        }
        val result = if (markup.crop == CropBox.Full) rotated else {
            val crop = markup.crop
            val left = (crop.left * rotated.width).roundToInt().coerceIn(0, rotated.width - 1)
            val top = (crop.top * rotated.height).roundToInt().coerceIn(0, rotated.height - 1)
            val right = (crop.right * rotated.width).roundToInt().coerceIn(left + 1, rotated.width)
            val bottom = (crop.bottom * rotated.height).roundToInt().coerceIn(top + 1, rotated.height)
            Bitmap.createBitmap(rotated, left, top, right - left, bottom - top)
        }
        val directory = File(context.cacheDir, "$OUTGOING_DIRECTORY/${UUID.randomUUID()}").apply { mkdirs() }
        val file = File(directory, "photo.jpg")
        file.outputStream().use { result.compress(Bitmap.CompressFormat.JPEG, PHOTO_JPEG_QUALITY, it) }
        MarkedPhoto(file.absolutePath, result.width, result.height)
    }
}
