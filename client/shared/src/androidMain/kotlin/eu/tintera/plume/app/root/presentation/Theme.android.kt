package eu.tintera.plume.app.root.presentation

import android.app.Activity
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import eu.tintera.plume.app.root.presentation.theme.expressiveColorScheme

actual val supportsDynamicColor: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@Composable
actual fun colorScheme(isDark: Boolean, dynamic: Boolean): ColorScheme {
    val context = LocalContext.current
    return when {
        // DYNAMIC = Material You ze systému (tapeta). Statické módy = naše vibrant expressive schéma.
        dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        else -> expressiveColorScheme(isDark)
    }
}

/**
 * Ikony systémových lišt musí odrážet ZVOLENÝ motiv, ne systémový dark mód — jinak vyjdou světlé
 * ikony na světlém pozadí, když má uživatel Light motiv na tmavém systému.
 */
@Composable
actual fun SystemBarsAppearance(isDark: Boolean) {
    val view = androidx.compose.ui.platform.LocalView.current
    if (view.isInEditMode) return
    val context = LocalContext.current
    SideEffect {
        val window = (context as? Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !isDark
            isAppearanceLightNavigationBars = !isDark
        }
    }
}
