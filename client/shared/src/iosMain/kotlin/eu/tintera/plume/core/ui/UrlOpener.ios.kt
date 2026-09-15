package eu.tintera.plume.core.ui

import platform.Foundation.NSURL
import platform.UIKit.UIApplication

actual class UrlOpener {

    actual fun open(url: String) {
        val nsUrl = NSURL.URLWithString(url) ?: return
        // `canOpenURL` schválně nekontrolujeme — na iOS vyžaduje deklaraci schémat v Info.plist
        // a u externích odkazů bychom dopředu nevěděli, co všechno vyjmenovat.
        UIApplication.sharedApplication.openURL(nsUrl, emptyMap<Any?, Any?>(), null)
    }
}
