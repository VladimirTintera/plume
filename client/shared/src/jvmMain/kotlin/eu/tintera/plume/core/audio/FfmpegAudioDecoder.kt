package eu.tintera.plume.core.audio

import org.bytedeco.ffmpeg.avcodec.AVCodec
import org.bytedeco.ffmpeg.avcodec.AVCodecContext
import org.bytedeco.ffmpeg.avcodec.AVPacket
import org.bytedeco.ffmpeg.avfilter.AVFilterContext
import org.bytedeco.ffmpeg.avfilter.AVFilterGraph
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
import org.bytedeco.ffmpeg.global.avfilter.av_buffersink_get_frame
import org.bytedeco.ffmpeg.global.avfilter.av_buffersrc_add_frame
import org.bytedeco.ffmpeg.global.avfilter.avfilter_get_by_name
import org.bytedeco.ffmpeg.global.avfilter.avfilter_graph_alloc
import org.bytedeco.ffmpeg.global.avfilter.avfilter_graph_config
import org.bytedeco.ffmpeg.global.avfilter.avfilter_graph_create_filter
import org.bytedeco.ffmpeg.global.avfilter.avfilter_graph_free
import org.bytedeco.ffmpeg.global.avfilter.avfilter_graph_send_command
import org.bytedeco.ffmpeg.global.avfilter.avfilter_link
import org.bytedeco.ffmpeg.global.avformat.AVSEEK_FLAG_BACKWARD
import org.bytedeco.ffmpeg.global.avformat.av_find_best_stream
import org.bytedeco.ffmpeg.global.avformat.av_read_frame
import org.bytedeco.ffmpeg.global.avformat.av_seek_frame
import org.bytedeco.ffmpeg.global.avformat.avformat_find_stream_info
import org.bytedeco.ffmpeg.global.avutil.AVERROR_EOF
import org.bytedeco.ffmpeg.global.avutil.AVMEDIA_TYPE_AUDIO
import org.bytedeco.ffmpeg.global.avutil.AV_LOG_ERROR
import org.bytedeco.ffmpeg.global.avutil.AV_NOPTS_VALUE
import org.bytedeco.ffmpeg.global.avutil.av_channel_layout_describe
import org.bytedeco.ffmpeg.global.avutil.av_frame_alloc
import org.bytedeco.ffmpeg.global.avutil.av_frame_free
import org.bytedeco.ffmpeg.global.avutil.av_frame_unref
import org.bytedeco.ffmpeg.global.avutil.av_get_sample_fmt_name
import org.bytedeco.ffmpeg.global.avutil.av_log_set_level
import org.bytedeco.ffmpeg.global.avutil.av_strerror
import org.bytedeco.ffmpeg.presets.avutil.AVERROR_EAGAIN
import eu.tintera.plume.core.video.FfmpegInput
import eu.tintera.plume.core.video.OpenedInput
import eu.tintera.plume.core.video.openInput
import org.bytedeco.javacpp.BytePointer
import org.bytedeco.javacpp.PointerPointer

/**
 * Zvuk souboru jako PCM pro reproduktor, přes FFmpeg (JavaCPP): hlasovky (Ogg/Opus), hudba
 * (MP3, AAC, FLAC…) i zvuk z videa. Výstup je vždycky [OUTPUT_RATE] Hz, stereo, 16 bitů
 * little endian — to bere `javax.sound` na všech třech systémech.
 *
 * - **Rychlost dělá filtr `atempo`**, ne převzorkování: hlas se zrychlí, ale nezvýší. Mění se
 *   za běhu příkazem filtru, takže změna uprostřed nahrávky nic nezahodí.
 * - **Posun je po rámcích** (Opus 20 ms, MP3 26 ms): FFmpeg skočí na paket před cílem
 *   a rámce, které celé končí před ním, se zahodí.
 *
 * Není vláknově bezpečný: volá ho jen vlákno přehrávače ([AudioEngine]).
 */
internal class FfmpegAudioDecoder private constructor(
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
    private val filtered: AVFrame = av_frame_alloc()

    private var graph: AVFilterGraph? = null
    private var source: AVFilterContext? = null
    private var sink: AVFilterContext? = null
    private var tempo = 1f

    /** Konec souboru přečtený, dekodér a filtr vydávají jen zbytek. */
    private var inputEnded = false
    private var filterFlushed = false

    /** Po posunu: rámce, které celé končí před tímto časem (ms), se zahodí. */
    private var skipUntilMs = 0L

    init {
        buildGraph()
    }

    /**
     * Další kus PCM pro reproduktor; `null` na konci souboru. Prázdný kus se nevrací — dokud
     * filtr nic nevydá, čte se dál.
     */
    fun read(): ByteArray? {
        while (true) {
            val sinkContext = sink ?: return null
            val pulled = av_buffersink_get_frame(sinkContext, filtered)
            if (pulled >= 0) {
                val bytes = filtered.nb_samples() * OUTPUT_FRAME_BYTES
                val chunk = ByteArray(bytes)
                filtered.data(0).get(chunk, 0, bytes)
                av_frame_unref(filtered)
                if (chunk.isNotEmpty()) return chunk
                continue
            }
            if (pulled == AVERROR_EOF) return null
            if (pulled != AVERROR_EAGAIN()) throw IllegalStateException("av_buffersink_get_frame: ${errorText(pulled)}")
            if (!feedFilter()) return null
        }
    }

    /** Dá filtru další dekódovaný rámec; `false`, když už není co dát ani dočíst. */
    private fun feedFilter(): Boolean {
        while (true) {
            val received = avcodec_receive_frame(codec, frame)
            if (received >= 0) {
                if (beforeSeekTarget()) {
                    av_frame_unref(frame)
                    continue
                }
                av_buffersrc_add_frame(source, frame).checked("av_buffersrc_add_frame")
                return true
            }
            if (received == AVERROR_EOF) {
                if (filterFlushed) return false
                // Konec dekodéru: filtr vydá, co drží (atempo si pár desítek ms schovává).
                av_buffersrc_add_frame(source, null as AVFrame?)
                filterFlushed = true
                return true
            }
            if (received != AVERROR_EAGAIN()) throw IllegalStateException("avcodec_receive_frame: ${errorText(received)}")
            if (inputEnded) {
                avcodec_send_packet(codec, null as AVPacket?)
                continue
            }
            // Konec souboru i chyba čtení: dekodér se vyprázdní a vydá rámce, které drží.
            if (av_read_frame(format, packet) < 0) {
                inputEnded = true
                avcodec_send_packet(codec, null as AVPacket?)
                continue
            }
            // Vadný paket dekodér odmítne a pokračuje se dalším — jedno poškozené místo nemá
            // zastavit celou nahrávku.
            if (packet.stream_index() == streamIndex) avcodec_send_packet(codec, packet)
            av_packet_unref(packet)
        }
    }

    private fun beforeSeekTarget(): Boolean {
        if (skipUntilMs <= 0) return false
        val ts = frame.best_effort_timestamp()
        if (ts == AV_NOPTS_VALUE) return false
        val endMs = toMs(ts - startTs) + frame.nb_samples() * 1000L / frame.sample_rate().coerceAtLeast(1)
        if (endMs > skipUntilMs) {
            skipUntilMs = 0
            return false
        }
        return true
    }

    /** Skočí na [positionMs]; další [read] vydává zvuk od ní. */
    fun seek(positionMs: Long) {
        val ts = startTs + positionMs * timeBaseDen / (timeBaseNum * 1000)
        av_seek_frame(format, streamIndex, ts, AVSEEK_FLAG_BACKWARD)
        avcodec_flush_buffers(codec)
        inputEnded = false
        skipUntilMs = positionMs
        // Filtr drží zvuk z místa před posunem; nový začíná načisto.
        buildGraph()
    }

    /** Rychlost přehrávání (1 = normální); platí od dalšího [read]. */
    fun setTempo(speed: Float) {
        if (speed == tempo) return
        tempo = speed
        val current = graph ?: return
        avfilter_graph_send_command(current, "atempo", "tempo", speed.toString(), null as BytePointer?, 0, 0)
    }

    private fun buildGraph() {
        freeGraph()
        filterFlushed = false
        val newGraph = avfilter_graph_alloc() ?: throw IllegalStateException("avfilter_graph_alloc")
        try {
            val sampleRate = codec.sample_rate()
            val layout = ByteArray(LAYOUT_BUFFER)
            av_channel_layout_describe(codec.ch_layout(), layout, layout.size.toLong())
            val sourceArgs = "time_base=1/$sampleRate:sample_rate=$sampleRate" +
                ":sample_fmt=${av_get_sample_fmt_name(codec.sample_fmt()).string}" +
                ":channel_layout=${layout.decodeToString().substringBefore('\u0000')}"
            val newSource = AVFilterContext()
            val atempo = AVFilterContext()
            val aformat = AVFilterContext()
            val newSink = AVFilterContext()
            avfilter_graph_create_filter(newSource, avfilter_get_by_name("abuffer"), "in", sourceArgs, null, newGraph)
                .checked("abuffer")
            avfilter_graph_create_filter(atempo, avfilter_get_by_name("atempo"), "atempo", "tempo=$tempo", null, newGraph)
                .checked("atempo")
            avfilter_graph_create_filter(
                aformat,
                avfilter_get_by_name("aformat"),
                "aformat",
                "sample_fmts=s16:sample_rates=$OUTPUT_RATE:channel_layouts=stereo",
                null,
                newGraph,
            ).checked("aformat")
            avfilter_graph_create_filter(newSink, avfilter_get_by_name("abuffersink"), "out", null as String?, null, newGraph)
                .checked("abuffersink")
            avfilter_link(newSource, 0, atempo, 0).checked("avfilter_link")
            avfilter_link(atempo, 0, aformat, 0).checked("avfilter_link")
            avfilter_link(aformat, 0, newSink, 0).checked("avfilter_link")
            avfilter_graph_config(newGraph, null).checked("avfilter_graph_config")
            graph = newGraph
            source = newSource
            sink = newSink
        } catch (failure: Throwable) {
            avfilter_graph_free(newGraph)
            throw failure
        }
    }

    private fun freeGraph() {
        graph?.let { avfilter_graph_free(it) }
        graph = null
        source = null
        sink = null
    }

    private fun toMs(ts: Long): Long = ts * timeBaseNum * 1000 / timeBaseDen

    override fun close() {
        freeGraph()
        av_frame_free(frame)
        av_frame_free(filtered)
        av_packet_free(packet)
        avcodec_free_context(codec)
        input.close()
    }

    companion object {
        /** Vzorkovací kmitočet výstupu; Opus je 48 kHz nativně. */
        const val OUTPUT_RATE = 48_000

        /** Bajtů na jeden vzorek výstupu: stereo × 16 bitů. */
        const val OUTPUT_FRAME_BYTES = 4

        private const val LAYOUT_BUFFER = 64
        private const val ERROR_BUFFER = 128

        /** Hlášky FFmpegu na stderr jen u chyb. */
        private val logLevel by lazy { av_log_set_level(AV_LOG_ERROR) }

        /** Otevře [path]; vyhodí výjimku, když soubor nejde přečíst nebo v něm není zvuk. */
        fun open(path: String): FfmpegAudioDecoder = open(FfmpegInput.File(path))

        /** Otevře [source]; vyhodí výjimku, když nejde přečíst. */
        fun open(source: FfmpegInput): FfmpegAudioDecoder {
            logLevel
            val input = openInput(source, ::errorText)
            val format = input.format
            var codec: AVCodecContext? = null
            try {
                avformat_find_stream_info(format, null as PointerPointer<*>?).checked("avformat_find_stream_info")
                val streamIndex = av_find_best_stream(format, AVMEDIA_TYPE_AUDIO, -1, -1, null as AVCodec?, 0)
                    .checked("av_find_best_stream")
                val stream = format.streams(streamIndex)
                val decoder = avcodec_find_decoder(stream.codecpar().codec_id())
                    ?: throw IllegalStateException("avcodec_find_decoder: ${stream.codecpar().codec_id()}")
                codec = avcodec_alloc_context3(decoder)
                avcodec_parameters_to_context(codec, stream.codecpar()).checked("avcodec_parameters_to_context")
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
                return FfmpegAudioDecoder(input, codec, streamIndex, num, den, start, durationMs)
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
