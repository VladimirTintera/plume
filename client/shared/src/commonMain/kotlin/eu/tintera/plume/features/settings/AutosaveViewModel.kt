package eu.tintera.plume.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.tintera.plume.core.telegram.AutosaveRepository
import eu.tintera.plume.core.telegram.AutosaveScope
import eu.tintera.plume.core.telegram.AutosaveScopeSettings
import eu.tintera.plume.core.telegram.AutosaveSettings
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.core.telegram.ChatSummary
import eu.tintera.plume.core.telegram.userMessage
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.autosave_save_failed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * @property settings nastavení účtu; `null` = načítá se (nebo selhalo, viz [loadFailed]).
 * @property exceptionChats název a fotka chatů s výjimkou; chat, který TDLib nezná, chybí.
 * @property error chyba změny — na snackbar, pak [consumeError].
 */
data class AutosaveState(
    val settings: AutosaveSettings? = null,
    val loadFailed: Boolean = false,
    val exceptionChats: Map<Long, ChatSummary> = emptyMap(),
    val error: UiText? = null,
)

/** Ukládání do galerie v Úložišti: tři výchozí pravidla a výjimky. Mění se hned (nastavení účtu). */
class AutosaveViewModel(
    private val autosave: AutosaveRepository,
    private val chats: ChatRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AutosaveState())
    val state: StateFlow<AutosaveState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loadFailed = false) }
        viewModelScope.launch {
            autosave.settings()
                .onSuccess { settings ->
                    val titles = chats.summaries(settings.exceptions.map { it.chatId }).associateBy { it.id }
                    _state.update { it.copy(settings = settings, exceptionChats = titles) }
                }
                .onFailure { _state.update { it.copy(loadFailed = true) } }
        }
    }

    /**
     * Změní pravidlo. Obrazovka se změní hned a po odpovědi se nastavení načte znovu — server
     * může hodnotu upravit (velikost videa do povoleného rozsahu) a výjimka po odebrání zmizí.
     */
    fun set(scope: AutosaveScope, settings: AutosaveScopeSettings?) {
        val previous = _state.value.settings ?: return
        _state.update { it.copy(settings = previous.with(scope, settings)) }
        viewModelScope.launch {
            autosave.set(scope, settings)
                .onSuccess { load() }
                .onFailure { failure ->
                    _state.update { it.copy(settings = previous, error = failure.userMessage(Res.string.autosave_save_failed)) }
                }
        }
    }

    fun consumeError() {
        _state.update { it.copy(error = null) }
    }
}

/** Nastavení s jedním změněným pravidlem; `null` u výjimky ji odebere. */
internal fun AutosaveSettings.with(scope: AutosaveScope, settings: AutosaveScopeSettings?): AutosaveSettings = when (scope) {
    AutosaveScope.PrivateChats -> settings?.let { copy(privateChats = it) } ?: this
    AutosaveScope.Groups -> settings?.let { copy(groups = it) } ?: this
    AutosaveScope.Channels -> settings?.let { copy(channels = it) } ?: this
    is AutosaveScope.Chat -> copy(
        exceptions = if (settings == null) {
            exceptions.filterNot { it.chatId == scope.chatId }
        } else {
            exceptions.map { if (it.chatId == scope.chatId) it.copy(settings = settings) else it }
        },
    )
}
