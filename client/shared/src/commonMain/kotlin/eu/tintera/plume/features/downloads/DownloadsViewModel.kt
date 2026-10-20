package eu.tintera.plume.features.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.DownloadsRepository
import eu.tintera.plume.core.telegram.FileDownloadItem
import eu.tintera.plume.core.telegram.userMessage
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.downloads_action_failed
import eu.tintera.plume.resources.downloads_load_failed
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DownloadsState(
    /** Stahované a stažené soubory, nejnovější nahoře; `null`, dokud se poprvé nenačtou. */
    val items: List<FileDownloadItem>? = null,
    val error: UiText? = null,
) {
    val active: List<FileDownloadItem> get() = items.orEmpty().filterNot { it.isCompleted }
    val completed: List<FileDownloadItem> get() = items.orEmpty().filter { it.isCompleted }

    /** Běží aspoň jedno stahování — „Pozastavit vše" má smysl. */
    val anyRunning: Boolean get() = active.any { !it.isPaused }
}

/**
 * Stahování (menu účtu): seznam souborů, které si uživatel stáhl z chatů, s pozastavením
 * a odebráním. Seznam vede TDLib; po každé změně (přidání, dokončení, pauza) se přečte znovu —
 * průběh jednotlivých souborů si řádky odebírají samy přes `FileRepository`.
 */
@OptIn(FlowPreview::class)
class DownloadsViewModel(private val downloads: DownloadsRepository) : ViewModel() {

    private val logger = Logger.withTag("Downloads")

    private val _state = MutableStateFlow(DownloadsState())
    val state: StateFlow<DownloadsState> = _state.asStateFlow()

    init {
        reload()
        viewModelScope.launch {
            // Dávka změn (pauza všeho, dokončení víc souborů) = jedno čtení.
            downloads.changes.debounce(RELOAD_DEBOUNCE_MS).collect { reload() }
        }
    }

    private fun reload() {
        viewModelScope.launch {
            downloads.list()
                .onSuccess { page -> _state.update { it.copy(items = page.items) } }
                .onFailure { failure ->
                    logger.w(failure) { "Seznam stahování se nepodařilo načíst." }
                    _state.update { it.copy(items = it.items ?: emptyList(), error = UiText(Res.string.downloads_load_failed)) }
                }
        }
    }

    fun setPaused(item: FileDownloadItem, paused: Boolean) = act { downloads.setPaused(item.fileId, paused) }

    fun setAllPaused(paused: Boolean) = act { downloads.setAllPaused(paused) }

    fun remove(item: FileDownloadItem, deleteFile: Boolean) = act { downloads.remove(item.fileId, deleteFile) }

    fun clearCompleted() = act { downloads.clearCompleted() }

    fun consumeError() = _state.update { it.copy(error = null) }

    private fun act(action: suspend () -> Result<Unit>) {
        viewModelScope.launch {
            action().onFailure { failure ->
                logger.w(failure) { "Akce nad seznamem stahování selhala." }
                _state.update { it.copy(error = failure.userMessage(Res.string.downloads_action_failed)) }
            }
        }
    }
}

private const val RELOAD_DEBOUNCE_MS = 300L
