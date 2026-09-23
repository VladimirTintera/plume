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
import eu.tintera.plume.resources.auth_error_qr
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

    init {
        loadCountries()
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

    /** Jedno tlačítko pro všechny fáze — co se pošle, určuje aktuální [AuthStage]. */
    fun submit() {
        val value = _input.value.trim()
        val ready = if (stage.value == AuthStage.WaitPhoneNumber) _phone.value.isComplete else value.isNotEmpty()
        if (!ready || _busy.value) return

        viewModelScope.launch {
            _busy.value = true
            val result = when (stage.value) {
                AuthStage.WaitPhoneNumber -> session.submitPhoneNumber(_phone.value.international)
                is AuthStage.WaitCode -> session.submitCode(value)
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

    /**
     * Zpátky z QR na zadání čísla. Trvá to okamžik (TDLib se zavírá a zakládá nový klient),
     * proto `busy` — a stav se mezitím ohlásí jako `Connecting`, ne jako odhlašování.
     */
    fun restartLogin() {
        if (_busy.value) return
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
