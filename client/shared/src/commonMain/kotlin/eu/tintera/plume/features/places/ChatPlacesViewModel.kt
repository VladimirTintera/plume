package eu.tintera.plume.features.places

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.ChatLocationsPage
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.ConversationRepository
import eu.tintera.plume.core.telegram.userMessage
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.features.conversation.ConversationJumpRequests
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.chat_places_failed
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Stav obrazovky „Místa v chatu". */
@Immutable
data class ChatPlacesState(
    /** Zprávy s polohou od nejnovější; `null`, dokud nedoběhne první úsek. */
    val places: List<ChatMessage>? = null,
    /** Kolik zpráv historie prošlo (polohy jsou mezi nimi vzácné). */
    val walked: Int = 0,
    /** Prochází se lokální databáze (první počítání). */
    val scanning: Boolean = true,
    val loadingOlder: Boolean = false,
    /** Server starší zprávy nemá — prošla celá historie. */
    val reachedStart: Boolean = false,
    val olderFailed: Boolean = false,
    val failure: UiText? = null,
) {
    val canLoadOlder: Boolean get() = places != null && !scanning && !loadingOlder && !reachedStart
}

/**
 * **Místa v chatu** (`ChatPlacesRoute`): polohy a místa, která kdo do chatu poslal, s přehledovou
 * mapou nahoře. TDLib na polohy filtr hledání nemá (`SearchMessagesFilter` je bez nich), takže se
 * prochází historie jako u statistiky chatu: napřed lokální databáze po úsecích, „Načíst starší"
 * ze serveru.
 */
class ChatPlacesViewModel(
    val chatId: Long,
    private val repository: ConversationRepository,
    private val jumpRequests: ConversationJumpRequests,
) : ViewModel() {

    private val logger = Logger.withTag("ChatPlaces")

    private val _state = MutableStateFlow(ChatPlacesState())
    val state: StateFlow<ChatPlacesState> = _state.asStateFlow()

    private var oldestId = 0L
    private var job: Job? = null

    init {
        load()
    }

    fun load() {
        job?.cancel()
        oldestId = 0L
        _state.value = ChatPlacesState()
        job = viewModelScope.launch {
            while (_state.value.walked < LOCAL_MAX) {
                val page = repository.chatLocations(chatId, oldestId, onlyLocal = true, maxMessages = LOCAL_CHUNK).getOrElse { error ->
                    logger.w(error) { "Místa v chatu $chatId se nepodařilo projít." }
                    _state.update { it.copy(scanning = false, failure = error.userMessage(Res.string.chat_places_failed)) }
                    return@launch
                }
                append(page, walked = LOCAL_CHUNK)
                if (page.reachedEnd || page.oldestMessageId == null) break
            }
            // V zařízení nic není (chat se ještě neotevřel): rovnou první dávka ze serveru.
            if (_state.value.walked == 0 || oldestId == 0L) loadOlderNow()
            _state.update { it.copy(scanning = false) }
        }
    }

    fun loadOlder() {
        if (!_state.value.canLoadOlder) return
        job = viewModelScope.launch { loadOlderNow() }
    }

    /** Skok na zprávu s polohou v konverzaci vedle (jako den z kalendáře). */
    fun jumpToMessage(messageId: Long) = jumpRequests.request(chatId, messageId)

    private suspend fun loadOlderNow() {
        _state.update { it.copy(loadingOlder = true, olderFailed = false) }
        repository.chatLocations(chatId, oldestId, onlyLocal = false, maxMessages = SERVER_CHUNK)
            .onSuccess { page ->
                append(page, walked = SERVER_CHUNK)
                _state.update { it.copy(reachedStart = page.reachedEnd) }
            }
            .onFailure { error ->
                logger.w(error) { "Starší místa v chatu $chatId se nepodařilo načíst." }
                _state.update { it.copy(olderFailed = true) }
            }
        _state.update { it.copy(loadingOlder = false) }
    }

    private fun append(page: ChatLocationsPage, walked: Int) {
        page.oldestMessageId?.let { oldestId = it }
        _state.update { current ->
            current.copy(
                places = (current.places.orEmpty() + page.messages).distinctBy { it.id }.sortedByDescending { it.id },
                walked = current.walked + if (page.oldestMessageId == null) 0 else walked,
            )
        }
    }
}

private const val LOCAL_CHUNK = 2_000
private const val LOCAL_MAX = 30_000
private const val SERVER_CHUNK = 1_000
