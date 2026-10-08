package eu.tintera.plume.core.settings

/**
 * Jak dlouho si aplikace drží stažená média, na která se nesáhlo. Starší se při úklidu
 * ([StorageCleaner][eu.tintera.plume.core.storage.StorageCleaner]) smažou — zůstanou na serveru
 * a stáhnou se znovu, až je někdo otevře.
 *
 * [seconds] je `null` u [Forever]: tam se neuklízí nic. Ukládá se názvem, jako ostatní výčty.
 */
enum class KeepMedia(val seconds: Int?) {
    ThreeDays(3 * DAY),
    Week(7 * DAY),
    Month(30 * DAY),
    Forever(null),
}

private const val DAY = 24 * 60 * 60
