package eu.tintera.plume.core.video

import java.nio.channels.FileChannel
import java.util.concurrent.atomic.AtomicBoolean
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import org.bytedeco.ffmpeg.avformat.AVFormatContext
import org.bytedeco.ffmpeg.avformat.AVIOContext
import org.bytedeco.ffmpeg.avformat.AVInputFormat
import org.bytedeco.ffmpeg.avformat.Read_packet_Pointer_BytePointer_int
import org.bytedeco.ffmpeg.avformat.Seek_Pointer_long_int
import org.bytedeco.ffmpeg.avformat.Write_packet_Pointer_BytePointer_int
import org.bytedeco.ffmpeg.avutil.AVDictionary
import org.bytedeco.ffmpeg.global.avformat.AVSEEK_SIZE
import org.bytedeco.ffmpeg.global.avformat.avformat_alloc_context
import org.bytedeco.ffmpeg.global.avformat.avformat_close_input
import org.bytedeco.ffmpeg.global.avformat.avformat_open_input
import org.bytedeco.ffmpeg.global.avformat.avio_alloc_context
import org.bytedeco.ffmpeg.global.avformat.avio_context_free
import org.bytedeco.ffmpeg.global.avutil.AVERROR_EOF
import org.bytedeco.ffmpeg.global.avutil.AVERROR_EXIT
import org.bytedeco.ffmpeg.global.avutil.av_free
import org.bytedeco.ffmpeg.global.avutil.av_malloc
import org.bytedeco.javacpp.BytePointer
import org.bytedeco.javacpp.Pointer

/**
 * Odkud FFmpeg čte: hotový soubor, nebo soubor, který TDLib teprve stahuje ([StreamingSource]).
 * Obraz ([FfmpegVideoDecoder]) i zvuk (`FfmpegAudioDecoder`) otevírají vstup každý zvlášť.
 */
internal sealed interface FfmpegInput {
    class File(val path: String) : FfmpegInput

    /**
     * [cancelled] sdílí obraz i zvuk jednoho načtení: přehrávač ho nastaví, když video opouští
     * (jiné video, uvolnění), a čtení, které zrovna čeká na data, hned skončí. Zrušení korutiny
     * by blokující čekání v nativním volání nepřerušilo.
     */
    class Streaming(val source: StreamingSource, val cancelled: AtomicBoolean = AtomicBoolean(false)) : FfmpegInput
}

/**
 * Otevřený vstup: kontext formátu a u streamování i vlastní čtení ([StreamingIo]), které musí
 * žít, dokud kontext. Zavírá se jednou, spolu s dekodérem.
 */
internal class OpenedInput(val format: AVFormatContext, private val io: StreamingIo?) : AutoCloseable {
    override fun close() {
        avformat_close_input(format)
        io?.close()
    }
}

/**
 * Otevře [input] pro FFmpeg; vyhodí výjimku, když nejde. Hotový soubor jde cestou v UTF-8, jak
 * ji FFmpeg čeká (na Windows ji sám převede na UTF-16) — JavaCPP by `String` převedl kódováním
 * systému, na českých Windows cp1250.
 */
internal fun openInput(input: FfmpegInput, errorText: (Int) -> String): OpenedInput = when (input) {
    is FfmpegInput.File -> {
        val format = AVFormatContext(null)
        val opened = BytePointer(input.path, "UTF-8").use { pathBytes ->
            avformat_open_input(format, pathBytes, null as AVInputFormat?, null as AVDictionary?)
        }
        if (opened < 0) throw IllegalStateException("avformat_open_input: ${errorText(opened)}")
        OpenedInput(format, null)
    }
    is FfmpegInput.Streaming -> {
        val io = StreamingIo(input.source, input.cancelled)
        val format = avformat_alloc_context()
        // Vlastní `pb` = FFmpeg čte přes [StreamingIo] (sám si nastaví AVFMT_FLAG_CUSTOM_IO
        // a `pb` při zavření neuvolní, to dělá `io`).
        format.pb(io.context)
        // Při chybě FFmpeg kontext formátu uvolní sám.
        val opened = avformat_open_input(format, null as String?, null as AVInputFormat?, null as AVDictionary?)
        if (opened < 0) {
            io.close()
            throw IllegalStateException("avformat_open_input (stream): ${errorText(opened)}")
        }
        OpenedInput(format, io)
    }
}

/**
 * Čtení rozestavěného souboru TDLib pro FFmpeg (`AVIOContext` s vlastním čtením a posunem):
 * na data, která ještě nedorazila, se čeká, a mimo stažený úsek se stahování přesune k nim.
 *
 * - **Stahování se přetahuje.** Obraz a zvuk čtou každý svým vstupem a MP4 bývá s indexem
 *   (`moov`) na konci, takže TDLib, který stahuje jen od jednoho místa, potřebují na dvou.
 *   Kdo čeká a stahování jde jinde, řekne si o ně znovu, nejdřív po [REREQUEST_MS] — stejně
 *   jako `StreamingReader` na iOS (AVPlayer posílá víc požadavků najednou).
 * - **Soubor se čte přes `FileChannel`, ne `RandomAccessFile`.** Dostažený soubor TDLib přesune
 *   z `temp/` jinam; otevřený soubor to na macOS i Linuxu přežije, na Windows jen otevřený se
 *   `FILE_SHARE_DELETE` — a ten dává NIO, kdežto `RandomAccessFile` ne (přesun by selhal).
 *   Cesta se proto bere jen při otevření.
 * - **Čeká se nejvýš [READ_TIMEOUT_MS]** (jako na Androidu), pak čtení selže a přehrávač ohlásí
 *   chybu. Zrušení ([FfmpegInput.Streaming.cancelled]) čekání ukončí hned.
 *
 * Volá ho vlákno, které zrovna dekóduje (FFmpeg volá zpětná volání ve vlákně volajícího).
 */
internal class StreamingIo(private val source: StreamingSource, private val cancelled: AtomicBoolean) : AutoCloseable {

    @Volatile
    private var closed = false
    private val released: Boolean get() = closed || cancelled.get()
    private var position = 0L
    private var channel: FileChannel? = null

    // Zpětná volání musí žít stejně dlouho jako kontext — jinak by je GC uklidil pod FFmpegem.
    private val readCallback = object : Read_packet_Pointer_BytePointer_int() {
        override fun call(opaque: Pointer?, buffer: BytePointer, size: Int): Int = read(buffer, size)
    }
    private val seekCallback = object : Seek_Pointer_long_int() {
        override fun call(opaque: Pointer?, offset: Long, whence: Int): Long = seek(offset, whence)
    }

    val context: AVIOContext = run {
        val buffer = BytePointer(av_malloc(IO_BUFFER_BYTES.toLong())).capacity(IO_BUFFER_BYTES.toLong())
        avio_alloc_context(buffer, IO_BUFFER_BYTES, 0, null, readCallback, null as Write_packet_Pointer_BytePointer_int?, seekCallback)
            ?: throw IllegalStateException("avio_alloc_context")
    }

    private fun read(buffer: BytePointer, size: Int): Int {
        if (released) return AVERROR_EXIT
        val total = source.totalSize
        if (total in 1..position) return AVERROR_EOF
        val end = if (total > 0) minOf(position + size, total) else position + size
        val available = awaitRange(position, end) ?: return AVERROR_EXIT
        return try {
            val file = channel ?: FileChannel.open(Path.of(source.path()), StandardOpenOption.READ).also { channel = it }
            val target = buffer.capacity(size.toLong()).asByteBuffer()
            target.limit(minOf(available, size.toLong()).toInt())
            val read = file.read(target, position)
            if (read <= 0) return AVERROR_EOF
            position += read
            read
        } catch (_: java.io.IOException) {
            AVERROR_EXIT
        }
    }

    private fun seek(offset: Long, whence: Int): Long {
        if (whence and AVSEEK_SIZE != 0) return source.totalSize.takeIf { it > 0 } ?: -1
        val target = when (whence and WHENCE_MASK) {
            SEEK_SET -> offset
            SEEK_CUR -> position + offset
            SEEK_END -> source.totalSize + offset
            else -> return -1
        }
        if (target < 0) return -1
        position = target
        return target
    }

    /**
     * Počká, až bude od [offset] co číst, a vrátí počet souvisle dostupných bajtů (nejvýš do
     * [end]); `null` po [READ_TIMEOUT_MS] bez dat nebo po uvolnění.
     */
    private fun awaitRange(offset: Long, end: Long): Long? {
        var waited = 0L
        var sinceRequest = REREQUEST_MS
        while (waited < READ_TIMEOUT_MS && !released) {
            val range = source.downloaded()
            if (offset in range) return minOf(range.last + 1, end) - offset
            // Stahuje se k nám, jen ještě nedorazilo (úsek končí těsně před [offset]): čekat.
            if (!downloadHeadsTo(range, offset) && sinceRequest >= REREQUEST_MS) {
                source.requestFrom(offset)
                sinceRequest = 0
            }
            Thread.sleep(POLL_MS)
            waited += POLL_MS
            sinceRequest += POLL_MS
        }
        return null
    }

    override fun close() {
        closed = true
        // FFmpeg mohl buffer vyměnit za větší, uvolňuje se ten, který kontext drží teď.
        av_free(context.buffer())
        avio_context_free(context)
        runCatching { channel?.close() }
        channel = null
        readCallback.close()
        seekCallback.close()
    }

    private companion object {
        const val IO_BUFFER_BYTES = 64 * 1024
        const val READ_TIMEOUT_MS = 30_000L
        const val POLL_MS = 50L
        const val REREQUEST_MS = 1_000L

        const val SEEK_SET = 0
        const val SEEK_CUR = 1
        const val SEEK_END = 2

        /** Bez příznaků (`AVSEEK_FORCE`), jen způsob posunu. */
        const val WHENCE_MASK = 0xFFFF
    }
}
