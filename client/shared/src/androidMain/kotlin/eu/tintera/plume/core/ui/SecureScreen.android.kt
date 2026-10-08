package eu.tintera.plume.core.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.Window
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import java.util.WeakHashMap

@Composable
actual fun SecureScreen(enabled: Boolean) {
    val context = LocalContext.current
    DisposableEffect(context, enabled) {
        val window = context.findActivity()?.window
        if (!enabled || window == null) return@DisposableEffect onDispose { }
        window.acquireSecure()
        onDispose { window.releaseSecure() }
    }
}

/**
 * Kolik míst o `FLAG_SECURE` na okně právě žádá. Příznak je na okně jeden, ale žádá o něj víc
 * míst naráz (zapnutý zámek aplikace, mizející fotka v prohlížeči): kdyby ho každé při odchodu
 * prostě shodilo, zavřený prohlížeč by ho vypnul i zámku a přehled úloh by zase ukazoval chaty.
 * Jen z hlavního vlákna (efekty kompozice).
 */
private val secureRequests = WeakHashMap<Window, Int>()

private fun Window.acquireSecure() {
    val count = secureRequests[this] ?: 0
    secureRequests[this] = count + 1
    if (count == 0) addFlags(WindowManager.LayoutParams.FLAG_SECURE)
}

private fun Window.releaseSecure() {
    val count = (secureRequests[this] ?: return) - 1
    if (count > 0) {
        secureRequests[this] = count
    } else {
        secureRequests.remove(this)
        clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
