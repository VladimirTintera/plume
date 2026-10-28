package eu.tintera.plume.features.forward

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.core.telegram.ChatSummary
import eu.tintera.plume.features.chats.ChatSearch
import eu.tintera.plume.features.chats.pickerChats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Výběr chatu pro „Odpovědět v jiném chatu". Jeden cíl, žádné odesílání: ťuknutí na chat ho
 * otevře s rozepsanou odpovědí, kterou si uživatel dopíše sám (jako v oficiálním klientu).
 * Seznam i hledání jsou tytéž jako u přeposílání ([ForwardViewModel]).
 */
class ReplyElsewhereViewModel(
    /** Chat zprávy, na kterou se odpovídá — do něj samého se „jinam" neodpovídá. */
    private val fromChatId: Long,
    chatRepository: ChatRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val search = ChatSearch(chatRepository, viewModelScope)

    val state: StateFlow<ReplyElsewhereState> = combine(
        combine(chatRepository.chats(), query, search.state) { list, text, found -> pickerChats(text, list, found) },
        query,
        chatRepository.savedMessagesChatId,
    ) { picker, text, saved ->
        ReplyElsewhereState(
            query = text,
            loading = picker.loading,
            chats = picker.chats.filter { it.id != fromChatId },
            savedMessagesChatId = saved,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReplyElsewhereState())

    fun onQueryChange(text: String) {
        query.value = text
        search.setQuery(text)
    }
}

data class ReplyElsewhereState(
    val query: String = "",
    val loading: Boolean = true,
    val chats: List<ChatSummary> = emptyList(),
    val savedMessagesChatId: Long? = null,
)
