package eu.tintera.plume.core.locale

import co.touchlab.kermit.Logger
import eu.tintera.plume.core.platform.desktopDataDirectory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.Locale

/**
 * Desktop: jazyk drží aplikace sama ([DesktopAppLanguage]) — systémový výběr jazyka pro jednu
 * aplikaci desktop nemá.
 */
actual class AppLanguage {

    actual val isSupported: Boolean = true

    actual val canChooseInApp: Boolean = true

    actual fun current(): String? = DesktopAppLanguage.stored()

    actual fun set(language: String?) = DesktopAppLanguage.set(language)

    actual fun openSystemSettings() = Unit
}

/**
 * Jazyk aplikace na desktopu: volba v `language` v datech aplikace a výchozí `Locale` JVM,
 * ze kterého čtou Compose Resources (v kompozici přes `Locale.current`, mimo ni
 * `getSystemResourceEnvironment` — oznámení, ikona v oznamovací oblasti).
 *
 * **Po změně se obsah okna skládá znovu** (`Main.kt`, `key` nad [changes]). Jazyk se v kompozici
 * čte jen při skládání a prostředí prostředků jde zvenku přepsat jen interním API; nová kompozice
 * přijde o stav obrazovek (aplikace skončí na seznamu chatů). U volby, která se mění jednou za
 * čas, je to přijatelná cena.
 */
object DesktopAppLanguage {

    private val logger = Logger.withTag("DesktopAppLanguage")

    /** Jazyk systému, zachycený dřív, než ho [applyAtStartup] přepíše. */
    private val systemLocale: Locale = Locale.getDefault()

    private val file: File get() = File(desktopDataDirectory, FILE_NAME)

    private val _changes = MutableStateFlow(0)

    /** Počítadlo změn; obsah okna se skládá znovu pokaždé, když se zvýší. */
    val changes: StateFlow<Int> = _changes.asStateFlow()

    /** Nastaví uložený jazyk jako výchozí `Locale`. Volat v `main` dřív, než se čte první text. */
    fun applyAtStartup() {
        stored()?.let { Locale.setDefault(Locale.forLanguageTag(it)) }
    }

    /** Uložená volba (kód z [APP_LANGUAGES]), `null` = podle systému. */
    fun stored(): String? =
        runCatching { file.takeIf { it.isFile }?.readText()?.trim() }
            .onFailure { logger.w(it) { "Jazyk aplikace se nepodařilo přečíst." } }
            .getOrNull()
            ?.takeIf { it in APP_LANGUAGES }

    fun set(language: String?) {
        runCatching {
            if (language == null) file.delete() else file.apply { parentFile?.mkdirs() }.writeText(language)
        }.onFailure { logger.w(it) { "Jazyk aplikace se nepodařilo uložit." } }
        Locale.setDefault(language?.let(Locale::forLanguageTag) ?: systemLocale)
        _changes.value++
    }

    private const val FILE_NAME = "language"
}

actual fun nativeLanguageName(code: String): String {
    val locale = Locale.forLanguageTag(code)
    return locale.getDisplayName(locale).ifBlank { code }.replaceFirstChar { it.uppercase() }
}
