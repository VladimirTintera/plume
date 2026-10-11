package eu.tintera.plume.core.currency

import java.net.URI
import java.util.Currency
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual class TextDownloader actual constructor() {
    actual suspend fun get(url: String): String? = withContext(Dispatchers.IO) {
        runCatching { URI(url).toURL().readText() }.getOrNull()
    }
}

actual class HomeRegion {
    actual val code: String? get() = runCatching { Currency.getInstance(Locale.getDefault()).currencyCode }.getOrNull()

    actual val metric: Boolean get() = Locale.getDefault().country !in setOf("US", "LR", "MM")
}
