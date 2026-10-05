package eu.tintera.plume.features.saved

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.settings.AppSettings
import eu.tintera.plume.core.telegram.SavedTopic
import eu.tintera.plume.core.telegram.SavedTopicsRepository
import eu.tintera.plume.core.telegram.userMessage
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.saved_topic_action_failed
import eu.tintera.plume.resources.saved_topics_load_failed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SavedTopicsState(
    /** Témata v pořadí od TDLib; `null`, dokud se poprvé nenačtou. */
    val topics: List<SavedTopic>? = null,
    val error: UiText? = null,
    /** Jednorázová hláška pro snackbar. */
    val notice: UiText? = null,
)

/**
 * Uložené zprávy podle zdrojového chatu. Seznam je živý tok z repository — TDLib témata posílá
 * jen updaty, takže se nic neskládá ani nepřepisuje lokálně; připnutí a smazání potvrdí update.
 */
class SavedTopicsViewModel(
    val chatId: Long,
    private val repository: SavedTopicsRepository,
    private val settings: AppSettings,
) : ViewModel() {

    private val logger = Logger.withTag("SavedTopics")

    private val local = MutableStateFlow(SavedTopicsState())

    val state: StateFlow<SavedTopicsState> = combine(local, repository.topics) { local, topics ->
        local.copy(topics = topics)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SavedTopicsState(topics = repository.topics.value))

    init {
        // Načítá se při každém otevření: po startu aplikace TDLib témata sám nepošle.
        viewModelScope.launch {
            repository.load().onFailure { failure ->
                logger.w(failure) { "Témata Uložených zpráv se nenačetla." }
                local.update { it.copy(error = UiText(Res.string.saved_topics_load_failed)) }
            }
        }
    }

    /** Zpátky na jednu konverzaci; volba se pamatuje. */
    fun viewAsMessages() = settings.setSavedMessagesAsChats(false)

    fun setPinned(topic: SavedTopic, pinned: Boolean) = act { repository.setPinned(topic.id, pinned) }

    fun delete(topic: SavedTopic) = act { repository.deleteHistory(topic.id) }

    fun noticeShown() = local.update { it.copy(notice = null) }

    private fun act(action: suspend () -> Result<Unit>) {
        viewModelScope.launch {
            action().onFailure { failure ->
                logger.w(failure) { "Akce nad tématem Uložených zpráv selhala." }
                local.update { it.copy(notice = failure.userMessage(Res.string.saved_topic_action_failed)) }
            }
        }
    }
}
