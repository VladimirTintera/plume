package eu.tintera.plume.core.webview

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.edit
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import co.touchlab.kermit.Logger

/**
 * Android `WebView`.
 *
 * - **JavaScript zapnutý, soubory a obsah zařízení vypnuté** (`allowFileAccess`,
 *   `allowContentAccess`). Mini aplikace je web; na soubory v telefonu nemá co sahat.
 * - **Most je posluchač zpráv** (`WebViewCompat.addWebMessageListener`), ne
 *   `addJavascriptInterface`: posluchač ke každé zprávě řekne origin a jestli přišla z hlavního
 *   rámce, takže cizí web ani vložený rámec třetí strany nic nepošle ([WebAppOriginGuard]).
 *   `addJavascriptInterface` je ve všech rámcích bez rozdílu a odesílatele nezná — zůstává jen
 *   pro WebView bez té funkce, a tam se origin hlídá aspoň podle adresy hlavního rámce.
 * - **Odkazy jiné než http(s) stránka neotevře** (`intent:`, `tg:`…): mini aplikace na ně má
 *   `web_app_open_link` / `web_app_open_tg_link`, a přímé přesměrování na schéma by obešlo
 *   potvrzení. **Hlavní rámec po načtení neopustí origin mini aplikace**: odkaz jinam jde
 *   do [onOpenExternal]. `shouldOverrideUrlLoading` se nevolá pro POST, proto origin hlídá
 *   i příjem událostí a doručení odpovědí, ne jen navigace.
 * - **Data WebView po účtech** přes profily (`ProfileStore`, `WebViewCompat.setProfile` hned po
 *   vytvoření, před jakýmkoli jiným voláním). WebView bez profilů má jedno úložiště, a tak se
 *   při změně účtu proti minulému otevření smaže ([clearWhenProfileChanged]).
 * - Po odchodu z kompozice se WebView zničí (`destroy`), jinak by stránka běžela dál.
 */
@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
actual fun WebAppWebView(
    url: String,
    profile: String,
    controller: WebAppViewController,
    backgroundColor: Int,
    onEvent: (type: String, data: String?) -> Unit,
    onPageLoaded: () -> Unit,
    onOpenExternal: (url: String) -> Unit,
    modifier: Modifier,
) {
    val currentEvent by rememberUpdatedState(onEvent)
    val currentLoaded by rememberUpdatedState(onPageLoaded)
    val currentExternal by rememberUpdatedState(onOpenExternal)
    AndroidView(
        factory = { context ->
            val main = Handler(Looper.getMainLooper())
            val guard = WebAppOriginGuard()
            WebView(context).apply {
                val profiles = WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)
                if (profiles) {
                    ProfileStore.getInstance().getOrCreateProfile(profile)
                    WebViewCompat.setProfile(this, profile)
                }
                setBackgroundColor(Color.rgb(backgroundColor shr 16 and 0xFF, backgroundColor shr 8 and 0xFF, backgroundColor and 0xFF))
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) &&
                    WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
                ) {
                    // Posluchač chodí na hlavním vlákně. Pravidlo „*" jen vloží objekt do všech
                    // rámců; kdo smí poslat, rozhoduje strážce podle originu a rámce.
                    WebViewCompat.addWebMessageListener(this, BRIDGE_OBJECT, setOf("*")) { _, message, origin, isMainFrame, _ ->
                        if (!guard.accepts(origin.toString(), isMainFrame)) return@addWebMessageListener
                        val (type, data) = parseBridgeMessage(message.data) ?: return@addWebMessageListener
                        currentEvent(type, data)
                    }
                    WebViewCompat.addDocumentStartJavaScript(this, BRIDGE_SHIM, setOf("*"))
                } else {
                    logger.w { "WebView bez WebMessageListener, most mini aplikace hlídá jen adresa hlavního rámce." }
                    addJavascriptInterface(
                        object {
                            @JavascriptInterface
                            fun postEvent(eventType: String, eventData: String?) {
                                main.post {
                                    if (guard.accepts(this@apply.url, isMainFrame = true)) currentEvent(eventType, eventData)
                                }
                            }
                        },
                        "TelegramWebviewProxy",
                    )
                }
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        val target = request.url.toString()
                        if (request.url.scheme !in setOf("http", "https")) return true
                        if (request.isForMainFrame && !guard.allowsNavigation(target)) {
                            main.post { currentExternal(target) }
                            return true
                        }
                        return false
                    }

                    override fun onPageFinished(view: WebView, url: String?) {
                        guard.pageLoaded(url)
                        currentLoaded()
                    }
                }
                controller.evaluate = { script ->
                    if (guard.accepts(this.url, isMainFrame = true)) evaluateJavascript(script, null)
                }
                if (profiles) loadUrl(url) else clearWhenProfileChanged(context, profile) { loadUrl(url) }
            }
        },
        onRelease = { webView ->
            controller.evaluate = null
            webView.destroy()
        },
        modifier = modifier,
    )
}

/**
 * WebView bez profilů (starší WebView) má jedno úložiště pro všechny účty: když se mini
 * aplikace otevírá pod jiným účtem než minule, cookies a úložiště stránek se před načtením
 * smažou. Mini aplikace pak po přepnutí účtu začínají odhlášené — lepší než sdílená identita.
 */
private fun clearWhenProfileChanged(context: Context, profile: String, then: () -> Unit) {
    val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    if (preferences.getString(KEY_LAST_PROFILE, null) == profile) return then()
    WebStorage.getInstance().deleteAllData()
    CookieManager.getInstance().removeAllCookies {
        preferences.edit { putString(KEY_LAST_PROFILE, profile) }
        then()
    }
}

private val logger = Logger.withTag("WebAppWebView")
private const val PREFERENCES = "webapp_webview"
private const val KEY_LAST_PROFILE = "last_profile"
