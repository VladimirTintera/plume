package eu.tintera.plume.core.audio

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import co.touchlab.kermit.Logger
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine
import javax.swing.SwingUtilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Přehrávání: FFmpeg dekóduje ([FfmpegAudioDecoder]), `javax.sound` hraje. Nahrávání zatím není:
// mikrofon přes javax.sound by potřeboval vlastní kodér do Ogg/Opus, takže nahrávání selže tak,
// jak to sdílený kód umí ohlásit — „nahrávání nejde spustit".

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

    /** Povely pro vlákno přehrávače, pod [lock]. */
    private var pendingSeekMs: Long? = null
    private var pendingSpeed: Float? = null

    @Volatile private var anchorMs = 0L
    @Volatile private var anchorFrame = 0L
    @Volatile private var speed = 1f

    actual suspend fun load(path: String, music: Boolean): Long = withContext(Dispatchers.IO) {
        val opened = FfmpegAudioDecoder.open(path)
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

actual class AudioRecorder : MediaCapture {

    actual override fun start(): Unit =
        throw UnsupportedOperationException("Nahrávání zvuku na desktopu zatím není.")

    actual override val level: Float = 0f

    actual override suspend fun stop(): String =
        throw UnsupportedOperationException("Nahrávání zvuku na desktopu zatím není.")

    actual override fun cancel() = Unit

    actual override fun pause() = Unit

    actual override fun resume() = Unit
}

/** Mikrofon žádné povolení nechce; nahrávání pak samo ohlásí, že nejde ([AudioRecorder.start]). */
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
