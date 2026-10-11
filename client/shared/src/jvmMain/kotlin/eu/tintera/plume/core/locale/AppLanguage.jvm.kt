package eu.tintera.plume.core.locale

import java.util.Locale

/**
 * Desktop: jazyk aplikace jde za jazykem systému (`Locale.getDefault()`, ze kterého čtou
 * i Compose Resources). Volba jazyka jen pro aplikaci by chtěla přepnout výchozí locale JVM
 * a složit okno znovu — zatím není.
 */
actual class AppLanguage {

    actual val isSupported: Boolean = false

    actual val canChooseInApp: Boolean = false

    actual fun current(): String? = null

    actual fun set(language: String?) = Unit

    actual fun openSystemSettings() = Unit
}

actual fun nativeLanguageName(code: String): String {
    val locale = Locale.forLanguageTag(code)
    return locale.getDisplayName(locale).ifBlank { code }.replaceFirstChar { it.uppercase() }
}
