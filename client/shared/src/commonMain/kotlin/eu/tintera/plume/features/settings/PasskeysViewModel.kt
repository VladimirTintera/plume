package eu.tintera.plume.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.tintera.plume.core.telegram.AccountRepository
import eu.tintera.plume.core.telegram.LoginPasskey
import eu.tintera.plume.core.telegram.userMessage
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.passkey_remove_failed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * @property passkeys klíče v pořadí od serveru; `null` = ještě se načítají (nebo selhaly).
 * @property confirming klíč, u kterého se ptáme na odebrání.
 * @property removing id klíče, který se právě odebírá.
 * @property error chyba odebrání — na snackbar, pak [consumeError].
 */
data class PasskeysState(
    val passkeys: List<LoginPasskey>? = null,
    val loadFailed: Boolean = false,
    val confirming: LoginPasskey? = null,
    val removing: String? = null,
    val error: UiText? = null,
)

/**
 * Přístupové klíče: seznam a odebrání. Přidat klíč Plume neumí (viz [AccountRepository.loginPasskeys]),
 * takže update o změně seznamu TDLib nepošle a jediný zdroj pravdy je tahle obrazovka.
 */
class PasskeysViewModel(private val accounts: AccountRepository) : ViewModel() {

    private val _state = MutableStateFlow(PasskeysState())
    val state: StateFlow<PasskeysState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loadFailed = false) }
        viewModelScope.launch {
            accounts.loginPasskeys()
                .onSuccess { keys -> _state.update { it.copy(passkeys = keys) } }
                .onFailure { _state.update { it.copy(loadFailed = true) } }
        }
    }

    fun requestRemove(passkey: LoginPasskey) {
        if (_state.value.removing != null) return
        _state.update { it.copy(confirming = passkey) }
    }

    fun dismissRemove() {
        _state.update { it.copy(confirming = null) }
    }

    fun confirmRemove() {
        val passkey = _state.value.confirming ?: return
        _state.update { it.copy(confirming = null, removing = passkey.id) }
        viewModelScope.launch {
            accounts.removeLoginPasskey(passkey.id)
                .onSuccess {
                    _state.update { state ->
                        state.copy(removing = null, passkeys = state.passkeys?.filterNot { it.id == passkey.id })
                    }
                }
                .onFailure { failure ->
                    _state.update { it.copy(removing = null, error = failure.userMessage(Res.string.passkey_remove_failed)) }
                }
        }
    }

    fun consumeError() {
        _state.update { it.copy(error = null) }
    }
}
