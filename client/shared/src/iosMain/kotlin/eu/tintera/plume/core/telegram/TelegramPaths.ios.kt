package eu.tintera.plume.core.telegram

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
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
}

@OptIn(ExperimentalForeignApi::class)
private fun applicationSupportDirectory(): String {
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
