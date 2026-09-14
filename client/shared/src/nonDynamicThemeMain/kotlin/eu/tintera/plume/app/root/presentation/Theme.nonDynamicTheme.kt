package eu.tintera.plume.app.root.presentation

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import eu.tintera.plume.app.root.presentation.theme.expressiveColorScheme

/**
 * Platformy bez systémové tapetové palety. Material You tu není, takže `dynamic` nemá co znamenat —
 * vždycky se generuje expressive schéma ze seed barvy.
 */
actual val supportsDynamicColor: Boolean = false

@Composable
actual fun colorScheme(isDark: Boolean, dynamic: Boolean): ColorScheme = expressiveColorScheme(isDark)

@Composable
actual fun SystemBarsAppearance(isDark: Boolean) = Unit
