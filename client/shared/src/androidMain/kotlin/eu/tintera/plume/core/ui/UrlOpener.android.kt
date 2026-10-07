package eu.tintera.plume.core.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.settings.AppSettings
import eu.tintera.plume.core.url.UrlCleaner

actual class UrlOpener(
    private val context: Context,
    private val settings: AppSettings,
) {

    private val logger = Logger.withTag("UrlOpener")

    actual fun open(url: String) {
        val targetUrl = if (settings.stripTrackingParameters.value) UrlCleaner.cleanUrl(url) else url
        val intent = Intent(Intent.ACTION_VIEW, targetUrl.toUri())
            // Spouštíme z aplikačního kontextu, ne z activity — bez NEW_TASK to Android odmítne.
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // Odkaz může mít schéma, které zařízení neumí. Spadnout kvůli tomu nesmíme.
            logger.w(e) { "Odkaz nejde otevřít: $targetUrl" }
        }
    }
}
