package eu.tintera.plume.features.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.DownloadsRepository
import eu.tintera.plume.core.telegram.FileDownloadItem
import eu.tintera.plume.core.telegram.FileRepository
import eu.tintera.plume.core.telegram.FileState
import eu.tintera.plume.core.telegram.userMessage
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.document_open_failed
import eu.tintera.plume.resources.downloads_action_failed
import eu.tintera.plume.resources.downloads_load_failed
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DownloadsState(
    /** Stahované a stažené soubory, nejnovější nahoře; `null`, dokud se poprvé nenačtou. */
    val items: List<FileDownloadItem>? = null,
    val error: UiText? = null,
    /** Právě odebrané soubory — obrazovka ukáže snackbar s „Vrátit" a událost spotřebuje. */
    val removal: DownloadRemoval? = null,
) {
    val active: List<FileDownloadItem> get() = items.orEmpty().filterNot { it.isCompleted }
    val completed: List<FileDownloadItem> get() = items.orEmpty().filter { it.isCompleted }

    /** Běží aspoň jedno stahování — „Pozastavit vše" má smysl. */
    val anyRunning: Boolean get() = active.any { !it.isPaused }
}

/** Odebrání čekající na lhůtu „Vrátit" ([PendingDownloadRemovals]); [batch] je dávka pro vrácení. */
data class DownloadRemoval(val batch: Long, val kind: Kind) {
    enum class Kind {
        /** Jeden soubor ze seznamu, soubor zůstává. */
        Removed,

        /** Jeden soubor ze seznamu i s uloženou kopií. */
        Deleted,

        /** „Vymazat stažené": všechna dokončená ze seznamu, soubory zůstávají. */
        Cleared,
    }
}

/**
 * Stahování (menu účtu): seznam souborů, které si uživatel stáhl z chatů, s pozastavením,
 * otevřením staženého souboru a odebráním s „Vrátit". Seznam vede TDLib; po každé změně
 * (přidání, dokončení, pauza) se přečte znovu — průběh jednotlivých souborů si řádky odebírají
 * samy přes `FileRepository`.
 */
@OptIn(FlowPreview::class)
class DownloadsViewModel(
    private val downloads: DownloadsRepository,
    private val files: FileRepository,
    /** Odebrání s „Vrátit"; sdílené s aplikací, aby doběhlo i po odchodu z obrazovky. */
    private val removals: PendingDownloadRemovals,
    /** Předá stažený soubor systému (`FileOpener.open`); `false` = nic ho neotevře. */
    private val openInSystem: (path: String, mimeType: String) -> Boolean,
) : ViewModel() {

    private val logger = Logger.withTag("Downloads")

    /** Seznam, jak ho dala TDLib; ve stavu je bez souborů čekajících na odebrání. */
    private var loaded: List<FileDownloadItem>? = null

    private val _state = MutableStateFlow(DownloadsState())
    val state: StateFlow<DownloadsState> = _state.asStateFlow()

    init {
        reload()
        viewModelScope.launch {
            // Dávka změn (pauza všeho, dokončení víc souborů) = jedno čtení.
            downloads.changes.debounce(RELOAD_DEBOUNCE_MS).collect { reload() }
        }
        viewModelScope.launch { removals.pending.collect { publish() } }
        viewModelScope.launch {
            removals.failures.collect { failure ->
                _state.update { it.copy(error = failure.userMessage(Res.string.downloads_action_failed)) }
            }
        }
    }

    private fun reload() {
        viewModelScope.launch {
            downloads.list()
                .onSuccess { page ->
                    loaded = page.items
                    publish()
                }
                .onFailure { failure ->
                    logger.w(failure) { "Seznam stahování se nepodařilo načíst." }
                    if (loaded == null) loaded = emptyList()
                    publish()
                    _state.update { it.copy(error = UiText(Res.string.downloads_load_failed)) }
                }
        }
    }

    private fun publish() {
        val hidden = removals.pending.value
        _state.update { state -> state.copy(items = loaded?.filterNot { it.fileId in hidden }) }
    }

    fun setPaused(item: FileDownloadItem, paused: Boolean) = act { downloads.setPaused(item.fileId, paused) }

    fun setAllPaused(paused: Boolean) = act { downloads.setAllPaused(paused) }

    /**
     * Otevře stažený soubor v systému. Když na disku už není (vymazaná mezipaměť), zavolá
     * [onMissing] — obrazovka otevře zprávu, odkud se dá stáhnout znovu.
     */
    fun openFile(item: FileDownloadItem, onMissing: () -> Unit) {
        viewModelScope.launch {
            // Jen zjistit stav: soubor z disku, nic se kvůli tomu nestahuje.
            val file = files.observe(item.fileId).first()
            when {
                file !is FileState.Ready -> {
                    logger.d { "Stažený soubor ${item.fileId} na disku není ($file), otevírám zprávu." }
                    onMissing()
                }
                !openInSystem(file.path, item.mimeType) -> _state.update { it.copy(error = UiText(Res.string.document_open_failed)) }
            }
        }
    }

    /** Odebere soubor ze seznamu (s [deleteFile] i uloženou kopii) — až po lhůtě „Vrátit". */
    fun remove(item: FileDownloadItem, deleteFile: Boolean) {
        val batch = removals.schedule(setOf(item.fileId)) { downloads.remove(item.fileId, deleteFile) }
        val kind = if (deleteFile) DownloadRemoval.Kind.Deleted else DownloadRemoval.Kind.Removed
        _state.update { it.copy(removal = DownloadRemoval(batch, kind)) }
    }

    /**
     * „Vymazat stažené" s „Vrátit": odeberou se dokončená, která jsou VIDĚT, po jednom. Hromadné
     * `removeAllFilesFromDownloads` by po lhůtě vzalo i soubor, který se mezitím dostáhl, nebo
     * jehož odebrání uživatel právě vrátil.
     */
    fun clearCompleted() {
        val completed = _state.value.completed.map { it.fileId }
        if (completed.isEmpty()) return
        val batch = removals.schedule(completed.toSet()) {
            completed
                .map { downloads.remove(it, deleteFile = false) }
                .firstOrNull { it.isFailure } ?: Result.success(Unit)
        }
        _state.update { it.copy(removal = DownloadRemoval(batch, DownloadRemoval.Kind.Cleared)) }
    }

    /** „Vrátit" ve snackbaru. */
    fun undoRemoval(removal: DownloadRemoval) = removals.undo(removal.batch)

    fun consumeRemoval() = _state.update { it.copy(removal = null) }

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
