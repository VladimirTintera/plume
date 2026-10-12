package eu.tintera.plume.core.ui

import android.app.Activity
import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.settings.AppSettings
import eu.tintera.plume.core.url.UrlCleaner
import java.lang.ref.WeakReference

/**
 * Android: webové odkazy podle nastavení v Custom Tabs, nebo v prohlížeči; ostatní schémata
 * (`mailto:`, `tel:`, `geo:`) vždycky přes `ACTION_VIEW`.
 *
 * **Aplikace pro odkaz má přednost i v režimu „v aplikaci".** Odkaz na YouTube patří do YouTube,
 * ne do karty prohlížeče. Od Androidu 11 to umí `FLAG_ACTIVITY_REQUIRE_NON_BROWSER`: intent
 * projde, jen když odkaz převezme jiná aplikace než prohlížeč, jinak spadne na
 * `ActivityNotFoundException` a teprve pak se otevře karta. Na starších verzích by stejná zkouška
 * znamenala procházet `queryIntentActivities` a porovnávat s prohlížeči — tam se otevírá rovnou
 * karta.
 *
 * **Karta se spouští z activity, ne z aplikačního kontextu.** Z něj by potřebovala
 * `FLAG_ACTIVITY_NEW_TASK` a otevřela by se jako samostatná úloha — v přehledu úloh by visela
 * vedle Plume a zpět z ní by nemuselo vést do konverzace. Activity v popředí se proto drží
 * (slabě) z callbacků aplikace; bez ní (odkaz otevřený mimo obrazovku) se jde přes novou úlohu.
 */
actual class UrlOpener(
    private val context: Context,
    private val settings: AppSettings,
) {

    private val logger = Logger.withTag("UrlOpener")

    /** Activity, která je právě v popředí; jen z hlavního vlákna (callbacky `Application`). */
    private var resumed: WeakReference<Activity>? = null

    init {
        (context.applicationContext as Application).registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityResumed(activity: Activity) {
                    resumed = WeakReference(activity)
                }

                override fun onActivityPaused(activity: Activity) {
                    if (resumed?.get() === activity) resumed = null
                }

                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
                override fun onActivityStarted(activity: Activity) = Unit
                override fun onActivityStopped(activity: Activity) = Unit
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
                override fun onActivityDestroyed(activity: Activity) = Unit
            },
        )
    }

    actual fun open(url: String) {
        val targetUrl = if (settings.stripTrackingParameters.value) UrlCleaner.cleanUrl(url) else url
        val uri = targetUrl.toUri()
        val web = uri.scheme?.lowercase() in WEB_SCHEMES

        try {
            when {
                !web || !settings.openLinksInApp.value -> view(uri)
                openInNativeApp(uri) -> Unit
                else -> openInTab(uri)
            }
        } catch (e: ActivityNotFoundException) {
            // Odkaz může mít schéma, které zařízení neumí. Spadnout kvůli tomu nesmíme.
            logger.w(e) { "Odkaz nejde otevřít: $targetUrl" }
        }
    }

    /**
     * Výchozí prohlížeč přes selektor (`CATEGORY_APP_BROWSER`): systém podle něj vybere
     * prohlížeč a doručí mu původní `ACTION_VIEW` s adresou. Obyčejný `ACTION_VIEW` ani karta
     * Custom Tabs nestačí — adresu `t.me` by systém mohl předat zpátky Plume, když ji má
     * uživatel povolenou jako odkaz, který Plume otevírá.
     */
    actual fun openInBrowser(url: String): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.selector = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER)
        return try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            logger.w(e) { "Odkaz nejde otevřít v prohlížeči: $url" }
            false
        }
    }

    private fun view(uri: Uri) {
        // Spouštíme z aplikačního kontextu, ne z activity — bez NEW_TASK to Android odmítne.
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** `true`, když odkaz převzala aplikace, která není prohlížeč (viz třída). */
    private fun openInNativeApp(uri: Uri): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        val intent = Intent(Intent.ACTION_VIEW, uri)
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REQUIRE_NON_BROWSER)
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    private fun openInTab(uri: Uri) {
        val tab = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .setShareState(CustomTabsIntent.SHARE_STATE_ON)
            .build()
        val activity = resumed?.get()
        if (activity != null) {
            tab.launchUrl(activity, uri)
        } else {
            tab.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            tab.launchUrl(context, uri)
        }
    }

    private companion object {
        val WEB_SCHEMES = setOf("http", "https")
    }
}
