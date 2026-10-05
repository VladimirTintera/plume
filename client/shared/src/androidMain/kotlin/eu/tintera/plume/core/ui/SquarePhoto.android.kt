package eu.tintera.plume.core.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class AndroidSquarePhoto(private val context: Context) : SquarePhoto {

    override suspend fun crop(path: String, region: CropRegion?): String = withContext(Dispatchers.Default) {
        val source = BitmapFactory.decodeFile(path) ?: error("Obrázek nejde dekódovat.")
        val (left, top, side) = squarePixels(region, source.width, source.height)
        val square = Bitmap.createBitmap(source, left, top, side, side)
        val edge = minOf(side, PROFILE_PHOTO_EDGE)
        val scaled = if (edge == side) square else Bitmap.createScaledBitmap(square, edge, edge, true)
        val directory = File(context.cacheDir, "$OUTGOING_DIRECTORY/${UUID.randomUUID()}").apply { mkdirs() }
        val file = File(directory, "profile.jpg")
        file.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, PHOTO_JPEG_QUALITY, it) }
        file.absolutePath
    }
}
