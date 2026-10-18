package eu.tintera.plume.features.direct

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.ChatInfo
import eu.tintera.plume.core.telegram.ConversationRepository
import eu.tintera.plume.core.telegram.DirectTopic
import eu.tintera.plume.core.telegram.DirectTopicsRepository
import eu.tintera.plume.core.telegram.userMessage
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.direct_topic_action_failed
import eu.tintera.plume.resources.direct_topics_load_failed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DirectTopicsState(
    /** Chat přímých zpráv kvůli hlavičce (název a fotka kanálu). */
    val info: ChatInfo? = null,
    /** Vlákna v pořadí od TDLib; `null`, dokud se poprvé nenačtou. */
    val topics: List<DirectTopic>? = null,
    val error: UiText? = null,
    /** Jednorázová hláška pro snackbar. */
    val notice: UiText? = null,
)

/**
 * Přímé zprávy kanálu po odběratelích. Seznam je živý tok z repository — vlákna posílá TDLib jen
 * updaty, takže se nic neskládá lokálně; smazání i značku nepřečtení potvrdí update.
 */
class DirectTopicsViewModel(
    val chatId: Long,
    private val repository: DirectTopicsRepository,
    private val conversations: ConversationRepository,
) : ViewModel() {

    private val logger = Logger.withTag("DirectTopics")

    private val local = MutableStateFlow(DirectTopicsState())

    val state: StateFlow<DirectTopicsState> = combine(local, repository.topics(chatId)) { local, topics ->
        local.copy(topics = topics)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DirectTopicsState())

    init {
        viewModelScope.launch { conversations.chatInfo(chatId).onSuccess { info -> local.update { it.copy(info = info) } } }
        // Načítá se při každém otevření: po startu aplikace TDLib vlákna sám nepošle.
        viewModelScope.launch {
            repository.load(chatId).onFailure { failure ->
                logger.w(failure) { "Vlákna přímých zpráv se nenačetla." }
                local.update { it.copy(error = UiText(Res.string.direct_topics_load_failed)) }
            }
        }
    }

    fun setMarkedAsUnread(topic: DirectTopic, unread: Boolean) =
        act { repository.setMarkedAsUnread(chatId, topic.id, unread) }

    fun delete(topic: DirectTopic) = act { repository.deleteHistory(chatId, topic.id) }

    fun noticeShown() = local.update { it.copy(notice = null) }

    private fun act(action: suspend () -> Result<Unit>) {
        viewModelScope.launch {
            action().onFailure { failure ->
                logger.w(failure) { "Akce nad vláknem přímých zpráv selhala." }
                local.update { it.copy(notice = failure.userMessage(Res.string.direct_topic_action_failed)) }
            }
        }
    }
}
