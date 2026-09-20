package eu.tintera.plume.core.telegram

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSLocale
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

/** `preferredLanguages` je „cs-CZ"; TDLib chce samotný kód jazyka. */
actual val platformLanguageCode: String
    get() = (NSLocale.preferredLanguages.firstOrNull() as? String)
        ?.substringBefore('-')
        ?: "en"
