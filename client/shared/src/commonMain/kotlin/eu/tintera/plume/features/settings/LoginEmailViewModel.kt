package eu.tintera.plume.features.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.AccountNotices
import eu.tintera.plume.core.telegram.EmailCodeInfo
import eu.tintera.plume.core.telegram.PasswordRepository
import eu.tintera.plume.core.telegram.SuggestedAction
import eu.tintera.plume.core.telegram.userMessage
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.auth_error_email
import eu.tintera.plume.resources.auth_error_generic
import eu.tintera.plume.resources.settings_two_step_error_email_invalid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Přihlašovací e-mail: adresa → kód → hotovo. Změna platí až po potvrzení kódu — do té doby
 * chodí přihlašovací kódy na starou adresu.
 */
class LoginEmailViewModel(
    private val passwords: PasswordRepository,
    private val notices: AccountNotices? = null,
) : ViewModel() {

    private val logger = Logger.withTag("LoginEmail")

    private val _state = MutableStateFlow(LoginEmailState())
    val state: StateFlow<LoginEmailState> = _state.asStateFlow()

    /** Text polí je Compose stav, ne `StateFlow` — viz `AuthViewModel.input`. */
    var email by mutableStateOf("")
        private set

    var code by mutableStateOf("")
        private set

    init {
        viewModelScope.launch {
            val pattern = passwords.status().getOrNull()?.loginEmailPattern
            _state.update { it.copy(currentPattern = pattern, loading = false) }
            // Účet bez e-mailu ho smí nastavit, jen když ho Telegram chce (`isLoginEmailAddressRequired`);
            // zeptat se je podle dokumentace TDLib podmínka, ne jen informace.
            if (pattern == null) passwords.isLoginEmailRequired()
        }
    }

    fun onEmailChange(value: String) {
        email = value
        _state.update { it.copy(error = null) }
    }

    fun onCodeChange(value: String) {
        code = value
        _state.update { it.copy(error = null) }
    }

    /** Adresa → kód. Hrubé síto proti překlepu je totéž jako u obnovovacího e-mailu. */
    fun submitEmail() {
        val address = email.trim()
        if (_state.value.busy) return
        if (!isPlausibleEmail(address)) {
            _state.update { it.copy(error = UiText(Res.string.settings_two_step_error_email_invalid)) }
            return
        }
        run(Res.string.auth_error_email) {
            passwords.setLoginEmail(address).onSuccess { info ->
                code = ""
                _state.update { it.copy(pendingCode = info) }
            }
        }
    }

    fun submitCode() {
        val value = code.trim()
        if (value.isEmpty() || _state.value.busy) return
        run(Res.string.auth_error_generic) {
            passwords.checkLoginEmailCode(value).onSuccess {
                _state.update { it.copy(done = true) }
                hideSuggestion()
            }
        }
    }

    fun resendCode() {
        if (_state.value.busy) return
        run(Res.string.auth_error_email) {
            passwords.resendLoginEmailCode().onSuccess { info ->
                code = ""
                _state.update { it.copy(pendingCode = info) }
            }
        }
    }

    /** Z kódu zpátky k adrese — překlep v ní. Zadaná adresa v poli zůstane. */
    fun changeEmail() {
        code = ""
        _state.update { it.copy(pendingCode = null, error = null) }
    }

    /** Zpět uvnitř průvodce: z kódu k adrese. Vrací `false`, když už není kam a má se odejít. */
    fun back(): Boolean {
        if (_state.value.pendingCode == null) return false
        changeEmail()
        return true
    }

    /**
     * Doporučená akce „přidej přihlašovací e-mail" je splněná. Server ji nejspíš odebere sám;
     * kdyby ne, karta by nad seznamem visela dál, i když je e-mail nastavený.
     */
    private suspend fun hideSuggestion() {
        val notices = notices ?: return
        notices.suggestedActions.value.filterIsInstance<SuggestedAction.SetLoginEmail>().forEach { action ->
            notices.hideSuggestedAction(action).onFailure { logger.w(it) { "Doporučenou akci se nepodařilo schovat." } }
        }
    }

    private fun run(fallback: org.jetbrains.compose.resources.StringResource, block: suspend () -> Result<*>) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            block().onFailure { failure ->
                logger.w(failure) { "Krok přihlašovacího e-mailu selhal." }
                _state.update { it.copy(error = failure.userMessage(fallback)) }
            }
            _state.update { it.copy(busy = false) }
        }
    }
}

data class LoginEmailState(
    val loading: Boolean = true,
    /** Maska současného přihlašovacího e-mailu, nebo `null`, když účet žádný nemá. */
    val currentPattern: String? = null,
    /** Kód odešel na novou adresu; `null` = zadává se adresa. */
    val pendingCode: EmailCodeInfo? = null,
    val busy: Boolean = false,
    val error: UiText? = null,
    /** E-mail je potvrzený — obrazovka se zavře. */
    val done: Boolean = false,
)
