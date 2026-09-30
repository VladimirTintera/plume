package eu.tintera.plume.features.forum

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.ChatInfo
import eu.tintera.plume.core.telegram.ConversationRepository
import eu.tintera.plume.core.telegram.ForumRepository
import eu.tintera.plume.core.telegram.ForumTopic
import eu.tintera.plume.core.telegram.ForumTopicsOffset
import eu.tintera.plume.core.telegram.TOPIC_COLORS
import eu.tintera.plume.core.telegram.TopicRights
import eu.tintera.plume.core.telegram.userMessage
import eu.tintera.plume.core.telegram.withPage
import eu.tintera.plume.core.telegram.withTopic
import eu.tintera.plume.core.telegram.withoutTopic
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.forum_topic_action_failed
import eu.tintera.plume.resources.forum_topic_create_failed
import eu.tintera.plume.resources.forum_topics_load_failed
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.transformLatest
import org.jetbrains.compose.resources.StringResource
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
    /** Co smí přihlášený účet s tématy; dokud nedorazí, nic se nenabízí. */
    val rights: TopicRights = TopicRights(),
    /** Obecné téma, i skryté — správce ho může vrátit (`null` = zatím nenačtené). */
    val general: ForumTopic? = null,
    /** Výsledky hledání podle názvu; `null` = nehledá se. */
    val searchResults: List<ForumTopic>? = null,
    /** Otevřený formulář tématu; `null` = zavřený. */
    val editor: TopicEditor? = null,
    /** Běží akce nad tématem — tlačítka se nesmí zmáčknout podruhé. */
    val busy: Boolean = false,
    /** Jednorázová hláška pro snackbar. */
    val notice: UiText? = null,
    /** Právě založené téma — obrazovka ho otevře a událost vynuluje. */
    val createdTopicId: Int? = null,
)

/** Formulář tématu: nové ([topicId] `null`, s barvou), nebo přejmenování existujícího. */
data class TopicEditor(
    val topicId: Int? = null,
    val name: String = "",
    val color: Int = TOPIC_COLORS.first(),
) {
    val isNew: Boolean get() = topicId == null
    val canSave: Boolean get() = name.isNotBlank()
}

/**
 * Seznam témat skupiny s tématy.
 *
 * Témata se načítají po stránkách a každá změna (nová zpráva, přečtení, přejmenování, uzavření)
 * přečte ZNOVU jen to jedno téma ([ForumRepository.topicChanges]) — stejný vzor jako připnuté
 * zprávy. Změna tématu, které ještě není načtené, ho přidá: nové téma nebo zpráva v tématu
 * z hluboka seznamu ho posune nahoru a má tam být vidět hned.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ForumTopicsViewModel(
    val chatId: Long,
    private val forums: ForumRepository,
    private val conversations: ConversationRepository,
) : ViewModel() {

    private val logger = Logger.withTag("ForumTopics")

    private val _state = MutableStateFlow(ForumTopicsState())
    val state: StateFlow<ForumTopicsState> = _state.asStateFlow()

    private val pageLock = Mutex()
    private val query = MutableStateFlow("")

    init {
        viewModelScope.launch { conversations.chatInfo(chatId).onSuccess { info -> _state.update { it.copy(info = info) } } }
        // Odběr stojí dřív než první stránka, aby se změna mezi nimi neztratila.
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            forums.topicChanges(chatId).collect { topicId -> refresh(topicId) }
        }
        viewModelScope.launch { loadMore() }
        viewModelScope.launch { forums.rights(chatId).onSuccess { rights -> _state.update { it.copy(rights = rights) } } }
        // Hledá server; rozepsaný dotaz další znak zahodí, takže pomalá odpověď na „ko"
        // nepřepíše výsledky pro „kotlin" (stejně jako hledání chatů).
        viewModelScope.launch {
            query.transformLatest { text ->
                if (text.isBlank()) {
                    emit(null)
                } else {
                    delay(SEARCH_DELAY_MS)
                    emit(forums.search(chatId, text.trim()).getOrNull() ?: emptyList())
                }
            }.collect { results -> _state.update { it.copy(searchResults = results) } }
        }
    }

    fun search(text: String) {
        query.value = text
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
                            it.copy(
                                topics = it.topics.withPage(page.topics),
                                next = page.next,
                                loading = false,
                                error = null,
                                general = page.general ?: it.general,
                            )
                        }
                    }
                    .onFailure { failure ->
                        logger.w(failure) { "Témata chatu $chatId nejde načíst." }
                        _state.update { it.copy(loading = false, next = null, error = failure.userMessage(Res.string.forum_topics_load_failed)) }
                    }
            }
        }
    }

    fun openCreate() = _state.update { it.copy(editor = TopicEditor()) }

    fun openRename(topic: ForumTopic) = _state.update { it.copy(editor = TopicEditor(topicId = topic.id, name = topic.name)) }

    fun updateEditor(editor: TopicEditor) = _state.update { it.copy(editor = editor) }

    fun closeEditor() = _state.update { it.copy(editor = null) }

    fun saveEditor() {
        val editor = _state.value.editor?.takeIf { it.canSave } ?: return
        act(if (editor.isNew) Res.string.forum_topic_create_failed else Res.string.forum_topic_action_failed) {
            val topicId = editor.topicId
            if (topicId == null) {
                val created = forums.create(chatId, editor.name, editor.color)
                created.getOrNull()?.let { id ->
                    refresh(id)
                    // Nové téma se otevře, jako u oficiálních klientů — zakládá se, aby se do něj psalo.
                    _state.update { it.copy(editor = null, createdTopicId = id) }
                }
                created.map { }
            } else {
                forums.rename(chatId, topicId, editor.name).also { result ->
                    if (result.isSuccess) {
                        _state.update { it.copy(editor = null) }
                        refresh(topicId)
                    }
                }
            }
        }
    }

    fun createdTopicOpened() = _state.update { it.copy(createdTopicId = null) }

    fun noticeShown() = _state.update { it.copy(notice = null) }

    fun setPinned(topic: ForumTopic, pinned: Boolean) = act(Res.string.forum_topic_action_failed) {
        forums.setPinned(chatId, topic.id, pinned).also { if (it.isSuccess) refresh(topic.id) }
    }

    fun setClosed(topic: ForumTopic, closed: Boolean) = act(Res.string.forum_topic_action_failed) {
        forums.setClosed(chatId, topic.id, closed).also { if (it.isSuccess) refresh(topic.id) }
    }

    fun setMuted(topic: ForumTopic, muted: Boolean) = act(Res.string.forum_topic_action_failed) {
        forums.setMuted(chatId, topic.id, muted).also { if (it.isSuccess) refresh(topic.id) }
    }

    fun delete(topic: ForumTopic) = act(Res.string.forum_topic_action_failed) {
        forums.delete(chatId, topic.id).also { result ->
            if (result.isSuccess) {
                _state.update { state ->
                    state.copy(
                        topics = state.topics.withoutTopic(topic.id),
                        searchResults = state.searchResults?.withoutTopic(topic.id),
                    )
                }
            }
        }
    }

    fun setGeneralHidden(hidden: Boolean) {
        val general = _state.value.general ?: return
        act(Res.string.forum_topic_action_failed) {
            forums.setGeneralHidden(chatId, hidden).also { if (it.isSuccess) refresh(general.id) }
        }
    }

    private fun act(failed: StringResource, action: suspend () -> Result<Unit>) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            action().onFailure { failure ->
                logger.w(failure) { "Akce nad tématem ve skupině $chatId selhala." }
                _state.update { it.copy(notice = failure.userMessage(failed)) }
            }
            _state.update { it.copy(busy = false) }
        }
    }

    private suspend fun refresh(topicId: Int) {
        forums.topic(chatId, topicId)
            .onSuccess { topic ->
                _state.update {
                    it.copy(
                        topics = it.topics.withTopic(topic),
                        general = if (topic.isGeneral) topic else it.general,
                        searchResults = it.searchResults?.map { found -> if (found.id == topic.id) topic else found },
                    )
                }
            }
            // Téma, které se dočíst nedá, bylo smazané.
            .onFailure { _state.update { it.copy(topics = it.topics.withoutTopic(topicId)) } }
    }
}

private const val SEARCH_DELAY_MS = 300L
