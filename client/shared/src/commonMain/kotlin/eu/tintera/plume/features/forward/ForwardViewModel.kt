package eu.tintera.plume.features.forward

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.tintera.plume.core.share.ShareTargets
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.core.telegram.ChatSummary
import eu.tintera.plume.core.telegram.ConversationRepository
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.features.chats.ChatSearch
import eu.tintera.plume.features.chats.pickerChats
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
 * Bez dotazu je to živý hlavní seznam z [ChatRepository], ten samý, který kreslí hlavní
 * obrazovka. S dotazem výsledky [ChatSearch] — ze všech chatů, i archivovaných.
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

    /**
     * Vybrané cíle. **Množina si drží pořadí výběru** (`Set.plus` vrací `LinkedHashSet`) — po
     * odeslání se otevře ten, který uživatel vybral první, takže je vidět, že zpráva dorazila.
     */
    private val _selected = MutableStateFlow<Set<Long>>(emptySet())

    /** Hledání ve všech chatech, i v archivu — v načteném hlavním seznamu by archiv chyběl. */
    private val search = ChatSearch(chatRepository, viewModelScope)

    val state: StateFlow<ForwardState> = combine(
        combine(chatRepository.chats(), query, search.state) { list, text, found -> pickerChats(text, list, found) },
        query,
        combine(_sending, _selected, ::Pair),
        _error,
        chatRepository.savedMessagesChatId,
    ) { picker, text, (sending, selected), error, saved ->
        ForwardState(
            query = text,
            loading = picker.loading,
            // Chat, ze kterého se přeposílá, ve výběru zůstává: přeposlat sám sobě jde a v Telegramu
            // to není výjimka.
            chats = picker.chats,
            sending = sending,
            selected = selected,
            error = error,
            savedMessagesChatId = saved,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ForwardState())

    fun onQueryChange(text: String) {
        query.value = text
        search.setQuery(text)
    }

    /** Ťuknutí na chat ho přidá mezi cíle, nebo ho z nich vyndá. Odesílá se až tlačítkem. */
    fun toggle(chatId: Long) {
        if (_sending.value) return
        _selected.update { if (chatId in it) it - chatId else it + chatId }
    }

    /**
     * Přepošle do všech vybraných cílů a ohlásí ten PRVNÍ vybraný — obrazovka ho otevře, aby
     * bylo vidět, že zpráva dorazila. Otevřít poslední nebo nic by bylo svévolné; první vybraný
     * je ten, na který uživatel myslel nejdřív.
     *
     * Posílá se **postupně**, ne souběžně: pořadí zpráv v cílech tím zůstane dané a server
     * nedostane deset přeposlání naráz. Selhání jednoho cíle ostatní nezastaví — co odešlo,
     * odešlo, a chyba se ohlásí na konci.
     */
    fun send(onDone: (chatId: Long) -> Unit) {
        val targets = _selected.value.toList()
        if (targets.isEmpty() || _sending.value) return
        _sending.value = true

        viewModelScope.launch {
            var failed = false
            targets.forEach { target ->
                repository.forward(fromChatId, messageIds, target)
                    .onSuccess { shareTargets.reportUsed(target) }
                    .onFailure { failed = true }
            }
            if (failed) {
                _error.update { UiText(Res.string.forward_failed) }
                _sending.value = false
            } else {
                onDone(targets.first())
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
    /** Vybrané cíle v pořadí výběru. */
    val selected: Set<Long> = emptySet(),
    val error: UiText? = null,
    val savedMessagesChatId: Long? = null,
)
