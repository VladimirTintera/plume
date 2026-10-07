package eu.tintera.plume.app.root.presentation

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import eu.tintera.plume.app.root.presentation.theme.expressiveColorScheme

/**
 * Platformy bez systémové tapetové palety. Material You tu není, takže `dynamic` nemá co znamenat —
 * vždycky se generuje schéma ze seed barvy, kterou si uživatel vybral.
 */
actual val supportsDynamicColor: Boolean = false

@Composable
actual fun colorScheme(isDark: Boolean, dynamic: Boolean, seed: Color): ColorScheme =
    expressiveColorScheme(isDark, seed)

@Composable
actual fun SystemBarsAppearance(isDark: Boolean) = Unit
