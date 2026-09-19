package eu.tintera.plume.features.forward

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.tintera.plume.core.share.ShareTargets
import eu.tintera.plume.core.telegram.ChatListState
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.core.telegram.ChatSummary
import eu.tintera.plume.core.telegram.ConversationRepository
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.forward_failed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Výběr chatu, do kterého se zpráva přepošle.
 *
 * Seznam se **nenačítá znovu** — bere se živý seznam z [ChatRepository], ten samý, který kreslí
 * hlavní obrazovka. Hledání je proto zatím jen filtr nad načteným seznamem: server by uměl víc
 * (`searchChats`), ale chaty, do kterých se přeposílá, bývají ty nahoře.
 */
class ForwardViewModel(
    private val fromChatId: Long,
    private val messageIds: List<Long>,
    private val repository: ConversationRepository,
    chatRepository: ChatRepository,
    /** Přeposlání je taky „sem posílám" — cíl se nahlásí jako použitý (Direct Share). */
    private val shareTargets: ShareTargets,
) : ViewModel() {

    private val query = MutableStateFlow("")

    private val _sending = MutableStateFlow(false)
    private val _error = MutableStateFlow<UiText?>(null)

    val state: StateFlow<ForwardState> = combine(
        chatRepository.chats,
        query,
        _sending,
        _error,
        chatRepository.savedMessagesChatId,
    ) { chatList, text, sending, error, saved ->
        val chats = (chatList as? ChatListState.Ready)?.chats.orEmpty()
        ForwardState(
            query = text,
            // Prázdný seznam při načítání není „nic nenalezeno" — seznam ještě nedorazil.
            loading = chatList is ChatListState.Loading,
            // Chat, ze kterého se přeposílá, ve výběru zůstává: přeposlat sám sobě jde a v Telegramu
            // to není výjimka.
            chats = chats.filter { it.title.contains(text.trim(), ignoreCase = true) },
            sending = sending,
            error = error,
            savedMessagesChatId = saved,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ForwardState())

    fun onQueryChange(text: String) {
        query.value = text
    }

    /**
     * Přepošle a ohlásí, kam — obrazovka pak cíl otevře, aby bylo vidět, že zpráva dorazila.
     * Dokud odesílání běží, další ťuknutí nic neudělá; dvě přeposlání by jinak odešla obě.
     */
    fun forward(toChatId: Long, onDone: (chatId: Long) -> Unit) {
        if (_sending.value) return
        _sending.value = true

        viewModelScope.launch {
            repository.forward(fromChatId, messageIds, toChatId)
                .onSuccess {
                    shareTargets.reportUsed(toChatId)
                    onDone(toChatId)
                }
                .onFailure { failure ->
                    _error.update { UiText(Res.string.forward_failed) }
                    _sending.value = false
                }
        }
    }

    fun consumeError() {
        _error.value = null
    }
}

data class ForwardState(
    val query: String = "",
    val loading: Boolean = true,
    val chats: List<ChatSummary> = emptyList(),
    val sending: Boolean = false,
    val error: UiText? = null,
    val savedMessagesChatId: Long? = null,
)
