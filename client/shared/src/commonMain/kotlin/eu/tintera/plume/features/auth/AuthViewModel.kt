package eu.tintera.plume.features.auth

import co.touchlab.kermit.Logger
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.tintera.plume.core.telegram.AuthStage
import eu.tintera.plume.core.telegram.Country
import eu.tintera.plume.core.telegram.EmailReset
import eu.tintera.plume.core.telegram.TelegramSession
import eu.tintera.plume.core.telegram.userMessage
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds
import eu.tintera.plume.core.ui.PhoneEntry
import eu.tintera.plume.core.ui.PhoneInput
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.features.settings.isPlausibleEmail
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.auth_error_email
import eu.tintera.plume.resources.auth_error_email_reset
import eu.tintera.plume.resources.auth_error_generic
import eu.tintera.plume.resources.auth_error_hint_equals_password
import eu.tintera.plume.resources.auth_error_qr
import eu.tintera.plume.resources.auth_error_recovery_request
import eu.tintera.plume.resources.auth_error_resend
import eu.tintera.plume.resources.auth_error_restart
import eu.tintera.plume.resources.settings_two_step_error_email_invalid

class AuthViewModel(
    private val session: TelegramSession,
) : ViewModel() {

    private val logger = Logger.withTag("Auth")

    val stage: StateFlow<AuthStage> = session.authStage

    /**
     * Text polí je Compose stav, ne `StateFlow`. Hodnota ze `StateFlow` se do pole dostane až
     * dalším průchodem collectoru; znak napsaný mezitím pole překreslí se starou hodnotou
     * a zahodí. Snapshot stav se zapíše i přečte hned, ve stejném snímku.
     */
    var input by mutableStateOf("")
        private set

    /** Probíhá request — blokuje tlačítko a přepne ho na expressive LoadingIndicator. */
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _error = MutableStateFlow<UiText?>(null)
    val error: StateFlow<UiText?> = _error.asStateFlow()

    private val phoneInput = PhoneInput(session, viewModelScope)

    /** Rozepsané číslo. Compose stav ze stejného důvodu jako [input]. */
    val phone: PhoneEntry get() = phoneInput.phone

    /** Seznam zemí pro výběr. Prázdný, dokud nedorazí z TDLib — řádek zatím ukazuje jen předvolbu. */
    val countries: StateFlow<List<Country>> = phoneInput.countries

    private val _passwordStep = MutableStateFlow(PasswordStep.Password)

    /** Kde v zadávání hesla uživatel je; mimo [AuthStage.WaitPassword] vždy [PasswordStep.Password]. */
    val passwordStep: StateFlow<PasswordStep> = _passwordStep.asStateFlow()

    /** Nápověda k novému heslu. Jediné pole navíc — ostatní kroky mají jedno pole ([input]). */
    var hint by mutableStateOf("")
        private set

    private val _recoveryUnavailable = MutableStateFlow(false)

    /** Uživatel chtěl obnovit heslo, ale účet nemá obnovovací e-mail — vysvětlí to dialog. */
    val recoveryUnavailable: StateFlow<Boolean> = _recoveryUnavailable.asStateFlow()

    private val _resendIn = MutableStateFlow(0)

    /**
     * Za kolik sekund jde kód poslat jinak ([AuthStage.WaitCode.next]); 0 = hned. Mimo zadání
     * kódu vždy 0.
     */
    val resendIn: StateFlow<Int> = _resendIn.asStateFlow()

    private val _changingEmail = MutableStateFlow(false)

    /**
     * Oprava adresy nad [AuthStage.WaitEmailCode]: TDLib zůstává v čekání na kód a novou adresu
     * vezme i odtud, takže krok „zadej jiný e-mail" drží ViewModel — stejně jako obnovu hesla.
     */
    val changingEmail: StateFlow<Boolean> = _changingEmail.asStateFlow()

    private val _emailResetIn = MutableStateFlow(0)

    /**
     * Za kolik sekund proběhne zažádaný reset e-mailu ([EmailReset.Pending]); 0 = hotovo, nebo
     * se nečeká. TDLib zbývající čas posílá jen s novým stavem, průběžně ho odpočítáváme sami.
     */
    val emailResetIn: StateFlow<Int> = _emailResetIn.asStateFlow()

    /**
     * Kód z e-mailu, který server už uznal. Drží se mimo [input], protože v kroku nového hesla
     * pole patří heslu, a kód se posílá až s ním (`recoverAuthenticationPassword`).
     */
    private var recoveryCode: String? = null

    init {
        // Oprava adresy patří k jednomu čekání na kód. Nový stav (kód na opravenou adresu,
        // reset, odchod jinam) ji ukončí — `StateFlow` stejný stav znovu nepošle, takže úspěch
        // opravy zavírá krok i v `submit`.
        viewModelScope.launch {
            stage.collect { _changingEmail.value = false }
        }
        viewModelScope.launch {
            stage.collectLatest { current ->
                var left = ((current as? AuthStage.WaitEmailCode)?.reset as? EmailReset.Pending)?.resetInSeconds ?: 0
                _emailResetIn.value = left
                while (left > 0) {
                    delay(1.seconds)
                    left--
                    _emailResetIn.value = left
                }
            }
        }
        // Země jen pro zadání čísla, ne při každém startu: ViewModel vzniká v `App` vždy, i když
        // je uživatel přihlášený a přihlašovací obrazovku nikdy neuvidí. Collector se po dobu
        // načítání nevolá znovu (StateFlow mezitím drží jen poslední stav), takže se nenačítá
        // dvakrát; po neúspěchu to zkusí příští návrat k číslu.
        viewModelScope.launch {
            stage.collect { if (it == AuthStage.WaitPhoneNumber && countries.value.isEmpty()) phoneInput.loadCountries() }
        }
        // Obnova je náš postup nad JEDNÍM stavem TDLib. Jakmile automat odejde jinam (přihlášeno,
        // restart), nesmí se do příštího hesla přenést rozdělaný krok.
        viewModelScope.launch {
            stage.collect { if (it !is AuthStage.WaitPassword) resetPasswordStep() }
        }
        // Lhůta platí od příchodu stavu (TDLib posílá zbývající čas). `collectLatest`: nový
        // stav — kód poslaný jinak, nebo odchod z kódu — rozjetý odpočet zruší.
        viewModelScope.launch {
            stage.collectLatest { current ->
                var left = (current as? AuthStage.WaitCode)?.resendTimeout ?: 0
                _resendIn.value = left
                while (left > 0) {
                    delay(1.seconds)
                    left--
                    _resendIn.value = left
                }
            }
        }
    }

    private fun resetPasswordStep() {
        _passwordStep.value = PasswordStep.Password
        hint = ""
        recoveryCode = null
    }

    fun selectCountry(country: Country) {
        phoneInput.selectCountry(country)
        _error.value = null
    }

    /** Uživatel píše číslo; jak se z textu stane číslo, řeší [PhoneInput.onPhoneChange]. */
    fun onPhoneChange(value: String) {
        _error.value = null
        phoneInput.onPhoneChange(value)
    }

    // Session startuje `startKoin`, ne tenhle ViewModel — TDLib musí být inicializovaný
    // i když se žádné UI neskládá (probuzení procesu pushem).

    fun onInputChange(value: String) {
        input = value
        _error.value = null
    }

    fun onHintChange(value: String) {
        hint = value
        _error.value = null
    }

    /**
     * Jedno tlačítko pro všechny fáze — co se pošle, určuje aktuální [AuthStage] a u hesla ještě
     * [passwordStep].
     *
     * **Heslo se neořezává**, kód ano. Mezera na konci hesla je jeho součást: dokud se ořezávalo
     * i heslo, nešel se přihlásit nikdo, kdo ji v hesle má — a nové heslo nastavené odsud by
     * s mezerou na konci přihlášení v jiném klientovi rozbilo naopak.
     */
    fun submit() {
        val stageNow = stage.value
        val step = _passwordStep.value
        val secret = stageNow is AuthStage.WaitPassword && step != PasswordStep.RecoveryCode
        val value = if (secret) input else input.trim()
        val ready = if (stageNow == AuthStage.WaitPhoneNumber) phone.isComplete else value.isNotEmpty()
        if (!ready || _busy.value) return

        // Server nápovědu shodnou s heslem odmítne taky, ale až po odeslání a obecnou chybou.
        val newHint = hint.trim()
        if (stageNow is AuthStage.WaitPassword && step == PasswordStep.NewPassword && newHint == value) {
            _error.value = UiText(Res.string.auth_error_hint_equals_password)
            return
        }

        // Adresa, ne kód: buď první zadání, nebo oprava nad čekáním na kód.
        val enteringEmail = stageNow == AuthStage.WaitEmailAddress ||
            (stageNow is AuthStage.WaitEmailCode && _changingEmail.value)
        // Hrubé síto proti překlepu; bez něj by chybu řekl až server, a obecnou větou.
        if (enteringEmail && !isPlausibleEmail(value)) {
            _error.value = UiText(Res.string.settings_two_step_error_email_invalid)
            return
        }

        viewModelScope.launch {
            _busy.value = true
            val result = when (stageNow) {
                AuthStage.WaitPhoneNumber -> session.submitPhoneNumber(phone.international)
                is AuthStage.WaitCode -> session.submitCode(value)
                AuthStage.WaitEmailAddress -> session.submitEmailAddress(value)
                is AuthStage.WaitEmailCode -> if (enteringEmail) {
                    session.submitEmailAddress(value).onSuccess { _changingEmail.value = false }
                } else {
                    session.submitEmailCode(value)
                }
                is AuthStage.WaitPassword -> when (step) {
                    PasswordStep.Password -> session.submitPassword(value)
                    PasswordStep.RecoveryCode -> session.checkPasswordRecoveryCode(value).onSuccess {
                        // Nové heslo se vymýšlí až nad kódem, o kterém je jisté, že platí.
                        recoveryCode = value
                        _passwordStep.value = PasswordStep.NewPassword
                    }
                    PasswordStep.NewPassword -> {
                        val code = recoveryCode
                        if (code == null) {
                            _passwordStep.value = PasswordStep.RecoveryCode
                            Result.success(Unit)
                        } else {
                            session.recoverPassword(code, newPassword = value, newHint = newHint)
                        }
                    }
                }
                else -> Result.success(Unit)
            }
            _busy.value = false

            result
                .onSuccess { input = "" }
                .onFailure { failure ->
                    logger.w(failure) { "Přihlašovací krok selhal." }
                    _error.value = failure.userMessage(if (enteringEmail) Res.string.auth_error_email else Res.string.auth_error_generic)
                }
        }
    }

    /**
     * Kód jinou cestou — SMS nebo hovor místo zprávy do Telegramu. Bez toho se nepřihlásil nikdo,
     * kdo k přihlášenému zařízení nemá přístup (ztracený telefon, recyklované číslo).
     *
     * Před uplynutím lhůty se nic neposílá: TDLib by to odmítl a uživatel by místo odpočtu
     * viděl chybu. Kudy kód odešel doopravdy, ohlásí TDLib novým stavem; rozepsaný kód se
     * zahodí, protože ten nový bude jiný (a může mít i jinou délku).
     */
    fun resendCode() {
        val current = stage.value as? AuthStage.WaitCode ?: return
        if (current.next == null || _resendIn.value > 0 || _busy.value) return
        viewModelScope.launch {
            _busy.value = true
            session.resendCode()
                .onSuccess {
                    input = ""
                    _error.value = null
                }
                .onFailure { failure ->
                    logger.w(failure) { "Kód se nepodařilo poslat jinou cestou." }
                    _error.value = failure.userMessage(Res.string.auth_error_resend)
                }
            _busy.value = false
        }
    }

    /** Oprava adresy, na kterou odešel kód — jen u adresy zadané v tomhle přihlášení. */
    fun changeEmail() {
        val current = stage.value as? AuthStage.WaitEmailCode ?: return
        if (!current.isSetup || _busy.value) return
        _changingEmail.value = true
        input = ""
        _error.value = null
    }

    /** Kód na tutéž adresu znovu. Jen u adresy zadané v tomhle přihlášení, viz [TelegramSession.resendEmailCode]. */
    fun resendEmailCode() {
        val current = stage.value as? AuthStage.WaitEmailCode ?: return
        if (!current.isSetup || _busy.value) return
        viewModelScope.launch {
            _busy.value = true
            session.resendEmailCode()
                .onSuccess {
                    input = ""
                    _error.value = null
                }
                .onFailure { failure ->
                    logger.w(failure) { "Kód na e-mail se nepodařilo poslat znovu." }
                    _error.value = failure.userMessage(Res.string.auth_error_email)
                }
            _busy.value = false
        }
    }

    /**
     * Reset přihlašovacího e-mailu. Žádost s čekáním ho jen rozjede (odpočet přijde novým stavem),
     * hotový reset pošle kód na číslo. Během čekání se nic neposílá — server by odpověděl
     * „ještě ne" a místo odpočtu by byla chyba.
     */
    fun resetEmail() {
        val current = stage.value as? AuthStage.WaitEmailCode ?: return
        if (current.reset == null || _emailResetIn.value > 0 || _busy.value) return
        viewModelScope.launch {
            _busy.value = true
            session.resetEmailAddress()
                .onSuccess {
                    input = ""
                    _error.value = null
                }
                .onFailure { failure ->
                    logger.w(failure) { "Přihlašovací e-mail se nepodařilo resetovat." }
                    _error.value = failure.userMessage(Res.string.auth_error_email_reset)
                }
            _busy.value = false
        }
    }

    /**
     * „Zapomenuté heslo?" Bez obnovovacího e-mailu se nic neposílá: server by odpověděl
     * `PASSWORD_RECOVERY_NA` a uživatel by z obecné chyby nepoznal, co dál — proto dialog, který
     * řekne, co zbývá. Volá se i jako „Poslat kód znovu".
     */
    fun forgotPassword() {
        val current = stage.value as? AuthStage.WaitPassword ?: return
        if (!current.canRecover) {
            _recoveryUnavailable.value = true
            return
        }
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            session.requestPasswordRecovery()
                .onSuccess {
                    _passwordStep.value = PasswordStep.RecoveryCode
                    input = ""
                    _error.value = null
                }
                .onFailure { failure ->
                    logger.w(failure) { "Kód na obnovu hesla se nepodařilo poslat." }
                    _error.value = failure.userMessage(Res.string.auth_error_recovery_request)
                }
            _busy.value = false
        }
    }

    fun dismissRecoveryUnavailable() {
        _recoveryUnavailable.value = false
    }

    /**
     * Krok zpátky. Uvnitř obnovy hesla o krok obnovy (nové heslo → kód, kód → heslo), jinak
     * k zadání čísla — přihlášení není v `NavDisplay`, takže bez tohohle zpět aplikaci zavřelo
     * a z kódu poslaného na špatné číslo nevedla cesta ven.
     */
    fun back() {
        _error.value = null
        if (stage.value is AuthStage.WaitPassword) {
            when (_passwordStep.value) {
                PasswordStep.NewPassword -> {
                    _passwordStep.value = PasswordStep.RecoveryCode
                    // Kód byl platný; uživatel se vrací nejspíš kvůli e-mailu, ne kvůli překlepu.
                    input = recoveryCode.orEmpty()
                    hint = ""
                    return
                }
                PasswordStep.RecoveryCode -> {
                    _passwordStep.value = PasswordStep.Password
                    input = ""
                    return
                }
                PasswordStep.Password -> Unit
            }
        }
        if (stage.value is AuthStage.WaitEmailCode && _changingEmail.value) {
            _changingEmail.value = false
            input = ""
            return
        }
        if (stage.value.canGoBack()) restartLogin()
    }

    /**
     * Zpátky na zadání čísla — z QR, z kódu i z hesla. Trvá to okamžik (TDLib se zavírá a zakládá
     * nový klient), proto `busy` — a stav se mezitím ohlásí jako `Connecting`, ne jako odhlašování.
     *
     * Z kódu a hesla by šlo zavolat rovnou `setAuthenticationPhoneNumber` (TDLib ho tam pustí),
     * jenže automat by pořád hlásil kód nebo heslo a obrazovka čísla by existovala jen u nás.
     * Restart vrátí TDLib do `WaitPhoneNumber`, takže obrazovka a automat říkají totéž.
     */
    fun restartLogin() {
        if (_busy.value) return
        input = ""
        _error.value = null
        viewModelScope.launch {
            _busy.value = true
            session.restartLogin().onFailure { failure ->
                logger.w(failure) { "Návrat z QR na zadání čísla se nepodařil." }
                _error.value = failure.userMessage(Res.string.auth_error_restart)
            }
            _busy.value = false
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

/**
 * Krok uvnitř [AuthStage.WaitPassword]. TDLib zná jediný stav — heslo, kód z e-mailu i nové heslo
 * se volají nad ním —, takže kde uživatel v obnově je, drží aplikace.
 */
enum class PasswordStep {
    /** Běžné zadání hesla. */
    Password,

    /** Kód z obnovovacího e-mailu. */
    RecoveryCode,

    /** Nové heslo a nápověda; kód už server uznal. */
    NewPassword,
}

/**
 * Jestli má zpět kam vést. Z čísla ne — tam zpět aplikaci zavře jako dřív, protože před číslem
 * nic není; z ostatních kroků k číslu.
 */
internal fun AuthStage.canGoBack(): Boolean = when (this) {
    is AuthStage.WaitCode, is AuthStage.WaitPassword, is AuthStage.WaitQrConfirmation,
    AuthStage.WaitEmailAddress, is AuthStage.WaitEmailCode -> true
    else -> false
}

/**
 * Jestli přihlášení nabízí proxy. Jen v krocích, kde se čeká na uživatele: start klienta
 * (`Connecting`) ještě nemá parametry TDLib a odhlašování ani chyba proxy nespraví.
 */
internal fun AuthStage.offersProxy(): Boolean = when (this) {
    AuthStage.WaitPhoneNumber, is AuthStage.WaitCode, is AuthStage.WaitPassword,
    is AuthStage.WaitQrConfirmation, AuthStage.WaitRegistration,
    AuthStage.WaitEmailAddress, is AuthStage.WaitEmailCode -> true
    else -> false
}
