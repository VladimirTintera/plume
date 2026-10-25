package eu.tintera.plume.core.video

import androidx.compose.ui.unit.IntSize
import kotlin.math.min
import kotlin.math.roundToInt
import org.bytedeco.ffmpeg.avcodec.AVCodec
import org.bytedeco.ffmpeg.avcodec.AVCodecContext
import org.bytedeco.ffmpeg.avcodec.AVPacket
import org.bytedeco.ffmpeg.avformat.AVFormatContext
import org.bytedeco.ffmpeg.avformat.AVInputFormat
import org.bytedeco.ffmpeg.avutil.AVDictionary
import org.bytedeco.ffmpeg.avutil.AVFrame
import org.bytedeco.ffmpeg.global.avcodec.av_packet_alloc
import org.bytedeco.ffmpeg.global.avcodec.av_packet_free
import org.bytedeco.ffmpeg.global.avcodec.av_packet_unref
import org.bytedeco.ffmpeg.global.avcodec.avcodec_alloc_context3
import org.bytedeco.ffmpeg.global.avcodec.avcodec_find_decoder
import org.bytedeco.ffmpeg.global.avcodec.avcodec_flush_buffers
import org.bytedeco.ffmpeg.global.avcodec.avcodec_free_context
import org.bytedeco.ffmpeg.global.avcodec.avcodec_open2
import org.bytedeco.ffmpeg.global.avcodec.avcodec_parameters_to_context
import org.bytedeco.ffmpeg.global.avcodec.avcodec_receive_frame
import org.bytedeco.ffmpeg.global.avcodec.avcodec_send_packet
import org.bytedeco.ffmpeg.global.avformat.AVSEEK_FLAG_BACKWARD
import org.bytedeco.ffmpeg.global.avformat.av_find_best_stream
import org.bytedeco.ffmpeg.global.avformat.av_read_frame
import org.bytedeco.ffmpeg.global.avformat.av_seek_frame
import org.bytedeco.ffmpeg.global.avformat.avformat_find_stream_info
import org.bytedeco.ffmpeg.global.avutil.AVCOL_RANGE_JPEG
import org.bytedeco.ffmpeg.global.avutil.AVCOL_SPC_BT709
import org.bytedeco.ffmpeg.global.avutil.AVERROR_EOF
import org.bytedeco.ffmpeg.global.avutil.AVMEDIA_TYPE_VIDEO
import org.bytedeco.ffmpeg.global.avutil.AV_LOG_ERROR
import org.bytedeco.ffmpeg.global.avutil.AV_NOPTS_VALUE
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_BGRA
import org.bytedeco.ffmpeg.global.avutil.av_frame_alloc
import org.bytedeco.ffmpeg.global.avutil.av_frame_free
import org.bytedeco.ffmpeg.global.avutil.av_log_set_level
import org.bytedeco.ffmpeg.global.avutil.av_strerror
import org.bytedeco.ffmpeg.global.swscale.SWS_BILINEAR
import org.bytedeco.ffmpeg.global.swscale.SWS_CS_DEFAULT
import org.bytedeco.ffmpeg.global.swscale.SWS_CS_ITU709
import org.bytedeco.ffmpeg.global.swscale.sws_freeContext
import org.bytedeco.ffmpeg.global.swscale.sws_getCachedContext
import org.bytedeco.ffmpeg.global.swscale.sws_getCoefficients
import org.bytedeco.ffmpeg.global.swscale.sws_scale
import org.bytedeco.ffmpeg.global.swscale.sws_setColorspaceDetails
import org.bytedeco.ffmpeg.presets.avutil.AVERROR_EAGAIN
import org.bytedeco.ffmpeg.swscale.SwsContext
import org.bytedeco.ffmpeg.swscale.SwsFilter
import org.bytedeco.javacpp.BytePointer
import org.bytedeco.javacpp.DoublePointer
import org.bytedeco.javacpp.IntPointer
import org.bytedeco.javacpp.PointerPointer
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo

/**
 * Obraz video souboru snímek po snímku, přes FFmpeg (JavaCPP). Zvuk nečte.
 *
 * Není vláknově bezpečný: všechno volá jedna korutina přehrávače za sebou ([VideoPlayer]).
 * Nativní knihovny se nahrají při prvním [open] (JavaCPP je napoprvé vybalí do `~/.javacpp`,
 * desítky MB) — proto se smí volat jen mimo hlavní vlákno.
 */
internal class FfmpegVideoDecoder private constructor(
    private val input: OpenedInput,
    private val codec: AVCodecContext,
    private val streamIndex: Int,
    private val timeBaseNum: Long,
    private val timeBaseDen: Long,
    private val startTs: Long,
    /** Délka v ms; `0`, když ji soubor neuvádí. */
    val durationMs: Long,
) : AutoCloseable {

    private val format: AVFormatContext = input.format

    private val packet: AVPacket = av_packet_alloc()
    private val frame: AVFrame = av_frame_alloc()

    /** Dekodér dostal konec vstupu a vydává jen to, co v něm zbylo. */
    private var inputEnded = false
    private var lastPtsMs = 0L

    private var sws: SwsContext? = null
    private var swsKey: List<Int> = emptyList()
    private var output = BytePointer()
    private var outputBytes = ByteArray(0)
    private var outputData = PointerPointer<BytePointer>(1L)
    private val outputStride = IntPointer(1L)

    /**
     * Dekóduje další snímek; vrací jeho čas v ms od začátku videa, `null` na konci souboru.
     * Snímek drží dekodér, dokud se nezavolá znovu — [toImage] převádí ten poslední.
     */
    fun decodeNext(): Long? {
        while (true) {
            val received = avcodec_receive_frame(codec, frame)
            if (received >= 0) {
                val ts = frame.best_effort_timestamp()
                lastPtsMs = if (ts == AV_NOPTS_VALUE) lastPtsMs + FALLBACK_FRAME_MS else toMs(ts - startTs)
                return lastPtsMs
            }
            if (received == AVERROR_EOF) return null
            if (received != AVERROR_EAGAIN()) throw IllegalStateException("avcodec_receive_frame: ${errorText(received)}")
            if (inputEnded) return null

            // Konec souboru i chyba čtení: dekodér se vyprázdní a vydá snímky, které drží.
            if (av_read_frame(format, packet) < 0) {
                avcodec_send_packet(codec, null as AVPacket?)
                inputEnded = true
                continue
            }
            // Vadný paket dekodér odmítne a pokračuje se dalším — jeden poškozený snímek
            // nemá zastavit celé video.
            if (packet.stream_index() == streamIndex) avcodec_send_packet(codec, packet)
            av_packet_unref(packet)
        }
    }

    /**
     * Skočí na klíčový snímek nejblíž před [positionMs]; další [decodeNext] vydá snímky od něj.
     * Přesnou pozici dohledá volající zahozením snímků před ní.
     */
    fun seek(positionMs: Long) {
        val ts = startTs + positionMs * timeBaseDen / (timeBaseNum * 1000)
        av_seek_frame(format, streamIndex, ts, AVSEEK_FLAG_BACKWARD)
        avcodec_flush_buffers(codec)
        inputEnded = false
    }

    /**
     * Poslední dekódovaný snímek jako obrázek pro Skiu, zmenšený tak, aby se vešel do
     * [target] (zvětšuje se až při kreslení). GIF 1280 × 720 v bublině o třetinové šířce by
     * jinak převáděl a nahrával do GPU devětkrát víc pixelů, než je vidět.
     */
    fun toImage(target: IntSize): Image {
        val sourceWidth = frame.width()
        val sourceHeight = frame.height()
        val (width, height) = fitSize(sourceWidth, sourceHeight, target)
        val colorspace = if (frame.colorspace() == AVCOL_SPC_BT709) SWS_CS_ITU709 else SWS_CS_DEFAULT
        val fullRange = if (frame.color_range() == AVCOL_RANGE_JPEG) 1 else 0
        val key = listOf(sourceWidth, sourceHeight, frame.format(), width, height, colorspace, fullRange)
        if (key != swsKey) {
            sws = sws_getCachedContext(
                sws, sourceWidth, sourceHeight, frame.format(), width, height, AV_PIX_FMT_BGRA,
                SWS_BILINEAR, null as SwsFilter?, null as SwsFilter?, null as DoublePointer?,
            ) ?: throw IllegalStateException("sws_getCachedContext ${frame.format()} $sourceWidth×$sourceHeight")
            // Bez toho swscale počítá barvy vždy podle BT.601 v omezeném rozsahu; HD video
            // (BT.709) by bylo do zelena.
            sws_setColorspaceDetails(
                sws, sws_getCoefficients(colorspace), fullRange,
                sws_getCoefficients(SWS_CS_DEFAULT), 1, 0, 1 shl 16, 1 shl 16,
            )
            val size = width * height * 4
            if (outputBytes.size != size) {
                output.close()
                outputData.close()
                output = BytePointer(size.toLong())
                outputData = PointerPointer<BytePointer>(1L).put(output)
                outputBytes = ByteArray(size)
            }
            outputStride.put(0L, width * 4)
            swsKey = key
        }
        // AVFrame začíná polem `data`, takže ukazatel na snímek je zároveň `uint8_t**` rovin.
        sws_scale(sws, PointerPointer<BytePointer>(frame), frame.linesize(), 0, sourceHeight, outputData, outputStride)
        output.get(outputBytes)
        // `makeRaster` pixely kopíruje, buffer se smí přepsat dalším snímkem.
        return Image.makeRaster(ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.OPAQUE), outputBytes, width * 4)
    }

    override fun close() {
        sws?.let(::sws_freeContext)
        output.close()
        outputData.close()
        outputStride.close()
        av_frame_free(frame)
        av_packet_free(packet)
        avcodec_free_context(codec)
        input.close()
    }

    private fun toMs(ts: Long): Long = ts * timeBaseNum * 1000 / timeBaseDen

    companion object {

        /** Hlášky FFmpegu na stderr jen u chyb; varování o každém snímku nikoho nezajímají. */
        private val logLevel by lazy { av_log_set_level(AV_LOG_ERROR) }

        /** Otevře [path]; vyhodí výjimku, když soubor nejde přečíst nebo v něm není video. */
        fun open(path: String): FfmpegVideoDecoder = open(FfmpegInput.File(path))

        /** Otevře [source]; vyhodí výjimku, když nejde přečíst. */
        fun open(source: FfmpegInput): FfmpegVideoDecoder {
            logLevel
            val input = openInput(source, ::errorText)
            val format = input.format
            var codec: AVCodecContext? = null
            try {
                avformat_find_stream_info(format, null as PointerPointer<*>?).checked("avformat_find_stream_info")
                val streamIndex = av_find_best_stream(format, AVMEDIA_TYPE_VIDEO, -1, -1, null as AVCodec?, 0)
                    .checked("av_find_best_stream")
                val stream = format.streams(streamIndex)
                val decoder = avcodec_find_decoder(stream.codecpar().codec_id())
                    ?: throw IllegalStateException("avcodec_find_decoder: ${stream.codecpar().codec_id()}")
                codec = avcodec_alloc_context3(decoder)
                avcodec_parameters_to_context(codec, stream.codecpar()).checked("avcodec_parameters_to_context")
                // Jedno vlákno na přehrávač: GIFů hraje v konverzaci víc naráz a každý
                // s vlastní sadou vláken by jich vyrobil desítky.
                codec.thread_count(1)
                avcodec_open2(codec, decoder, null as AVDictionary?).checked("avcodec_open2")

                val timeBase = stream.time_base()
                val num = timeBase.num().toLong().coerceAtLeast(1)
                val den = timeBase.den().toLong().coerceAtLeast(1)
                val start = stream.start_time().takeIf { it != AV_NOPTS_VALUE } ?: 0L
                val durationMs = when {
                    stream.duration() != AV_NOPTS_VALUE && stream.duration() > 0 -> stream.duration() * num * 1000 / den
                    format.duration() != AV_NOPTS_VALUE && format.duration() > 0 -> format.duration() / 1000
                    else -> 0L
                }
                return FfmpegVideoDecoder(input, codec, streamIndex, num, den, start, durationMs)
            } catch (failure: Throwable) {
                codec?.let(::avcodec_free_context)
                input.close()
                throw failure
            }
        }

        private fun Int.checked(call: String): Int =
            if (this < 0) throw IllegalStateException("$call: ${errorText(this)}") else this

        private fun errorText(code: Int): String {
            val buffer = ByteArray(ERROR_BUFFER)
            av_strerror(code, buffer, buffer.size.toLong())
            return buffer.decodeToString().substringBefore('\u0000') + " ($code)"
        }
    }
}

/**
 * Rozměry, do kterých se snímek [width] × [height] vepíše do [target] se zachovaným poměrem
 * stran. Nikdy větší než zdroj — zvětšuje až Skia při kreslení — a dokud plocha velikost nezná
 * (nulová), zdroj beze změny.
 */
internal fun fitSize(width: Int, height: Int, target: IntSize): Pair<Int, Int> {
    if (target.width <= 0 || target.height <= 0) return width to height
    val scale = min(1f, min(target.width / width.toFloat(), target.height / height.toFloat()))
    return (width * scale).roundToInt().coerceAtLeast(1) to (height * scale).roundToInt().coerceAtLeast(1)
}

/** Odhad délky snímku, když u něj soubor čas neuvádí (25 snímků za vteřinu). */
private const val FALLBACK_FRAME_MS = 40L

private const val ERROR_BUFFER = 256
