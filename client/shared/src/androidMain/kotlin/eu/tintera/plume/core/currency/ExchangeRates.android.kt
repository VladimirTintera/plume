package eu.tintera.plume.core.currency

import android.app.LocaleManager
import android.content.Context
import android.content.res.Resources
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.Currency
import java.util.Locale

actual class TextDownloader actual constructor() {
    actual suspend fun get(url: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = TIMEOUT_MS
                if (connection.responseCode != HttpURLConnection.HTTP_OK) return@runCatching null
                connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }

    private companion object {
        const val TIMEOUT_MS = 10_000
    }
}

/**
 * Region SYSTÉMU, ne jazyk aplikace. **`Resources.getSystem()` na to nestačí**: v procesu aplikace
 * do něj Android promítne i jazyk nastavený jen pro aplikaci (Nastavení → Jazyk aplikace), takže
 * s češtinou v Plume a systémem `en_US` vyšla koruna místo dolaru (ověřeno na emulátoru).
 * Od Androidu 13 proto `LocaleManager.getSystemLocales()`, který jazyk aplikace nezná.
 */
actual class HomeRegion(private val context: Context) {
    actual val code: String?
        get() = runCatching { Currency.getInstance(systemLocale()).currencyCode }.getOrNull()

    actual val metric: Boolean
        get() = runCatching { systemLocale().country !in IMPERIAL_REGIONS }.getOrDefault(true)

    private fun systemLocale(): Locale =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java).systemLocales[0]
        } else {
            Resources.getSystem().configuration.locales[0]
        }

    private companion object {
        /** Regiony s imperiálními jednotkami (CLDR `measurementSystem` = US). */
        val IMPERIAL_REGIONS = setOf("US", "LR", "MM")
    }
}
