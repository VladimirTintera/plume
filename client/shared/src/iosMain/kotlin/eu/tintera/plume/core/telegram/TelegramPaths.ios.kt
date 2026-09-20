package eu.tintera.plume.core.telegram

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSLocale
import platform.Foundation.NSLocaleCountryCode
import platform.Foundation.preferredLanguages
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUserDomainMask
import platform.UIKit.UIDevice

/**
 * Application Support, ne Documents: Documents se zálohuje do iCloudu a je vidět v souborové appce.
 * Šifrovaná databáze zpráv tam nepatří — patří do Application Support, který systém považuje
 * za data spravovaná aplikací.
 */
actual class TelegramPaths {
    actual val databaseDirectory: String = applicationSupportDirectory() + "/tdlib"
    actual val filesDirectory: String = applicationSupportDirectory() + "/tdlib-files"

    /**
     * Application Support se na rozdíl od Androidu zálohuje (iCloud i lokální záloha), takže se
     * oba adresáře musí označit `NSURLIsExcludedFromBackupKey`. Příznak jde nastavit jen na
     * existující cestu — proto se adresáře napřed zakládají.
     */
    actual fun prepare() {
        excludeFromBackup(databaseDirectory)
        excludeFromBackup(filesDirectory)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun excludeFromBackup(path: String) {
    val manager = NSFileManager.defaultManager
    if (!manager.fileExistsAtPath(path)) {
        manager.createDirectoryAtPath(
            path = path,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )
    }
    NSURL.fileURLWithPath(path, isDirectory = true)
        .setResourceValue(value = true, forKey = NSURLIsExcludedFromBackupKey, error = null)
}

/** Application Support aplikace. Sdílí ho databáze TDLib i nastavení aplikace. */
@OptIn(ExperimentalForeignApi::class)
internal fun applicationSupportDirectory(): String {
    val url = NSFileManager.defaultManager.URLForDirectory(
        directory = NSApplicationSupportDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null,
    )
    return requireNotNull(url?.path) { "Application Support adresář není dostupný." }
}

actual val platformDeviceModel: String = UIDevice.currentDevice.model

actual val platformSystemVersion: String =
    "${UIDevice.currentDevice.systemName} ${UIDevice.currentDevice.systemVersion}"

/**
 * Locale se staví z [platformLanguageCode], NE z `NSLocale.currentLocale`.
 *
 * `currentLocale` jde za regionálním nastavením systému, ne za jazykem aplikace: s českou
 * aplikací na anglickém simulátoru vracel „Czechia" k česky psanému zbytku obrazovky. Název
 * země má být v tom, v čem uživatel čte všechno ostatní, takže se bere tentýž zdroj jazyka,
 * jaký dostává i TDLib.
 *
 * Neznámý region vrací `displayNameForKey` jako `null`, takže není co odfiltrovávat navíc.
 */
actual fun localizedCountryName(code: String): String? =
    NSLocale(localeIdentifier = platformLanguageCode)
        .displayNameForKey(NSLocaleCountryCode, code)
        ?.takeIf { it.isNotBlank() && !it.equals(code, ignoreCase = true) }

/** `preferredLanguages` je „cs-CZ"; jazyk se z toho bere bez regionu. */
actual val platformLanguageCode: String
    get() = (NSLocale.preferredLanguages.firstOrNull() as? String)
        ?.substringBefore('-')
        ?: "en"
