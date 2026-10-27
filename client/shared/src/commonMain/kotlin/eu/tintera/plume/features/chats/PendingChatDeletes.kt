package eu.tintera.plume.features.chats

import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.ChatRepository
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
 * **Smazání / opuštění chatu s „Vrátit".** Smazat jde vrátit jen tak, že se smazání ODLOŽÍ: z opuštěné
 * skupiny se zpátky dostane jen pozvánkou a smazanou historii nic nevrátí. Chat proto ze seznamu
 * hned zmizí ([pending], seznam ho schová), snackbar s ubíhajícím odpočtem nabídne „Vrátit"
 * a `deleteOrLeave` se zavolá až po [UNDO_WINDOW_MS] — o chvilku déle, než odpočet běží
 * ([UNDO_COUNTDOWN_MS]), aby „Vrátit" ťuknuté na poslední chvíli ještě stihlo.
 *
 * - **Jedna instance na aplikaci, ve scope aplikace** ([scope]): hlavní seznam i archiv vidí
 *   tytéž čekající chaty a smazání doběhne, i když se archiv mezitím zavře (jeho ViewModel zaniká).
 * - **Zánik procesu ve lhůtě smazání zruší** — chat zůstane. Opačná chyba (smazat, co uživatel
 *   vrátit chtěl) by byla horší.
 * - **Smazaný chat zůstává schovaný ještě [SETTLE_MS] po odpovědi**: update, který ho ze seznamu
 *   odebere, může dorazit až po ní, a chat by mezitím na okamžik vyskočil zpátky.
 * - **Chyba** chat vrátí do seznamu a ohlásí se přes [failures] (snackbar seznamu, který zrovna
 *   běží).
 */
class PendingChatDeletes(
    private val repository: ChatRepository,
    private val scope: CoroutineScope,
) {
    private val logger = Logger.withTag("Chats")

    private val _pending = MutableStateFlow<Set<Long>>(emptySet())

    /** Chaty, jejichž smazání čeká na lhůtu „Vrátit" (nebo právě doběhlo) — seznam je nekreslí. */
    val pending: StateFlow<Set<Long>> = _pending.asStateFlow()

    private val _failures = MutableSharedFlow<Throwable>(extraBufferCapacity = 1)

    /** Smazání, které selhalo; chat je zpátky v seznamu. */
    val failures: SharedFlow<Throwable> = _failures.asSharedFlow()

    private val jobs = mutableMapOf<Long, Job>()
    private var nextBatch = 0L

    /** Odloží smazání [chatIds]; vrátí dávku pro [undo]. */
    fun schedule(chatIds: List<Long>): Long {
        val batch = nextBatch++
        _pending.update { it + chatIds }
        jobs[batch] = scope.launch {
            delay(UNDO_WINDOW_MS)
            jobs.remove(batch)
            val failed = chatIds.filter { id ->
                repository.deleteOrLeave(id)
                    .onFailure { failure ->
                        logger.w(failure) { "Smazání chatu $id selhalo." }
                        _failures.tryEmit(failure)
                    }
                    .isFailure
            }
            // Co selhalo, se vrací hned; smazané až po doběhnutí updatů (viz KDoc třídy).
            _pending.update { it - failed.toSet() }
            delay(SETTLE_MS)
            _pending.update { it - chatIds.toSet() }
        }
        return batch
    }

    /** „Vrátit": smazání dávky se zruší a chaty se vrátí do seznamu. Po lhůtě už nic nedělá. */
    fun undo(batch: Long, chatIds: List<Long>) {
        val job = jobs.remove(batch) ?: return
        job.cancel()
        _pending.update { it - chatIds.toSet() }
    }
}

/** Jak dlouho zůstává smazaný chat schovaný po odpovědi, než ho ze seznamu odebere update. */
private const val SETTLE_MS = 3_000L
