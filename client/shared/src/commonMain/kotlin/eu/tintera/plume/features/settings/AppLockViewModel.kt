package eu.tintera.plume.features.settings

import eu.tintera.plume.core.lock.BiometricResult
import eu.tintera.plume.core.lock.BiometricAuth
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.tintera.plume.core.lock.APP_LOCK_PIN_LENGTH
import eu.tintera.plume.core.lock.AppLock
import eu.tintera.plume.core.lock.AppLockSettings
import eu.tintera.plume.core.lock.PinResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AppLockFlow {
    /** Hlavní přehled nastavení nebo úvodní obrazovka vypnutého zámku. */
    data object Idle : AppLockFlow

    /** Zadání nového PINu (krok 1). */
    data class EnterNewPin(val isChanging: Boolean) : AppLockFlow

    /** Zopakování nového PINu (krok 2). */
    data class ConfirmNewPin(val firstPin: String, val isChanging: Boolean) : AppLockFlow
}

data class AppLockUiState(
    val flow: AppLockFlow = AppLockFlow.Idle,
    val isVerified: Boolean = false,
    val enteredPin: String = "",
    val errorTrigger: Int = 0,
    val errorMessage: String? = null,
    val blockedUntil: Long = 0,
    val isBusy: Boolean = false,
)

class AppLockViewModel(
    val appLock: AppLock,
) : ViewModel() {

    val settings: StateFlow<AppLockSettings?> = appLock.settings

    private val _uiState = MutableStateFlow(AppLockUiState())
    val uiState: StateFlow<AppLockUiState> = _uiState.asStateFlow()

    fun startSetup() {
        _uiState.value = _uiState.value.copy(
            flow = AppLockFlow.EnterNewPin(isChanging = false),
            enteredPin = "",
            errorMessage = null,
        )
    }

    fun startChangePin() {
        _uiState.value = _uiState.value.copy(
            flow = AppLockFlow.EnterNewPin(isChanging = true),
            enteredPin = "",
            errorMessage = null,
        )
    }

    fun cancelFlow() {
        _uiState.value = _uiState.value.copy(
            flow = AppLockFlow.Idle,
            enteredPin = "",
            errorMessage = null,
        )
    }

    fun onDigit(digit: Int) {
        val current = _uiState.value
        if (current.isBusy || current.enteredPin.length >= APP_LOCK_PIN_LENGTH) return
        val nextPin = current.enteredPin + digit
        _uiState.value = current.copy(enteredPin = nextPin, errorMessage = null)

        if (nextPin.length == APP_LOCK_PIN_LENGTH) {
            handleCompletePin(nextPin)
        }
    }

    fun onDelete() {
        val current = _uiState.value
        if (current.isBusy || current.enteredPin.isEmpty()) return
        _uiState.value = current.copy(
            enteredPin = current.enteredPin.dropLast(1),
            errorMessage = null,
        )
    }

    private fun handleCompletePin(pin: String) {
        val state = _uiState.value
        when (val flow = state.flow) {
            AppLockFlow.Idle -> {
                // Pokud ještě není ověřeno aktuální heslo pro přístup k nastavení
                if (!state.isVerified && settings.value != null) {
                    verifyCurrentPin(pin)
                }
            }
            is AppLockFlow.EnterNewPin -> {
                _uiState.value = state.copy(
                    flow = AppLockFlow.ConfirmNewPin(firstPin = pin, isChanging = flow.isChanging),
                    enteredPin = "",
                    errorMessage = null,
                )
            }
            is AppLockFlow.ConfirmNewPin -> {
                if (pin == flow.firstPin) {
                    savePin(pin, isChanging = flow.isChanging)
                } else {
                    _uiState.value = state.copy(
                        flow = AppLockFlow.EnterNewPin(isChanging = flow.isChanging),
                        enteredPin = "",
                        errorTrigger = state.errorTrigger + 1,
                        errorMessage = "mismatch",
                    )
                }
            }
        }
    }

    fun onBiometricsVerify(biometricAuth: BiometricAuth, promptTitle: String) {
        viewModelScope.launch {
            val result = biometricAuth.authenticate(promptTitle)
            // Změněné otisky biometrii vypnou i tady; zbývá PIN, a ten tahle obrazovka chce stejně.
            if (result == BiometricResult.Invalidated) {
                appLock.revokeBiometrics()
                _uiState.value = _uiState.value.copy(errorMessage = BIOMETRICS_REVOKED)
            }
            if (result == BiometricResult.Success) {
                appLock.unlockWithBiometrics()
                _uiState.value = _uiState.value.copy(
                    isVerified = true,
                    enteredPin = "",
                    errorMessage = null,
                )
            }
        }
    }

    private fun verifyCurrentPin(pin: String) {
        _uiState.value = _uiState.value.copy(isBusy = true)
        viewModelScope.launch {
            when (val result = appLock.verify(pin)) {
                PinResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isVerified = true,
                        enteredPin = "",
                        errorMessage = null,
                        isBusy = false,
                    )
                }
                is PinResult.Wrong -> {
                    _uiState.value = _uiState.value.copy(
                        enteredPin = "",
                        errorTrigger = _uiState.value.errorTrigger + 1,
                        errorMessage = "wrong",
                        blockedUntil = result.blockedUntil,
                        isBusy = false,
                    )
                }
                is PinResult.Blocked -> {
                    _uiState.value = _uiState.value.copy(
                        enteredPin = "",
                        errorTrigger = _uiState.value.errorTrigger + 1,
                        blockedUntil = result.blockedUntil,
                        isBusy = false,
                    )
                }
            }
        }
    }

    private fun savePin(pin: String, isChanging: Boolean) {
        _uiState.value = _uiState.value.copy(isBusy = true)
        viewModelScope.launch {
            val success = if (isChanging) {
                appLock.changePin(pin)
            } else {
                appLock.enable(pin)
            }
            if (success) {
                _uiState.value = _uiState.value.copy(
                    flow = AppLockFlow.Idle,
                    isVerified = true,
                    enteredPin = "",
                    errorMessage = null,
                    isBusy = false,
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    enteredPin = "",
                    errorTrigger = _uiState.value.errorTrigger + 1,
                    isBusy = false,
                )
            }
        }
    }

    fun setTimeout(seconds: Int) {
        viewModelScope.launch {
            appLock.setTimeout(seconds)
        }
    }

    /**
     * Zapnutí si nejdřív zapamatuje současný seznam otisků ([BiometricAuth.enroll]) — jinak by
     * biometrii vypnulo hned první odemčení. Když to platforma neumí, biometrie zůstane vypnutá.
     */
    fun setBiometrics(enabled: Boolean, biometricAuth: BiometricAuth) {
        viewModelScope.launch {
            if (enabled && !biometricAuth.enroll()) return@launch
            appLock.setBiometrics(enabled)
        }
    }

    fun disableLock() {
        viewModelScope.launch {
            appLock.disable()
            _uiState.value = AppLockUiState()
        }
    }
}

/** Kód hlášky: otisky v telefonu se změnily a biometrie se vypnula (viz [BiometricResult.Invalidated]). */
internal const val BIOMETRICS_REVOKED = "biometrics_revoked"
