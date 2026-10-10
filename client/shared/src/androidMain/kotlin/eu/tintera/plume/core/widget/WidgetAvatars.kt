package eu.tintera.plume.core.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

/**
 * Kulaté miniatury fotek chatů pro widget (`filesDir/widget/avatars/<fileId>.png`).
 *
 * **Připravují se při zápisu podkladů, ne při kreslení.** Launcher widget překresluje často
 * a dvanáct dekódování plné fotky (stovky kB) by to brzdilo; miniatura 128 px je pár kB
 * a ořez do kruhu je v ní už hotový — Glance obrázek do kruhu oříznout neumí.
 *
 * Soubor se jmenuje podle `fileId`: nová fotka chatu má nové id, takže stará miniatura se nikdy
 * nepoužije omylem. Nepoužívané se po každém zápisu smažou ([prune]).
 */
internal object WidgetAvatars {

    private fun directory(context: Context) = File(context.filesDir, "widget/avatars")

    /** Miniatura fotky [sourcePath] s id [fileId]; vytvoří ji, když ještě není. `null` = nejde. */
    suspend fun thumbnail(context: Context, fileId: Int, sourcePath: String): String? = withContext(Dispatchers.IO) {
        val target = File(directory(context), "$fileId.png")
        if (target.exists()) return@withContext target.absolutePath
        runCatching {
            val bitmap = decodeSampled(sourcePath, EDGE) ?: return@runCatching null
            val round = circle(bitmap, EDGE)
            target.parentFile?.mkdirs()
            val temp = File(target.parentFile, "$fileId.png.tmp")
            temp.outputStream().use { round.compress(Bitmap.CompressFormat.PNG, 100, it) }
            temp.renameTo(target)
            target.absolutePath
        }.getOrNull()
    }

    /** Smaže miniatury, které už žádný chat ve widgetu nepoužívá. */
    suspend fun prune(context: Context, keep: Set<String>) = withContext(Dispatchers.IO) {
        directory(context).listFiles()?.forEach { file -> if (file.absolutePath !in keep) file.delete() }
    }

    /** Dekóduje fotku zmenšenou rovnou při čtení (`inSampleSize`) — plnou velikost netřeba. */
    private fun decodeSampled(path: String, edge: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= edge) sample *= 2
        return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    /** Středový čtverec fotky v kruhu o průměru [edge]. */
    private fun circle(source: Bitmap, edge: Int): Bitmap {
        val output = Bitmap.createBitmap(edge, edge, Bitmap.Config.ARGB_8888)
        val scale = edge.toFloat() / minOf(source.width, source.height)
        val matrix = Matrix().apply {
            setScale(scale, scale)
            postTranslate((edge - source.width * scale) / 2, (edge - source.height * scale) / 2)
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            shader = BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply { setLocalMatrix(matrix) }
        }
        Canvas(output).drawCircle(edge / 2f, edge / 2f, max(edge, 1) / 2f, paint)
        return output
    }

    /** Průměr miniatury v pixelech — widget ji kreslí na 40 dp, tedy do ~120 px. */
    private const val EDGE = 128
}
