package eu.tintera.plume.features.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.tintera.plume.core.telegram.AccountRepository
import eu.tintera.plume.core.telegram.PasswordRepository
import eu.tintera.plume.core.telegram.userMessage
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.delete_account_failed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * @property needsPassword účet chrání dvoufázové ověření; `null` = ještě se zjišťuje (nebo selhalo).
 * @property statusFailed nepodařilo se zjistit, jestli heslo je — bez toho smazat nejde.
 * @property confirming ukazuje se poslední potvrzení.
 * @property busy maže se. Po úspěchu zůstává `true`: klient se zavírá a aplikace přejde na
 *   přihlášení sama, obrazovka už nemá co ukázat.
 */
data class DeleteAccountState(
    val needsPassword: Boolean? = null,
    val statusFailed: Boolean = false,
    val confirming: Boolean = false,
    val busy: Boolean = false,
    val error: UiText? = null,
)

/**
 * Smazání účtu. Heslo se chce jen u účtu s dvoufázovým ověřením — a tam POVINNĚ: bez něj by
 * server smazání jen naplánoval na týden (`2FA_CONFIRM_WAIT_…`), a to je jiná funkce (reset
 * účtu se zapomenutým heslem), kterou tahle obrazovka nenabízí.
 *
 * Heslo a důvod jsou stav Compose ve ViewModelu, ne v uloženém stavu: heslo do `SavedState`
 * nepatří.
 */
class DeleteAccountViewModel(
    private val accounts: AccountRepository,
    private val passwords: PasswordRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DeleteAccountState())
    val state: StateFlow<DeleteAccountState> = _state.asStateFlow()

    var password by mutableStateOf("")
        private set

    var reason by mutableStateOf("")
        private set

    init {
        loadStatus()
    }

    fun loadStatus() {
        _state.update { it.copy(statusFailed = false) }
        viewModelScope.launch {
            passwords.status()
                .onSuccess { status -> _state.update { it.copy(needsPassword = status.hasPassword) } }
                .onFailure { _state.update { it.copy(statusFailed = true) } }
        }
    }

    fun onPasswordChange(value: String) {
        password = value
        _state.update { it.copy(error = null) }
    }

    fun onReasonChange(value: String) {
        reason = value
    }

    /** Smazat jde, až je jasné, jestli heslo je, a když je, až je vyplněné. */
    val canDelete: Boolean
        get() {
            val state = _state.value
            val needsPassword = state.needsPassword ?: return false
            return !state.busy && (!needsPassword || password.isNotEmpty())
        }

    fun requestDelete() {
        if (!canDelete) return
        _state.update { it.copy(confirming = true, error = null) }
    }

    fun dismissConfirm() {
        _state.update { it.copy(confirming = false) }
    }

    fun confirmDelete() {
        val needsPassword = _state.value.needsPassword ?: return
        if (_state.value.busy) return
        _state.update { it.copy(confirming = false, busy = true, error = null) }
        viewModelScope.launch {
            accounts.deleteAccount(reason = reason.trim(), password = if (needsPassword) password else "")
                .onFailure { failure ->
                    _state.update { it.copy(busy = false, error = failure.userMessage(Res.string.delete_account_failed)) }
                }
        }
    }
}
