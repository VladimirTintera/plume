package eu.tintera.plume.core.currency

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/** Stáhne text z adresy; `null` při jakékoli chybě (síť, server, kódování). */
expect class TextDownloader() {
    suspend fun get(url: String): String?
}

/**
 * Kde uživatel žije — podle REGIONU telefonu, ne jazyka aplikace (česky může psát i někdo, kdo
 * žije v Německu): v jaké měně počítá a jaké jednotky používá.
 */
expect class HomeRegion {
    /** Měna (ISO 4217), nebo `null`, když ji systém neřekne. */
    val code: String?

    /** Metrická soustava; imperiální mají jen USA, Libérie a Myanmar. */
    val metric: Boolean
}

/**
 * Kurzovní lístek ČNB: kolik korun stojí jednotka měny. Koruna sama je 1, takže jde převádět
 * i mezi dvěma cizími měnami (přes korunu, jak to dělají banky).
 */
data class RateTable(val date: LocalDate?, val czkPerUnit: Map<String, Double>) {
    fun convert(amount: Double, from: String, to: String): Double? {
        val fromRate = rateOf(from) ?: return null
        val toRate = rateOf(to) ?: return null
        return amount * fromRate / toRate
    }

    fun supports(code: String): Boolean = rateOf(code) != null

    private fun rateOf(code: String): Double? = if (code == CZK) 1.0 else czkPerUnit[code]
}

/**
 * Denní kurzy ČNB (anglická verze lístku: desetinná tečka, ASCII). Zdarma, bez klíče a z banky,
 * které se dá věřit; kurz je jednou denně, na převod ceny ze zprávy to stačí.
 *
 * - **Obsah zprávy nikam neodchází** — stahuje se jen celý lístek, nic se neposílá.
 * - **Lístek se drží v paměti [CACHE] hodin**; nový vychází v pracovní dny po 14:30.
 */
class ExchangeRates(private val downloader: TextDownloader) {
    private val logger = Logger.withTag("ExchangeRates")
    private val lock = Mutex()
    private var cached: Pair<Instant, RateTable>? = null

    suspend fun table(): RateTable? = lock.withLock {
        val now = Clock.System.now()
        cached?.takeIf { now - it.first < CACHE }?.let { return@withLock it.second }
        val text = try {
            downloader.get(CNB_URL)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.w(e) { "Kurzovní lístek ČNB se nepodařilo stáhnout" }
            null
        }
        val table = text?.let(::parseCnbRates)
        if (table == null) logger.w { "Kurzovní lístek ČNB chybí nebo nejde přečíst" }
        table?.also { cached = now to it }
    }

    private companion object {
        const val CNB_URL =
            "https://www.cnb.cz/en/financial-markets/foreign-exchange-market/central-bank-exchange-rate-fixing/central-bank-exchange-rate-fixing/daily.txt"
        val CACHE = 6.hours
    }
}

const val CZK = "CZK"

/**
 * Lístek ČNB: první řádek datum („09 Oct 2026 #195"), druhý hlavička, pak
 * `Country|Currency|Amount|Code|Rate`. `Amount` je počet jednotek, ke kterému se kurz vztahuje
 * (100 HUF), takže kurz za jednotku je `Rate / Amount`. Řádek, který nejde přečíst, se přeskočí.
 */
fun parseCnbRates(text: String): RateTable? {
    val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
    if (lines.size < 3) return null
    val rates = lines.drop(2).mapNotNull { line ->
        val parts = line.split('|')
        if (parts.size != 5) return@mapNotNull null
        val amount = parts[2].toDoubleOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
        val rate = parts[4].toDoubleOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
        parts[3].trim().uppercase() to rate / amount
    }.toMap()
    if (rates.isEmpty()) return null
    return RateTable(parseCnbDate(lines.first()), rates)
}

private fun parseCnbDate(line: String): LocalDate? {
    val parts = line.substringBefore('#').trim().split(' ')
    if (parts.size != 3) return null
    val day = parts[0].toIntOrNull() ?: return null
    val month = Month.entries.firstOrNull { it.name.startsWith(parts[1].uppercase()) } ?: return null
    val year = parts[2].toIntOrNull() ?: return null
    return runCatching { LocalDate(year, month, day) }.getOrNull()
}
