package eu.tintera.plume.features.auth

import eu.tintera.plume.core.telegram.Country

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
