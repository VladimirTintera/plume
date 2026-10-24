package eu.tintera.plume.core.telegram

/**
 * Kolik dat aplikace přenesla (`getNetworkStatistics`), rozdělené podle sítě a druhu.
 *
 * Dělení podle sítě funguje jen proto, že aplikace TDLib hlásí typ připojení (`setNetworkType`
 * v `ConnectionController`); bez toho by všechno spadlo do jedné sítě.
 *
 * @property sinceDate odkdy se počítá (Unix timestamp) — od instalace, nebo od vynulování.
 * @property networks sítě, kterými něco teklo, v pořadí [UsageNetwork].
 */
data class NetworkUsage(
    val sinceDate: Int,
    val networks: List<NetworkTraffic>,
) {
    val sentBytes: Long get() = networks.sumOf { it.sentBytes }
    val receivedBytes: Long get() = networks.sumOf { it.receivedBytes }
}

/**
 * Přenos jednou sítí.
 *
 * @property kinds druhy, kterými něco teklo, od největšího.
 * @property callSeconds délka hovorů touto sítí.
 */
data class NetworkTraffic(
    val network: UsageNetwork,
    val kinds: List<KindTraffic>,
    val callSeconds: Long,
) {
    val sentBytes: Long get() = kinds.sumOf { it.sentBytes }
    val receivedBytes: Long get() = kinds.sumOf { it.receivedBytes }
}

data class KindTraffic(val kind: TrafficKind, val sentBytes: Long, val receivedBytes: Long) {
    val totalBytes: Long get() = sentBytes + receivedBytes
}

/** Síť, jak ji TDLib rozlišuje. „Žádná" se vynechává — nic jí neteče. */
enum class UsageNetwork { Mobile, WiFi, Roaming, Other }

/**
 * Druh přenosu. Soubory ve stejných druzích jako přehled úložiště ([StorageCategory]), navíc
 * [Messages] — všechno, co není soubor (zprávy, seznam chatů, aktualizace) — a [Calls].
 */
enum class TrafficKind { Messages, Photos, Videos, VoiceMessages, Music, Files, Gifs, Stickers, Other, Calls }

/** Jedna položka statistiky od TDLib, už v doménových pojmech. */
data class TrafficRecord(
    val network: UsageNetwork,
    val kind: TrafficKind,
    val sentBytes: Long,
    val receivedBytes: Long,
    val callSeconds: Long = 0,
)

/**
 * Sečte položky: TDLib jich vrací víc pro tutéž síť a druh (každý typ souboru zvlášť — fotka,
 * fotka z příběhu, mizející fotka…), my je slučujeme do druhů z přehledu úložiště. Síť ani druh
 * bez jediného bajtu se neukazují; hovor bez dat, ale s délkou ano.
 */
fun networkUsageOf(sinceDate: Int, records: List<TrafficRecord>): NetworkUsage = NetworkUsage(
    sinceDate = sinceDate,
    networks = UsageNetwork.entries.mapNotNull { network ->
        val ofNetwork = records.filter { it.network == network }
        val kinds = ofNetwork.groupBy { it.kind }
            .map { (kind, items) -> KindTraffic(kind, items.sumOf { it.sentBytes }, items.sumOf { it.receivedBytes }) }
            .filter { it.totalBytes > 0 }
            .sortedByDescending { it.totalBytes }
        val callSeconds = ofNetwork.sumOf { it.callSeconds }
        if (kinds.isEmpty() && callSeconds == 0L) null else NetworkTraffic(network, kinds, callSeconds)
    },
)
