package eu.tintera.plume.features.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.tintera.plume.core.telegram.PasswordRepository
import eu.tintera.plume.core.telegram.PasswordResetResult
import eu.tintera.plume.core.telegram.TwoStepStatus
import eu.tintera.plume.core.telegram.userMessage
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.settings_two_step_done_disabled
import eu.tintera.plume.resources.settings_two_step_done_email
import eu.tintera.plume.resources.settings_two_step_done_enabled
import eu.tintera.plume.resources.settings_two_step_done_password
import eu.tintera.plume.resources.settings_two_step_done_reset
import eu.tintera.plume.resources.settings_two_step_done_reset_cancelled
import eu.tintera.plume.resources.settings_two_step_email_code_sent
import eu.tintera.plume.resources.settings_two_step_error_email_empty
import eu.tintera.plume.resources.settings_two_step_error_email_invalid
import eu.tintera.plume.resources.settings_two_step_error_failed
import eu.tintera.plume.resources.settings_two_step_error_hint_reveals
import eu.tintera.plume.resources.settings_two_step_error_mismatch
import eu.tintera.plume.resources.settings_two_step_error_too_long
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource

/**
 * Probíhající průvodce.
 *
 * @property codePattern maska adresy, kam odešel kód (kód z e-mailu i kód k obnově).
 * @property currentEmail obnovovací e-mail zjištěný ověřením hesla; ukazuje se u změny e-mailu.
 */
data class TwoStepFlowState(
    val action: TwoStepAction,
    val step: TwoStepStep,
    val codePattern: String = "",
    val currentEmail: String = "",
)

/** Potvrzení, na které obrazovka čeká. Každé pojmenovává následek. */
enum class TwoStepDialog {
    /** Zapnutí bez obnovovacího e-mailu. */
    SkipEmail,

    /** Vypnutí hesla (heslo už je ověřené). */
    Disable,

    /** Zapomenuté heslo bez e-mailu: zrušení s týdenním čekáním. */
    Reset,

    /** Čekání na reset uplynulo: zrušit heslo hned. */
    ResetNow,

    /** Zrušit běžící reset. */
    CancelReset,

    /** Zahodit čekající potvrzení e-mailu (a s ním nové heslo, které na něj čeká). */
    CancelEmail,
}

/**
 * @property status `null` = ještě se načítá.
 * @property resetDeclinedUntil server reset odmítl; znovu jde zkusit od tohohle okamžiku.
 */
data class TwoStepState(
    val status: TwoStepStatus? = null,
    val loadFailed: Boolean = false,
    val flow: TwoStepFlowState? = null,
    val busy: Boolean = false,
    val error: UiText? = null,
    val dialog: TwoStepDialog? = null,
    val notice: UiText? = null,
    val resetDeclinedUntil: Long? = null,
)

/**
 * Dvoufázové ověření v Nastavení → Obecné → Soukromí.
 *
 * **Hesla, kód a e-mail jsou Compose stav přímo ve ViewModelu**, ne `StateFlow` (kurzor by
 * poskakoval, viz úprava profilu) a **ne `rememberSaveable`**: uložený stav se serializuje
 * a přežil by i zabití procesu. Tady přežijí jen otočení displeje a s koncem průvodce se mažou
 * ([clearSecrets]). Do `toString` stavu (a tím do logu) se nedostanou.
 */
class TwoStepViewModel(private val repository: PasswordRepository) : ViewModel() {

    private val _state = MutableStateFlow(TwoStepState())
    val state: StateFlow<TwoStepState> = _state.asStateFlow()

    var currentPassword by mutableStateOf("")
        private set
    var newPassword by mutableStateOf("")
        private set
    var repeatPassword by mutableStateOf("")
        private set
    var hint by mutableStateOf("")
        private set
    var email by mutableStateOf("")
        private set
    var code by mutableStateOf("")
        private set

    private var job: Job? = null

    init {
        reload()
    }

    fun reload() {
        viewModelScope.launch {
            repository.status()
                .onSuccess { status -> _state.update { it.copy(status = status, loadFailed = false) } }
                .onFailure { _state.update { it.copy(loadFailed = it.status == null) } }
        }
    }

    fun onCurrentPasswordChange(value: String) { currentPassword = value; clearError() }
    fun onNewPasswordChange(value: String) { newPassword = value; clearError() }
    fun onRepeatPasswordChange(value: String) { repeatPassword = value; clearError() }
    fun onHintChange(value: String) { hint = value; clearError() }
    fun onEmailChange(value: String) { email = value; clearError() }
    fun onCodeChange(value: String) { code = value; clearError() }

    /** Začne průvodce. Zapomenuté heslo bez e-mailu průvodce nemá — rovnou nabídne reset. */
    fun start(action: TwoStepAction) {
        val status = _state.value.status ?: return
        clearSecrets()
        when (action) {
            TwoStepAction.Recover -> {
                if (!status.hasRecoveryEmail) {
                    _state.update { it.copy(flow = null, error = null, dialog = TwoStepDialog.Reset) }
                    return
                }
                call(onFailureStay = false) {
                    repository.requestRecovery().map { info ->
                        _state.update {
                            it.copy(flow = TwoStepFlowState(action, TwoStepStep.RecoveryCode, codePattern = info.pattern))
                        }
                    }
                }
            }
            TwoStepAction.ConfirmEmail -> _state.update {
                it.copy(
                    flow = TwoStepFlowState(action, TwoStepStep.EmailCode, codePattern = status.pendingEmail?.pattern.orEmpty()),
                    error = null,
                )
            }
            else -> _state.update { it.copy(flow = TwoStepFlowState(action, action.steps().first()), error = null) }
        }
    }

    /** Zpět: zavře dialog, vrátí krok, nebo opustí průvodce. */
    fun back() {
        val state = _state.value
        if (state.busy) return
        if (state.dialog != null) {
            dismissDialog()
            return
        }
        val flow = state.flow ?: return
        val previous = flow.action.previous(flow.step)
        if (previous == null) {
            exitFlow()
        } else {
            _state.update { it.copy(flow = flow.copy(step = previous), error = null) }
        }
    }

    fun submit() {
        val flow = _state.value.flow ?: return
        if (_state.value.busy) return
        when (flow.step) {
            TwoStepStep.CurrentPassword -> {
                if (currentPassword.isEmpty()) return
                call {
                    // Ověření bez změny: jediné volání, které heslo zkontroluje a nic nezmění.
                    repository.recoveryEmail(currentPassword).map { address ->
                        if (flow.action == TwoStepAction.Disable) {
                            _state.update { it.copy(flow = flow.copy(currentEmail = address), dialog = TwoStepDialog.Disable) }
                        } else {
                            advance(flow.copy(currentEmail = address))
                        }
                    }
                }
            }
            TwoStepStep.RecoveryCode -> {
                if (code.isBlank()) return
                call { repository.checkRecoveryCode(code.trim()).map { advance(flow) } }
            }
            TwoStepStep.NewPassword -> {
                val problem = newPasswordProblem(newPassword, repeatPassword)
                if (problem != null) {
                    setError(
                        when (problem) {
                            NewPasswordProblem.Empty -> return
                            NewPasswordProblem.Mismatch -> Res.string.settings_two_step_error_mismatch
                            NewPasswordProblem.TooLong -> Res.string.settings_two_step_error_too_long
                        },
                    )
                    return
                }
                advance(flow)
            }
            TwoStepStep.Hint -> {
                if (hintRevealsPassword(hint, newPassword)) {
                    setError(Res.string.settings_two_step_error_hint_reveals)
                    return
                }
                if (flow.action.commitsAt(TwoStepStep.Hint)) commit(flow) else advance(flow)
            }
            TwoStepStep.Email -> {
                val blank = email.isBlank()
                when {
                    blank && flow.action == TwoStepAction.Enable ->
                        _state.update { it.copy(dialog = TwoStepDialog.SkipEmail, error = null) }
                    blank -> setError(Res.string.settings_two_step_error_email_empty)
                    !isPlausibleEmail(email) -> setError(Res.string.settings_two_step_error_email_invalid)
                    else -> commit(flow)
                }
            }
            TwoStepStep.EmailCode -> {
                if (code.isBlank()) return
                val hadPassword = _state.value.status?.hasPassword == true
                call {
                    repository.checkEmailCode(code.trim()).map { status ->
                        // Heslo s e-mailem začne platit až potvrzením — i když se kód zadává
                        // z karty na obrazovce stavu, ne hned v průvodci zapnutí.
                        val enabledNow = flow.action == TwoStepAction.Enable || (!hadPassword && status.hasPassword)
                        finish(
                            status,
                            if (enabledNow) Res.string.settings_two_step_done_enabled else Res.string.settings_two_step_done_email,
                        )
                    }
                }
            }
        }
    }

    /** Potvrzení otevřeného dialogu. */
    fun confirmDialog() {
        val dialog = _state.value.dialog ?: return
        _state.update { it.copy(dialog = null) }
        val flow = _state.value.flow
        when (dialog) {
            TwoStepDialog.SkipEmail -> if (flow != null) commit(flow)
            TwoStepDialog.Disable -> if (flow != null) commit(flow)
            TwoStepDialog.Reset, TwoStepDialog.ResetNow -> reset()
            TwoStepDialog.CancelReset -> call(onFailureStay = false) {
                repository.cancelReset().map {
                    refreshAfter()
                    _state.update { it.copy(notice = UiText(Res.string.settings_two_step_done_reset_cancelled)) }
                }
            }
            TwoStepDialog.CancelEmail -> call(onFailureStay = false) {
                repository.cancelEmailVerification().map { status ->
                    clearSecrets()
                    _state.update { it.copy(status = status, flow = null) }
                }
            }
        }
    }

    fun dismissDialog() = _state.update { it.copy(dialog = null) }

    fun askResetNow() = _state.update { it.copy(dialog = TwoStepDialog.ResetNow, error = null) }
    fun askCancelReset() = _state.update { it.copy(dialog = TwoStepDialog.CancelReset, error = null) }
    fun askCancelEmail() = _state.update { it.copy(dialog = TwoStepDialog.CancelEmail, error = null) }

    fun resendEmailCode() {
        call(onFailureStay = _state.value.flow != null) {
            repository.resendEmailCode().map { status ->
                _state.update { it.copy(status = status, notice = UiText(Res.string.settings_two_step_email_code_sent)) }
            }
        }
    }

    fun noticeShown() = _state.update { it.copy(notice = null) }

    fun resetDeclinedShown() = _state.update { it.copy(resetDeclinedUntil = null) }

    private fun reset() {
        call(onFailureStay = false) {
            repository.reset().map { result ->
                when (result) {
                    PasswordResetResult.Done -> {
                        refreshAfter()
                        _state.update { it.copy(notice = UiText(Res.string.settings_two_step_done_reset)) }
                    }
                    is PasswordResetResult.Pending -> refreshAfter()
                    is PasswordResetResult.Declined -> _state.update { it.copy(resetDeclinedUntil = result.retryAt) }
                }
            }
        }
    }

    /** Odešle změnu, kterou průvodce sesbíral. */
    private fun commit(flow: TwoStepFlowState) {
        call {
            when (flow.action) {
                TwoStepAction.Enable -> repository.setPassword(
                    oldPassword = "",
                    newPassword = newPassword,
                    hint = hint.trim(),
                    newEmail = email.trim().ifEmpty { null },
                ).map { status -> afterEmailChange(flow, status, Res.string.settings_two_step_done_enabled) }

                TwoStepAction.ChangeEmail -> repository.setRecoveryEmail(currentPassword, email.trim())
                    .map { status -> afterEmailChange(flow, status, Res.string.settings_two_step_done_email) }

                TwoStepAction.ChangePassword -> repository.setPassword(
                    oldPassword = currentPassword,
                    newPassword = newPassword,
                    hint = hint.trim(),
                    newEmail = null,
                ).map { status -> finish(status, Res.string.settings_two_step_done_password) }

                TwoStepAction.Disable -> repository.setPassword(
                    oldPassword = currentPassword,
                    newPassword = "",
                    hint = "",
                    newEmail = null,
                ).map { status -> finish(status, Res.string.settings_two_step_done_disabled) }

                TwoStepAction.Recover -> repository.recover(code.trim(), newPassword, hint.trim())
                    .map { status -> finish(status, Res.string.settings_two_step_done_password) }

                TwoStepAction.ConfirmEmail -> Result.success(Unit)
            }
        }
    }

    /** Po zadání e-mailu: když server čeká na kód, průvodce pokračuje jeho zadáním. */
    private fun afterEmailChange(flow: TwoStepFlowState, status: TwoStepStatus, done: StringResource) {
        val pending = status.pendingEmail
        if (pending == null) {
            finish(status, done)
        } else {
            code = ""
            _state.update {
                it.copy(status = status, flow = flow.copy(step = TwoStepStep.EmailCode, codePattern = pending.pattern), error = null)
            }
        }
    }

    private fun advance(flow: TwoStepFlowState) {
        val next = flow.action.next(flow.step) ?: return
        _state.update { it.copy(flow = flow.copy(step = next), error = null) }
    }

    private fun finish(status: TwoStepStatus, notice: StringResource) {
        clearSecrets()
        _state.update { it.copy(status = status, flow = null, error = null, notice = UiText(notice)) }
    }

    private fun exitFlow() {
        clearSecrets()
        _state.update { it.copy(flow = null, error = null) }
        // Kód z e-mailu mohl zůstat čekat; obrazovka stavu ho má ukázat.
        refreshAfter()
    }

    private fun refreshAfter() {
        viewModelScope.launch {
            repository.status().onSuccess { status -> _state.update { it.copy(status = status) } }
        }
    }

    private fun setError(resource: StringResource) =
        _state.update { it.copy(error = UiText(resource)) }

    private fun clearError() {
        if (_state.value.error != null) _state.update { it.copy(error = null) }
    }

    private fun clearSecrets() {
        currentPassword = ""
        newPassword = ""
        repeatPassword = ""
        hint = ""
        email = ""
        code = ""
    }

    /**
     * Volání serveru s indikátorem. Chyba se ukáže pod formulářem ([onFailureStay]), nebo jako
     * hláška, když průvodce neběží (karty na obrazovce stavu).
     */
    private fun call(onFailureStay: Boolean = true, block: suspend () -> Result<Unit>) {
        if (job?.isActive == true) return
        _state.update { it.copy(busy = true, error = null) }
        job = viewModelScope.launch {
            val result = block()
            _state.update { state ->
                val failure = result.exceptionOrNull()
                val message = failure?.userMessage(Res.string.settings_two_step_error_failed)
                when {
                    message == null -> state.copy(busy = false)
                    onFailureStay -> state.copy(busy = false, error = message)
                    else -> state.copy(busy = false, notice = message)
                }
            }
        }
    }
}
