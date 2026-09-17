package eu.tintera.plume.features.auth

import co.touchlab.kermit.Logger
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.tintera.plume.core.telegram.AuthStage
import eu.tintera.plume.core.telegram.TelegramSession
import eu.tintera.plume.core.telegram.userMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.auth_error_generic
import eu.tintera.plume.resources.auth_error_qr

class AuthViewModel(
    private val session: TelegramSession,
) : ViewModel() {

    private val logger = Logger.withTag("Auth")

    val stage: StateFlow<AuthStage> = session.authStage

    private val _input = MutableStateFlow("")
    val input: StateFlow<String> = _input.asStateFlow()

    /** Probíhá request — blokuje tlačítko a přepne ho na expressive LoadingIndicator. */
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _error = MutableStateFlow<UiText?>(null)
    val error: StateFlow<UiText?> = _error.asStateFlow()

    // Session startuje `startKoin`, ne tenhle ViewModel — TDLib musí být inicializovaný
    // i když se žádné UI neskládá (probuzení procesu pushem).

    fun onInputChange(value: String) {
        _input.value = value
        _error.value = null
    }

    /** Jedno tlačítko pro všechny fáze — co se pošle, určuje aktuální [AuthStage]. */
    fun submit() {
        val value = _input.value.trim()
        if (value.isEmpty() || _busy.value) return

        viewModelScope.launch {
            _busy.value = true
            val result = when (stage.value) {
                AuthStage.WaitPhoneNumber -> session.submitPhoneNumber(value)
                AuthStage.WaitCode -> session.submitCode(value)
                is AuthStage.WaitPassword -> session.submitPassword(value)
                else -> Result.success(Unit)
            }
            _busy.value = false

            result
                .onSuccess { _input.value = "" }
                .onFailure { failure ->
                    logger.w(failure) { "Přihlašovací krok selhal." }
                    _error.value = failure.userMessage(Res.string.auth_error_generic)
                }
        }
    }

    fun startQrLogin() {
        viewModelScope.launch {
            _busy.value = true
            session.startQrLogin().onFailure { failure ->
                logger.w(failure) { "QR přihlášení se nepodařilo spustit." }
                _error.value = failure.userMessage(Res.string.auth_error_qr)
            }
            _busy.value = false
        }
    }
}
