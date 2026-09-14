package eu.tintera.plume.features.conversation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.tintera.plume.core.telegram.ChatInfo
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.ConversationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Stav a stránkování jedné konverzace.
 *
 * Zprávy se drží **seřazené od nejnovější po nejstarší**, protože stejně je vykresluje
 * `LazyColumn(reverseLayout = true)` — nejnovější dole, stránkování nahoru. Kdyby se držely
 * opačně, každé přidání staré stránky by znamenalo přepočet celého seznamu.
 */
class ConversationViewModel(
    private val chatId: Long,
    private val repository: ConversationRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ConversationState())
    val state: StateFlow<ConversationState> = _state.asStateFlow()

    /** Serializuje stránkování — bez toho by dva rychlé scrolly načetly tutéž stránku dvakrát. */
    private val loadMutex = Mutex()

    init {
        viewModelScope.launch {
            repository.chatInfo(chatId).onSuccess { info ->
                _state.update { it.copy(info = info) }
            }
        }

        loadOlder()
        observeUpdates()
    }

    /**
     * Načte další stránku směrem do minulosti.
     *
     * Konec historie se pozná jen podle prázdné odpovědi — TDLib žádný příznak „jsi na začátku"
     * nedává. Proto [ConversationState.reachedStart].
     */
    fun loadOlder() {
        val current = _state.value
        if (current.loading || current.reachedStart) return

        viewModelScope.launch {
            loadMutex.withLock {
                if (_state.value.loading || _state.value.reachedStart) return@withLock
                _state.update { it.copy(loading = true) }

                // Nula znamená „od nejnovější"; jinak navazujeme pod nejstarší už načtenou zprávou.
                val from = _state.value.messages.lastOrNull()?.id ?: 0L

                repository.history(chatId = chatId, fromMessageId = from)
                    .onSuccess { page ->
                        _state.update { state ->
                            state.copy(
                                messages = merge(state.messages, page),
                                loading = false,
                                reachedStart = page.isEmpty(),
                                error = null,
                            )
                        }
                    }
                    .onFailure { failure ->
                        _state.update {
                            it.copy(loading = false, error = failure.message ?: "Historii se nepodařilo načíst.")
                        }
                    }
            }
        }
    }

    private fun observeUpdates() {
        viewModelScope.launch {
            repository.newMessages(chatId).collect { message ->
                _state.update { it.copy(messages = merge(it.messages, listOf(message))) }
            }
        }

        viewModelScope.launch {
            repository.editedMessages(chatId).collect { message ->
                _state.update { it.copy(messages = merge(it.messages, listOf(message))) }
            }
        }

        viewModelScope.launch {
            repository.deletedMessages(chatId).collect { ids ->
                val removed = ids.toSet()
                _state.update { state ->
                    state.copy(messages = state.messages.filterNot { it.id in removed })
                }
            }
        }
    }

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        viewModelScope.launch {
            // Odeslaná zpráva se do seznamu nepřidává ručně — TDLib ji vrátí přes `newMessages`
            // včetně skutečného id a času. Ruční přidání by znamenalo duplicitu.
            repository.send(chatId, trimmed).onFailure { failure ->
                _state.update { it.copy(error = failure.message ?: "Zprávu se nepodařilo odeslat.") }
            }
        }
    }

    /** Označí viditelné zprávy za přečtené. Volá obrazovka podle toho, co je reálně na displeji. */
    fun markRead(messageIds: List<Long>) {
        if (messageIds.isEmpty()) return
        viewModelScope.launch { repository.markRead(chatId, messageIds) }
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }
}

/**
 * Sloučí novou dávku do stávajícího seznamu.
 *
 * Deduplikuje podle id (překryv stránek i editace přijdou jako tatáž zpráva) a udrží řazení
 * od nejnovější. Novější verze téže zprávy přebíjí starší.
 */
private fun merge(current: List<ChatMessage>, incoming: List<ChatMessage>): List<ChatMessage> {
    if (incoming.isEmpty()) return current

    val byId = LinkedHashMap<Long, ChatMessage>(current.size + incoming.size)
    current.forEach { byId[it.id] = it }
    incoming.forEach { byId[it.id] = it }

    return byId.values.sortedByDescending { it.id }
}

data class ConversationState(
    val info: ChatInfo? = null,
    /** Od nejnovější po nejstarší. */
    val messages: List<ChatMessage> = emptyList(),
    val loading: Boolean = false,
    val reachedStart: Boolean = false,
    val error: String? = null,
)
