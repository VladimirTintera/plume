package eu.tintera.plume.core.telegram

import eu.tintera.plume.core.accounts.AccountSlot
import eu.tintera.plume.core.platform.desktopDataDirectory
import eu.tintera.plume.core.platform.desktopDebugBuild
import java.io.File
import java.util.Locale

/** Data aplikace podle systému, viz [desktopDataDirectory]. Zálohu desktop neřeší za aplikaci. */
actual class TelegramPaths(slot: AccountSlot = AccountSlot.Primary) {
    actual val databaseDirectory: String = File(desktopDataDirectory, slot.directory("tdlib")).path
    actual val filesDirectory: String = File(desktopDataDirectory, slot.directory("tdlib-files")).path

    actual val isDebugBuild: Boolean = desktopDebugBuild

    /**
     * Verze balíčku, kterou do spouštěče zapeče jpackage (`packageVersion`, při vydání z tagu).
     * Při `run` z Gradlu vlastnost není — pak „dev".
     */
    actual val applicationVersion: String = System.getProperty("jpackage.app-version") ?: "dev"

    actual fun prepare() {
        File(databaseDirectory).mkdirs()
        File(filesDirectory).mkdirs()
    }
}

actual val platformDeviceModel: String =
    "Desktop (${System.getProperty("os.arch").orEmpty()})"

actual val platformSystemVersion: String =
    "${System.getProperty("os.name").orEmpty()} ${System.getProperty("os.version").orEmpty()}".trim()

actual val platformLanguageCode: String
    get() = Locale.getDefault().language.ifBlank { "en" }

/** Stejně jako na iOS v jazyce aplikace ([platformLanguageCode]), ne podle regionu systému. */
actual fun localizedCountryName(code: String): String? =
    Locale.Builder().setRegion(code.uppercase()).build()
        .getDisplayCountry(Locale.forLanguageTag(platformLanguageCode))
        .takeIf { it.isNotBlank() && !it.equals(code, ignoreCase = true) }

actual fun localizedLanguageName(code: String): String? =
    Locale.forLanguageTag(code).getDisplayName(Locale.forLanguageTag(platformLanguageCode))
        .takeIf { it.isNotBlank() && !it.equals(code, ignoreCase = true) }
        ?.replaceFirstChar { it.uppercaseChar() }
