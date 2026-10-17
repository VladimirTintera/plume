package eu.tintera.plume.core.locale

import android.app.LocaleManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.LocaleList
import android.provider.Settings
import kotlinx.coroutines.flow.MutableSharedFlow
import java.util.Locale
import androidx.core.content.edit
import android.content.res.Resources
import android.content.res.Configuration
import androidx.core.net.toUri
import co.touchlab.kermit.Logger

/**
 * Android: `LocaleManager` (od Androidu 13). Seznam jazyků pro systémový výběr nese
 * `res/xml/locales_config.xml` v `:client:androidApp`.
 *
 * Do Androidu 12 jazyk pro jednu aplikaci systém nezná, a tak ho drží aplikace sama
 * ([LegacyAppLanguage]): volba v `SharedPreferences`, výchozí `Locale` procesu a konfigurace
 * aktivit. AppCompat (`AppCompatDelegate.setApplicationLocales`) by to uměl taky, jenže jen
 * s `AppCompatActivity` a motivem AppCompat — kvůli jedné volbě by se měnily obě aktivity.
 */
actual class AppLanguage(private val context: Context) {

    actual val isSupported: Boolean = true

    actual val canChooseInApp: Boolean = true

    actual fun current(): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return LegacyAppLanguage.stored(context)
        val locales = context.getSystemService(LocaleManager::class.java).applicationLocales
        if (locales.isEmpty) return null
        // `cs-CZ` z `cmd locale set-app-locales` je pořád čeština.
        val language = locales[0].language
        return language.takeIf { it in APP_LANGUAGES }
    }

    actual fun set(language: String?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            LegacyAppLanguage.set(context, language)
            return
        }
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

actual fun nativeLanguageName(code: String): String {
    val locale = java.util.Locale.forLanguageTag(code)
    return locale.getDisplayName(locale).replaceFirstChar { it.titlecase(locale) }
}

/**
 * Jazyk aplikace do Androidu 12, kde ho systém pro jednu aplikaci nezná.
 *
 * Compose Resources berou jazyk z výchozího `Locale` procesu (v kompozici přes `Locale.current`,
 * mimo ni `getSystemResourceEnvironment`, tedy i notifikace a snackbary), kalendář M3 a formáty
 * dat z konfigurace aktivity. Proto tři místa: kontext aplikace ([wrapApplication]), výchozí
 * `Locale` ([applyToProcess]) a konfigurace aktivity ([overrideConfiguration]).
 *
 * **Výchozí `Locale` samo nestačí, Android ho přepisuje.** Při změnách konfigurace (i při
 * spuštění aktivity) ho framework nastaví znovu podle jazyka KONTEXTU APLIKACE
 * (`ActivityThread.updateLocaleListFromAppContext`). Bez [wrapApplication] tak vycházely kanály
 * notifikací zakládané při startu napůl česky a napůl anglicky — podle toho, kdy je čtení
 * textu potkalo (ověřeno na emulátoru s Androidem 11, při každém startu jinak).
 *
 * **Jen jazyk, ne celá konfigurace.** Aktivity si změny rozměrů, motivu a hustoty obsluhují
 * samy (`configChanges` v manifestu); celá konfigurace podstrčená v `attachBaseContext` by v nich
 * ty hodnoty zmrazila a třeba rozložený Fold by dál měřil jako složený. Prázdná konfigurace
 * s jazykem je pro `applyOverrideConfiguration` přírůstek a Android ji přenese i do
 * `onConfigurationChanged`.
 */
object LegacyAppLanguage {

    /** Změna jazyka: aktivity se mají znovu vytvořit, aby se přeložily (`recreate`). */
    val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private val isLegacy: Boolean get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU

    /** Uložená volba (kód z [APP_LANGUAGES]), `null` = podle systému nebo novější Android. */
    fun stored(context: Context): String? {
        if (!isLegacy) return null
        return prefs(context).getString(KEY, null)?.takeIf { it in APP_LANGUAGES }
    }

    internal fun set(context: Context, language: String?) {
        prefs(context).edit { if (language == null) remove(KEY) else putString(KEY, language) }
        // Kontext aplikace je obalený už od startu; změnu za běhu dostane jeho konfigurace
        // přímo, jinak by ji framework při příští změně konfigurace vrátil (viz výš).
        val resources = context.applicationContext.resources
        val updated = Configuration(resources.configuration).apply { setLocales(LocaleList(chosenLocale(context))) }
        @Suppress("DEPRECATION")
        resources.updateConfiguration(updated, resources.displayMetrics)
        applyToProcess(context)
        changes.tryEmit(Unit)
    }

    /**
     * Kontext aplikace s jazykem z volby, pro `Application.attachBaseContext`. Jen přírůstek
     * (prázdná konfigurace s jazykem), takže ostatní hodnoty dál sledují systém.
     */
    fun wrapApplication(base: Context): Context {
        val language = stored(base) ?: return base
        return base.createConfigurationContext(Configuration().apply { setLocales(LocaleList(Locale.forLanguageTag(language))) })
    }

    /** Výchozí `Locale` procesu podle volby; bez volby jazyk systému. */
    fun applyToProcess(context: Context) {
        if (!isLegacy) return
        val locale = chosenLocale(context)
        if (Locale.getDefault() != locale) Locale.setDefault(locale)
    }

    /** Zvolený jazyk, bez volby jazyk systému. */
    private fun chosenLocale(context: Context): Locale =
        stored(context)?.let(Locale::forLanguageTag) ?: Resources.getSystem().configuration.locales[0]

    /**
     * Konfigurace s jazykem pro `applyOverrideConfiguration` v `attachBaseContext` aktivity;
     * `null` = není co přepisovat (bez volby, novější Android).
     */
    fun overrideConfiguration(context: Context): Configuration? {
        val language = stored(context) ?: return null
        applyToProcess(context)
        return Configuration().apply { setLocales(LocaleList(Locale.forLanguageTag(language))) }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private const val PREFS = "app_language"
    private const val KEY = "language"
}
