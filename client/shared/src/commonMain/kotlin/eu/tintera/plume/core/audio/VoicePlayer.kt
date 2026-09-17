package eu.tintera.plume.core.audio

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.voice_playback_failed

/**
 * Přehrávání samotného souboru — platformní část.
 *
 * Android: `MediaPlayer` (Ogg/Opus umí sám). iOS: Ogg nezná, takže se hlasovka nejdřív dekóduje do
 * WAV (viz `parseOggOpus`) a hraje `AVAudioPlayer`. Volá se jen z hlavního vlákna.
 */
expect class AudioEngine() {
    /** Připraví soubor k přehrání a vrátí jeho délku v milisekundách. Může trvat (dekódování). */
    suspend fun load(path: String): Long

    fun start()

    fun pause()

    fun seekTo(positionMs: Long)

    val positionMs: Long

    /** Zavolá se, když přehrávání dojede na konec. */
    var onCompleted: (() -> Unit)?

    fun release()
}

/** Která hlasovka hraje. Id zprávy je jedinečné jen v rámci chatu. */
data class VoiceKey(val chatId: Long, val messageId: Long)

data class VoicePlayback(
    val key: VoiceKey,
    /** Soubor se stahuje nebo dekóduje; ještě nehraje. */
    val loading: Boolean = true,
    val playing: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
) {
    val progress: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

/**
 * Přehrávač hlasovek pro celou aplikaci — **hraje nejvýš jedna**. Spuštění jiné hlasovky tu
 * předchozí zastaví, stejně jako v Telegramu.
 *
 * Pozice se čte po [POSITION_TICK_MS] jen, když něco hraje; systémové přehrávače průběh samy
 * nehlásí. Po dohrání se stav vynuluje, takže hlasovka je zase na začátku.
 */
class VoicePlayer(private val scope: CoroutineScope) {

    private val _state = MutableStateFlow<VoicePlayback?>(null)
    val state: StateFlow<VoicePlayback?> = _state.asStateFlow()

    private val _errors = MutableSharedFlow<UiText>(extraBufferCapacity = 1)
    val errors: SharedFlow<UiText> = _errors.asSharedFlow()

    private var engine: AudioEngine? = null
    private var loadJob: Job? = null
    private var tickJob: Job? = null
    private val logger = Logger.withTag("VoicePlayer")

    /**
     * Pustí hlasovku, nebo u té, která už je vybraná, přepne pauzu. [path] se volá jen při novém
     * spuštění — stahování souboru tedy začne až tady.
     */
    fun toggle(key: VoiceKey, path: suspend () -> String) {
        val current = _state.value
        if (current?.key == key) {
            when {
                current.loading -> Unit
                current.playing -> pause()
                else -> resume()
            }
            return
        }

        stop()
        _state.value = VoicePlayback(key)
        loadJob = scope.launch {
            try {
                val file = path()
                val prepared = AudioEngine()
                val duration = try {
                    prepared.load(file)
                } catch (failure: Throwable) {
                    prepared.release()
                    throw failure
                }
                prepared.onCompleted = { stop() }
                engine = prepared
                _state.value = VoicePlayback(key, loading = false, durationMs = duration)
                resume()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                logger.w(failure) { "Hlasovku se nepodařilo přehrát." }
                _state.value = null
                _errors.tryEmit(UiText(Res.string.voice_playback_failed))
            }
        }
    }

    /** Posun v právě vybrané hlasovce. Jiné hlasovky se to netýká. */
    fun seek(key: VoiceKey, fraction: Float) {
        val current = _state.value?.takeIf { it.key == key && !it.loading } ?: return
        val engine = engine ?: return
        val position = (current.durationMs * fraction.coerceIn(0f, 1f)).toLong()
        engine.seekTo(position)
        _state.value = current.copy(positionMs = position)
    }

    /** Zastaví přehrávání; s [chatId] jen tehdy, když hraje hlasovka z toho chatu. */
    fun stop(chatId: Long? = null) {
        if (chatId != null && _state.value?.key?.chatId != chatId) return
        loadJob?.cancel()
        tickJob?.cancel()
        engine?.release()
        engine = null
        _state.value = null
    }

    private fun pause() {
        engine?.pause()
        tickJob?.cancel()
        _state.update { it?.copy(playing = false, positionMs = engine?.positionMs ?: it.positionMs) }
    }

    private fun resume() {
        val engine = engine ?: return
        engine.start()
        _state.update { it?.copy(playing = true) }
        tickJob?.cancel()
        tickJob = scope.launch {
            while (isActive) {
                _state.update { it?.copy(positionMs = engine.positionMs) }
                delay(POSITION_TICK_MS)
            }
        }
    }
}

private const val POSITION_TICK_MS = 50L
