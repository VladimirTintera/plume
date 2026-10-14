package eu.tintera.plume.app.root.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import eu.tintera.plume.StatusBarHost

/**
 * Ikony stavové lišty podle ZVOLENÉHO motivu, ne systémového vzhledu — jinak vyjdou tmavé ikony
 * na tmavé ploše, když má uživatel tmavý motiv ve světlém systému. Viz [eu.tintera.plume.StatusBarHostViewController].
 */
@Composable
actual fun SystemBarsAppearance(isDark: Boolean) {
    SideEffect { StatusBarHost.controller?.isDark = isDark }
}

/** Titulek okna má jen desktop. */
@Composable
actual fun WindowTitleBarAppearance(isDark: Boolean, background: Color, content: Color) = Unit
