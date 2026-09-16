package eu.tintera.plume.core.adaptive

import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.window.core.layout.WindowSizeClass

enum class WindowWidthClass { COMPACT, MEDIUM, EXPANDED }

/** Odvodí [WindowWidthClass] z této [WindowSizeClass] podle šířkových breakpointů. */
fun WindowSizeClass.widthClass(): WindowWidthClass = when {
    isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND) -> WindowWidthClass.EXPANDED
    isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) -> WindowWidthClass.MEDIUM
    else -> WindowWidthClass.COMPACT
}

/**
 * Šířková třída okna.
 *
 * V2 zná navíc třídy Large a XL. Na výsledku to nic nemění: obě leží nad hranicí Expanded
 * a [widthClass] se ptá jen „aspoň Expanded / aspoň Medium".
 */
@Composable
fun windowWidthClass(): WindowWidthClass =
    currentWindowAdaptiveInfoV2().windowSizeClass.widthClass()

@Composable
fun isCompact(): Boolean = windowWidthClass() == WindowWidthClass.COMPACT
