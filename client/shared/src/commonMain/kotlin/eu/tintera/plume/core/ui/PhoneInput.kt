package eu.tintera.plume.core.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.Country
import eu.tintera.plume.core.telegram.TelegramSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Rozepsané telefonní číslo: vybraná země zvlášť, národní část zvlášť.
 *
 * Dvě pole místo jednoho proto, že předvolba není text, který by uživatel psal — je to volba,
 * a jako volba se i ukazuje (vlajka, název země). Do TDLib se posílá [international].
 */
data class PhoneEntry(
    val country: Country? = null,
    /** Národní část, jen číslice. Formátování je věc zobrazení, ne stavu. */
    val digits: String = "",
    /** Národní část, jak se ukazuje v poli. Skládá ji TDLib, viz [formatNationalNumber]. */
    val formatted: String = "",
) {
    val callingCode: String get() = country?.callingCode ?: DEFAULT_CALLING_CODE

    /** Číslo pro TDLib. Mezery a pomlčky z formátování se nepřikládají. */
    val international: String get() = "$callingCode$digits"

    val isComplete: Boolean get() = digits.isNotEmpty()

    companion object {
        /** Než dorazí seznam zemí. Ukáže se jen na zlomek vteřiny při startu. */
        const val DEFAULT_CALLING_CODE = "+"
    }
}

/**
 * Národní část z masky, kterou vrací TDLib (`formattedPhoneNumber`).
 *
 * V masce **`-` znamená očekávanou číslici**, cokoli jiného je oddělovač nebo už zadaná číslice.
 * Ukazovat ji celou nejde — „777 --- ---" vypadá jako rozbité pole —, takže se z ní bere jen
 * to, co uživatel opravdu napsal: konec od první očekávané číslice se zahodí a zbylé oddělovače
 * se oříznou.
 *
 * Vlastní tabulku formátů tím držet nemusíme; zemí je přes dvě stě a jejich předpisy se mění.
 */
fun formatNationalNumber(mask: String): String =
    mask.substringBefore(EXPECTED_DIGIT).trimEnd { !it.isDigit() }

private const val EXPECTED_DIGIT = '-'

/**
 * Zadávání čísla s výběrem země pro ViewModel: seznam zemí, odhad podle IP, formátování od
 * TDLib a vložené mezinárodní číslo. **Sdílí ho přihlášení a nový kontakt** — obojí je „zadej
 * číslo" a dvě kopie by se rozešly (formátování, vložení s `+`). Kreslí ho [PhoneField].
 *
 * [phone] je Compose stav, ne `StateFlow`: hodnota ze `StateFlow` by do pole dorazila až dalším
 * průchodem collectoru a znak napsaný mezitím by se zahodil.
 */
class PhoneInput(
    private val session: TelegramSession,
    private val scope: CoroutineScope,
) {
    private val logger = Logger.withTag("PhoneInput")

    var phone by mutableStateOf(PhoneEntry())
        private set

    /** Seznam zemí pro výběr. Prázdný, dokud nedorazí z TDLib — řádek zatím ukazuje jen předvolbu. */
    private val _countries = MutableStateFlow<List<Country>>(emptyList())
    val countries: StateFlow<List<Country>> = _countries.asStateFlow()

    /**
     * Seznam zemí a odhad té naší. **Odhad se použije, jen dokud si uživatel nevybral sám**
     * — dorazí po síti a přepsat jím ruční volbu by vypadalo jako chyba.
     */
    suspend fun loadCountries() {
        val all = session.countries().getOrElse { failure ->
            logger.w(failure) { "Seznam zemí se nepodařilo načíst." }
            return
        }
        _countries.value = all

        val suggested = session.suggestedCountryCode().getOrNull() ?: return
        if (phone.country != null) return
        all.firstOrNull { it.code.equals(suggested, ignoreCase = true) }
            ?.let { selectCountry(it) }
    }

    fun selectCountry(country: Country) {
        phone = phone.copy(country = country)
        reformat()
    }

    /**
     * Uživatel píše národní část. Bere se z ní jen to, co je číslice — oddělovače dodá
     * formátování a znaky navíc (mezery z vložené schránky) by se do čísla neměly dostat.
     *
     * **Vložené mezinárodní číslo se pozná podle `+`** a rozdělí se na zemi a zbytek; jinak by
     * uživatel musel po vložení předvolbu ručně mazat.
     */
    fun onPhoneChange(value: String) {
        if (value.startsWith('+')) {
            adoptInternational(value)
            return
        }
        phone = phone.copy(digits = value.filter { it.isDigit() })
        reformat()
    }

    private fun adoptInternational(value: String) {
        scope.launch {
            val normalized = "+" + value.filter { it.isDigit() }
            val info = session.phoneNumberInfo(normalized).getOrNull()
            val country = info?.country?.let { found -> _countries.value.firstOrNull { it.code == found.code } ?: found }
            if (country == null) {
                phone = phone.copy(digits = normalized.drop(1))
            } else {
                phone = PhoneEntry(
                    country = country,
                    digits = normalized.removePrefix(country.callingCode),
                )
            }
            reformat()
        }
    }

    /**
     * Formátování dělá TDLib (`getPhoneNumberInfoSync`), ne vlastní tabulka. Je to lokální dotaz,
     * takže se smí volat při každém úhozu; kdyby selhal, ukáže se číslo neformátované — to je
     * pořád použitelné pole.
     */
    private fun reformat() {
        val entry = phone
        scope.launch {
            val mask = session.phoneNumberInfo(entry.international).getOrNull()?.mask
            val formatted = mask?.let(::formatNationalNumber) ?: entry.digits
            // Mezitím mohl uživatel psát dál; přepsat by znamenalo skákat kurzorem.
            if (phone.digits != entry.digits || phone.country != entry.country) return@launch
            phone = phone.copy(formatted = formatted)
        }
    }
}
