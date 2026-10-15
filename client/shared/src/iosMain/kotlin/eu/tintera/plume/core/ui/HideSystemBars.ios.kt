package eu.tintera.plume.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import eu.tintera.plume.StatusBarHost

/**
 * Stavovou lištu schová hostitel Compose controlleru ([eu.tintera.plume.StatusBarHostViewController]);
 * SwiftUI nad ním `prefersStatusBarHidden` převezme stejně jako styl lišty.
 */
@Composable
actual fun HideSystemBars(hidden: Boolean) {
    DisposableEffect(hidden) {
        if (hidden) StatusBarHost.hideRequests++
        onDispose { if (hidden) StatusBarHost.hideRequests-- }
    }
}
