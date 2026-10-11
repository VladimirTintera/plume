package eu.tintera.plume.core.ui

import co.touchlab.kermit.Logger
import eu.tintera.plume.core.settings.AppSettings
import eu.tintera.plume.core.url.UrlCleaner
import java.awt.Desktop
import java.net.URI

/**
 * Desktop: odkaz vždycky ve výchozím prohlížeči systému. Prohlížeč uvnitř aplikace tu není,
 * volba „otevírat v aplikaci" se proto neuplatní; čištění sledovacích parametrů ano.
 */
actual class UrlOpener(private val settings: AppSettings) {

    private val logger = Logger.withTag("UrlOpener")

    actual fun open(url: String) {
        val target = if (settings.stripTrackingParameters.value) UrlCleaner.cleanUrl(url) else url
        runCatching { openInSystem(URI(target)) }
            .onFailure { logger.w(it) { "Odkaz se nepodařilo otevřít: $target" } }
    }
}

/**
 * `Desktop.browse` na Linuxu bez GNOME knihoven nefunguje (`isSupported` vrací `false`), proto
 * náhradou `xdg-open`. Na macOS a Windows je `Desktop` spolehlivý.
 */
internal fun openInSystem(uri: URI) {
    val desktop = Desktop.getDesktop().takeIf { Desktop.isDesktopSupported() }
    if (desktop?.isSupported(Desktop.Action.BROWSE) == true) {
        desktop.browse(uri)
    } else {
        ProcessBuilder("xdg-open", uri.toString()).start()
    }
}
