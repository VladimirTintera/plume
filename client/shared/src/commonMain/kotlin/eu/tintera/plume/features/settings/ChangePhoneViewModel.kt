package eu.tintera.plume.features.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.AccountNotices
import eu.tintera.plume.core.telegram.AccountRepository
import eu.tintera.plume.core.telegram.Country
import eu.tintera.plume.core.telegram.PhoneChangeCode
import eu.tintera.plume.core.telegram.SuggestedAction
import eu.tintera.plume.core.telegram.TelegramSession
import eu.tintera.plume.core.telegram.userMessage
import eu.tintera.plume.core.ui.PhoneEntry
import eu.tintera.plume.core.ui.PhoneInput
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.core.ui.displayPhone
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.auth_error_resend
import eu.tintera.plume.resources.change_phone_failed
import eu.tintera.plume.resources.change_phone_send_failed
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import kotlin.time.Duration.Companion.seconds

/**
 * Změna telefonního čísla: nové číslo → kód z něj → hotovo. Číslo se zadává stejně jako při
 * přihlášení ([PhoneInput]: země, formát od TDLib, vložené mezinárodní číslo).
 */
class ChangePhoneViewModel(
    private val accounts: AccountRepository,
    private val session: TelegramSession,
    private val notices: AccountNotices? = null,
) : ViewModel() {

    private val logger = Logger.withTag("ChangePhone")

    private val phoneInput = PhoneInput(session, viewModelScope)

    val phone: PhoneEntry get() = phoneInput.phone
    val countries: StateFlow<List<Country>> = phoneInput.countries

    /** Compose stav, ne `StateFlow` — viz `AuthViewModel.input`. */
    var code by mutableStateOf("")
        private set

    private val _state = MutableStateFlow(ChangePhoneState())
    val state: StateFlow<ChangePhoneState> = _state.asStateFlow()

    private var countdown: Job? = null

    init {
        viewModelScope.launch { phoneInput.loadCountries() }
    }

    fun selectCountry(country: Country) {
        phoneInput.selectCountry(country)
        _state.update { it.copy(error = null) }
    }

    fun onPhoneChange(value: String) {
        phoneInput.onPhoneChange(value)
        _state.update { it.copy(error = null) }
    }

    fun onCodeChange(value: String) {
        code = value
        _state.update { it.copy(error = null) }
    }

    fun submitPhone() {
        if (!phone.isComplete || _state.value.busy) return
        val number = phone.international
        run(Res.string.change_phone_send_failed) {
            accounts.sendPhoneChangeCode(number).onSuccess { sent ->
                code = ""
                _state.update { it.copy(sent = sent, sentTo = session.displayPhone(number)) }
                startCountdown(sent.resendTimeout)
            }
        }
    }

    fun submitCode() {
        val value = code.trim()
        if (value.isEmpty() || _state.value.busy) return
        run(Res.string.change_phone_failed) {
            accounts.checkPhoneChangeCode(value).onSuccess {
                _state.update { it.copy(done = true) }
                hideSuggestion()
            }
        }
    }

    /** Kód jinou cestou, jen když ji server nabízí a lhůta uplynula — jako při přihlášení. */
    fun resendCode() {
        val sent = _state.value.sent ?: return
        if (sent.next == null || _state.value.resendIn > 0 || _state.value.busy) return
        run(Res.string.auth_error_resend) {
            accounts.resendPhoneChangeCode().onSuccess { again ->
                code = ""
                _state.update { it.copy(sent = again) }
                startCountdown(again.resendTimeout)
            }
        }
    }

    /** Z kódu zpátky k číslu — překlep v něm. Zadané číslo v poli zůstane. */
    fun changeNumber() {
        countdown?.cancel()
        code = ""
        _state.update { it.copy(sent = null, sentTo = null, resendIn = 0, error = null) }
    }

    /** Zpět uvnitř průvodce. Vrací `false`, když už není kam a má se odejít. */
    fun back(): Boolean {
        if (_state.value.sent == null) return false
        changeNumber()
        return true
    }

    private fun startCountdown(seconds: Int) {
        countdown?.cancel()
        countdown = viewModelScope.launch {
            var left = seconds
            _state.update { it.copy(resendIn = left) }
            while (left > 0) {
                delay(1.seconds)
                left--
                _state.update { it.copy(resendIn = left) }
            }
        }
    }

    /** „Je … pořád tvoje číslo?" je po změně vyřízené. */
    private suspend fun hideSuggestion() {
        val notices = notices ?: return
        if (SuggestedAction.CheckPhoneNumber in notices.suggestedActions.value) {
            notices.hideSuggestedAction(SuggestedAction.CheckPhoneNumber)
                .onFailure { logger.w(it) { "Doporučenou akci se nepodařilo schovat." } }
        }
    }

    private fun run(fallback: StringResource, block: suspend () -> Result<*>) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            block().onFailure { failure ->
                logger.w(failure) { "Krok změny čísla selhal." }
                _state.update { it.copy(error = failure.userMessage(fallback)) }
            }
            _state.update { it.copy(busy = false) }
        }
    }
}

data class ChangePhoneState(
    /** Kód odešel na nové číslo; `null` = zadává se číslo. */
    val sent: PhoneChangeCode? = null,
    /** Naformátované číslo, na které kód odešel. */
    val sentTo: String? = null,
    /** Za kolik sekund jde kód poslat jinak; 0 = hned (nebo vůbec, když `next` je `null`). */
    val resendIn: Int = 0,
    val busy: Boolean = false,
    val error: UiText? = null,
    /** Číslo je změněné — obrazovka se zavře. */
    val done: Boolean = false,
)
