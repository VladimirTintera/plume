package eu.tintera.plume.core.currency

import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSLocale
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.currencyCode
import platform.Foundation.currentLocale
import platform.Foundation.dataTaskWithURL
import kotlin.coroutines.resume

actual class TextDownloader actual constructor() {
    actual suspend fun get(url: String): String? = suspendCancellableCoroutine { continuation ->
        val address = NSURL.URLWithString(url)
        if (address == null) {
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }
        val task = NSURLSession.sharedSession.dataTaskWithURL(address) { data, _, error ->
            val text = if (error == null && data != null) {
                NSString.create(data = data, encoding = NSUTF8StringEncoding)?.toString()
            } else {
                null
            }
            continuation.resume(text)
        }
        continuation.invokeOnCancellation { task.cancel() }
        task.resume()
    }
}

actual class HomeCurrency {
    actual val code: String? get() = NSLocale.currentLocale.currencyCode
}
