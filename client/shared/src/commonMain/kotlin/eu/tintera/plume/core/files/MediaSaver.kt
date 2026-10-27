package eu.tintera.plume.core.files

import androidx.compose.runtime.Composable
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import okio.Path.Companion.toPath

/**
 * Uloží stažený snímek nebo video do galerie telefonu.
 *
 * Soubor TDLib leží v soukromém adresáři aplikace, takže ho ostatní aplikace nevidí a s
 * odhlášením zmizí. Ukládá se **kopie** do sbírky, kterou spravuje systém: Android `MediaStore`
 * (Obrázky / Filmy, podadresář Plume), iOS knihovna fotek.
 */
expect class MediaSaver {
    /**
     * [fileName] je jméno v galerii (viz [savedFileName]). Vrací `null`, když se uložit
     * nepodařilo; příčina jde do logu.
     */
    suspend fun save(path: String, fileName: String, isVideo: Boolean): SavedMedia?
}

/**
 * Uložené médium. [file] je cesta k uloženému souboru tam, kde je to obyčejný soubor, který jde
 * ukázat ve složce ([revealInFolder]) — desktop ukládá do Stažených. Galerie telefonu žádnou
 * takovou cestu nemá (`null`).
 */
data class SavedMedia(val file: String? = null)

/**
 * Otevře správce souborů se složkou souboru [path] a soubor v ní vybere (Průzkumník, Finder).
 * `false`, když to nejde; na telefonu nikdy nic.
 */
expect fun revealInFolder(path: String): Boolean

/**
 * Má platforma galerii, do které se dá ukládat automaticky? Android a iOS ano; desktop ne —
 * ruční „Do galerie" tam ukládá do Stažených, a ty by automatika zahltila.
 */
expect val autosavesToGallery: Boolean

/**
 * Jméno, pod kterým se médium uloží.
 *
 * Soubor si nese své původní jméno ([originalName]) a to zůstává — odesílatel ho vybral. Fotka
 * a video jméno nemají; TDLib je ukládá jako `5868681087951770588_121.jpg`, což v galerii nic
 * neřekne. Dostanou proto `Plume_20260918_233017.jpg` podle času zprávy, jak to dělá foťák.
 * Stejné jméno podruhé nevadí, obě platformy ho samy očíslují.
 */
fun savedFileName(originalName: String?, sourcePath: String, timestamp: Int, zone: TimeZone): String {
    if (!originalName.isNullOrBlank()) return originalName
    val moment = Instant.fromEpochSeconds(timestamp.toLong()).toLocalDateTime(zone)
    fun Int.pad() = toString().padStart(2, '0')
    val stamp = "${moment.year}${moment.month.number.pad()}${moment.day.pad()}_" +
        "${moment.hour.pad()}${moment.minute.pad()}${moment.second.pad()}"
    val extension = sourcePath.toPath().name.substringAfterLast('.', "")
    return if (extension.isEmpty()) "Plume_$stamp" else "Plume_$stamp.$extension"
}

/**
 * Oprávnění zapisovat do galerie. [request] se zeptá systému jen tam, kde je to potřeba:
 * Android od verze 10 zápis do vlastních položek `MediaStore` nepodmiňuje ničím, iOS chce
 * souhlas s přidáváním do knihovny fotek.
 */
interface SavePermission {
    fun request(onResult: (granted: Boolean) -> Unit)
}

@Composable
expect fun rememberSavePermission(): SavePermission
