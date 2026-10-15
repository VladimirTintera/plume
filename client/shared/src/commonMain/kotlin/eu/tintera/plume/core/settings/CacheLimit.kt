package eu.tintera.plume.core.settings

/**
 * Strop velikosti stažených médií (Nastavení → Úložiště). Nad ním se při úklidu
 * ([StorageCleaner][eu.tintera.plume.core.storage.StorageCleaner]) mažou soubory, na které se
 * nejdéle nesáhlo — zůstanou na serveru a stáhnou se znovu, až je někdo otevře.
 *
 * [bytes] je `null` u [None]: bez stropu. Ukládá se názvem, jako ostatní výčty; neznámý název
 * z novější verze spadne na [None] — volba, které nerozumíme, nesmí znamenat mazání.
 */
enum class CacheLimit(val bytes: Long?) {
    OneGb(1 * GB),
    TwoGb(2 * GB),
    FiveGb(5 * GB),
    TenGb(10 * GB),
    None(null),
}

private const val GB = 1024L * 1024 * 1024
