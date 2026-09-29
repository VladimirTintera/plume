package eu.tintera.plume.features.profile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.InviteLinkRepository
import eu.tintera.plume.core.telegram.JoinRequest
import eu.tintera.plume.core.telegram.userMessage
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.join_requests_approved
import eu.tintera.plume.resources.join_requests_declined
import eu.tintera.plume.resources.join_requests_failed
import eu.tintera.plume.resources.join_requests_load_failed
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource

/**
 * Žádosti o vstup do skupiny nebo kanálu: přijmout a odmítnout po jedné i všechny naráz.
 *
 * - **Vyřizovaná žádost z řádku hned zmizí** ([processing]) a seznam se pak přečte znovu —
 *   druhé ťuknutí by poslalo totéž rozhodnutí podruhé a server by odpověděl chybou.
 * - **Seznam se čte znovu i při změně počtu čekajících** (`UpdateChatPendingJoinRequests`):
 *   žádost mohl vyřídit jiný správce nebo si ji uživatel stáhl.
 */
class JoinRequestsViewModel(
    private val chatId: Long,
    private val repository: InviteLinkRepository,
) : ViewModel() {

    private val logger = Logger.withTag("JoinRequests")

    var requests by mutableStateOf<List<JoinRequest>?>(null)
        private set
    var loadFailed by mutableStateOf(false)
        private set

    /** Žádosti, které se právě vyřizují — z obrazovky zmizí hned. */
    var processing by mutableStateOf<Set<Long>>(emptySet())
        private set

    var busy by mutableStateOf(false)
        private set

    /** Jednorázová hláška pro snackbar; obrazovka ji po zobrazení vynuluje. */
    var notice by mutableStateOf<UiText?>(null)

    val shown: List<JoinRequest>? get() = requests?.filterNot { it.userId in processing }

    init {
        reload()
        viewModelScope.launch {
            // První hodnota je současný počet, ten už seznam má.
            repository.pendingJoinRequests(chatId).drop(1).collect { reload() }
        }
    }

    fun reload() {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        repository.joinRequests(chatId)
            .onSuccess {
                requests = it
                loadFailed = false
            }
            .onFailure { failure ->
                if (requests == null) loadFailed = true
                notice = failure.reported(Res.string.join_requests_load_failed)
            }
    }

    fun process(request: JoinRequest, approve: Boolean) {
        if (request.userId in processing) return
        processing = processing + request.userId
        viewModelScope.launch {
            repository.processJoinRequest(chatId, request.userId, approve)
                .onSuccess {
                    notice = UiText(if (approve) Res.string.join_requests_approved else Res.string.join_requests_declined)
                }
                .onFailure { notice = it.reported(Res.string.join_requests_failed) }
            // Až po novém čtení, jinak by vyřízená žádost na okamžik naskočila zpátky.
            load()
            processing = processing - request.userId
        }
    }

    fun processAll(approve: Boolean) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            repository.processAllJoinRequests(chatId, approve)
                .onFailure { notice = it.reported(Res.string.join_requests_failed) }
            load()
            busy = false
        }
    }

    /** Surová hláška od TDLib patří do logu, ne na obrazovku — viz `userMessage`. */
    private fun Throwable.reported(fallback: StringResource): UiText {
        logger.w(this) { "Žádosti o vstup do chatu $chatId: akce selhala." }
        return userMessage(fallback)
    }
}
