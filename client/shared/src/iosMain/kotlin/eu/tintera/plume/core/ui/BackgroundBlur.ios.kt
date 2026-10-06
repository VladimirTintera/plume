package eu.tintera.plume.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIAccessibilityIsReduceTransparencyEnabled
import platform.UIKit.UIAccessibilityReduceTransparencyStatusDidChangeNotification

/** „Omezit průhlednost" v Přístupnosti; změnu hlásí oznámení UIKitu. */
@Composable
actual fun rememberBackgroundBlurAllowed(): Boolean {
    var allowed by remember { mutableStateOf(!UIAccessibilityIsReduceTransparencyEnabled()) }
    DisposableEffect(Unit) {
        val observer = NSNotificationCenter.defaultCenter.addObserverForName(
            name = UIAccessibilityReduceTransparencyStatusDidChangeNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { _ -> allowed = !UIAccessibilityIsReduceTransparencyEnabled() }
        onDispose { NSNotificationCenter.defaultCenter.removeObserver(observer) }
    }
    return allowed
}
