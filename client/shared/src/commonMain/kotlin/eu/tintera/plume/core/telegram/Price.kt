package eu.tintera.plume.core.telegram

/** Co nese karta od bota: fakturu, nebo hru. */
enum class ProductKind { Invoice, Game }

/**
 * Cena faktury tak, jak ji posílá TDLib: [amount] v NEJMENŠÍCH jednotkách měny ([currency] je
 * kód ISO 4217, hvězdy Telegramu `XTR`). Kolik desetinných míst měna má, TDLib neříká — viz
 * [currencyExponent].
 */
data class Price(val amount: Long, val currency: String) {
    /** Platí se hvězdami Telegramu — karta je ukazuje ikonou, ne kódem měny. */
    val isStars: Boolean get() = currency == STARS_CURRENCY
}

/** Cena rozložená na celou část a desetiny; desetinný oddělovač patří jazyku, ne číslu. */
data class PriceParts(val whole: Long, val fraction: String?)

/**
 * Částka v nejmenších jednotkách převedená na celou část a desetiny podle počtu desetinných míst
 * měny: 1250 EUR = 12 a „50", 1250 JPY = 1250 bez desetin, 1250 KWD = 1 a „250".
 */
fun Price.parts(): PriceParts {
    val exponent = currencyExponent(currency)
    if (exponent == 0) return PriceParts(whole = amount, fraction = null)
    var divisor = 1L
    repeat(exponent) { divisor *= 10 }
    val fraction = (amount % divisor).toString().padStart(exponent, '0')
    return PriceParts(whole = amount / divisor, fraction = fraction)
}

/**
 * Počet desetinných míst měny. TDLib ho neposílá (oficiální klienti mají tabulku z
 * `currencies.json` od Telegramu); většina měn má dvě, výjimky jsou tady. Neznámá měna dostane
 * dvě — tak to má drtivá většina a chyba se pozná jen jako posunutá desetinná čárka.
 */
internal fun currencyExponent(currency: String): Int = when (currency.uppercase()) {
    STARS_CURRENCY, "BIF", "CLP", "DJF", "GNF", "ISK", "JPY", "KMF", "KRW", "PYG", "RWF",
    "UGX", "VND", "VUV", "XAF", "XOF", "XPF",
    -> 0
    "BHD", "IQD", "JOD", "KWD", "LYD", "OMR", "TND" -> 3
    else -> 2
}

private const val STARS_CURRENCY = "XTR"
