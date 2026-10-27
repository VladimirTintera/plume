package eu.tintera.plume.features.downloads

import co.touchlab.kermit.Logger
import eu.tintera.plume.core.ui.UNDO_WINDOW_MS
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
import kotlinx.coroutines.launch

/**
 * **Odebrání ze Stahování s „Vrátit".** TDLib odebrání vrátit neumí (znovu přidaný soubor dostane
 * nové datum a smazaný soubor by se stahoval znovu), takže se odebrání ODKLÁDÁ — jako smazání
 * chatu (`PendingChatDeletes`): soubory ze seznamu hned zmizí ([pending]), snackbar s odpočtem
 * nabídne „Vrátit" a akce proběhne až po [UNDO_WINDOW_MS].
 *
 * - **Jedna instance na aplikaci, ve scope aplikace**: odebrání doběhne, i když uživatel ze
 *   Stahování mezitím odejde (ViewModel obrazovky zaniká).
 * - **Zánik procesu ve lhůtě odebrání zruší** — soubor zůstane, i se smazáním souboru.
 * - **Odebrané zůstávají schované ještě [SETTLE_MS] po odpovědi**: seznam se čte znovu až po
 *   updatu od TDLib a soubor by mezitím na okamžik vyskočil zpátky.
 * - **Chyba** soubory vrátí a ohlásí se přes [failures].
 */
class PendingDownloadRemovals(private val scope: CoroutineScope) {

    private val logger = Logger.withTag("Downloads")

    private val _pending = MutableStateFlow<Set<Int>>(emptySet())

    /** Soubory (`fileId`), jejichž odebrání čeká na lhůtu „Vrátit" (nebo právě doběhlo). */
    val pending: StateFlow<Set<Int>> = _pending.asStateFlow()

    private val _failures = MutableSharedFlow<Throwable>(extraBufferCapacity = 1)

    /** Odebrání, které selhalo; soubory jsou zpátky v seznamu. */
    val failures: SharedFlow<Throwable> = _failures.asSharedFlow()

    private val batches = mutableMapOf<Long, Pair<Job, Set<Int>>>()
    private var nextBatch = 0L

    /**
     * Schová [fileIds] a po lhůtě zavolá [action] (odebrání jednoho souboru, vymazání stažených).
     * Vrátí dávku pro [undo].
     */
    fun schedule(fileIds: Set<Int>, action: suspend () -> Result<Unit>): Long {
        val batch = nextBatch++
        _pending.update { it + fileIds }
        val job = scope.launch {
            delay(UNDO_WINDOW_MS)
            batches.remove(batch)
            val failure = action().exceptionOrNull()
            if (failure != null) {
                logger.w(failure) { "Odebrání ze Stahování selhalo." }
                _failures.tryEmit(failure)
                _pending.update { it - fileIds }
                return@launch
            }
            delay(SETTLE_MS)
            _pending.update { it - fileIds }
        }
        batches[batch] = job to fileIds
        return batch
    }

    /** „Vrátit": odebrání dávky se zruší a soubory se vrátí do seznamu. Po lhůtě už nic nedělá. */
    fun undo(batch: Long) {
        val (job, fileIds) = batches.remove(batch) ?: return
        job.cancel()
        _pending.update { it - fileIds }
    }
}

/** Jak dlouho zůstává odebraný soubor schovaný po odpovědi, než seznam dorazí bez něj. */
private const val SETTLE_MS = 3_000L
