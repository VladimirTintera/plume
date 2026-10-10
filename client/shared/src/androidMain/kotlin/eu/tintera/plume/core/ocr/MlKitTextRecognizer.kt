package eu.tintera.plume.core.ocr

import android.graphics.BitmapFactory
import co.touchlab.kermit.Logger
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * ML Kit z Play služeb, model latinky (pokrývá češtinu i angličtinu).
 *
 * - **Obrázek se dekóduje sám, zmenšený** (nejdelší strana do [MAX_SIDE]), ne `fromFilePath`.
 *   Originál fotky z Telegramu má až 2560 bodů a víc rozpoznání nepomůže, jen ho zpomalí; a souřadnice
 *   řádků jsou pak v rozměrech téže bitmapy, kterými se dělí na podíly.
 * - **Model si stahují Play služby** (meta-data `ocr` v manifestu aplikace, hned po instalaci).
 *   Než doběhne, rozpoznání selže — to je `null` a hláška „zkus to za chvíli", ne „žádný text".
 */
class MlKitTextRecognizer : TextRecognizer {
    private val logger = Logger.withTag("TextRecognizer")
    private val client by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    override suspend fun recognize(path: String): RecognizedText? {
        val bitmap = withContext(Dispatchers.IO) { decode(path) } ?: run {
            logger.w { "Obrázek $path nejde přečíst." }
            return null
        }
        val width = bitmap.width.toFloat()
        val height = bitmap.height.toFloat()
        val result = runCatching { client.process(InputImage.fromBitmap(bitmap, 0)).await() }
            .onFailure { logger.w(it) { "Rozpoznání textu selhalo." } }
            .getOrNull()
        bitmap.recycle()
        result ?: return null
        // Řádky, ne bloky: vybírá se po řádcích (viz [RecognizedLine]).
        val lines = result.textBlocks.flatMap { it.lines }.mapNotNull { line ->
            val box = line.boundingBox ?: return@mapNotNull null
            RecognizedLine(
                text = line.text,
                left = box.left / width,
                top = box.top / height,
                right = box.right / width,
                bottom = box.bottom / height,
            )
        }
        return RecognizedText(inReadingOrder(lines))
    }

    private fun decode(path: String): android.graphics.Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private companion object {
        const val MAX_SIDE = 2048
    }
}

/** Task API bez `coroutines-play-services` — kvůli jednomu volání (viz `playAppUpdate`). */
private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { continuation.resume(it) }
    addOnFailureListener { continuation.resumeWith(Result.failure(it)) }
    addOnCanceledListener { continuation.cancel() }
}
