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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    /**
     * Připraví soubor k přehrání a vrátí jeho délku v milisekundách. Může trvat (dekódování).
     *
     * [music] = skladba, ne hlasovka: na Androidu jiné atributy zvuku a trvalý audio focus.
     */
    suspend fun load(path: String, music: Boolean = false): Long

    fun start()

    fun pause()

    fun seekTo(positionMs: Long)

    /** Rychlost přehrávání (1 = normální). Platí hned, i uprostřed hlasovky. */
    fun setSpeed(speed: Float)

    /**
     * Přepne zvuk do sluchátka u ucha (`true`), nebo zpátky do reproduktoru.
     *
     * `suspend`, protože Android kvůli tomu musí postavit `MediaPlayer` znovu — do sluchátka se
     * dostane jen zvuk s `USAGE_VOICE_COMMUNICATION` a atributy jdou nastavit jen před `prepare()`.
     * Na iOS stačí přepnout kategorii `AVAudioSession`, tam je to okamžité.
     */
    suspend fun setEarpiece(enabled: Boolean)

    /** Jsou připojená sluchátka? Pak se do sluchátka u ucha nepřepíná — viz [raisedToEar]. */
    val headphonesConnected: Boolean

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

/**
 * Která hlasovka hraje. Id zprávy je jedinečné jen v rámci chatu.
 *
 * [part] je pořadí zvuku UVNITŘ zprávy — článek nese zvuků a hlasovek víc pod jedním id;
 * [NO_PART] = příloha zprávy.
 */
data class VoiceKey(val chatId: Long, val messageId: Long, val part: Int = NO_PART) {
    companion object {
        const val NO_PART = -1
    }
}

data class VoicePlayback(
    val key: VoiceKey,
    /** Skladba, ne hlasovka: hraje i bez otevřené konverzace a ovládá se ze systému ([MusicQueue]). */
    val music: Boolean = false,
    /** Soubor se stahuje nebo dekóduje; ještě nehraje. */
    val loading: Boolean = true,
    val playing: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
) {
    val progress: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

/**
 * Přehrávač hlasovek a hudby pro celou aplikaci — **hraje nejvýš jedna**. Spuštění jiné hlasovky tu
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
 *
 * **Senzory u ucha sleduje jen po dobu, kdy se HRAJE** ([EarSensor]). Pauza je sundá taky: kdo si
 * hlasovku pozastavil, ten ji u ucha nedrží, a zhasínající displej by mu jen překážel.
 * **Hudba u ucha nepřepíná vůbec** (`music = true`): písnička do sluchátka nepatří
 * a telefon v ruce při poslechu hudby by jen zhasínal displej.
 *
 * **Hudbu sem posílá [MusicQueue], ne obrazovka** — ta drží frontu a systémové ovládání, protože
 * skladba hraje dál i po odchodu z konverzace.
 */
class VoicePlayer(
    private val scope: CoroutineScope,
    private val newEngine: () -> AudioEngine,
    private val settings: AppSettings,
    private val earSensor: EarSensor,
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
    private var raiseToEar = true
    private var loadJob: Job? = null
    private var tickJob: Job? = null
    private val logger = Logger.withTag("VoicePlayer")

    /**
     * Přesměrování výstupu je na Androidu přestavba přehrávače, takže se dvě nesmí potkat.
     *
     * Ruka u ucha a zpátky umí poslat dvě změny těsně po sobě; bez zámku by se druhá pustila do
     * přehrávače, který ta první právě uvolnila.
     */
    private val routeMutex = Mutex()

    /**
     * Pustí hlasovku, nebo u té, která už je vybraná, přepne pauzu. [path] se volá jen při novém
     * spuštění — stahování souboru tedy začne až tady.
     */
    fun toggle(key: VoiceKey, music: Boolean = false, path: suspend () -> String) {
        val current = _state.value
        if (current?.key == key) {
            when {
                current.loading -> Unit
                current.playing -> pause()
                else -> resume()
            }
            return
        }
        play(key, music, path = path)
    }

    /**
     * Pustí hlasovku od začátku (nebo od [startMs] — čas v médiu), ať byla vybraná, nebo ne.
     * Tudy jde řetězení neposlechnutých.
     */
    fun play(
        key: VoiceKey,
        music: Boolean = false,
        startMs: Long = 0L,
        /**
         * Zavolá se po dohrání, SYNCHRONNĚ a ještě před vynulováním stavu — na rozdíl od
         * [completions], které doběhnou až v korutině. [MusicQueue] tak ví, že přehrávání končí
         * kvůli další skladbě, dřív, než uvidí prázdný stav.
         */
        onFinished: (() -> Unit)? = null,
        path: suspend () -> String,
    ) {
        stop()
        this.raiseToEar = !music
        _state.value = VoicePlayback(key, music = music)
        loadJob = scope.launch {
            try {
                val file = path()
                val prepared = newEngine()
                val duration = try {
                    prepared.load(file, music)
                } catch (failure: Throwable) {
                    prepared.release()
                    throw failure
                }
                prepared.onCompleted = {
                    // Dohráno není zastaveno: stav se uklidí jako vždy, ale ještě se to ohlásí ven.
                    onFinished?.invoke()
                    stop()
                    _completions.tryEmit(key)
                }
                // Hovor nebo budík hlasovku zastaví; přehrávač o tom sám neví, musí to jít do stavu.
                prepared.onInterrupted = { pause() }
                prepared.setSpeed(speed.value.rate)
                // Čas za koncem (překlep v „1:23" u krátké hlasovky) by přehrávač zastavil hned.
                val start = startMs.takeIf { it in 1 until duration } ?: 0L
                if (start > 0) prepared.seekTo(start)
                engine = prepared
                _state.value = VoicePlayback(key, music = music, loading = false, durationMs = duration, positionMs = start)
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
        seekTo(key, (current.durationMs * fraction.coerceIn(0f, 1f)).toLong())
    }

    /** Posun na čas v milisekundách — systémové ovládání (notifikace, zamykací obrazovka). */
    fun seekTo(key: VoiceKey, positionMs: Long) {
        val current = _state.value?.takeIf { it.key == key && !it.loading } ?: return
        val engine = engine ?: return
        val position = positionMs.coerceIn(0L, current.durationMs)
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

    /**
     * Zastaví přehrávání; s [chatId] jen tehdy, když hraje hlasovka z toho chatu.
     *
     * [keepMusic]: hudba se nezastaví — odchod z konverzace ji nechá hrát dál, hlasovku ne
     * (neměla by kde ukazovat průběh a nahrávala by se do cizí obrazovky).
     */
    fun stop(chatId: Long? = null, keepMusic: Boolean = false) {
        if (chatId != null && _state.value?.key?.chatId != chatId) return
        if (keepMusic && _state.value?.music == true) return
        // Výstup vracet netřeba: `release()` níž systému komunikační režim sundá sám.
        stopListeningForEar(returnToSpeaker = false)
        loadJob?.cancel()
        tickJob?.cancel()
        engine?.release()
        engine = null
        _state.value = null
    }

    /**
     * Začne sledovat, jestli si uživatel nedal telefon k uchu, a podle toho přepíná výstup.
     *
     * Rozhodnutí je čistá funkce [raisedToEar]; senzor jen hlásí, co měří. Sluchátka se čtou při
     * KAŽDÉ změně, ne jednou na začátku — dají se připojit i uprostřed hlasovky.
     */
    private fun startListeningForEar() {
        if (!raiseToEar) return
        earSensor.start { sensors ->
            val toEar = raisedToEar(sensors, headphonesConnected = engine?.headphonesConnected ?: false)
            scope.launch {
                routeMutex.withLock {
                    // Mezitím mohla hlasovka skončit; přesměrovávat je pak co přestavovat.
                    val current = engine ?: return@withLock
                    runCatching { current.setEarpiece(toEar) }
                        .onFailure { failure -> logger.w(failure) { "Výstup hlasovky nejde přepnout." } }
                }
            }
        }
    }

    /**
     * Přestane sledovat senzory.
     *
     * [returnToSpeaker] platí tam, kde přehrávač ŽIJE DÁL (pauza): komunikační režim je nastavení
     * celého systému, takže ho hlasovka pozastavená u ucha nesmí nechat viset. Při zastavení se
     * naopak vracet nemá co — přehrávač se hned uvolňuje a uklidí si to sám.
     */
    private fun stopListeningForEar(returnToSpeaker: Boolean) {
        earSensor.stop()
        if (!returnToSpeaker) return
        val current = engine ?: return
        scope.launch {
            routeMutex.withLock {
                runCatching { current.setEarpiece(false) }
                    .onFailure { failure -> logger.w(failure) { "Výstup hlasovky nejde vrátit." } }
            }
        }
    }

    fun pause() {
        stopListeningForEar(returnToSpeaker = true)
        engine?.pause()
        tickJob?.cancel()
        _state.update { it?.copy(playing = false, positionMs = engine?.positionMs ?: it.positionMs) }
    }

    fun resume() {
        val engine = engine ?: return
        engine.start()
        startListeningForEar()
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
