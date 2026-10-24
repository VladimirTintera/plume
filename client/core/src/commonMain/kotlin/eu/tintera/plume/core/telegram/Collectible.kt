package eu.tintera.plume.core.telegram

/** Co se dá koupit na Fragmentu a ukázat v profilu. */
sealed interface CollectibleItem {
    /** Jméno bez `@`. */
    data class Username(val username: String) : CollectibleItem

    /** Číslo tak, jak ho ukazuje profil (s „+"). */
    data class Phone(val phone: String) : CollectibleItem
}

/**
 * Koupě na Fragmentu: [purchaseDate] v sekundách epochy, cena v kryptoměně
 * ([cryptocurrencyAmount] v nejmenších jednotkách, u TONu nanotony) a přibližně v měně
 * ([price]), [url] je stránka položky na Fragmentu.
 */
data class CollectibleInfo(
    val purchaseDate: Int,
    val price: Price,
    val cryptocurrency: String,
    val cryptocurrencyAmount: Long,
    val url: String,
)

/**
 * Anonymní čísla +888 jsou jen z Fragmentu, jiné číslo sběratelské není. Profil ho ukazuje
 * s „+", TDLib bez něj — bere se obojí.
 */
fun isCollectiblePhone(phone: String): Boolean = phone.removePrefix("+").startsWith(ANONYMOUS_NUMBER_PREFIX)

/**
 * Částka kryptoměny na celou část a desetiny bez koncových nul: 1 250 000 000 000 nanotonů
 * = 1250 TON, 1 500 000 000 = 1 a „5". Desetinných míst má TON devět; jiná kryptoměna na
 * Fragmentu není, neznámá se bere stejně.
 */
fun cryptoAmountParts(amount: Long, cryptocurrency: String): PriceParts {
    val exponent = when (cryptocurrency.uppercase()) {
        "TON" -> TON_DECIMALS
        else -> TON_DECIMALS
    }
    var divisor = 1L
    repeat(exponent) { divisor *= 10 }
    val fraction = (amount % divisor).toString().padStart(exponent, '0').trimEnd('0')
    return PriceParts(whole = amount / divisor, fraction = fraction.ifEmpty { null })
}

/** Jména kromě vlastního (viz [ChatProfile.collectibleUsernames]). */
internal fun collectibleUsernames(active: List<String>, editable: String?): Set<String> =
    active.filterTo(mutableSetOf()) { it.isNotEmpty() && it != editable }

private const val ANONYMOUS_NUMBER_PREFIX = "888"
private const val TON_DECIMALS = 9
