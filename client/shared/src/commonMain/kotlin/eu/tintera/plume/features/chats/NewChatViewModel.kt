package eu.tintera.plume.features.chats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.core.telegram.ChatSummary
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.new_chat_load_failed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class NewChatState(
    val query: String = "",
    val loading: Boolean = true,
    val contacts: List<ChatSummary> = emptyList(),
    val chatResults: List<ChatSummary> = emptyList(),
    val globalResults: List<ChatSummary> = emptyList(),
    val savedMessagesChatId: Long? = null,
    val error: UiText? = null,
)

class NewChatViewModel(
    private val chatRepository: ChatRepository,
) : ViewModel() {

    private val logger = Logger.withTag("NewChatViewModel")

    private val query = MutableStateFlow("")
    private val contacts = MutableStateFlow<List<ChatSummary>>(emptyList())
    private val loading = MutableStateFlow(true)
    private val error = MutableStateFlow<UiText?>(null)

    private val search = ChatSearch(chatRepository, viewModelScope, everywhere = true)

    val state: StateFlow<NewChatState> = combine(
        combine(loading, error, contacts) { l, e, c -> Triple(l, e, c) },
        combine(query, search.state, chatRepository.savedMessagesChatId) { q, s, id -> Triple(q, s, id) },
    ) { (isLoading, err, allContacts), (text, searchState, savedId) ->
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            NewChatState(
                query = text,
                loading = isLoading,
                contacts = allContacts,
                savedMessagesChatId = savedId,
                error = err,
            )
        } else {
            val filteredContacts = allContacts.filter { it.title.contains(trimmed, ignoreCase = true) }
            val contactIds = allContacts.mapTo(HashSet()) { it.id }
            val otherKnown = searchState.results.filter { it.id !in contactIds && it.id != savedId }
            NewChatState(
                query = text,
                loading = isLoading || searchState.searching,
                contacts = filteredContacts,
                chatResults = otherKnown,
                globalResults = searchState.globalOnly.filter { it.id !in contactIds },
                savedMessagesChatId = savedId,
                error = err,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NewChatState())

    init {
        loadContacts()
    }

    fun onQueryChange(text: String) {
        query.value = text
        search.setQuery(text)
    }

    fun loadContacts() {
        loading.value = true
        viewModelScope.launch {
            chatRepository.contacts()
                .onSuccess { list ->
                    contacts.value = list
                    loading.value = false
                }
                .onFailure { failure ->
                    logger.w(failure) { "Načtení kontaktů selhalo." }
                    error.value = UiText(Res.string.new_chat_load_failed)
                    loading.value = false
                }
        }
    }

    fun consumeError() {
        error.value = null
    }
}
