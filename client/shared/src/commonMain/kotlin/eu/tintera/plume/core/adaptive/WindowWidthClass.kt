package eu.tintera.plume.core.adaptive

import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.window.core.layout.WindowSizeClass

enum class WindowWidthClass { COMPACT, MEDIUM, EXPANDED }

/** Odvodí [WindowWidthClass] z této [WindowSizeClass] podle šířkových breakpointů. */
fun WindowSizeClass.widthClass(): WindowWidthClass = when {
    isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND) -> WindowWidthClass.EXPANDED
    isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) -> WindowWidthClass.MEDIUM
    else -> WindowWidthClass.COMPACT
}

@Composable
fun windowWidthClass(): WindowWidthClass =
    currentWindowAdaptiveInfo().windowSizeClass.widthClass()

@Composable
fun isCompact(): Boolean = windowWidthClass() == WindowWidthClass.COMPACT
