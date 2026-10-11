package eu.tintera.plume.core.video

import android.graphics.Bitmap
import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import co.touchlab.kermit.Logger
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext

actual val webmStickersSupported: Boolean = true

private val logger = Logger.withTag("WebmSticker")

/**
 * Kolik videosamolepek smí hrát naráz. Každá drží jeden až dva dekodéry a instancí kodeku je
 * v systému omezený počet (u hardwarových i jednotky) — mřížka sady by je vyčerpala a video
 * v konverzaci by pak nešlo pustit. Co se nevejde, čeká ve frontě na statickém náhledu a rozběhne
 * se, až některá hrající odjede z obrazovky (`Semaphore` je spravedlivý, pořadí drží).
 */
private val players = Semaphore(MAX_PLAYING)

@Composable
actual fun WebmSticker(
    path: String,
    playing: Boolean,
    contentDescription: String?,
    modifier: Modifier,
    placeholder: @Composable () -> Unit,
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val frames = remember(path) { FrameBuffer() }
    val isPlaying by rememberUpdatedState(playing)
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    // Klíčem je jen to, JESTLI plocha velikost má — ne velikost sama. Buňky mřížky se o pixel
    // přeměřují (171 ↔ 172) a každá změna by dekodér zahodila a založila znovu; snímek se pak
    // do trochu jiné plochy jen přeškáluje při kreslení.
    val measured = size.width > 0 && size.height > 0
    LaunchedEffect(path, measured) {
        if (!measured) return@LaunchedEffect
        withContext(Dispatchers.Default) {
            val video = withContext(Dispatchers.IO) { runCatching { File(path).readBytes() }.getOrNull() }
                ?.let(WebmDemuxer::parse)
                ?: return@withContext logger.w { "Videosamolepku $path se nepodařilo přečíst." }
            // Čeká ve frontě: až některá samolepka odjede z obrazovky, tahle se rozběhne. Odjede-li
            // dřív sama, z fronty ji vyřadí zrušení korutiny.
            players.acquire()
            try {
                StickerDecoder(video, fitInto(video, size)).use { decoder ->
                    // V pozadí se nedekóduje: smyčka stojí, dekodéry zůstávají připravené.
                    lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                        while (true) {
                            val started = System.nanoTime()
                            val frame = decoder.next() ?: break
                            frames.publish(frame.bitmap)
                            if (!isPlaying) snapshotFlow { isPlaying }.first { it }
                            val spent = (System.nanoTime() - started) / 1_000_000
                            delay((frame.durationMs - spent).coerceAtLeast(MIN_FRAME_MS))
                        }
                    }
                }
            } catch (cancel: CancellationException) {
                // Samolepka odjela z obrazovky nebo se změnila velikost — žádná chyba.
                throw cancel
            } catch (failure: Exception) {
                // `MediaCodec.CodecException` i stav kodeku po odpojení; samolepka zůstane stát.
                logger.w(failure) { "Videosamolepku $path se nepodařilo přehrát." }
            } finally {
                players.release()
            }
        }
    }

    Box(
        modifier
            .onSizeChanged { size = it }
            .semantics { contentDescription?.let { this.contentDescription = it } }
            .drawWithContent {
                val bitmap = frames.front
                // Čtení stavu až při kreslení: nový snímek překreslí jen tuhle plochu, nesloží znovu
                // kompozici.
                frames.tick
                if (bitmap == null) {
                    drawContent()
                    return@drawWithContent
                }
                val scale = min(this.size.width / bitmap.width, this.size.height / bitmap.height)
                val w = (bitmap.width * scale).roundToInt()
                val h = (bitmap.height * scale).roundToInt()
                drawImage(
                    image = bitmap.asImageBitmap(),
                    dstOffset = IntOffset(((this.size.width - w) / 2).roundToInt(), ((this.size.height - h) / 2).roundToInt()),
                    dstSize = IntSize(w, h),
                )
            },
    ) {
        if (!frames.ready) placeholder()
    }
}

/** Rozměr, do kterého se snímky dekódují: vepsaný do plochy, nikdy větší než video samo. */
private fun fitInto(video: WebmVideo, area: IntSize): IntSize {
    val scale = min(1f, min(area.width.toFloat() / video.width, area.height.toFloat() / video.height))
    return IntSize((video.width * scale).roundToInt().coerceAtLeast(1), (video.height * scale).roundToInt().coerceAtLeast(1))
}

/**
 * Hotové snímky pro kreslení. Tři bitmapy na střídačku: do jedné se dekóduje, druhá se kreslí
 * a třetí může ještě nahrávat `RenderThread` z minulého snímku — se dvěma by se do kreslené
 * bitmapy občas psalo a samolepka by se trhala.
 */
private class FrameBuffer {
    @Volatile
    var front: Bitmap? = null
        private set
    var tick by mutableIntStateOf(0)
        private set
    var ready by mutableStateOf(false)
        private set

    fun publish(bitmap: Bitmap) {
        front = bitmap
        tick++
        if (!ready) ready = true
    }
}

private class DecodedFrame(val bitmap: Bitmap, val durationMs: Long)

/**
 * Dekodér jedné videosamolepky: barvy jedním VP9 dekodérem, průhlednost (je-li) druhým — viz
 * [WebmVideo]. Snímky dává ve smyčce; po posledním začne znovu od prvního (klíčového).
 */
private class StickerDecoder(private val video: WebmVideo, private val target: IntSize) : AutoCloseable {
    private val color = Vp9Stream(video) { it.data }

    // Průhlednost jen když ji má KAŽDÝ snímek — jinak by se proudy rozjely.
    private val alpha = if (video.hasAlpha && video.frames.all { it.alpha != null }) Vp9Stream(video) { it.alpha!! } else null

    private val pixels = IntArray(target.width * target.height)
    private val bitmaps = Array(BUFFERS) {
        Bitmap.createBitmap(target.width, target.height, Bitmap.Config.ARGB_8888).apply { setHasAlpha(true) }
    }
    private var written = 0
    private val converter = YuvConverter(video.colour)

    fun next(): DecodedFrame? {
        val colorImage = color.next() ?: return null
        try {
            val alphaImage = alpha?.next()
            try {
                converter.convert(colorImage.image, alphaImage?.image, target.width, target.height, pixels)
            } finally {
                alphaImage?.close()
            }
        } finally {
            colorImage.close()
        }
        val bitmap = bitmaps[written++ % BUFFERS]
        bitmap.setPixels(pixels, 0, target.width, 0, 0, target.width, target.height)
        return DecodedFrame(bitmap, color.lastDurationMs)
    }

    override fun close() {
        color.close()
        alpha?.close()
    }
}

/** Výstup dekodéru, který se musí vrátit — obrázek patří kodeku, dokud se buffer neuvolní. */
private class OutputImage(val image: Image, private val codec: MediaCodec, private val index: Int) : AutoCloseable {
    override fun close() {
        image.close()
        codec.releaseOutputBuffer(index, false)
    }
}

/**
 * Jeden VP9 (nebo VP8) proud nad `MediaCodec` v synchronním režimu. [payload] vybírá, co se
 * dekóduje — data snímku, nebo jeho průhlednost. Po konci proudu (EOS) se kodek vyprázdní
 * (`flush`) a jede se znovu od prvního snímku.
 */
private class Vp9Stream(private val video: WebmVideo, private val payload: (WebmFrame) -> ByteArray) : AutoCloseable {
    private val codec: MediaCodec = createDecoder(video).apply {
        val format = MediaFormat.createVideoFormat(mime(video), video.width, video.height)
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
        configure(format, null, null, 0)
        start()
    }
    private val info = MediaCodec.BufferInfo()
    private var input = 0
    private var endQueued = false

    /** Délka posledního vydaného snímku — rozdíl časů snímků, u posledního délka ze stopy. */
    var lastDurationMs = video.frameDurationMs.coerceAtLeast(MIN_FRAME_MS)
        private set

    fun next(): OutputImage? {
        repeat(MAX_ATTEMPTS) {
            feed()
            val index = codec.dequeueOutputBuffer(info, DEQUEUE_TIMEOUT_US)
            if (index < 0) return@repeat
            val end = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
            val image = if (info.size > 0) codec.getOutputImage(index) else null
            if (image == null) {
                codec.releaseOutputBuffer(index, false)
                if (end) restart()
                return@repeat
            }
            lastDurationMs = durationOf(info.presentationTimeUs)
            // Poslední snímek může přijít spolu s koncem proudu. Smyčka se otočí až při dalším
            // volání — `flush` by zneplatnil obrázek, který se teprve bude převádět.
            pendingRestart = end
            return OutputImage(image, codec, index)
        }
        return null
    }

    private var pendingRestart = false

    private fun feed() {
        if (pendingRestart) {
            pendingRestart = false
            restart()
        }
        if (endQueued) return
        val index = codec.dequeueInputBuffer(0)
        if (index < 0) return
        if (input >= video.frames.size) {
            codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            endQueued = true
            return
        }
        val frame = video.frames[input++]
        val data = payload(frame)
        val buffer: ByteBuffer = codec.getInputBuffer(index) ?: return
        buffer.clear()
        buffer.put(data)
        codec.queueInputBuffer(index, 0, data.size, frame.timeMs * 1000, 0)
    }

    private fun restart() {
        codec.flush()
        input = 0
        endQueued = false
    }

    private fun durationOf(presentationUs: Long): Long {
        val ms = presentationUs / 1000
        val at = video.frames.indexOfFirst { it.timeMs == ms }
        val following = video.frames.getOrNull(at + 1)?.timeMs
        val duration = if (at >= 0 && following != null) following - ms else video.frameDurationMs
        return duration.coerceAtLeast(MIN_FRAME_MS)
    }

    override fun close() {
        runCatching { codec.stop() }
        codec.release()
    }
}

private fun mime(video: WebmVideo): String = when (video.codec) {
    WebmCodec.Vp9 -> MediaFormat.MIMETYPE_VIDEO_VP9
    WebmCodec.Vp8 -> MediaFormat.MIMETYPE_VIDEO_VP8
}

/**
 * Softwarový dekodér, když je. Hardwarových instancí bývá v systému pár a patří přehrávání videa;
 * samolepka má 512 × 512 a softwarový VP9 ji dekóduje bez námahy.
 */
private fun createDecoder(video: WebmVideo): MediaCodec {
    val mime = mime(video)
    val name = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
        .filter { !it.isEncoder && it.supportedTypes.any { type -> type.equals(mime, ignoreCase = true) } }
        .sortedBy { if (it.isSoftware()) 0 else 1 }
        .firstOrNull()?.name
    return name?.let(MediaCodec::createByCodecName) ?: MediaCodec.createDecoderByType(mime)
}

private fun MediaCodecInfo.isSoftware(): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        isSoftwareOnly
    } else {
        name.startsWith("OMX.google.") || name.startsWith("c2.android.")
    }

/**
 * Převod YUV 4:2:0 (výstup dekodéru) na ARGB, rovnou zmenšený na cílovou velikost — každý cílový
 * bod si vezme nejbližší zdrojový. Průhlednost je jas druhého proudu, bez převodu rozsahu (tak ji
 * kreslí i prohlížeče).
 *
 * Koeficienty podle [WebmColour] v pevné řádové čárce (× 1024); plány se kopírují do polí, ať se
 * v cyklu nečte po bajtu z `ByteBuffer`.
 */
private class YuvConverter(colour: WebmColour) {
    private val yScale: Int
    private val yOffset: Int
    private val rv: Int
    private val gu: Int
    private val gv: Int
    private val bu: Int

    init {
        if (colour.fullRange) {
            yScale = 1024
            yOffset = 0
        } else {
            yScale = 1192 // 1,164 × 1024
            yOffset = 16
        }
        val limited = !colour.fullRange
        if (colour.bt709) {
            rv = if (limited) 1836 else 1613
            gu = if (limited) 218 else 192
            gv = if (limited) 546 else 479
            bu = if (limited) 2163 else 1900
        } else {
            rv = if (limited) 1634 else 1436
            gu = if (limited) 401 else 352
            gv = if (limited) 833 else 731
            bu = if (limited) 2066 else 1815
        }
    }

    private var yPlane = ByteArray(0)
    private var uPlane = ByteArray(0)
    private var vPlane = ByteArray(0)
    private var aPlane = ByteArray(0)
    private var columnsY = IntArray(0)
    private var columnsU = IntArray(0)
    private var columnsV = IntArray(0)
    private var columnsA = IntArray(0)

    fun convert(color: Image, alpha: Image?, width: Int, height: Int, out: IntArray) {
        val crop = color.cropRect
        val srcW = crop.width()
        val srcH = crop.height()
        val planes = color.planes
        yPlane = copy(planes[0].buffer, yPlane)
        uPlane = copy(planes[1].buffer, uPlane)
        vPlane = copy(planes[2].buffer, vPlane)
        val yRow = planes[0].rowStride
        val yPix = planes[0].pixelStride
        val uvRow = planes[1].rowStride
        val uvPix = planes[1].pixelStride
        val vRow = planes[2].rowStride
        val vPix = planes[2].pixelStride

        val alphaPlane = alpha?.planes?.get(0)
        if (alphaPlane != null) aPlane = copy(alphaPlane.buffer, aPlane)
        val aRow = alphaPlane?.rowStride ?: 0
        val aPix = alphaPlane?.pixelStride ?: 0
        val aCrop = alpha?.cropRect

        // Posuny sloupců se počítají jednou za snímek, ne v nejvnitřnější smyčce: dělení pro
        // každý bod bralo u samolepky přes celou šířku znatelnou část procesoru.
        if (columnsY.size != width) {
            columnsY = IntArray(width)
            columnsU = IntArray(width)
            columnsV = IntArray(width)
            columnsA = IntArray(width)
        }
        for (dx in 0 until width) {
            val sx = crop.left + dx * srcW / width
            columnsY[dx] = sx * yPix
            columnsU[dx] = (sx / 2) * uvPix
            columnsV[dx] = (sx / 2) * vPix
            columnsA[dx] = if (aCrop != null) (aCrop.left + dx * aCrop.width() / width) * aPix else 0
        }

        var o = 0
        for (dy in 0 until height) {
            val sy = crop.top + dy * srcH / height
            val yLine = sy * yRow
            val uLine = (sy / 2) * uvRow
            val vLine = (sy / 2) * vRow
            val aLine = if (aCrop != null) (aCrop.top + dy * aCrop.height() / height) * aRow else -1
            for (dx in 0 until width) {
                val y = ((yPlane[yLine + columnsY[dx]].toInt() and 0xFF) - yOffset) * yScale
                val u = (uPlane[uLine + columnsU[dx]].toInt() and 0xFF) - 128
                val v = (vPlane[vLine + columnsV[dx]].toInt() and 0xFF) - 128
                val r = clamp((y + rv * v) shr 10)
                val g = clamp((y - gu * u - gv * v) shr 10)
                val b = clamp((y + bu * u) shr 10)
                val a = if (aLine >= 0) aPlane[aLine + columnsA[dx]].toInt() and 0xFF else 0xFF
                out[o++] = (a shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
    }

    private fun copy(buffer: ByteBuffer, into: ByteArray): ByteArray {
        val source = buffer.duplicate().apply { rewind() }
        val target = if (into.size >= source.remaining()) into else ByteArray(source.remaining())
        source.get(target, 0, source.remaining())
        return target
    }

    private fun clamp(value: Int): Int = if (value < 0) 0 else if (value > 255) 255 else value
}

private const val MAX_PLAYING = 10
private const val BUFFERS = 3
private const val MAX_ATTEMPTS = 50
private const val DEQUEUE_TIMEOUT_US = 10_000L
