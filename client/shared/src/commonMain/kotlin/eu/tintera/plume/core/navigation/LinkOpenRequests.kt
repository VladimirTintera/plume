package eu.tintera.plume.core.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Odkaz na Telegram, který přišel zvenčí: z jiné aplikace (`t.me/…`, `tg://…` přes systém)
 * nebo z čtečky QR kódů. Otevírá ho shell ([eu.tintera.plume.features.links.ExternalLinkHost]),
 * ne obrazovka, ze které přišel — žádná tu být nemusí.
 *
 * Stav, ne událost, ze stejného důvodu jako [ChatOpenRequests]: odkaz může přijít do aplikace,
 * která teprve naběhla nebo visí na přihlášení, a musí si počkat, až ho bude mít kdo otevřít.
 * Čeká vždy jen jeden — novější přepíše starší, jako druhé ťuknutí na odkaz.
 */
class LinkOpenRequests {

    private val pending = MutableStateFlow<LinkOpenRequest?>(null)

    val request: StateFlow<LinkOpenRequest?> = pending.asStateFlow()

    fun request(url: String) {
        pending.value = LinkOpenRequest(url, serial++)
    }

    /** Vyřízeno; porovnává se s tím, co volající opravdu vyzvedl (viz [ChatOpenRequests.consume]). */
    fun consume(request: LinkOpenRequest) {
        pending.compareAndSet(request, null)
    }

    private var serial = 0L
}

/**
 * Odkaz k otevření. [serial] odliší dvakrát po sobě poslaný tentýž odkaz — druhé naskenování
 * stejného kódu se má otevřít znovu, ne splynout s prvním.
 */
data class LinkOpenRequest(val url: String, val serial: Long)

/**
 * Vypadá text jako odkaz na Telegram? Hrubé síto pro přečtený QR kód: co odkaz doopravdy
 * znamená, rozhodne až TDLib (`getInternalLinkType`). Tady se jen nepustí dál QR kód Wi-Fi,
 * vizitka nebo cizí web — ty by skončily obecnou hláškou „odkaz nejde otevřít", ze které
 * uživatel nepozná, že naskenoval něco jiného.
 *
 * Domény jsou tytéž, které Plume přijímá z jiných aplikací (`AndroidManifest.xml`).
 */
fun isTelegramLink(text: String): Boolean {
    val scheme = text.substringBefore(':', missingDelimiterValue = "").lowercase()
    // `tg:resolve?…` i `tg://resolve?…` — obojí TDLib bere.
    if (scheme == "tg") return text.substringAfter(':').trimStart('/').isNotEmpty()
    if ((scheme != "http" && scheme != "https") || "://" !in text) return false
    val host = text.substringAfter("://").substringBefore('/').substringBefore('?').substringBefore('#')
        .substringAfterLast('@').substringBefore(':').lowercase()
    return host in TELEGRAM_HOSTS || TELEGRAM_HOSTS.any { host.endsWith(".$it") }
}

/** `t.me` a jeho starší podoby; subdoména (`jmeno.t.me`) je veřejný chat. */
private val TELEGRAM_HOSTS = setOf("t.me", "telegram.me", "telegram.dog")
