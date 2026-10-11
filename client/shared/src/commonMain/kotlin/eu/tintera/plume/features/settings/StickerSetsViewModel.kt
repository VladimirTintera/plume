package eu.tintera.plume.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.tintera.plume.core.telegram.StickerRepository
import eu.tintera.plume.core.telegram.StickerSetSummary
import eu.tintera.plume.core.telegram.userMessage
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.sticker_sets_failed
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StickerSetsState(
    /** Nainstalované sady v pořadí záložek výběru; `null`, dokud se nenačetly. */
    val installed: List<StickerSetSummary>? = null,
    val archived: List<StickerSetSummary> = emptyList(),
    /** Kolik archivovaných sad je celkem — víc, než je načteno, znamená tlačítko „Načíst další". */
    val archivedTotal: Int = 0,
    val loadingMoreArchived: Boolean = false,
    /** Co se právě stalo se sadou — snackbar s „Vrátit". */
    val notice: StickerSetNotice? = null,
    val error: UiText? = null,
) {
    val hasMoreArchived: Boolean get() = archived.size < archivedTotal
}

/**
 * Sada, která právě zmizela z nainstalovaných; „Vrátit" ji tam vrátí — i na její místo
 * ([orderBefore], pořadí sad před změnou).
 */
sealed interface StickerSetNotice {
    val set: StickerSetSummary
    val orderBefore: List<Long>

    data class Archived(override val set: StickerSetSummary, override val orderBefore: List<Long>) : StickerSetNotice
    data class Removed(override val set: StickerSetSummary, override val orderBefore: List<Long>) : StickerSetNotice
}

/**
 * Správa sad samolepek v Nastavení: pořadí tažením, archivace, odebrání a archivované sady.
 *
 * - **Pořadí je i pořadí záložek ve výběru** a platí na všech zařízeních účtu. Během tahu se drží
 *   lokálně ([dragOrder]) a serveru odchází až po puštění, jedním voláním — jako složky chatů.
 *   Na rozdíl od nich se lokální pořadí po úspěchu NEZAHAZUJE hned: vydrží, dokud ho server
 *   nepotvrdí updatem, jinak by seznam na okamžik skočil do starého pořadí.
 * - **Archivace a odebrání se neptají, nabídnou „Vrátit".** Ani jedno nic nesmaže — sada zůstává
 *   na serveru a jde přidat zpět —, takže dialog by byl jen krok navíc.
 * - **Archivované se čtou po stránkách** (TDLib víc než 100 najednou nevrátí) a první stránka
 *   znovu po každé změně nainstalovaných: archivace sadu přesune sem a vrácení odsud.
 */
class StickerSetsViewModel(
    private val repository: StickerRepository,
) : ViewModel() {

    private val server: StateFlow<List<StickerSetSummary>?> = repository.installedSets()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val dragOrder = MutableStateFlow<List<StickerSetSummary>?>(null)
    private val local = MutableStateFlow(StickerSetsState())

    val state: StateFlow<StickerSetsState> = combine(server, dragOrder, local) { installed, drag, base ->
        base.copy(installed = drag ?: installed)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), StickerSetsState())

    /** Pojistka pro případ, že potvrzení pořadí updatem nepřijde. */
    private var dragTimeout: Job? = null

    init {
        viewModelScope.launch {
            server.collect { installed ->
                // Server potvrdil pořadí z tahu — lokální kopie už není potřeba.
                if (installed != null && dragOrder.value?.map { it.id } == installed.map { it.id }) {
                    dragOrder.value = null
                }
            }
        }
        viewModelScope.launch {
            server.map { it?.map { set -> set.id } }.distinctUntilChanged().collect { ids ->
                if (ids != null) reloadArchived()
            }
        }
    }

    fun move(from: Any, to: Any) {
        val current = dragOrder.value ?: server.value ?: return
        val fromIndex = current.indexOfFirst { it.id == from }
        val toIndex = current.indexOfFirst { it.id == to }
        if (fromIndex < 0 || toIndex < 0) return
        dragOrder.value = current.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
    }

    /** Puštěný tah: pořadí odejde serveru, a jen když se opravdu změnilo. */
    fun commitOrder() {
        val order = dragOrder.value ?: return
        if (order.map { it.id } == server.value?.map { it.id }) {
            dragOrder.value = null
            return
        }
        viewModelScope.launch {
            repository.reorderSets(order.map { it.id })
                .onSuccess {
                    dragTimeout?.cancel()
                    dragTimeout = launch {
                        delay(CONFIRM_TIMEOUT_MS)
                        if (dragOrder.value == order) dragOrder.value = null
                    }
                }
                .onFailure { failure ->
                    dragOrder.value = null
                    local.update { it.copy(error = failure.userMessage(Res.string.sticker_sets_failed)) }
                }
        }
    }

    fun archive(set: StickerSetSummary) = change(StickerSetNotice.Archived(set, currentOrder())) {
        repository.setArchived(set.id, archived = true)
    }

    fun remove(set: StickerSetSummary) = change(StickerSetNotice.Removed(set, currentOrder())) {
        repository.setInstalled(set.id, installed = false)
    }

    private fun currentOrder(): List<Long> = server.value.orEmpty().map { it.id }

    /** Archivovanou sadu vrátí mezi nainstalované. */
    fun unarchive(set: StickerSetSummary) = change(notice = null) {
        repository.setArchived(set.id, archived = false)
    }

    /**
     * „Vrátit" ze snackbaru. Vrácená sada by skončila NA ZAČÁTKU (TDLib přidanou sadu řadí
     * první), takže se pak obnoví i pořadí z doby před změnou — jinak by „Vrátit" pořadí rozbilo.
     */
    fun undo(notice: StickerSetNotice) = change(notice = null) {
        when (notice) {
            is StickerSetNotice.Archived -> repository.setArchived(notice.set.id, archived = false)
            is StickerSetNotice.Removed -> repository.setInstalled(notice.set.id, installed = true)
        }.mapCatching { if (notice.orderBefore.isNotEmpty()) repository.reorderSets(notice.orderBefore).getOrThrow() }
    }

    private fun change(notice: StickerSetNotice?, action: suspend () -> Result<Unit>) {
        viewModelScope.launch {
            action()
                .onSuccess { if (notice != null) local.update { it.copy(notice = notice) } }
                .onFailure { failure ->
                    local.update { it.copy(error = failure.userMessage(Res.string.sticker_sets_failed)) }
                }
        }
    }

    fun loadMoreArchived() {
        val current = local.value
        if (current.loadingMoreArchived || !current.hasMoreArchived) return
        val after = current.archived.lastOrNull()?.id ?: return
        local.update { it.copy(loadingMoreArchived = true) }
        viewModelScope.launch {
            repository.archivedSets(afterSetId = after)
                .onSuccess { page ->
                    local.update { state ->
                        val known = state.archived.map { it.id }.toSet()
                        state.copy(
                            archived = state.archived + page.sets.filterNot { it.id in known },
                            // Prázdná stránka = konec, i když server hlásí víc (počet je přibližný).
                            archivedTotal = if (page.sets.isEmpty()) state.archived.size else page.total,
                        )
                    }
                }
            local.update { it.copy(loadingMoreArchived = false) }
        }
    }

    private suspend fun reloadArchived() {
        repository.archivedSets(afterSetId = 0)
            .onSuccess { page -> local.update { it.copy(archived = page.sets, archivedTotal = page.total) } }
    }

    fun consumeNotice() = local.update { it.copy(notice = null) }

    fun consumeError() = local.update { it.copy(error = null) }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L

        /** Jak dlouho čekat na update s novým pořadím, než se lokální pořadí zahodí. */
        const val CONFIRM_TIMEOUT_MS = 3_000L
    }
}
