package eu.tintera.plume.core.stickers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import co.touchlab.kermit.Logger
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.coroutines.resume
import kotlin.math.roundToInt

/**
 * [StickerMaker] na Androidu: dekódování zmenšené fotky, odstranění pozadí přes ML Kit Subject
 * Segmentation z Play služeb (popředí jako bitmapa s průhledností), ořez na neprůhlednou část,
 * případně bílý obrys, zmenšení na 512 px a WebP.
 *
 * - **Model si stahují Play služby** (meta-data `subject_segment` v manifestu aplikace, jako
 *   u čtení textu). Než doběhne, segmentace selže s `UNAVAILABLE` — to je
 *   [StickerModelNotReadyException] a hláška „zkus to za chvíli", ne chyba.
 * - **WebP, ne PNG**: PNG fotky v 512 px snadno přeleze 512 kB, které Telegram u statické
 *   samolepky snese; ztrátové WebP s průhledností je zlomek a server ho bere rovnou.
 */
class AndroidStickerMaker(context: Context) : StickerMaker {
    private val logger = Logger.withTag("StickerMaker")
    private val appContext = context.applicationContext

    override val canRemoveBackground: Boolean by lazy {
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(appContext) == ConnectionResult.SUCCESS
    }

    private val segmenter by lazy {
        SubjectSegmentation.getClient(SubjectSegmenterOptions.Builder().enableForegroundBitmap().build())
    }

    override suspend fun make(path: String, removeBackground: Boolean, outline: Boolean): Result<MadeSticker> = runCatching {
        val photo = withContext(Dispatchers.IO) { decode(path) } ?: error("Obrázek $path nejde přečíst.")
        val content = if (removeBackground) cutOut(photo) else photo
        withContext(Dispatchers.Default) {
            val scaled = if (removeBackground && outline) withOutline(content) else scaleToStickerSize(content)
            val file = File(appContext.cacheDir, "stickers").apply { mkdirs() }
                .resolve("sticker-${System.currentTimeMillis()}.webp")
            file.outputStream().use { out -> scaled.compress(webp(), WEBP_QUALITY, out) }
            MadeSticker(file.absolutePath, scaled.width, scaled.height)
        }
    }.onFailure { logger.w(it) { "Samolepku z $path nejde vyrobit." } }

    override suspend fun makeCover(path: String): Result<MadeSticker> = runCatching {
        withContext(Dispatchers.Default) {
            val sticker = BitmapFactory.decodeFile(path) ?: error("Samolepku $path nejde přečíst.")
            val cover = Bitmap.createBitmap(COVER_SIDE, COVER_SIDE, Bitmap.Config.ARGB_8888)
            val (width, height) = fitSize(sticker.width, sticker.height, COVER_SIDE)
            val left = (COVER_SIDE - width) / 2f
            val top = (COVER_SIDE - height) / 2f
            Canvas(cover).drawBitmap(
                sticker,
                null,
                RectF(left, top, left + width, top + height),
                Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG),
            )
            val file = File(appContext.cacheDir, "stickers").apply { mkdirs() }
                .resolve("cover-${System.currentTimeMillis()}.webp")
            // Strop 32 kB: v 100 px ho WebP s kvalitou 90 přeleze jen výjimečně, pak se ubírá.
            var quality = WEBP_QUALITY
            do {
                file.outputStream().use { out -> cover.compress(webp(), quality, out) }
                quality -= COVER_QUALITY_STEP
            } while (file.length() > COVER_MAX_BYTES && quality > 0)
            MadeSticker(file.absolutePath, COVER_SIDE, COVER_SIDE)
        }
    }.onFailure { logger.w(it) { "Obálku sady z $path nejde vyrobit." } }

    /** Jen hlavní objekt, oříznutý těsně na neprůhlednou část. */
    private suspend fun cutOut(photo: Bitmap): Bitmap {
        val result = runCatching { segmenter.process(InputImage.fromBitmap(photo, 0)).await() }
            .getOrElse { failure ->
                if (failure is MlKitException && failure.errorCode == MlKitException.UNAVAILABLE) {
                    requestModel()
                    throw StickerModelNotReadyException(failure)
                }
                throw failure
            }
        val foreground = result.foregroundBitmap ?: throw NoStickerSubjectException()
        return withContext(Dispatchers.Default) {
            val bounds = opaqueBounds(foreground) ?: throw NoStickerSubjectException()
            Bitmap.createBitmap(foreground, bounds.left, bounds.top, bounds.width(), bounds.height())
        }
    }

    /**
     * Požádá Play služby o model hned. Meta-data v manifestu stahování spustí po instalaci,
     * ale kdo samolepku zkusí dřív, než doběhne, by jinak čekal naslepo.
     */
    private fun requestModel() {
        runCatching {
            ModuleInstall.getClient(appContext)
                .installModules(ModuleInstallRequest.newBuilder().addApi(segmenter).build())
                .addOnFailureListener { logger.w(it) { "Model odstranění pozadí nejde vyžádat." } }
        }
    }

    private fun decode(path: String): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= DECODE_SIDE) sample *= 2
        return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private fun webp(): Bitmap.CompressFormat =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY else @Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP

    private companion object {
        /** Dekóduje se zhruba na dvojnásobek samolepky: segmentaci víc nepomůže, jen ji zpomalí. */
        const val DECODE_SIDE = 1024
        const val WEBP_QUALITY = 90
        const val COVER_SIDE = 100
        const val COVER_MAX_BYTES = 32 * 1024
        const val COVER_QUALITY_STEP = 15
    }
}

/** Delší strana přesně [STICKER_SIDE] (Telegram chce jednu stranu 512 a druhou nejvýš 512). */
internal fun stickerSize(width: Int, height: Int): Pair<Int, Int> = fitSize(width, height, STICKER_SIDE)

/** Rozměry vepsané do čtverce [side]: delší strana přesně [side], poměr stran zůstává. */
internal fun fitSize(width: Int, height: Int, side: Int): Pair<Int, Int> {
    val scale = side.toFloat() / maxOf(width, height)
    return (width * scale).roundToInt().coerceIn(1, side) to (height * scale).roundToInt().coerceIn(1, side)
}

/**
 * Vyříznutý objekt s bílým obrysem po svém tvaru, jako u samolepek z oficiálního klienta.
 * Objekt se zmenší tak, aby se i s obrysem vešel do [STICKER_SIDE]: obrys má tak v hotové
 * samolepce vždycky stejnou tloušťku ([OUTLINE_WIDTH]), ať byla fotka jakkoli velká.
 *
 * Obrys je rozšířená maska objektu: neprůhledná část se nakreslí bíle mnohokrát posunutá po
 * soustředných kružnicích (tři poloměry, jinak by kolem tenkých částí zůstaly mezery) a objekt
 * přijde navrch. Do masky jdou jen pixely nad [ALPHA_THRESHOLD] — téměř průhledné zbytky po
 * segmentaci by jinak dostaly vlastní bílé skvrny.
 */
private fun withOutline(content: Bitmap): Bitmap {
    val (width, height) = fitSize(content.width, content.height, STICKER_SIDE - 2 * OUTLINE_WIDTH)
    val scaled = Bitmap.createScaledBitmap(content, width, height, true)
    val mask = solidMask(scaled)
    val result = Bitmap.createBitmap(width + 2 * OUTLINE_WIDTH, height + 2 * OUTLINE_WIDTH, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(result)
    val white = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { color = android.graphics.Color.WHITE }
    for ((radius, steps) in OUTLINE_RINGS) {
        for (step in 0 until steps) {
            val angle = 2 * PI * step / steps
            val dx = OUTLINE_WIDTH + radius * OUTLINE_WIDTH * cos(angle).toFloat()
            val dy = OUTLINE_WIDTH + radius * OUTLINE_WIDTH * sin(angle).toFloat()
            canvas.drawBitmap(mask, dx, dy, white)
        }
    }
    canvas.drawBitmap(scaled, OUTLINE_WIDTH.toFloat(), OUTLINE_WIDTH.toFloat(), null)
    return result
}

/** Maska (`ALPHA_8`) jen z pixelů, které jsou opravdu vidět. */
private fun solidMask(bitmap: Bitmap): Bitmap {
    val width = bitmap.width
    val height = bitmap.height
    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
    for (index in pixels.indices) {
        val a = pixels[index] ushr 24
        pixels[index] = if (a > ALPHA_THRESHOLD) a shl 24 else 0
    }
    // `extractAlpha`, ne ALPHA_8 z bufferu: řádek ALPHA_8 bitmapy může být zarovnaný na víc bajtů.
    return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888).extractAlpha()
}

private fun scaleToStickerSize(bitmap: Bitmap): Bitmap {
    val (width, height) = stickerSize(bitmap.width, bitmap.height)
    return Bitmap.createScaledBitmap(bitmap, width, height, true)
}

/** Obdélník, kde je obrázek aspoň trochu neprůhledný; `null` = celý průhledný. */
private fun opaqueBounds(bitmap: Bitmap): Rect? {
    val width = bitmap.width
    val height = bitmap.height
    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
    var left = width
    var top = height
    var right = -1
    var bottom = -1
    for (y in 0 until height) {
        val row = y * width
        for (x in 0 until width) {
            if ((pixels[row + x] ushr 24) > ALPHA_THRESHOLD) {
                if (x < left) left = x
                if (x > right) right = x
                if (y < top) top = y
                if (y > bottom) bottom = y
            }
        }
    }
    return if (right < 0) null else Rect(left, top, right + 1, bottom + 1)
}

private const val STICKER_SIDE = 512

/** Tloušťka bílého obrysu v hotové samolepce (px z 512). */
private const val OUTLINE_WIDTH = 16

/** Kružnice, po kterých se maska posouvá: poměr k [OUTLINE_WIDTH] a počet kroků. */
private val OUTLINE_RINGS = listOf(1f to 48, 0.66f to 32, 0.33f to 16)

/** Téměř průhledné okraje masky (vyhlazení) se do ořezu nepočítají. */
private const val ALPHA_THRESHOLD = 16

/** Task API bez `coroutines-play-services`, jako u čtení textu. */
private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { continuation.resume(it) }
    addOnFailureListener { continuation.resumeWith(Result.failure(it)) }
    addOnCanceledListener { continuation.cancel() }
}
