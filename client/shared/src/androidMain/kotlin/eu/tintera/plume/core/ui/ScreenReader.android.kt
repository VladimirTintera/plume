package eu.tintera.plume.core.ui

import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * `AccessibilityManager.isTouchExplorationEnabled` — zapíná ho TalkBack (a jiné čtečky
 * s prozkoumáváním dotykem). Služby přístupnosti bez něj (správce hesel, automatizace) se
 * nepočítají: ty obsah nečtou.
 */
@Composable
actual fun rememberScreenReaderEnabled(): Boolean {
    val context = LocalContext.current
    val manager = remember(context) { context.getSystemService(AccessibilityManager::class.java) }
    var enabled by remember(manager) { mutableStateOf(manager.isTouchExplorationEnabled) }
    DisposableEffect(manager) {
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { enabled = it }
        manager.addTouchExplorationStateChangeListener(listener)
        // Změna mezi čtením a registrací by se jinak ztratila.
        enabled = manager.isTouchExplorationEnabled
        onDispose { manager.removeTouchExplorationStateChangeListener(listener) }
    }
    return enabled
}
