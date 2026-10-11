package eu.tintera.plume.core.telegram

import eu.tintera.plume.core.platform.desktopDataDirectory
import eu.tintera.plume.core.platform.desktopDebugBuild
import java.io.File
import java.util.Locale

/** Data aplikace podle systému, viz [desktopDataDirectory]. Zálohu desktop neřeší za aplikaci. */
actual class TelegramPaths {
    actual val databaseDirectory: String = File(desktopDataDirectory, "tdlib").path
    actual val filesDirectory: String = File(desktopDataDirectory, "tdlib-files").path

    actual val isDebugBuild: Boolean = desktopDebugBuild

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
