package eu.tintera.plume.core.locale

import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString

/**
 * iOS: jazyk aplikace se volí v Nastavení systému u aplikace (nabídne ho sám podle `cs.lproj`
 * a `en.lproj` v bundlu). API, které by ho změnilo z aplikace, iOS nemá — přepsat
 * `AppleLanguages` v `NSUserDefaults` by se projevilo až po restartu a obešlo by systémovou volbu.
 */
actual class AppLanguage {

    actual val isSupported: Boolean = true

    actual val canChooseInApp: Boolean = false

    actual fun current(): String? = null

    actual fun set(language: String?) = Unit

    actual fun openSystemSettings() {
        val url = NSURL.URLWithString(UIApplicationOpenSettingsURLString) ?: return
        UIApplication.sharedApplication.openURL(url, emptyMap<Any?, Any?>(), null)
    }
}

actual fun nativeLanguageName(code: String): String {
    val locale = platform.Foundation.NSLocale(localeIdentifier = code)
    val name = locale.displayNameForKey(platform.Foundation.NSLocaleIdentifier, value = code) ?: code
    return name.replaceFirstChar { it.uppercase() }
}
