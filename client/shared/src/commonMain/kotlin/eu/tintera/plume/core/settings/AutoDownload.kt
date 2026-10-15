package eu.tintera.plume.core.settings

import eu.tintera.plume.core.connectivity.NetworkKind

/**
 * Kolik se smí stáhnout samo, bez ťuknutí. Volí se zvlášť pro každý druh sítě
 * ([AutoDownloadSettings]) a ukládá se názvem, jako ostatní výčty v [AppSettings].
 *
 * Úrovně místo jednotlivých přepínačů a stropů po druzích: Plume stahuje samo jen tři věci
 * (fotky ve zprávách, GIFy a kolečka, obrázky poslané jako soubor), takže by šest voleb na síť
 * nastavovalo rozdíly, které uživatel nepozná. Limity jsou v [limits].
 */
enum class AutoDownloadLevel { Off, Low, Medium, High }

/**
 * Úroveň pro každý druh sítě. Výchozí: Wi-Fi všechno, mobilní data jako dřív (fotky, GIFy
 * a kolečka do 5 MB), roaming jen fotky a drobnosti — roaming je drahý a nikdo nečeká, že
 * se v něm budou samy stahovat videa.
 */
data class AutoDownloadSettings(
    val wifi: AutoDownloadLevel = AutoDownloadLevel.High,
    val mobile: AutoDownloadLevel = AutoDownloadLevel.Medium,
    val roaming: AutoDownloadLevel = AutoDownloadLevel.Low,
)

/**
 * Co se smí stáhnout samo právě teď.
 *
 * [photos]: fotka ve zprávě (i v albu a v náhledu odkazu). [maxVideoBytes]: GIF a kolečko
 * se samy přehrají jen do téhle velikosti. [maxFileBytes]: obrázek poslaný jako soubor se kvůli
 * ostrému náhledu stáhne celý jen do téhle velikosti. Nula = nic.
 *
 * [maxVideoLines] je strop automatické kvality videa v prohlížeči (kratší strana, „720p",
 * `pickVideoQuality` v `core/video`); [Int.MAX_VALUE] = původní soubor. Video se sice samo
 * nestahuje, ale puštěné na mobilních datech nemá sníst stovky megabajtů, když server nabízí
 * menší kvalitu.
 */
data class AutoDownloadLimits(
    val photos: Boolean,
    val maxVideoBytes: Long,
    val maxFileBytes: Long,
    val maxVideoLines: Int,
)

val AutoDownloadLevel.limits: AutoDownloadLimits
    get() = when (this) {
        AutoDownloadLevel.Off -> AutoDownloadLimits(photos = false, maxVideoBytes = 0, maxFileBytes = 0, maxVideoLines = OFF_VIDEO_LINES)
        AutoDownloadLevel.Low -> AutoDownloadLimits(photos = true, maxVideoBytes = LOW_VIDEO_BYTES, maxFileBytes = 0, maxVideoLines = LOW_VIDEO_LINES)
        AutoDownloadLevel.Medium -> AutoDownloadLimits(photos = true, maxVideoBytes = MEDIUM_BYTES, maxFileBytes = MEDIUM_BYTES, maxVideoLines = MEDIUM_VIDEO_LINES)
        AutoDownloadLevel.High -> AutoDownloadLimits(photos = true, maxVideoBytes = HIGH_BYTES, maxFileBytes = HIGH_BYTES, maxVideoLines = Int.MAX_VALUE)
    }

/**
 * Úroveň, která platí na síti [kind].
 *
 * [saveData] je systémový spořič dat (Android) nebo režim nízkých dat (iOS): uživatel jím
 * řekl, že data šetří, a aplikace, která to přeslechne, mu je stejně snědí. Na mobilní síti
 * proto úroveň klesne nejvýš na [AutoDownloadLevel.Low]; na Wi-Fi se spořič neuplatní, protože
 * Android ho na neměřené síti nevynucuje.
 *
 * Ethernet, VPN a ostatní ([NetworkKind.Other]) se berou jako Wi-Fi. Bez sítě ([NetworkKind.None])
 * se nic nestáhne tak jako tak; platí úroveň mobilních dat, aby se po návratu sítě nestáhlo
 * víc, než se monitor ozve.
 */
fun autoDownloadLevel(settings: AutoDownloadSettings, kind: NetworkKind, saveData: Boolean): AutoDownloadLevel {
    val level = when (kind) {
        NetworkKind.WiFi, NetworkKind.Other -> settings.wifi
        NetworkKind.Mobile, NetworkKind.None -> settings.mobile
        NetworkKind.MobileRoaming -> settings.roaming
    }
    val metered = kind != NetworkKind.WiFi && kind != NetworkKind.Other
    return if (saveData && metered && level > AutoDownloadLevel.Low) AutoDownloadLevel.Low else level
}

/** Strop GIFů a koleček na úrovni [AutoDownloadLevel.Low]. */
const val LOW_VIDEO_BYTES: Long = 1L * 1024 * 1024

/** Strop na úrovni [AutoDownloadLevel.Medium] — tak se aplikace chovala na všech sítích dřív. */
const val MEDIUM_BYTES: Long = 5L * 1024 * 1024

/** Strop na úrovni [AutoDownloadLevel.High]. */
const val HIGH_BYTES: Long = 20L * 1024 * 1024

/**
 * Stropy automatické kvality videa. „Vypnuto" neznamená, že se video nepustí — uživatel ho pustil
 * sám —, jen že se bere nejmenší rozumná kvalita. „Naplno" (výchozí na Wi-Fi) hraje původní
 * soubor, jako aplikace dřív všude.
 */
const val OFF_VIDEO_LINES: Int = 360
const val LOW_VIDEO_LINES: Int = 480
const val MEDIUM_VIDEO_LINES: Int = 720
