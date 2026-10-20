package eu.tintera.plume.features.channellimit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.core.telegram.PublicChat
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.public_limit_failed
import eu.tintera.plume.resources.public_limit_release_failed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PublicLinkLimitState(
    /** Vlastní veřejné skupiny a kanály; `null`, dokud se nenačtou. */
    val chats: List<PublicChat>? = null,
    /** Čeká na potvrzení odebrání odkazu. */
    val confirming: PublicChat? = null,
    /** Odkaz se právě odebírá tomuhle chatu. */
    val releasing: Long? = null,
    /** Odkaz se uvolnil — veřejný chat už jde, sheet se zavře. */
    val released: Boolean = false,
    val error: UiText? = null,
)

/**
 * Limit vlastních veřejných skupin a kanálů (`isPublicChatsLimit`): seznam těch, kterým jde
 * odebrat odkaz. Stačí jeden — tím se uvolní místo a zakládaný nebo upravovaný chat veřejný být
 * smí. Odebrání se potvrzuje: odkaz si pak může vzít kdokoli a vrátit ho nemusí jít.
 */
class PublicLinkLimitViewModel(private val chats: ChatRepository) : ViewModel() {

    private val _state = MutableStateFlow(PublicLinkLimitState())
    val state: StateFlow<PublicLinkLimitState> = _state.asStateFlow()

    val isPremium: StateFlow<Boolean> get() = chats.isPremium

    /** Sheet se otevřel: seznam se čte pokaždé znovu, odkazy se mezitím mohly změnit. */
    fun load() {
        _state.value = PublicLinkLimitState()
        viewModelScope.launch {
            chats.createdPublicChats()
                .onSuccess { found -> _state.update { it.copy(chats = found) } }
                .onFailure { _state.update { it.copy(chats = emptyList(), error = UiText(Res.string.public_limit_failed)) } }
        }
    }

    fun askRelease(chat: PublicChat) {
        if (_state.value.releasing != null) return
        _state.update { it.copy(confirming = chat) }
    }

    fun cancelRelease() = _state.update { it.copy(confirming = null) }

    fun confirmRelease() {
        val chat = _state.value.confirming ?: return
        _state.update { it.copy(confirming = null, releasing = chat.chat.id, error = null) }
        viewModelScope.launch {
            chats.releasePublicLink(chat.chat.id)
                .onSuccess { _state.update { it.copy(releasing = null, released = true) } }
                .onFailure { _state.update { it.copy(releasing = null, error = UiText(Res.string.public_limit_release_failed)) } }
        }
    }
}
