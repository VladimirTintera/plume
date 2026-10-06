package eu.tintera.plume.core.ui

import android.os.Build
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import java.util.function.Consumer

/**
 * `WindowManager.isCrossWindowBlurEnabled` (Android 12+). Systém ho vypne volbou „Omezit efekty
 * rozmazání" (zapisuje `Settings.Global.disable_window_blurs`, ověřeno na emulátoru s Androidem
 * 17), úsporou baterie a vývojářskou volbou. Pod Androidem 12 Haze rozostřovat neumí, takže na
 * hodnotě nezáleží.
 */
@Composable
actual fun rememberBackgroundBlurAllowed(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
    val context = LocalContext.current
    val windowManager = remember(context) { context.getSystemService(WindowManager::class.java) }
    var allowed by remember(windowManager) { mutableStateOf(windowManager.isCrossWindowBlurEnabled) }
    DisposableEffect(windowManager) {
        val listener = Consumer<Boolean> { allowed = it }
        // Posluchač dostane hned i současnou hodnotu, takže se změna mezi čtením a registrací neztratí.
        windowManager.addCrossWindowBlurEnabledListener(ContextCompat.getMainExecutor(context), listener)
        onDispose { windowManager.removeCrossWindowBlurEnabledListener(listener) }
    }
    return allowed
}
