package eu.tintera.plume.core.audio

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.video.FfmpegInput
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine
import javax.sound.sampled.TargetDataLine
import javax.swing.SwingUtilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.log10
import kotlin.math.sqrt

// Přehrávání: FFmpeg dekóduje ([FfmpegAudioDecoder]), `javax.sound` hraje. Nahrávání: `javax.sound`
// nahrává PCM, FFmpeg (`libopus`) ho zakóduje a [oggOpusFile] zabalí do Oggu ([AudioRecorder]).

/**
 * Přehrávač zvuku na desktopu: hlasovky, hudba. Dekódování i zápis do reproduktoru jsou na
 * vlastním vlákně ([run]); metody volá hlavní vlákno a vláknu jen předávají povely.
 *
 * - **Pozice se počítá z odehraných vzorků** (`SourceDataLine.longFramePosition`), ne z toho,
 *   co se do linky zapsalo: v bufferu linky leží desítky ms zvuku, které ještě nezazněly.
 *   Od posunu nebo změny rychlosti se počítá z kotvy ([anchorMs], [anchorFrame]) — vzorek
 *   výstupu je při rychlosti 1,5 o půl vzorku delší kus nahrávky.
 * - **Do linky se píše jen tolik, kolik se vejde** (`available()`): zastavená linka by jinak
 *   zápis zablokovala a vlákno by nevzalo další povel (posun, uvolnění).
 * - **Sluchátko u ucha a přerušení jiným zvukem** desktop nezná; `onInterrupted` se nevolá.
 */
actual class AudioEngine {

    private val lock = Object()
    private var decoder: FfmpegAudioDecoder? = null
    private var line: SourceDataLine? = null
    private var thread: Thread? = null
    private var durationMs = 0L

    @Volatile private var playing = false
    @Volatile private var released = false

    /**
     * Ztlumený zvuk se dál „hraje“ jako ticho: linka běží a pozice s ní, takže video, které se
     * podle ní časuje ([eu.tintera.plume.core.video.VideoPlayer]), neztratí hodiny.
     */
    @Volatile internal var muted = false

    /** Povely pro vlákno přehrávače, pod [lock]. */
    private var pendingSeekMs: Long? = null
    private var pendingSpeed: Float? = null

    @Volatile private var anchorMs = 0L
    @Volatile private var anchorFrame = 0L
    @Volatile private var speed = 1f

    actual suspend fun load(path: String, music: Boolean): Long = load(FfmpegInput.File(path))

    /** Zvuk z [input] — i rozestavěného souboru, ze kterého hraje video ([FfmpegInput.Streaming]). */
    internal suspend fun load(input: FfmpegInput): Long = withContext(Dispatchers.IO) {
        val opened = FfmpegAudioDecoder.open(input)
        try {
            val format = AudioFormat(FfmpegAudioDecoder.OUTPUT_RATE.toFloat(), 16, 2, true, false)
            val output = AudioSystem.getSourceDataLine(format)
            output.open(format, LINE_BUFFER_BYTES)
            decoder = opened
            line = output
            durationMs = opened.durationMs
            thread = Thread(::run, "plume-audio").apply {
                isDaemon = true
                start()
            }
            opened.durationMs
        } catch (failure: Throwable) {
            opened.close()
            throw failure
        }
    }

    actual fun start() {
        playing = true
        line?.start()
        synchronized(lock) { lock.notifyAll() }
    }

    actual fun pause() {
        playing = false
        line?.stop()
    }

    actual fun seekTo(positionMs: Long) {
        // Pozice platí hned (posuvník nesmí cuknout zpátky); linku vyprázdní až vlákno.
        anchor(positionMs)
        synchronized(lock) {
            pendingSeekMs = positionMs
            lock.notifyAll()
        }
    }

    actual fun setSpeed(speed: Float) {
        synchronized(lock) {
            pendingSpeed = speed
            lock.notifyAll()
        }
    }

    actual fun setSkipSilence(enabled: Boolean) = Unit

    actual suspend fun setEarpiece(enabled: Boolean) = Unit

    actual val headphonesConnected: Boolean = false

    actual val positionMs: Long
        get() {
            val output = line ?: return 0
            val played = output.longFramePosition - anchorFrame
            val position = anchorMs + (played * speed * 1000 / FfmpegAudioDecoder.OUTPUT_RATE).toLong()
            return if (durationMs > 0) position.coerceIn(0, durationMs) else position.coerceAtLeast(0)
        }

    actual var onCompleted: (() -> Unit)? = null

    actual var onInterrupted: (() -> Unit)? = null

    actual fun release() {
        released = true
        playing = false
        line?.let { output ->
            output.stop()
            output.flush()
        }
        synchronized(lock) { lock.notifyAll() }
        // Dekodér a linku zavře vlákno samo, až dokončí rozdělaný krok: zavřít je odsud by
        // mu je mohlo vzít uprostřed čtení.
        if (thread == null) closeResources()
    }

    private fun anchor(positionMs: Long) {
        anchorFrame = line?.longFramePosition ?: 0
        anchorMs = positionMs
    }

    private fun run() {
        var pending: ByteArray? = null
        var offset = 0
        var finished = false
        try {
            while (!released) {
                val decoder = decoder ?: break
                val output = line ?: break
                val (seek, newSpeed) = synchronized(lock) {
                    (pendingSeekMs to pendingSpeed).also {
                        pendingSeekMs = null
                        pendingSpeed = null
                    }
                }
                if (seek != null) {
                    decoder.seek(seek)
                    output.flush()
                    pending = null
                    finished = false
                    anchor(seek)
                }
                if (newSpeed != null && newSpeed != speed) {
                    // Kotva v místě, kde teď zvuk je; dál se počítá novou rychlostí.
                    anchor(positionMs)
                    speed = newSpeed
                    decoder.setTempo(newSpeed)
                }
                if (!playing || finished) {
                    synchronized(lock) { lock.wait(IDLE_WAIT_MS) }
                    continue
                }
                val chunk = pending ?: decoder.read()
                if (chunk == null) {
                    // Konec souboru: dohrát, co je v lince, a teprve pak ohlásit konec.
                    if (output.available() < output.bufferSize) {
                        Thread.sleep(DRAIN_POLL_MS)
                        continue
                    }
                    finished = true
                    playing = false
                    SwingUtilities.invokeLater { if (!released) onCompleted?.invoke() }
                    continue
                }
                if (pending == null) {
                    pending = chunk
                    offset = 0
                }
                val free = output.available() / FfmpegAudioDecoder.OUTPUT_FRAME_BYTES * FfmpegAudioDecoder.OUTPUT_FRAME_BYTES
                if (free == 0) {
                    Thread.sleep(WRITE_POLL_MS)
                    continue
                }
                val count = minOf(free, chunk.size - offset)
                if (muted) chunk.fill(0, offset, offset + count)
                output.write(chunk, offset, count)
                offset += count
                if (offset >= chunk.size) pending = null
            }
        } catch (_: InterruptedException) {
            // Konec vlákna.
        } catch (failure: Throwable) {
            logger.w(failure) { "Přehrávání zvuku skončilo chybou." }
            playing = false
            SwingUtilities.invokeLater { if (!released) onCompleted?.invoke() }
        } finally {
            closeResources()
        }
    }

    private fun closeResources() {
        line?.close()
        line = null
        decoder?.close()
        decoder = null
    }

    private companion object {
        val logger = Logger.withTag("AudioEngine")

        /** Buffer linky ~85 ms: kratší by na zatížené JVM lupal, delší by zpožďoval pauzu a posun. */
        const val LINE_BUFFER_BYTES = 4096 * FfmpegAudioDecoder.OUTPUT_FRAME_BYTES
        const val IDLE_WAIT_MS = 50L
        const val WRITE_POLL_MS = 5L
        const val DRAIN_POLL_MS = 10L
    }
}

/** Přeskakování ticha desktop neumí (Android ho má z Media3). */
actual val skipSilenceSupported: Boolean = false

/**
 * Nahrávání hlasovky na desktopu: PCM z mikrofonu (`TargetDataLine`, 48 kHz mono 16 bitů) do
 * paměti, po skončení zakódování do Opusu ([FfmpegOpusEncoder]) a zabalení do Oggu
 * ([oggOpusFile]) — jako na iOS. Minuta PCM je ~5,5 MB a kódování trvá zlomek délky nahrávky.
 *
 * - **Čte se na vlastním vlákně**; pozastavená linka (`stop()`) z `read` vrací rovnou nulu,
 *   proto se pak chvíli čeká, ne točí naprázdno.
 * - **Úroveň je průměrný výkon posledního kusu v dB převedený na 0..1** (−50 dB a níž je ticho),
 *   stejně jako `averagePower` na iOS, ať vlna vypadá na obou platformách stejně.
 * - **Povolení k mikrofonu dává na macOS systém** při prvním otevření linky (TCC); popis
 *   v Info.plist a oprávnění `audio-input` má balíček kvůli hovorům. Spuštěné přes Gradle
 *   ho dostává aplikace, ze které běží (terminál, IDE). Zamítnuté = linka nahrává ticho.
 */
actual class AudioRecorder : MediaCapture {

    private var line: TargetDataLine? = null
    private var thread: Thread? = null
    private var pcm = ByteArrayOutputStream()

    @Volatile private var capturing = false
    @Volatile private var lastLevel = 0f

    actual override fun start() {
        val format = AudioFormat(FfmpegOpusEncoder.SAMPLE_RATE.toFloat(), 16, 1, true, false)
        val input = AudioSystem.getTargetDataLine(format)
        input.open(format, CAPTURE_BUFFER_BYTES)
        input.start()
        pcm = ByteArrayOutputStream()
        line = input
        capturing = true
        thread = Thread({ capture(input) }, "plume-record").apply {
            isDaemon = true
            start()
        }
    }

    private fun capture(input: TargetDataLine) {
        val buffer = ByteArray(CHUNK_BYTES)
        while (capturing) {
            val count = input.read(buffer, 0, buffer.size)
            if (count <= 0) {
                Thread.sleep(PAUSED_POLL_MS)
                continue
            }
            synchronized(pcm) { pcm.write(buffer, 0, count) }
            lastLevel = levelOf(buffer, count)
        }
    }

    actual override val level: Float
        get() = lastLevel

    actual override suspend fun stop(): String {
        val recorded = finishCapture() ?: error("Nic se nenahrává.")
        return withContext(Dispatchers.Default) {
            val encoded = FfmpegOpusEncoder.encode(recorded)
            val ogg = oggOpusFile(
                packets = encoded.packets,
                channels = 1,
                preSkip = encoded.preSkip,
                samplesPerPacket = encoded.samplesPerPacket,
            )
            val directory = File(System.getProperty("java.io.tmpdir"), "plume-outgoing/${UUID.randomUUID()}").apply { mkdirs() }
            File(directory, "voice.ogg").apply { writeBytes(ogg) }.absolutePath
        }
    }

    actual override fun cancel() {
        finishCapture()
    }

    actual override fun pause() {
        line?.stop()
        lastLevel = 0f
    }

    actual override fun resume() {
        line?.start()
    }

    /** Zastaví mikrofon a vrátí nahrané PCM; `null`, když se nenahrávalo. */
    private fun finishCapture(): ByteArray? {
        val input = line ?: return null
        line = null
        capturing = false
        input.stop()
        input.close()
        thread?.join(CAPTURE_JOIN_MS)
        thread = null
        lastLevel = 0f
        return synchronized(pcm) { pcm.toByteArray() }
    }

    private companion object {
        /** ~100 ms zvuku na jedno čtení: úroveň pro vlnu se obnovuje častěji, než ji UI čte. */
        const val CHUNK_BYTES = 4800 * FfmpegOpusEncoder.BYTES_PER_SAMPLE
        const val CAPTURE_BUFFER_BYTES = CHUNK_BYTES * 4
        const val PAUSED_POLL_MS = 20L
        const val CAPTURE_JOIN_MS = 1000L
    }
}

/** Průměrný výkon PCM (16 bitů LE) v dB převedený na 0..1; −50 dB a níž je ticho. */
internal fun levelOf(buffer: ByteArray, count: Int): Float {
    val samples = count / 2
    if (samples == 0) return 0f
    var sum = 0.0
    for (i in 0 until samples) {
        val sample = ((buffer[2 * i].toInt() and 0xFF) or (buffer[2 * i + 1].toInt() shl 8)).toShort().toDouble()
        sum += sample * sample
    }
    val rms = sqrt(sum / samples) / Short.MAX_VALUE
    if (rms <= 0.0) return 0f
    val db = 20 * log10(rms)
    return ((db + SILENCE_DB) / SILENCE_DB).toFloat().coerceIn(0f, 1f)
}

private const val SILENCE_DB = 50.0

/**
 * Povolení k mikrofonu si na macOS řekne systém sám při prvním otevření linky (TCC); Java
 * dotaz předem položit neumí. Když mikrofon nejde otevřít, ohlásí to [AudioRecorder.start].
 */
@Composable
actual fun rememberRecordPermission(): RecordPermission = remember {
    object : RecordPermission {
        override fun request(onResult: (granted: Boolean) -> Unit) = onResult(true)
    }
}

/** Desktop u ucha nikdo nedrží. */
actual class EarSensor {
    actual fun start(onChange: (EarSensors) -> Unit) = onChange(EarSensors())

    actual fun stop() = Unit
}
