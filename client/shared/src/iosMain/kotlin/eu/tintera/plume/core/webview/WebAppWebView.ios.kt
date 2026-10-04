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
import platform.UIKit.UIColor
import platform.WebKit.WKScriptMessage
import platform.WebKit.WKScriptMessageHandlerProtocol
import platform.WebKit.WKUserContentController
import platform.WebKit.WKUserScript
import platform.WebKit.WKUserScriptInjectionTime
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.darwin.NSObject

/**
 * iOS `WKWebView`. `TelegramWebviewProxy` na iOS neexistuje, a tak ho vloží skript na začátku
 * dokumentu: `postEvent` pošle `{eventType, eventData}` do `WKScriptMessageHandler`. Mini
 * aplikace tak mluví na obou platformách stejně.
 *
 * Handler se drží silnou referencí (`remember`) — `WKUserContentController` ho drží taky, ale
 * odhlašuje se v `onRelease`, aby nevznikl cyklus WebView → handler → Compose.
 */
@OptIn(ExperimentalForeignApi::class)
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
    val handler = remember {
        object : NSObject(), WKScriptMessageHandlerProtocol {
            override fun userContentController(userContentController: WKUserContentController, didReceiveScriptMessage: WKScriptMessage) {
                val body = didReceiveScriptMessage.body as? Map<*, *> ?: return
                val type = body["eventType"] as? String ?: return
                currentEvent(type, body["eventData"] as? String)
            }
        }
    }
    UIKitView(
        factory = {
            val configuration = WKWebViewConfiguration().apply {
                userContentController.addScriptMessageHandler(handler, name = HANDLER)
                userContentController.addUserScript(
                    WKUserScript(
                        source = PROXY_SCRIPT,
                        injectionTime = WKUserScriptInjectionTime.WKUserScriptInjectionTimeAtDocumentStart,
                        forMainFrameOnly = true,
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
                controller.evaluate = { script -> evaluateJavaScript(script, completionHandler = null) }
                NSURL.URLWithString(url)?.let { loadRequest(NSURLRequest.requestWithURL(it)) }
            }
        },
        onRelease = { webView ->
            controller.evaluate = null
            webView.configuration.userContentController.removeScriptMessageHandlerForName(HANDLER)
            webView.stopLoading()
        },
        modifier = modifier,
    )
}

private const val HANDLER = "plumeWebApp"

private const val PROXY_SCRIPT =
    "window.TelegramWebviewProxy = { postEvent: function (eventType, eventData) { " +
        "window.webkit.messageHandlers.$HANDLER.postMessage({ eventType: eventType, eventData: eventData }); } };"
