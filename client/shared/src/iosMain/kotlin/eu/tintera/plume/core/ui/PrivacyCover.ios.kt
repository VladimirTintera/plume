package eu.tintera.plume.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.uikit.LocalUIViewController
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationWillResignActiveNotification
import platform.UIKit.UIColor
import platform.UIKit.UIView
import platform.UIKit.UIViewAutoresizingFlexibleHeight
import platform.UIKit.UIViewAutoresizingFlexibleWidth

/**
 * Kryt je nativní `UIView` přidané přímo do okna, ne plocha v Compose: musí být vidět na snímku,
 * který systém pořídí hned po `didEnterBackground`, a Compose v té chvíli už nemusí kreslit.
 *
 * `willResignActive` přijde i při stažení Ovládacího centra nebo při dialogu Face ID; kryt se
 * pak ukáže i tam. Dělá to tak i Telegram s kódem — o snímek, který se ukáže jinde, nejde.
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun PrivacyCover(enabled: Boolean, color: Color) {
    val controller = LocalUIViewController.current
    val currentColor by rememberUpdatedState(color)
    DisposableEffect(controller, enabled) {
        if (!enabled) return@DisposableEffect onDispose { }
        val center = NSNotificationCenter.defaultCenter
        var cover: UIView? = null

        val resign = center.addObserverForName(
            name = UIApplicationWillResignActiveNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { _ ->
            val window = controller.view.window
            if (cover == null && window != null) {
                cover = UIView(frame = window.bounds).apply {
                    backgroundColor = currentColor.toUIColor()
                    autoresizingMask = UIViewAutoresizingFlexibleWidth or UIViewAutoresizingFlexibleHeight
                    window.addSubview(this)
                }
            }
        }
        val active = center.addObserverForName(
            name = UIApplicationDidBecomeActiveNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { _ ->
            cover?.removeFromSuperview()
            cover = null
        }

        onDispose {
            center.removeObserver(resign)
            center.removeObserver(active)
            cover?.removeFromSuperview()
        }
    }
}

private fun Color.toUIColor(): UIColor =
    UIColor(red = red.toDouble(), green = green.toDouble(), blue = blue.toDouble(), alpha = 1.0)
