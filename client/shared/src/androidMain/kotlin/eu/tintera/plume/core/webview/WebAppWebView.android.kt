package eu.tintera.plume.core.webview

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Android `WebView`.
 *
 * - **JavaScript zapnutý, soubory a obsah zařízení vypnuté** (`allowFileAccess`,
 *   `allowContentAccess`). Mini aplikace je web; na soubory v telefonu nemá co sahat.
 * - **`postEvent` přichází z vlákna WebView**, do Compose se předává přes hlavní `Handler`.
 * - **Odkazy jiné než http(s) stránka neotevře** (`intent:`, `tg:`…): mini aplikace na ně má
 *   `web_app_open_link` / `web_app_open_tg_link`, a přímé přesměrování na schéma by obešlo
 *   potvrzení.
 * - Po odchodu z kompozice se WebView zničí (`destroy`), jinak by stránka běžela dál.
 */
@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
actual fun WebAppWebView(
    url: String,
    controller: WebAppViewController,
    backgroundColor: Int,
    onEvent: (type: String, data: String?) -> Unit,
    onPageLoaded: () -> Unit,
    modifier: Modifier,
) {
    val currentEvent by rememberUpdatedState(onEvent)
    val currentLoaded by rememberUpdatedState(onPageLoaded)
    AndroidView(
        factory = { context ->
            val main = Handler(Looper.getMainLooper())
            WebView(context).apply {
                setBackgroundColor(Color.rgb(backgroundColor shr 16 and 0xFF, backgroundColor shr 8 and 0xFF, backgroundColor and 0xFF))
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                addJavascriptInterface(
                    object {
                        @JavascriptInterface
                        fun postEvent(eventType: String, eventData: String?) {
                            main.post { currentEvent(eventType, eventData) }
                        }
                    },
                    "TelegramWebviewProxy",
                )
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                        request.url.scheme !in setOf("http", "https")

                    override fun onPageFinished(view: WebView, url: String?) = currentLoaded()
                }
                controller.evaluate = { script -> evaluateJavascript(script, null) }
                loadUrl(url)
            }
        },
        onRelease = { webView ->
            controller.evaluate = null
            webView.destroy()
        },
        modifier = modifier,
    )
}
