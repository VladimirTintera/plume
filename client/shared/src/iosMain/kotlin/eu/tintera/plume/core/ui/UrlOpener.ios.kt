package eu.tintera.plume.core.ui

import eu.tintera.plume.core.settings.AppSettings
import eu.tintera.plume.core.url.UrlCleaner
import platform.Foundation.NSURL
import platform.SafariServices.SFSafariViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenURLOptionUniversalLinksOnly
import platform.UIKit.UIViewController

/**
 * iOS: webové odkazy podle nastavení v `SFSafariViewController`, nebo v Safari; ostatní schémata
 * vždycky přes systém.
 *
 * **Aplikace pro odkaz má přednost i v režimu „v aplikaci"** (stejně jako na Androidu): napřed
 * se zkusí otevřít jen jako univerzální odkaz (`UniversalLinksOnly`), a teprve když si ho žádná
 * aplikace nevezme, ukáže se Safari uvnitř Plume.
 */
actual class UrlOpener(private val settings: AppSettings) {

    actual fun open(url: String) {
        val targetUrl = if (settings.stripTrackingParameters.value) UrlCleaner.cleanUrl(url) else url
        val nsUrl = NSURL.URLWithString(targetUrl) ?: return
        val web = nsUrl.scheme?.lowercase() in WEB_SCHEMES
        val application = UIApplication.sharedApplication

        if (!web || !settings.openLinksInApp.value) {
            // `canOpenURL` schválně nekontrolujeme — na iOS vyžaduje deklaraci schémat v Info.plist
            // a u externích odkazů bychom dopředu nevěděli, co všechno vyjmenovat.
            application.openURL(nsUrl, emptyMap<Any?, Any?>(), null)
            return
        }

        application.openURL(
            nsUrl,
            mapOf<Any?, Any?>(UIApplicationOpenURLOptionUniversalLinksOnly to true),
        ) { opened ->
            if (!opened) presentSafari(nsUrl)
        }
    }

    /**
     * Rovnou Safari, bez pokusu o univerzální odkaz. Plume si na iOS odkazy `t.me` zatím nebere,
     * ale ať se po přidání asociovaných domén nevrátí odkaz zpátky do ní.
     */
    actual fun openInBrowser(url: String): Boolean {
        val nsUrl = NSURL.URLWithString(url) ?: return false
        presentSafari(nsUrl)
        return true
    }

    private fun presentSafari(url: NSURL) {
        val top = topViewController()
        if (top == null) {
            UIApplication.sharedApplication.openURL(url, emptyMap<Any?, Any?>(), null)
            return
        }
        top.presentViewController(SFSafariViewController(uRL = url), animated = true, completion = null)
    }

    /** Nejvýš položený kontroler — Compose obrazovka sama může něco prezentovat (sheet). */
    private fun topViewController(): UIViewController? {
        var top = UIApplication.sharedApplication.keyWindow?.rootViewController ?: return null
        while (true) top = top.presentedViewController ?: break
        return top
    }

    private companion object {
        val WEB_SCHEMES = setOf("http", "https")
    }
}
