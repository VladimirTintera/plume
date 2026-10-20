package eu.tintera.plume.features.calls

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.tintera.plume.core.telegram.CallHistoryEntry
import eu.tintera.plume.core.telegram.CallPhase
import eu.tintera.plume.core.telegram.CallRepository
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.calls_delete_failed
import eu.tintera.plume.resources.calls_load_failed
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CallsState(
    /** Načtené hovory; `null`, dokud nedorazí první stránka. */
    val entries: List<CallHistoryEntry>? = null,
    /** Jen zmeškané a odmítnuté. */
    val onlyMissed: Boolean = false,
    val loadingMore: Boolean = false,
    /** Další stránka už není. */
    val endReached: Boolean = false,
    val deleting: Boolean = false,
    val error: UiText? = null,
)

/**
 * Historie hovorů napříč chaty (Hovory z menu účtu). Stránkuje se od nejnovějšího
 * (`searchCallMessages`); přepnutí na zmeškané čte od začátku.
 *
 * - **Po skončeném hovoru se čte znovu**: nový hovor přibude nahoře bez znovuotevření.
 * - **Přepnutí filtru zahodí rozběhnuté čtení** ([generation]) — stránka staršího filtru by se
 *   jinak přilepila k novému.
 */
class CallsViewModel(
    private val chats: ChatRepository,
    calls: CallRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CallsState())
    val state: StateFlow<CallsState> = _state.asStateFlow()

    private var nextOffset = ""
    private var generation = 0
    private var loading: Job? = null

    init {
        reload()
        viewModelScope.launch {
            calls.active
                .map { it?.phase == CallPhase.Ended || it?.phase == CallPhase.Failed }
                .distinctUntilChanged()
                .drop(1)
                .filter { it }
                .collect { reload() }
        }
    }

    fun setOnlyMissed(onlyMissed: Boolean) {
        if (_state.value.onlyMissed == onlyMissed) return
        _state.update { it.copy(onlyMissed = onlyMissed) }
        reload()
    }

    /** Od začátku: první otevření, změna filtru, skončený hovor. Načtené zůstává do odpovědi. */
    fun reload() {
        generation++
        loading?.cancel()
        nextOffset = ""
        _state.update { it.copy(endReached = false, loadingMore = false) }
        loadPage(replace = true)
    }

    /** Další stránka, když seznam dojel ke konci. */
    fun loadMore() {
        val current = _state.value
        if (current.entries == null || current.loadingMore || current.endReached || loading?.isActive == true) return
        loadPage(replace = false)
    }

    private fun loadPage(replace: Boolean) {
        val sentIn = generation
        val onlyMissed = _state.value.onlyMissed
        _state.update { it.copy(loadingMore = !replace) }
        loading = viewModelScope.launch {
            val result = chats.callHistory(offset = nextOffset, onlyMissed = onlyMissed)
            if (sentIn != generation) return@launch
            result
                .onSuccess { page ->
                    nextOffset = page.nextOffset
                    _state.update { state ->
                        val known = if (replace) emptyList() else state.entries.orEmpty()
                        // Tentýž hovor na hranici dvou stránek jen jednou (klíč seznamu).
                        val merged = (known + page.entries).distinctBy { it.chat.id to it.messageId }
                        state.copy(entries = merged, loadingMore = false, endReached = page.nextOffset.isEmpty())
                    }
                }
                .onFailure {
                    _state.update { state ->
                        state.copy(entries = state.entries ?: emptyList(), loadingMore = false, error = UiText(Res.string.calls_load_failed))
                    }
                }
        }
    }

    /** Smazat celou historii; [revoke] = i protějškům. Seznam se pak přečte znovu (prázdný). */
    fun deleteAll(revoke: Boolean) {
        if (_state.value.deleting) return
        _state.update { it.copy(deleting = true) }
        viewModelScope.launch {
            chats.deleteCallHistory(revoke)
                .onSuccess {
                    _state.update { it.copy(deleting = false, entries = emptyList(), endReached = true) }
                    reload()
                }
                .onFailure { _state.update { it.copy(deleting = false, error = UiText(Res.string.calls_delete_failed)) } }
        }
    }

    fun consumeError() = _state.update { it.copy(error = null) }
}
