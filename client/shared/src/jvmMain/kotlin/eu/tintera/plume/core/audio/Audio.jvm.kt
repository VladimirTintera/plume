package eu.tintera.plume.core.audio

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

// Zvuk na desktopu zatím není (viz docs/roadmap.md): Java sama Opus ani MP3 nedekóduje a mikrofon
// přes javax.sound by potřeboval vlastní kodér do Ogg/Opus. Přehrávač i nahrávání proto selžou
// tak, jak to sdílený kód umí ohlásit — „hlasovku se nepodařilo přehrát" / „nahrávání nejde spustit".

actual class AudioEngine {

    actual suspend fun load(path: String, music: Boolean): Long =
        throw UnsupportedOperationException("Přehrávání zvuku na desktopu zatím není.")

    actual fun start() = Unit

    actual fun pause() = Unit

    actual fun seekTo(positionMs: Long) = Unit

    actual fun setSpeed(speed: Float) = Unit

    actual fun setSkipSilence(enabled: Boolean) = Unit

    actual suspend fun setEarpiece(enabled: Boolean) = Unit

    actual val headphonesConnected: Boolean = false

    actual val positionMs: Long = 0

    actual var onCompleted: (() -> Unit)? = null

    actual var onInterrupted: (() -> Unit)? = null

    actual fun release() = Unit
}

/** Zvuk na desktopu není, a s ním ani přeskakování ticha. */
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
