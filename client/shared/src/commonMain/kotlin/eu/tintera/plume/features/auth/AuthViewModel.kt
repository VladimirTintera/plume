package eu.tintera.plume.features.auth

import co.touchlab.kermit.Logger
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.tintera.plume.core.telegram.AuthStage
import eu.tintera.plume.core.telegram.Country
import eu.tintera.plume.core.telegram.TelegramSession
import eu.tintera.plume.core.telegram.userMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.auth_error_generic
import eu.tintera.plume.resources.auth_error_hint_equals_password
import eu.tintera.plume.resources.auth_error_qr
import eu.tintera.plume.resources.auth_error_recovery_request
import eu.tintera.plume.resources.auth_error_restart

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

    private val _phone = MutableStateFlow(PhoneEntry())
    val phone: StateFlow<PhoneEntry> = _phone.asStateFlow()

    /** Seznam zemí pro výběr. Prázdný, dokud nedorazí z TDLib — řádek zatím ukazuje jen předvolbu. */
    private val _countries = MutableStateFlow<List<Country>>(emptyList())
    val countries: StateFlow<List<Country>> = _countries.asStateFlow()

    private val _passwordStep = MutableStateFlow(PasswordStep.Password)

    /** Kde v zadávání hesla uživatel je; mimo [AuthStage.WaitPassword] vždy [PasswordStep.Password]. */
    val passwordStep: StateFlow<PasswordStep> = _passwordStep.asStateFlow()

    private val _hint = MutableStateFlow("")

    /** Nápověda k novému heslu. Jediné pole navíc — ostatní kroky mají jedno pole ([input]). */
    val hint: StateFlow<String> = _hint.asStateFlow()

    private val _recoveryUnavailable = MutableStateFlow(false)

    /** Uživatel chtěl obnovit heslo, ale účet nemá obnovovací e-mail — vysvětlí to dialog. */
    val recoveryUnavailable: StateFlow<Boolean> = _recoveryUnavailable.asStateFlow()

    /**
     * Kód z e-mailu, který server už uznal. Drží se mimo [input], protože v kroku nového hesla
     * pole patří heslu, a kód se posílá až s ním (`recoverAuthenticationPassword`).
     */
    private var recoveryCode: String? = null

    init {
        loadCountries()
        // Obnova je náš postup nad JEDNÍM stavem TDLib. Jakmile automat odejde jinam (přihlášeno,
        // restart), nesmí se do příštího hesla přenést rozdělaný krok.
        viewModelScope.launch {
            stage.collect { if (it !is AuthStage.WaitPassword) resetPasswordStep() }
        }
    }

    private fun resetPasswordStep() {
        _passwordStep.value = PasswordStep.Password
        _hint.value = ""
        recoveryCode = null
    }

    /**
     * Seznam zemí a odhad té naší. **Odhad se použije, jen dokud si uživatel nevybral sám**
     * — dorazí po síti a přepsat jím ruční volbu by vypadalo jako chyba.
     */
    private fun loadCountries() {
        viewModelScope.launch {
            val all = session.countries().getOrElse { failure ->
                logger.w(failure) { "Seznam zemí se nepodařilo načíst." }
                return@launch
            }
            _countries.value = all

            val suggested = session.suggestedCountryCode().getOrNull() ?: return@launch
            if (_phone.value.country != null) return@launch
            all.firstOrNull { it.code.equals(suggested, ignoreCase = true) }
                ?.let { selectCountry(it) }
        }
    }

    fun selectCountry(country: Country) {
        _phone.value = _phone.value.copy(country = country)
        _error.value = null
        reformatPhone()
    }

    /**
     * Uživatel píše národní část. Bere se z ní jen to, co je číslice — oddělovače dodá
     * formátování a znaky navíc (mezery z vložené schránky) by se do čísla neměly dostat.
     *
     * **Vložené mezinárodní číslo se pozná podle `+`** a rozdělí se na zemi a zbytek; jinak by
     * uživatel musel po vložení předvolbu ručně mazat.
     */
    fun onPhoneChange(value: String) {
        _error.value = null
        if (value.startsWith('+')) {
            adoptInternational(value)
            return
        }
        _phone.value = _phone.value.copy(digits = value.filter { it.isDigit() })
        reformatPhone()
    }

    private fun adoptInternational(value: String) {
        viewModelScope.launch {
            val normalized = "+" + value.filter { it.isDigit() }
            val info = session.phoneNumberInfo(normalized).getOrNull()
            val country = info?.country?.let { found -> _countries.value.firstOrNull { it.code == found.code } ?: found }
            if (country == null) {
                _phone.value = _phone.value.copy(digits = normalized.drop(1))
            } else {
                _phone.value = PhoneEntry(
                    country = country,
                    digits = normalized.removePrefix(country.callingCode),
                )
            }
            reformatPhone()
        }
    }

    /**
     * Formátování dělá TDLib (`getPhoneNumberInfoSync`), ne vlastní tabulka. Je to lokální dotaz,
     * takže se smí volat při každém úhozu; kdyby selhal, ukáže se číslo neformátované — to je
     * pořád použitelné pole.
     */
    private fun reformatPhone() {
        val entry = _phone.value
        viewModelScope.launch {
            val mask = session.phoneNumberInfo(entry.international).getOrNull()?.mask
            val formatted = mask?.let(::formatNationalNumber) ?: entry.digits
            // Mezitím mohl uživatel psát dál; přepsat by znamenalo skákat kurzorem.
            if (_phone.value.digits != entry.digits || _phone.value.country != entry.country) return@launch
            _phone.value = _phone.value.copy(formatted = formatted)
        }
    }

    // Session startuje `startKoin`, ne tenhle ViewModel — TDLib musí být inicializovaný
    // i když se žádné UI neskládá (probuzení procesu pushem).

    fun onInputChange(value: String) {
        _input.value = value
        _error.value = null
    }

    fun onHintChange(value: String) {
        _hint.value = value
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
        val value = if (secret) _input.value else _input.value.trim()
        val ready = if (stageNow == AuthStage.WaitPhoneNumber) _phone.value.isComplete else value.isNotEmpty()
        if (!ready || _busy.value) return

        // Server nápovědu shodnou s heslem odmítne taky, ale až po odeslání a obecnou chybou.
        val newHint = _hint.value.trim()
        if (stageNow is AuthStage.WaitPassword && step == PasswordStep.NewPassword && newHint == value) {
            _error.value = UiText(Res.string.auth_error_hint_equals_password)
            return
        }

        viewModelScope.launch {
            _busy.value = true
            val result = when (stageNow) {
                AuthStage.WaitPhoneNumber -> session.submitPhoneNumber(_phone.value.international)
                is AuthStage.WaitCode -> session.submitCode(value)
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
                .onSuccess { _input.value = "" }
                .onFailure { failure ->
                    logger.w(failure) { "Přihlašovací krok selhal." }
                    _error.value = failure.userMessage(Res.string.auth_error_generic)
                }
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
                    _input.value = ""
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
                    _input.value = recoveryCode.orEmpty()
                    _hint.value = ""
                    return
                }
                PasswordStep.RecoveryCode -> {
                    _passwordStep.value = PasswordStep.Password
                    _input.value = ""
                    return
                }
                PasswordStep.Password -> Unit
            }
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
        _input.value = ""
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
    is AuthStage.WaitCode, is AuthStage.WaitPassword, is AuthStage.WaitQrConfirmation -> true
    else -> false
}
