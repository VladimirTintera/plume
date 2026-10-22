package eu.tintera.plume.core.audio

import androidx.compose.runtime.Composable
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.TimeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path.Companion.toPath

/**
 * Jedno nahrávání do souboru — hlasovka z mikrofonu ([AudioRecorder]) nebo video kolečko
 * z kamery (`VideoNoteCamera`). Čas, pauzy, úrovně a zahození krátké nahrávky řeší nad ním
 * [VoiceRecorder] jednou pro oba druhy.
 */
interface MediaCapture {
    /** Začne nahrávat do nového souboru. Vyhodí výjimku, když mikrofon (kamera) nejde otevřít. */
    fun start()

    /** Hlasitost 0..1 od posledního čtení; čte se opakovaně během nahrávání. */
    val level: Float

    /** Ukončí nahrávání a vrátí cestu k souboru připravenému k odeslání. */
    suspend fun stop(): String

    /** Zahodí rozpracovanou nahrávku i se souborem. */
    fun cancel()

    /** Pozastaví nahrávání; soubor zůstává rozpracovaný a [resume] na něj naváže. */
    fun pause()

    fun resume()

    /**
     * Soubor, do kterého se PRÁVĚ nahrává a který bude i výsledkem [stop], dokud nahrávání běží
     * vcelku (bez pauzy). Podle něj se hlasovka nahrává do Telegramu už během nahrávání
     * (`LiveUploads`). `null` = nahrávač soubor dopisuje až na konci (iOS kóduje po skončení,
     * MP4 dopíše index) a nahrávat předem nemá smysl.
     */
    val livePath: String? get() = null

    /**
     * Jde za hotovou nahrávku [path] připojit další úsek ([join]) — nahrávání po poslechu
     * náhledu. Výchozí je Ogg/Opus z mikrofonu; M4A z Androidu 9 a staršího spojit neumíme.
     */
    fun canJoin(path: String): Boolean = path.endsWith(".ogg")

    /**
     * Připojí nahrávku [next] za [previous] a vrátí cestu k výsledku; [previous] se smaže.
     * Výchozí spojuje Ogg/Opus ([joinOggOpus]) a výsledek zapíše do [next]. Kamera spojuje MP4
     * po svém (přes kodér platformy), proto je to člen, ne funkce [VoiceRecorder].
     */
    suspend fun join(previous: String, next: String): String = withContext(Dispatchers.Default) {
        val fileSystem = FileSystem.SYSTEM
        val first = fileSystem.read(previous.toPath()) { readByteArray() }
        val second = fileSystem.read(next.toPath()) { readByteArray() }
        val joined = joinOggOpus(listOf(first, second))
        fileSystem.write(next.toPath()) { write(joined) }
        fileSystem.delete(previous.toPath(), mustExist = false)
        next
    }
}

/**
 * Nahrávání z mikrofonu — platformní část.
 *
 * Výsledek musí být soubor, který Telegram přijme jako hlasovku: Ogg/Opus mono, případně M4A.
 * Android 10+ nahrává Ogg/Opus rovnou, starší M4A. iOS nahraje PCM a po skončení ho zakóduje
 * systémovým Opusem a zabalí do Oggu ([oggOpusFile]).
 */
expect class AudioRecorder : MediaCapture {
    override fun start()

    override val level: Float

    override suspend fun stop(): String

    override fun cancel()

    override fun pause()

    override fun resume()
}

/** Oprávnění k mikrofonu. [request] se nejdřív zeptá systému; výsledek dostane v callbacku. */
interface RecordPermission {
    fun request(onResult: (granted: Boolean) -> Unit)
}

@Composable
expect fun rememberRecordPermission(): RecordPermission

/** Probíhající nahrávání, jak ho kreslí vstupní pole. */
data class VoiceRecording(
    val elapsedMs: Long,
    /** Posledních pár úrovní hlasitosti (0..1), nejnovější poslední. */
    val recentLevels: List<Float>,
)

/**
 * Hotová nahrávka k odeslání.
 *
 * [levels] jsou surové úrovně hlasitosti, ze kterých je [waveform] — drží se kvůli navázání
 * ([VoiceRecorder.start] s `continuing`): waveform se normuje na špičku CELÉ nahrávky, takže
 * se musí spočítat znovu ze všech úrovní, ne slepit ze dvou hotových. Jestli navázat jde,
 * říká [VoiceRecorder.canContinue] — rozhoduje druh nahrávače, ne nahrávka.
 */
class RecordedVoice(
    val path: String,
    val durationSeconds: Int,
    val waveform: ByteArray,
    val durationMs: Long = durationSeconds * 1000L,
    val levels: List<Float> = emptyList(),
    /** Soubor už nahraný do Telegramu během nahrávání (`LiveUploads`); `null` = posílá se [path]. */
    val uploadedFileId: Int? = null,
)

/**
 * Řídí jedno nahrávání: čas, úrovně hlasitosti pro živou vizualizaci i waveform, a hotový soubor.
 *
 * Úrovně se čtou po [LEVEL_TICK_MS]; všechny se drží na waveform (minuta je 1 200 čísel), do stavu
 * jde jen konec. Nahrávka kratší než [MIN_DURATION_MS] se zahodí — je to skoro vždycky omyl.
 *
 * **Není vláknově bezpečný:** volá se z jednoho vlákna a [scope] musí běžet na témž (v aplikaci
 * `viewModelScope`). Tik úrovní na jiném vlákně by po [finish] mohl zapsat stav zpátky
 * a další [start] by pak nic nespustil.
 */
class VoiceRecorder(private val recorder: MediaCapture, private val scope: CoroutineScope) {

    private val _state = MutableStateFlow<VoiceRecording?>(null)
    val state: StateFlow<VoiceRecording?> = _state.asStateFlow()

    private var tickJob: Job? = null
    private val levels = mutableListOf<Float>()

    /** Začátek běžícího úseku; s pauzami se nahraný čas přičítá do [recordedMs]. */
    private var startedAt: TimeSource.Monotonic.ValueTimeMark? = null
    private var recordedMs = 0L
    private var paused = false

    /** Nahrávka, na kterou se navazuje (pokračování po poslechu náhledu); `null` = nová. */
    private var prefix: RecordedVoice? = null
    private val logger = Logger.withTag("VoiceRecorder")

    /**
     * Začne nahrávat. S [continuing] naváže na hotovou nahrávku: čas i úrovně běží od jejího
     * konce a [finish] oba úseky spojí do jednoho souboru. Nahrávka, na kterou nejde navázat
     * ([canContinue]), se bere jako žádná.
     */
    fun start(continuing: RecordedVoice? = null): Result<Unit> {
        if (_state.value != null) return Result.success(Unit)
        val previous = continuing?.takeIf(::canContinue)
        return runCatching { recorder.start() }
            .onSuccess {
                levels.clear()
                previous?.let { levels += it.levels }
                prefix = previous
                recordedMs = previous?.durationMs ?: 0
                startedAt = TimeSource.Monotonic.markNow()
                _state.value = VoiceRecording(elapsedMs = recordedMs, recentLevels = levels.takeLast(RECENT_LEVELS))
                tick()
            }
            .onFailure { failure ->
                logger.w(failure) { "Nahrávání nejde spustit." }
                recorder.cancel()
            }
    }

    /** Jde na hotovou [recorded] navázat dalším nahráváním ([MediaCapture.canJoin]). */
    fun canContinue(recorded: RecordedVoice): Boolean = recorder.canJoin(recorded.path)

    /**
     * Pozastaví nahrávání — třeba po dobu nabídky odeslání a výběru času, kdy by se jinak do
     * hlasovky nahrálo šustění při vybírání. Čas pauzy se do délky nepočítá a úrovně se nesbírají.
     */
    fun pause() {
        val mark = startedAt ?: return
        if (paused) return
        runCatching { recorder.pause() }
            .onFailure { failure ->
                logger.w(failure) { "Nahrávání nejde pozastavit." }
                return
            }
        recordedMs += mark.elapsedNow().inWholeMilliseconds
        paused = true
        tickJob?.cancel()
        tickJob = null
    }

    /** Naváže na pozastavené nahrávání. */
    fun resume() {
        if (startedAt == null || !paused) return
        runCatching { recorder.resume() }
            .onFailure { failure -> logger.w(failure) { "Nahrávání nejde obnovit." } }
        paused = false
        startedAt = TimeSource.Monotonic.markNow()
        tick()
    }

    private fun elapsedMs(): Long =
        recordedMs + (startedAt?.takeUnless { paused }?.elapsedNow()?.inWholeMilliseconds ?: 0L)

    private fun tick() {
        tickJob = scope.launch {
            while (isActive) {
                delay(LEVEL_TICK_MS)
                levels += recorder.level
                _state.value = VoiceRecording(
                    elapsedMs = elapsedMs(),
                    recentLevels = levels.takeLast(RECENT_LEVELS),
                )
            }
        }
    }

    /** Ukončí nahrávání. `null`, když nic neběželo, nahrávka je moc krátká, nebo selhala. */
    suspend fun finish(): RecordedVoice? {
        if (startedAt == null) return null
        val elapsed = elapsedMs()
        val previous = prefix
        val allLevels = levels.toList()
        reset()

        if (previous != null) return finishContinuation(previous, elapsed, allLevels)
        if (elapsed < MIN_DURATION_MS) {
            recorder.cancel()
            return null
        }
        return runCatching { recorder.stop() }
            .onFailure { failure -> logger.w(failure) { "Nahrávku nejde dokončit." } }
            .getOrNull()
            ?.let { path -> recorded(path, elapsed, allLevels) }
    }

    /**
     * Konec navázaného nahrávání: nový úsek se připojí za [previous] ([MediaCapture.join]). Když nový úsek nic nenese (ťuknutí hned po spuštění) nebo se
     * spojit nedá, zůstane aspoň původní nahrávka — tu už uživatel jednou slyšel a chtěl.
     */
    private suspend fun finishContinuation(previous: RecordedVoice, elapsed: Long, allLevels: List<Float>): RecordedVoice {
        if (elapsed - previous.durationMs < MIN_SEGMENT_MS) {
            recorder.cancel()
            return previous
        }
        val path = runCatching { recorder.stop() }
            .onFailure { failure -> logger.w(failure) { "Navázaný úsek nahrávky nejde dokončit." } }
            .getOrNull() ?: return previous
        return runCatching { recorded(recorder.join(previous.path, path), elapsed, allLevels) }
            .getOrElse { failure ->
                logger.w(failure) { "Navázaný úsek nejde připojit k nahrávce." }
                runCatching { FileSystem.SYSTEM.delete(path.toPath(), mustExist = false) }
                previous
            }
    }

    private fun recorded(path: String, elapsed: Long, allLevels: List<Float>) = RecordedVoice(
        path = path,
        durationSeconds = ((elapsed + 500) / 1000).toInt(),
        waveform = encodeWaveform(allLevels),
        durationMs = elapsed,
        levels = allLevels,
    )

    /**
     * Zahodí nahrávku. U navázaného nahrávání i tu, na kterou se navazovalo: koš v pruhu
     * nahrávání zahazuje celou hlasovku, ne jen dovětek.
     */
    fun cancel() {
        if (startedAt == null) return
        val previous = prefix
        reset()
        recorder.cancel()
        previous?.let { voice ->
            runCatching { FileSystem.SYSTEM.delete(voice.path.toPath(), mustExist = false) }
                .onFailure { failure -> logger.w(failure) { "Zahozenou nahrávku nejde smazat." } }
        }
    }

    private fun reset() {
        tickJob?.cancel()
        tickJob = null
        startedAt = null
        recordedMs = 0
        paused = false
        prefix = null
        _state.value = null
    }
}

private const val LEVEL_TICK_MS = 50L
private const val RECENT_LEVELS = 40

/**
 * Navázaný úsek kratší než tohle se zahodí a zůstane původní nahrávka: `MediaRecorder` po tak
 * krátkém nahrávání soubor nedokončí a na dovětek pod čtvrt vteřiny stejně nikdo nemluvil.
 */
private const val MIN_SEGMENT_MS = 300L

/** Kratší nahrávka je skoro jistě omylem ťuknuté tlačítko. */
const val MIN_DURATION_MS = 1000L
