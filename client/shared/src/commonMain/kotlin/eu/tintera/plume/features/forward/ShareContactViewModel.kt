package eu.tintera.plume.features.forward

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.tintera.plume.core.share.ShareTargets
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.core.telegram.ChatSummary
import eu.tintera.plume.core.telegram.ConversationRepository
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.features.chats.ChatSearch
import eu.tintera.plume.features.chats.pickerChats
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.share_contact_failed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Výběr chatů, do kterých poslat kontakt z profilu ([userId]).
 *
 * Stejný výběr jako přeposílání ([ForwardViewModel]): víc cílů naráz, odesílá se tlačítkem
 * a otevře se první vybraný. Co odejde, ukazuje karta nahoře ([card]) — jméno i ČÍSLO, protože
 * odchází cizí telefonní číslo. Proto tu není potvrzovací dialog jako u kontaktu ze sponky:
 * tam se posílá ťuknutím na řádek, tady až tlačítkem pod kartou, která to říká.
 */
class ShareContactViewModel(
    private val userId: Long,
    private val repository: ConversationRepository,
    chatRepository: ChatRepository,
    private val shareTargets: ShareTargets,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val _sending = MutableStateFlow(false)
    private val _error = MutableStateFlow<UiText?>(null)
    private val _selected = MutableStateFlow<Set<Long>>(emptySet())
    private val _card = MutableStateFlow<MessageMedia.Contact?>(null)
    private val search = ChatSearch(chatRepository, viewModelScope)

    init {
        viewModelScope.launch { repository.contactCard(userId).onSuccess { card -> _card.value = card } }
    }

    val state: StateFlow<ShareContactState> = combine(
        combine(chatRepository.chats(), query, search.state) { list, text, found -> pickerChats(text, list, found) },
        query,
        combine(_sending, _selected, ::Pair),
        _error,
        combine(chatRepository.savedMessagesChatId, _card, ::Pair),
    ) { picker, text, (sending, selected), error, (saved, card) ->
        ShareContactState(
            query = text,
            loading = picker.loading,
            chats = picker.chats,
            sending = sending,
            selected = selected,
            error = error,
            savedMessagesChatId = saved,
            card = card,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShareContactState())

    fun onQueryChange(text: String) {
        query.value = text
        search.setQuery(text)
    }

    fun toggle(chatId: Long) {
        if (_sending.value) return
        _selected.update { if (chatId in it) it - chatId else it + chatId }
    }

    /** Pošle do všech vybraných postupně (jako přeposílání) a ohlásí první vybraný. */
    fun send(onDone: (chatId: Long) -> Unit) {
        val targets = _selected.value.toList()
        // Bez čísla kontakt poslat nejde; karta to řekne a tlačítko se nenabídne.
        if (targets.isEmpty() || _sending.value || _card.value?.phone.isNullOrBlank()) return
        _sending.value = true
        viewModelScope.launch {
            var failed = false
            targets.forEach { target ->
                repository.sendContact(target, userId, replyToMessageId = null, quote = null)
                    .onSuccess { shareTargets.reportUsed(target) }
                    .onFailure { failed = true }
            }
            if (failed) {
                _error.update { UiText(Res.string.share_contact_failed) }
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

data class ShareContactState(
    val query: String = "",
    val loading: Boolean = true,
    val chats: List<ChatSummary> = emptyList(),
    val sending: Boolean = false,
    val selected: Set<Long> = emptySet(),
    val error: UiText? = null,
    val savedMessagesChatId: Long? = null,
    /** Co odejde; `null`, dokud se nenačte. Prázdné číslo = kontakt ho skrývá a poslat nejde. */
    val card: MessageMedia.Contact? = null,
)
