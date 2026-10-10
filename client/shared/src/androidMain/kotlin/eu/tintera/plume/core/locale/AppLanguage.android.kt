package eu.tintera.plume.core.locale

import android.app.LocaleManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.LocaleList
import android.provider.Settings
import androidx.core.net.toUri
import co.touchlab.kermit.Logger

/**
 * Android: `LocaleManager` (od Androidu 13). Seznam jazyků pro systémový výběr nese
 * `res/xml/locales_config.xml` v `:client:androidApp`.
 *
 * Do Androidu 12 jazyk pro jednu aplikaci systém nezná; obchvat přes `AppCompatDelegate` by
 * znamenal AppCompat kvůli jedné volbě a vlastní ukládání, které systém před startem nepřečte.
 */
actual class AppLanguage(private val context: Context) {

    actual val isSupported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    actual val canChooseInApp: Boolean = isSupported

    actual fun current(): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        val locales = context.getSystemService(LocaleManager::class.java).applicationLocales
        if (locales.isEmpty) return null
        // `cs-CZ` z `cmd locale set-app-locales` je pořád čeština.
        val language = locales[0].language
        return language.takeIf { it in APP_LANGUAGES }
    }

    actual fun set(language: String?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        context.getSystemService(LocaleManager::class.java).applicationLocales =
            if (language == null) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(language)
    }

    actual fun openSystemSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val intent = Intent(Settings.ACTION_APP_LOCALE_SETTINGS, "package:${context.packageName}".toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Logger.withTag("AppLanguage").w(e) { "Nastavení jazyka aplikace nejde otevřít." }
        }
    }
}
