package eu.tintera.plume.core.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class AndroidStoryPhoto(private val context: Context) : StoryPhoto {

    override suspend fun render(path: String, fill: Boolean): String = withContext(Dispatchers.Default) {
        val source = BitmapFactory.decodeFile(path) ?: error("Obrázek nejde dekódovat.")
        val place = storyPhotoPlacement(source.width, source.height, fill)
        val canvasBitmap = Bitmap.createBitmap(STORY_PHOTO_WIDTH, STORY_PHOTO_HEIGHT, Bitmap.Config.ARGB_8888)
        Canvas(canvasBitmap).apply {
            drawColor(Color.BLACK)
            drawBitmap(
                source,
                null,
                RectF(place.left, place.top, place.left + place.width, place.top + place.height),
                Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG),
            )
        }
        val directory = File(context.cacheDir, "$OUTGOING_DIRECTORY/${UUID.randomUUID()}").apply { mkdirs() }
        val file = File(directory, "story.jpg")
        file.outputStream().use { canvasBitmap.compress(Bitmap.CompressFormat.JPEG, PHOTO_JPEG_QUALITY, it) }
        source.recycle()
        canvasBitmap.recycle()
        file.absolutePath
    }
}
