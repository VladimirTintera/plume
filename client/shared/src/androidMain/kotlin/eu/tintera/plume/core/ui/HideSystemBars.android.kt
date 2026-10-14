package eu.tintera.plume.core.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * `WindowInsetsController.hide(systemBars)` s chováním `SHOW_TRANSIENT_BARS_BY_SWIPE`: tah od
 * okraje lišty ukáže jen na chvíli a režim celé obrazovky zůstane.
 */
@Composable
actual fun HideSystemBars(hidden: Boolean) {
    val activity = LocalActivity.current ?: return
    val view = LocalView.current
    DisposableEffect(activity, hidden) {
        val controller = WindowCompat.getInsetsController(activity.window, view)
        if (hidden) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            if (hidden) controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}
