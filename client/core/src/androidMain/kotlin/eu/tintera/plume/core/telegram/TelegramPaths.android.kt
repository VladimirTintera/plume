package eu.tintera.plume.core.telegram

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import eu.tintera.plume.core.accounts.AccountSlot
import java.io.File
import java.util.Locale

/**
 * `filesDir` je privátní pro aplikaci a systém ho nezálohuje do cloudu bez našeho svolení —
 * pro šifrovanou databázi zpráv je to správné místo. Cache adresář by byl špatně: systém ho
 * může kdykoli vymazat a TDLib by přišel o stav.
 */
actual class TelegramPaths(context: Context, slot: AccountSlot = AccountSlot.Primary) {
    actual val databaseDirectory: String = context.filesDir.resolve(slot.directory("tdlib")).absolutePath
    actual val filesDirectory: String = context.filesDir.resolve(slot.directory("tdlib-files")).absolutePath

    /** Z manifestu aplikace, ne z knihovny: `:client:shared` se staví stejně pro debug i release. */
    actual val isDebugBuild: Boolean = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    /** `versionName` z manifestu aplikace (z tagu při vydání), stejně jako [isDebugBuild]. */
    actual val applicationVersion: String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull() ?: UNKNOWN_VERSION

    /**
     * Na Androidu stačí adresáře založit: zálohu vypíná manifest (`allowBackup="false"`) pro celou
     * aplikaci, takže se tu nic označovat nemusí.
     */
    actual fun prepare() {
        File(databaseDirectory).mkdirs()
        File(filesDirectory).mkdirs()
    }
}

private const val UNKNOWN_VERSION = "?"

actual val platformDeviceModel: String = "${Build.MANUFACTURER} ${Build.MODEL}"

actual val platformSystemVersion: String = "Android ${Build.VERSION.RELEASE}"

actual val platformLanguageCode: String get() = Locale.getDefault().language

/**
 * `Locale.getDefault()` sleduje i jazyk nastavený jen pro aplikaci (per-app locale), takže
 * název země odpovídá tomu, v čem je zbytek obrazovky.
 *
 * Neznámý region vrací `getDisplayCountry` jako samotný kód — to je „nevím", ne překlad.
 */
actual fun localizedCountryName(code: String): String? =
    runCatching { Locale.Builder().setRegion(code).build().getDisplayCountry(Locale.getDefault()) }
        .getOrNull()
        ?.takeIf { it.isNotBlank() && !it.equals(code, ignoreCase = true) }

actual fun localizedLanguageName(code: String): String? =
    runCatching { Locale.forLanguageTag(code).getDisplayName(Locale.getDefault()) }
        .getOrNull()
        ?.takeIf { it.isNotBlank() && !it.equals(code, ignoreCase = true) }
        ?.replaceFirstChar { it.titlecase(Locale.getDefault()) }
