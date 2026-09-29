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

/** Hotová nahrávka k odeslání. */
class RecordedVoice(val path: String, val durationSeconds: Int, val waveform: ByteArray)

/**
 * Řídí jedno nahrávání: čas, úrovně hlasitosti pro živou vizualizaci i waveform, a hotový soubor.
 *
 * Úrovně se čtou po [LEVEL_TICK_MS]; všechny se drží na waveform (minuta je 1 200 čísel), do stavu
 * jde jen konec. Nahrávka kratší než [MIN_DURATION_MS] se zahodí — je to skoro vždycky omyl.
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
    private val logger = Logger.withTag("VoiceRecorder")

    fun start(): Result<Unit> {
        if (_state.value != null) return Result.success(Unit)
        return runCatching { recorder.start() }
            .onSuccess {
                levels.clear()
                recordedMs = 0
                startedAt = TimeSource.Monotonic.markNow()
                _state.value = VoiceRecording(elapsedMs = 0, recentLevels = emptyList())
                tick()
            }
            .onFailure { failure ->
                logger.w(failure) { "Nahrávání nejde spustit." }
                recorder.cancel()
            }
    }

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
        reset()

        if (elapsed < MIN_DURATION_MS) {
            recorder.cancel()
            return null
        }
        return runCatching { recorder.stop() }
            .onFailure { failure -> logger.w(failure) { "Nahrávku nejde dokončit." } }
            .getOrNull()
            ?.let { path ->
                RecordedVoice(
                    path = path,
                    durationSeconds = ((elapsed + 500) / 1000).toInt(),
                    waveform = encodeWaveform(levels.toList()),
                )
            }
    }

    fun cancel() {
        if (startedAt == null) return
        reset()
        recorder.cancel()
    }

    private fun reset() {
        tickJob?.cancel()
        tickJob = null
        startedAt = null
        recordedMs = 0
        paused = false
        _state.value = null
    }
}

private const val LEVEL_TICK_MS = 50L
private const val RECENT_LEVELS = 40

/** Kratší nahrávka je skoro jistě omylem ťuknuté tlačítko. */
const val MIN_DURATION_MS = 1000L
