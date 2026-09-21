package eu.tintera.plume.core.ui

import android.app.Activity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun SecureScreen(enabled: Boolean) {
    val context = LocalContext.current
    DisposableEffect(context, enabled) {
        val window = (context as? Activity)?.window
        if (!enabled || window == null) return@DisposableEffect onDispose { }
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
}
