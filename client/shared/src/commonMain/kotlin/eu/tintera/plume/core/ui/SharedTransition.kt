package eu.tintera.plume.core.ui

import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.compositionLocalOf

/**
 * Scope pro sdílené prvky mezi obrazovkami. Poskytuje ho kořenové téma, protože
 * [androidx.compose.animation.SharedTransitionLayout] musí obalovat celý navigační strom — jinak
 * sdílený prvek nepřežije výměnu obrazovky.
 */
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }
