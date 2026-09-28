package eu.tintera.plume.features.forum

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.ChatInfo
import eu.tintera.plume.core.telegram.ConversationRepository
import eu.tintera.plume.core.telegram.ForumRepository
import eu.tintera.plume.core.telegram.ForumTopic
import eu.tintera.plume.core.telegram.ForumTopicsOffset
import eu.tintera.plume.core.telegram.userMessage
import eu.tintera.plume.core.telegram.withPage
import eu.tintera.plume.core.telegram.withTopic
import eu.tintera.plume.core.telegram.withoutTopic
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.forum_topics_load_failed
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class ForumTopicsState(
    /** Hlavička: název a fotka skupiny. */
    val info: ChatInfo? = null,
    val topics: List<ForumTopic> = emptyList(),
    /** První stránka ještě nedorazila. */
    val loading: Boolean = true,
    /** Kde pokračuje další stránka; `null` = všechno načtené. */
    val next: ForumTopicsOffset? = null,
    val error: UiText? = null,
)

/**
 * Seznam témat skupiny s tématy.
 *
 * Témata se načítají po stránkách a každá změna (nová zpráva, přečtení, přejmenování, uzavření)
 * přečte ZNOVU jen to jedno téma ([ForumRepository.topicChanges]) — stejný vzor jako připnuté
 * zprávy. Změna tématu, které ještě není načtené, ho přidá: nové téma nebo zpráva v tématu
 * z hluboka seznamu ho posune nahoru a má tam být vidět hned.
 */
class ForumTopicsViewModel(
    val chatId: Long,
    private val forums: ForumRepository,
    private val conversations: ConversationRepository,
) : ViewModel() {

    private val logger = Logger.withTag("ForumTopics")

    private val _state = MutableStateFlow(ForumTopicsState())
    val state: StateFlow<ForumTopicsState> = _state.asStateFlow()

    private val pageLock = Mutex()

    init {
        viewModelScope.launch { conversations.chatInfo(chatId).onSuccess { info -> _state.update { it.copy(info = info) } } }
        // Odběr stojí dřív než první stránka, aby se změna mezi nimi neztratila.
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            forums.topicChanges(chatId).collect { topicId -> refresh(topicId) }
        }
        viewModelScope.launch { loadMore() }
    }

    /** Další stránka témat; volá ji seznam, když se blíží ke konci. */
    fun loadMore() {
        viewModelScope.launch {
            pageLock.withLock {
                val current = _state.value
                if (!current.loading && current.next == null) return@withLock
                forums.topics(chatId, current.next)
                    .onSuccess { page ->
                        _state.update {
                            it.copy(topics = it.topics.withPage(page.topics), next = page.next, loading = false, error = null)
                        }
                    }
                    .onFailure { failure ->
                        logger.w(failure) { "Témata chatu $chatId nejde načíst." }
                        _state.update { it.copy(loading = false, next = null, error = failure.userMessage(Res.string.forum_topics_load_failed)) }
                    }
            }
        }
    }

    private suspend fun refresh(topicId: Int) {
        forums.topic(chatId, topicId)
            .onSuccess { topic -> _state.update { it.copy(topics = it.topics.withTopic(topic)) } }
            // Téma, které se dočíst nedá, bylo smazané.
            .onFailure { _state.update { it.copy(topics = it.topics.withoutTopic(topicId)) } }
    }
}
