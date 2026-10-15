package eu.tintera.plume.features.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.tintera.plume.core.telegram.AutosaveChatType
import eu.tintera.plume.core.telegram.AutosaveException
import eu.tintera.plume.core.telegram.AutosaveRepository
import eu.tintera.plume.core.telegram.AutosaveScope
import eu.tintera.plume.core.telegram.AutosaveScopeSettings
import eu.tintera.plume.core.telegram.AutosaveSettings
import eu.tintera.plume.core.telegram.ProfileKind
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
 * Co platí v jednom chatu.
 *
 * @property settings výjimka, jinak výchozí pravidlo druhu chatu.
 * @property isException chat má výjimku — jde ji odebrat a vrátit se k výchozímu pravidlu.
 */
data class ChatAutosave(val settings: AutosaveScopeSettings, val isException: Boolean)

/**
 * @property settings celé nastavení účtu; `null` = načítá se, nebo se načíst nepodařilo
 *   (pak se řádek v profilu neukáže — profil je k užitku i bez něj).
 */
data class ChatAutosaveState(
    val settings: AutosaveSettings? = null,
    val error: UiText? = null,
)

/**
 * Ukládání do galerie pro jeden chat z jeho profilu: výjimku tu jde ZALOŽIT, upravit a odebrat
 * (v Úložišti jen upravit a odebrat — tam chybí výběr chatu). Nastavení drží Telegram na serveru
 * a je společné s oficiálními aplikacemi; viz `docs/poznatky/prohlizec-medii.md`.
 */
class ChatAutosaveViewModel(
    private val chatId: Long,
    private val autosave: AutosaveRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ChatAutosaveState())
    val state: StateFlow<ChatAutosaveState> = _state.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            autosave.settings().onSuccess { settings -> _state.update { it.copy(settings = settings) } }
        }
    }

    /**
     * Uloží, co uživatel v dialogu zvolil. Výjimka se ZAKLÁDÁ jen tehdy, když se volba liší od
     * výchozího pravidla — „Hotovo" nad nezměněným dialogem by jinak chat tiše odpojil od
     * výchozího nastavení a pozdější změna druhu chatů by se ho netýkala.
     */
    fun save(changed: AutosaveScopeSettings, type: AutosaveChatType) {
        val previous = _state.value.settings ?: return
        val next = previous.withChatException(chatId, type, changed) ?: return
        commit(previous, next, changed)
    }

    /** Odebere výjimku; chat se vrátí k výchozímu pravidlu svého druhu. */
    fun removeException() {
        val previous = _state.value.settings ?: return
        if (previous.exceptions.none { it.chatId == chatId }) return
        commit(previous, previous.copy(exceptions = previous.exceptions.filterNot { it.chatId == chatId }), null)
    }

    fun consumeError() {
        _state.update { it.copy(error = null) }
    }

    /**
     * Obrazovka se změní hned a po odpovědi se nastavení načte znovu — server může hodnotu
     * upravit (velikost videa do povoleného rozsahu).
     */
    private fun commit(previous: AutosaveSettings, next: AutosaveSettings, changed: AutosaveScopeSettings?) {
        _state.update { it.copy(settings = next) }
        viewModelScope.launch {
            autosave.set(AutosaveScope.Chat(chatId), changed)
                .onSuccess { load() }
                .onFailure { failure ->
                    _state.update { it.copy(settings = previous, error = failure.userMessage(Res.string.autosave_save_failed)) }
                }
        }
    }
}

/** Co platí v chatu a jestli je to výjimka. */
fun AutosaveSettings.chatAutosave(chatId: Long, type: AutosaveChatType): ChatAutosave = ChatAutosave(
    settings = forChat(chatId, type),
    isException = exceptions.any { it.chatId == chatId },
)

/**
 * Nastavení po volbě [changed] v chatu; `null` = nic se nemění (volba je táž jako dnes, nebo
 * chat výjimku nemá a volba se shoduje s výchozím pravidlem — pak se výjimka nezakládá).
 */
internal fun AutosaveSettings.withChatException(chatId: Long, type: AutosaveChatType, changed: AutosaveScopeSettings): AutosaveSettings? {
    val current = chatAutosave(chatId, type)
    if (changed == current.settings) return null
    val exception = AutosaveException(chatId, changed)
    return copy(
        exceptions = if (current.isException) {
            exceptions.map { if (it.chatId == chatId) exception else it }
        } else {
            exceptions + exception
        },
    )
}

/**
 * Druh chatu pro výchozí pravidlo; `null` = v chatu se do galerie neukládá nic a řádek se
 * nenabízí: Uložené zprávy jsou samé vlastní zprávy (ty se neukládají nikdy) a tajný chat
 * se vynechává záměrně (`TdlAutosaveRepository.candidateOf`).
 */
fun autosaveChatType(kind: ProfileKind, isSecretChat: Boolean): AutosaveChatType? = when {
    isSecretChat -> null
    else -> when (kind) {
        ProfileKind.User, ProfileKind.Bot -> AutosaveChatType.Private
        ProfileKind.Group -> AutosaveChatType.Group
        ProfileKind.Channel -> AutosaveChatType.Channel
        ProfileKind.SavedMessages -> null
    }
}
