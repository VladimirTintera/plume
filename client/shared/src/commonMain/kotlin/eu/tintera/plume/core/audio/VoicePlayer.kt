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
import eu.tintera.plume.core.accounts.AccountSlot
import eu.tintera.plume.core.settings.AppSettings
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.core.video.MIN_RESUME_AUDIO_DURATION_MS
import eu.tintera.plume.core.video.VideoPositions
import kotlin.time.TimeSource
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
     * Přeskakovat ticho: pauzy v řeči se zkrátí, hlas se nezrychlí ani nezkreslí. Platí hned.
     * Kde to platforma neumí ([skipSilenceSupported] `false`), nedělá nic.
     */
    fun setSkipSilence(enabled: Boolean)

    /**
     * Přepne zvuk do sluchátka u ucha (`true`), nebo zpátky do reproduktoru.
     *
     * `suspend` z doby, kdy Android kvůli tomu stavěl `MediaPlayer` znovu (atributy šly nastavit
     * jen před `prepare()`); ExoPlayer je mění za běhu, iOS přepne kategorii `AVAudioSession`.
     * Volání přesto drží zámek ([VoicePlayer] `routeMutex`), dvě změny výstupu se nesmí potkat.
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
 * Umí platforma přeskakovat ticho ([AudioEngine.setSkipSilence])? Android ano (Media3
 * `skipSilenceEnabled`); iOS (`AVAudioPlayer`) ani desktop ne — tam se volba v Nastavení neukazuje.
 */
expect val skipSilenceSupported: Boolean

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
 * Která hlasovka hraje. Id zprávy je jedinečné jen v rámci chatu a chat jen v rámci účtu
 * ([slot]): společný kanál nebo chat s týmž člověkem má ve dvou účtech totéž id, takže bez účtu
 * by bublina v jiném účtu ukázala, že hraje.
 *
 * [part] je pořadí zvuku UVNITŘ zprávy — článek nese zvuků a hlasovek víc pod jedním id;
 * [NO_PART] = příloha zprávy.
 */
data class VoiceKey(val slot: AccountSlot, val chatId: Long, val messageId: Long, val part: Int = NO_PART) {
    /** Patří zvuk chatu [chatId] účtu [slot]? */
    fun isIn(slot: AccountSlot, chatId: Long): Boolean = this.slot == slot && this.chatId == chatId

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
    /**
     * Pokračuje se z pamatované pozice (dlouhá nahrávka, viz [audioStart]); od kdy. Obrazovka
     * podle toho ukáže „Pokračuje od 12:34" s nabídkou začít znovu. `null` = od začátku nebo od
     * výslovného času.
     */
    val resumedFromMs: Long? = null,
) {
    val progress: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

/**
 * Co z přehrávače hlasovek potřebuje konverzace ([VoicePlayer]). Rozhraní, aby šly části
 * konverzace testovat bez platformního [AudioEngine] a [EarSensor].
 */
interface VoicePlayerControl {
    val state: StateFlow<VoicePlayback?>
    val speed: StateFlow<VoiceSpeed>

    /** Viz [VoicePlayer.toggle]. */
    fun toggle(key: VoiceKey, music: Boolean = false, resumeKey: String? = null, path: suspend () -> String)

    /** Viz [VoicePlayer.play]. */
    fun play(
        key: VoiceKey,
        music: Boolean = false,
        startMs: Long = 0L,
        resumeKey: String? = null,
        onFinished: (() -> Unit)? = null,
        path: suspend () -> String,
    )

    fun seek(key: VoiceKey, fraction: Float)

    /** Viz [VoicePlayer.stop]. */
    fun stop(chat: Pair<AccountSlot, Long>? = null, keepMusic: Boolean = false)

    fun pause()

    fun resume()
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
 *
 * **Pozici v dlouhé nahrávce si pamatuje** ([positions], tentýž seznam jako u videa): kdo zvuk
 * pouští, podá `resumeKey` (trvalé id souboru), a od [MIN_RESUME_AUDIO_DURATION_MS] se pozice
 * ukládá při pauze, zastavení a každých [AUDIO_POSITION_SAVE_INTERVAL_MS] za přehrávání; dohraná
 * se zapomene. Příště se pokračuje odtamtud ([audioStart]).
 *
 * **Přeskakování ticha** je volba v [AppSettings.skipSilence] a platí i pro hrající zvuk.
 */
class VoicePlayer(
    private val scope: CoroutineScope,
    private val newEngine: () -> AudioEngine,
    private val settings: AppSettings,
    private val earSensor: EarSensor,
    private val positions: VideoPositions,
) : VoicePlayerControl {

    private val _state = MutableStateFlow<VoicePlayback?>(null)
    override val state: StateFlow<VoicePlayback?> = _state.asStateFlow()

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
    override val speed: StateFlow<VoiceSpeed> = settings.voiceSpeed

    private var engine: AudioEngine? = null

    /** Klíč pamatované pozice hrajícího zvuku (`RemoteFile.uniqueId`); `null` = nepamatovat. */
    private var resumeKey: String? = null
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
    override fun toggle(key: VoiceKey, music: Boolean, resumeKey: String?, path: suspend () -> String) {
        val current = _state.value
        if (current?.key == key) {
            when {
                current.loading -> Unit
                current.playing -> pause()
                else -> resume()
            }
            return
        }
        play(key, music, resumeKey = resumeKey, path = path)
    }

    init {
        // Přepnutí v Nastavení platí i pro to, co právě hraje.
        scope.launch { settings.skipSilence.collect { enabled -> engine?.setSkipSilence(enabled) } }
    }

    /**
     * Pustí hlasovku od začátku (nebo od [startMs] — čas v médiu), ať byla vybraná, nebo ne.
     * Tudy jde řetězení neposlechnutých.
     *
     * [resumeKey] (trvalé id souboru) zapne paměť pozice: bez [startMs] se pokračuje, kde
     * uživatel minule přestal, a během poslechu se pozice ukládá — viz [audioStart].
     */
    override fun play(
        key: VoiceKey,
        music: Boolean,
        startMs: Long,
        resumeKey: String?,
        /**
         * Zavolá se po dohrání, SYNCHRONNĚ a ještě před vynulováním stavu — na rozdíl od
         * [completions], které doběhnou až v korutině. [MusicQueue] tak ví, že přehrávání končí
         * kvůli další skladbě, dřív, než uvidí prázdný stav.
         */
        onFinished: (() -> Unit)?,
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
                    // Dohraná nahrávka se zapomene (konec se nepamatuje, viz `resumePosition`).
                    _state.value?.let { savePosition(it.durationMs) }
                    this@VoicePlayer.resumeKey = null
                    // Dohráno není zastaveno: stav se uklidí jako vždy, ale ještě se to ohlásí ven.
                    onFinished?.invoke()
                    stop()
                    _completions.tryEmit(key)
                }
                // Hovor nebo budík hlasovku zastaví; přehrávač o tom sám neví, musí to jít do stavu.
                prepared.onInterrupted = { pause() }
                prepared.setSpeed(speed.value.rate)
                prepared.setSkipSilence(settings.skipSilence.value)
                val start = audioStart(startMs, resumeKey?.let { positions.of(it)?.positionMs }, duration)
                if (start.startMs > 0) prepared.seekTo(start.startMs)
                engine = prepared
                this@VoicePlayer.resumeKey = resumeKey
                _state.value = VoicePlayback(
                    key,
                    music = music,
                    loading = false,
                    durationMs = duration,
                    positionMs = start.startMs,
                    resumedFromMs = start.resumedFromMs,
                )
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
    override fun seek(key: VoiceKey, fraction: Float) {
        val current = _state.value?.takeIf { it.key == key && !it.loading } ?: return
        seekTo(key, (current.durationMs * fraction.coerceIn(0f, 1f)).toLong())
    }

    /** Posun na čas v milisekundách — systémové ovládání (notifikace, zamykací obrazovka). */
    fun seekTo(key: VoiceKey, positionMs: Long) {
        val current = _state.value?.takeIf { it.key == key && !it.loading } ?: return
        val engine = engine ?: return
        val position = positionMs.coerceIn(0L, current.durationMs)
        engine.seekTo(position)
        // Posunem si uživatel místo vybral sám — „pokračuje od…" už neplatí.
        _state.value = current.copy(positionMs = position, resumedFromMs = null)
        // Posun u pozastavené nahrávky se jinak uloží až zastavením — zabitý proces by ho ztratil.
        if (!current.playing) savePosition(position)
    }

    /** Přepne rychlost na další v pořadí. Platí hned, i uprostřed hrající hlasovky. */
    fun cycleSpeed(): VoiceSpeed {
        val next = speed.value.next()
        settings.setVoiceSpeed(next)
        engine?.setSpeed(next.rate)
        return next
    }

    /**
     * Zastaví přehrávání; s [chat] (účet a chat) jen tehdy, když hraje hlasovka z toho chatu.
     *
     * [keepMusic]: hudba se nezastaví — odchod z konverzace ji nechá hrát dál, hlasovku ne
     * (neměla by kde ukazovat průběh a nahrávala by se do cizí obrazovky).
     */
    override fun stop(chat: Pair<AccountSlot, Long>?, keepMusic: Boolean) {
        if (chat != null && _state.value?.key?.isIn(chat.first, chat.second) != true) return
        if (keepMusic && _state.value?.music == true) return
        // Jen u načteného zvuku: zastavení během stahování by zapsalo nulu a pozici tím smazalo.
        engine?.let { savePosition(it.positionMs) }
        resumeKey = null
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

    override fun pause() {
        stopListeningForEar(returnToSpeaker = true)
        engine?.pause()
        tickJob?.cancel()
        _state.update { it?.copy(playing = false, positionMs = engine?.positionMs ?: it.positionMs) }
        engine?.let { savePosition(it.positionMs) }
    }

    /**
     * Uloží pozici hrajícího zvuku ([resumeKey]), nebo ji zapomene (začátek, konec, krátká
     * nahrávka — rozhoduje [eu.tintera.plume.core.video.resumePosition] s prahem pro zvuk).
     */
    private fun savePosition(positionMs: Long) {
        val key = resumeKey ?: return
        val duration = _state.value?.takeIf { !it.loading }?.durationMs ?: return
        positions.record(key, positionMs, duration, MIN_RESUME_AUDIO_DURATION_MS)
    }

    override fun resume() {
        val engine = engine ?: return
        engine.start()
        startListeningForEar()
        _state.update { it?.copy(playing = true) }
        tickJob?.cancel()
        tickJob = scope.launch {
            var savedAt = TimeSource.Monotonic.markNow()
            while (isActive) {
                val position = engine.positionMs
                _state.update { it?.copy(positionMs = position) }
                if (resumeKey != null && savedAt.elapsedNow().inWholeMilliseconds >= AUDIO_POSITION_SAVE_INTERVAL_MS) {
                    savePosition(position)
                    savedAt = TimeSource.Monotonic.markNow()
                }
                delay(POSITION_TICK_MS)
            }
        }
    }
}

private const val POSITION_TICK_MS = 50L
