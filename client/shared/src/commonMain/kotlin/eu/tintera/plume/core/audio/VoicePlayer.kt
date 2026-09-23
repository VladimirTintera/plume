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
import eu.tintera.plume.core.settings.AppSettings
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.voice_playback_failed

/**
 * Přehrávání samotného souboru — platformní část.
 *
 * Android: `MediaPlayer` (Ogg/Opus umí sám). iOS: Ogg nezná, takže se hlasovka nejdřív dekóduje do
 * WAV (viz `parseOggOpus`) a hraje `AVAudioPlayer`. Volá se jen z hlavního vlákna.
 *
 * Konstruktor je platformní (Android potřebuje `Context` kvůli audio focus), takže se engine bere
 * z Koinu jako `factory` — stejně jako `AudioRecorder`.
 */
expect class AudioEngine {
    /** Připraví soubor k přehrání a vrátí jeho délku v milisekundách. Může trvat (dekódování). */
    suspend fun load(path: String): Long

    fun start()

    fun pause()

    fun seekTo(positionMs: Long)

    /** Rychlost přehrávání (1 = normální). Platí hned, i uprostřed hlasovky. */
    fun setSpeed(speed: Float)

    val positionMs: Long

    /** Zavolá se, když přehrávání dojede na konec. */
    var onCompleted: (() -> Unit)?

    /**
     * Zavolá se, když o zvuk přišla aplikace, ne uživatel: příchozí hovor, budík, jiná aplikace.
     * Přehrávač je v tu chvíli už zastavený — zbývá to promítnout do stavu.
     */
    var onInterrupted: (() -> Unit)?

    fun release()
}

/**
 * Rychlost přehrávání hlasovky. Tři hodnoty, ne posuvník — mezi 1,25 a 1,3 nikdo nerozhoduje
 * a ťuknutí na čip musí vždycky někam dojít.
 */
enum class VoiceSpeed(val rate: Float) {
    Normal(1f),
    Fast(1.5f),
    Double(2f),
    ;

    /** Další v pořadí; za poslední se vrací na začátek — čip je přepínač, ne nabídka. */
    fun next(): VoiceSpeed = entries[(ordinal + 1) % entries.size]
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
 *
 * **Engine se bere z [newEngine], nevyrábí se tady.** Android potřebuje ke zvuku `Context` (audio
 * focus), a ten `core/audio` nezná — dodá ho Koin, jako u `AudioRecorder`.
 *
 * **Rychlost drží přehrávač, ne obrazovka.** Je to volba přes celou aplikaci (další hlasovka se má
 * pustit toutéž rychlostí, i v jiném chatu) a přežívá restart, takže bydlí v [AppSettings].
 */
class VoicePlayer(
    private val scope: CoroutineScope,
    private val newEngine: () -> AudioEngine,
    private val settings: AppSettings,
) {

    private val _state = MutableStateFlow<VoicePlayback?>(null)
    val state: StateFlow<VoicePlayback?> = _state.asStateFlow()

    private val _errors = MutableSharedFlow<UiText>(extraBufferCapacity = 1)
    val errors: SharedFlow<UiText> = _errors.asSharedFlow()

    /**
     * Hlasovka, která právě DOHRÁLA (ne ta, kterou zastavil uživatel).
     *
     * Odsud se řetězí přehrávání dalších neposlechnutých. Rozhodnout, která je další, umí jen
     * obrazovka — přehrávač o konverzaci nic neví.
     */
    private val _completions = MutableSharedFlow<VoiceKey>(extraBufferCapacity = 1)
    val completions: SharedFlow<VoiceKey> = _completions.asSharedFlow()

    /** Zvolená rychlost; platí pro všechny hlasovky a přežívá restart. */
    val speed: StateFlow<VoiceSpeed> = settings.voiceSpeed

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
        play(key, path)
    }

    /** Pustí hlasovku od začátku, ať byla vybraná, nebo ne. Tudy jde řetězení neposlechnutých. */
    fun play(key: VoiceKey, path: suspend () -> String) {
        stop()
        _state.value = VoicePlayback(key)
        loadJob = scope.launch {
            try {
                val file = path()
                val prepared = newEngine()
                val duration = try {
                    prepared.load(file)
                } catch (failure: Throwable) {
                    prepared.release()
                    throw failure
                }
                prepared.onCompleted = {
                    // Dohráno není zastaveno: stav se uklidí jako vždy, ale ještě se to ohlásí ven.
                    stop()
                    _completions.tryEmit(key)
                }
                // Hovor nebo budík hlasovku zastaví; přehrávač o tom sám neví, musí to jít do stavu.
                prepared.onInterrupted = { pause() }
                prepared.setSpeed(speed.value.rate)
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

    /** Přepne rychlost na další v pořadí. Platí hned, i uprostřed hrající hlasovky. */
    fun cycleSpeed(): VoiceSpeed {
        val next = speed.value.next()
        settings.setVoiceSpeed(next)
        engine?.setSpeed(next.rate)
        return next
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
