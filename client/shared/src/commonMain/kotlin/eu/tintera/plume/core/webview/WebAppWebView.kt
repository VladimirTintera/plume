package eu.tintera.plume.core.webview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier

/**
 * WebView pro mini aplikace botů. `core/webview` je hranice jako `core/camera`: `android.webkit`
 * a `platform.WebKit` smí být jen tady.
 *
 * - **Stránce se vystavuje jediná věc**: `window.TelegramWebviewProxy.postEvent(typ, data)`,
 *   kterou volá `telegram-web-app.js` (Android `addJavascriptInterface`, iOS obdobný objekt
 *   vložený skriptem na začátku dokumentu, který posílá do `WKScriptMessageHandler`). Žádný
 *   přístup k souborům ani k ničemu dalšímu z aplikace.
 * - Co s událostí, řeší společný kód (`features/webapp/WebAppBridge`); WebView jen přenáší.
 */
@Stable
class WebAppViewController {
    internal var evaluate: ((String) -> Unit)? = null

    /** Spustí JavaScript ve stránce (doručení události); před načtením WebView se nestane nic. */
    fun run(script: String) {
        evaluate?.invoke(script)
    }
}

/**
 * [onEvent] dostane typ a data (JSON jako řetězec) z `postEvent`, vždy na hlavním vlákně.
 * [onPageLoaded] se ozve po načtení stránky — ne všechny mini aplikace posílají `web_app_ready`.
 * [backgroundColor] je RGB plochy pod stránkou, než se vykreslí.
 */
@Composable
expect fun WebAppWebView(
    url: String,
    controller: WebAppViewController,
    backgroundColor: Int,
    onEvent: (type: String, data: String?) -> Unit,
    onPageLoaded: () -> Unit,
    modifier: Modifier = Modifier,
)
