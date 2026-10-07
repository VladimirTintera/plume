package eu.tintera.plume.core.ui

import eu.tintera.plume.core.settings.AppSettings
import eu.tintera.plume.core.url.UrlCleaner
import platform.Foundation.NSURL
import platform.UIKit.UIApplication

actual class UrlOpener(private val settings: AppSettings) {

    actual fun open(url: String) {
        val targetUrl = if (settings.stripTrackingParameters.value) UrlCleaner.cleanUrl(url) else url
        val nsUrl = NSURL.URLWithString(targetUrl) ?: return
        // `canOpenURL` schválně nekontrolujeme — na iOS vyžaduje deklaraci schémat v Info.plist
        // a u externích odkazů bychom dopředu nevěděli, co všechno vyjmenovat.
        UIApplication.sharedApplication.openURL(nsUrl, emptyMap<Any?, Any?>(), null)
    }
}
