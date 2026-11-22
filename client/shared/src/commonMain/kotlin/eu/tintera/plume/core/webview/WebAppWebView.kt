package eu.tintera.plume.core.webview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier

/**
 * WebView pro mini aplikace botů. `core/webview` je hranice jako `core/camera`: `android.webkit`
 * a `platform.WebKit` smí být jen tady.
 *
 * - **Stránce se vystavuje jediná věc**: `window.TelegramWebviewProxy.postEvent(typ, data)`,
 *   kterou volá `telegram-web-app.js` (Android posluchač zpráv `WebViewCompat.addWebMessageListener`
 *   se skriptem [BRIDGE_SHIM] na začátku dokumentu, iOS obdobný skript, který posílá do
 *   `WKScriptMessageHandler`). Žádný přístup k souborům ani k ničemu dalšímu z aplikace.
 * - **Most patří jen originu mini aplikace** a hlavní rámec ho neopustí — viz [WebAppOriginGuard].
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
 * [onEvent] dostane typ a data (JSON jako řetězec) z `postEvent`, vždy na hlavním vlákně, a jen
 * z originu mini aplikace ([WebAppOriginGuard]); odpovědi ([WebAppViewController.run]) jdou
 * jen do stránky na tomtéž originu.
 * [onPageLoaded] se ozve po načtení stránky — ne všechny mini aplikace posílají `web_app_ready`.
 * [onOpenExternal] dostane adresu, na kterou hlavní rámec chtěl odejít z originu mini aplikace;
 * WebView na ni nepůjde, otevřít ji má volající (v prohlížeči).
 * [profile] odděluje data WebView po účtech ([webAppProfileName]).
 * [backgroundColor] je RGB plochy pod stránkou, než se vykreslí.
 */
@Composable
expect fun WebAppWebView(
    url: String,
    profile: String,
    controller: WebAppViewController,
    backgroundColor: Int,
    onEvent: (type: String, data: String?) -> Unit,
    onPageLoaded: () -> Unit,
    onOpenExternal: (url: String) -> Unit,
    modifier: Modifier = Modifier,
)
