package eu.tintera.plume.core.webview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.Foundation.NSUUID
import platform.UIKit.UIColor
import platform.WebKit.WKFrameInfo
import platform.WebKit.WKNavigation
import platform.WebKit.WKNavigationAction
import platform.WebKit.WKNavigationActionPolicy
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKScriptMessage
import platform.WebKit.WKScriptMessageHandlerProtocol
import platform.WebKit.WKUserContentController
import platform.WebKit.WKUserScript
import platform.WebKit.WKUserScriptInjectionTime
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.WebKit.WKWebsiteDataStore
import platform.darwin.NSObject

/**
 * iOS `WKWebView`. `TelegramWebviewProxy` na iOS neexistuje, a tak ho vloží skript na začátku
 * dokumentu: `postEvent` pošle `{eventType, eventData}` do `WKScriptMessageHandler`. Mini
 * aplikace tak mluví na obou platformách stejně.
 *
 * - **Kdo zprávu poslal, říká `WKScriptMessage.frameInfo`** (origin a hlavní rámec); handler je
 *   v `window.webkit.messageHandlers` všech rámců, takže bez kontroly by poslal i vložený rámec
 *   třetí strany. Rozhoduje [WebAppOriginGuard], stejně jako na Androidu.
 * - **Navigace hlídá `WKNavigationDelegate`**: hlavní rámec po načtení neopustí origin mini
 *   aplikace (odkaz jinam jde do [onOpenExternal]), schémata jiná než http(s) se neotevřou.
 * - **Data po účtech: `WKWebsiteDataStore(forIdentifier:)`** (iOS 17+, aplikace chce 18.2)
 *   s UUID odvozeným z [profile].
 *
 * Handler i delegát se drží silnou referencí (`remember`) — `WKUserContentController` drží
 * handler, delegát je ve WebView slabý. Handler se odhlašuje v `onRelease`, aby nevznikl cyklus
 * WebView → handler → Compose.
 */
@OptIn(ExperimentalForeignApi::class)
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
    val guard = remember { WebAppOriginGuard() }
    val handler = remember {
        object : NSObject(), WKScriptMessageHandlerProtocol {
            override fun userContentController(userContentController: WKUserContentController, didReceiveScriptMessage: WKScriptMessage) {
                val frame = didReceiveScriptMessage.frameInfo
                if (!guard.accepts(frame.origin(), frame.mainFrame)) return
                val body = didReceiveScriptMessage.body as? Map<*, *> ?: return
                val type = body["eventType"] as? String ?: return
                currentEvent(type, body["eventData"] as? String)
            }
        }
    }
    val navigation = remember {
        object : NSObject(), WKNavigationDelegateProtocol {
            override fun webView(
                webView: WKWebView,
                decidePolicyForNavigationAction: WKNavigationAction,
                decisionHandler: (WKNavigationActionPolicy) -> Unit,
            ) {
                val target = decidePolicyForNavigationAction.request.URL?.absoluteString
                val scheme = decidePolicyForNavigationAction.request.URL?.scheme?.lowercase()
                // `targetFrame == null` = nové okno (`window.open`, `target=_blank`); WebView ho
                // neotevře, ale počítá se jako odchod hlavního rámce.
                val mainFrame = decidePolicyForNavigationAction.targetFrame?.mainFrame ?: true
                val allow = when {
                    // Vložené rámce smí mít obsah bez adresy (`about:srcdoc`, `blob:`, `data:`),
                    // hlavní rámec jen web; `tg:`, `intent:` a spol. mini aplikace otevírá přes most.
                    scheme != "http" && scheme != "https" -> !mainFrame && scheme in LOCAL_SCHEMES
                    mainFrame && !guard.allowsNavigation(target) -> {
                        target?.let(currentExternal)
                        false
                    }
                    else -> true
                }
                decisionHandler(if (allow) WKNavigationActionPolicy.WKNavigationActionPolicyAllow else WKNavigationActionPolicy.WKNavigationActionPolicyCancel)
            }

            override fun webView(webView: WKWebView, didFinishNavigation: WKNavigation?) {
                guard.pageLoaded(webView.URL?.absoluteString)
                currentLoaded()
            }
        }
    }
    UIKitView(
        factory = {
            val configuration = WKWebViewConfiguration().apply {
                websiteDataStore = NSUUID(uUIDString = webAppProfileUuid(profile))
                    .let { WKWebsiteDataStore.dataStoreForIdentifier(it) }
                userContentController.addScriptMessageHandler(handler, name = HANDLER)
                userContentController.addUserScript(
                    WKUserScript(
                        source = PROXY_SCRIPT,
                        injectionTime = WKUserScriptInjectionTime.WKUserScriptInjectionTimeAtDocumentStart,
                        // I do vložených rámců: vlastní rámec mini aplikace (hra) smí mluvit,
                        // cizí odmítne handler podle originu.
                        forMainFrameOnly = false,
                    ),
                )
            }
            WKWebView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0), configuration = configuration).apply {
                setOpaque(false)
                setBackgroundColor(
                    UIColor.colorWithRed(
                        red = (backgroundColor shr 16 and 0xFF) / 255.0,
                        green = (backgroundColor shr 8 and 0xFF) / 255.0,
                        blue = (backgroundColor and 0xFF) / 255.0,
                        alpha = 1.0,
                    ),
                )
                navigationDelegate = navigation
                controller.evaluate = { script ->
                    if (guard.accepts(URL?.absoluteString, isMainFrame = true)) evaluateJavaScript(script, completionHandler = null)
                }
                NSURL.URLWithString(url)?.let { loadRequest(NSURLRequest.requestWithURL(it)) }
            }
        },
        onRelease = { webView ->
            controller.evaluate = null
            webView.navigationDelegate = null
            webView.configuration.userContentController.removeScriptMessageHandlerForName(HANDLER)
            webView.stopLoading()
        },
        modifier = modifier,
    )
}

/** Origin rámce jako adresa (`https://host[:port]`); port 0 = výchozí. */
private fun WKFrameInfo.origin(): String {
    val origin = securityOrigin
    return "${origin.protocol}://${origin.host}" + if (origin.port != 0L) ":${origin.port}" else ""
}

private const val HANDLER = "plumeWebApp"

private val LOCAL_SCHEMES = setOf("about", "blob", "data")

private const val PROXY_SCRIPT =
    "window.TelegramWebviewProxy = { postEvent: function (eventType, eventData) { " +
        "window.webkit.messageHandlers.$HANDLER.postMessage({ eventType: eventType, eventData: eventData }); } };"
