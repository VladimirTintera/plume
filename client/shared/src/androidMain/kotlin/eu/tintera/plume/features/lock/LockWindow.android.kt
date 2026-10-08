package eu.tintera.plume.features.lock

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.core.view.WindowCompat

@Composable
actual fun LockWindow(onBack: () -> Unit, content: @Composable () -> Unit) {
    Dialog(
        onDismissRequest = onBack,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            // Zadávání PINu nemá jít do snímku obrazovky ani do přehledu úloh.
            securePolicy = SecureFlagPolicy.SecureOn,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        // Okno dialogu má vlastní systémové lišty; vzhled ikon z `SystemBarsAppearance` platí
        // jen pro okno activity.
        val view = LocalView.current
        val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
        SideEffect {
            val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !isDark
                isAppearanceLightNavigationBars = !isDark
            }
        }
        content()
    }
}
