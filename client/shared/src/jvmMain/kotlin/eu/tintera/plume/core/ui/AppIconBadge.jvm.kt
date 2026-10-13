package eu.tintera.plume.core.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import eu.tintera.plume.core.platform.DesktopTaskbar

@Composable
actual fun AppIconBadgeColors() {
    val container = MaterialTheme.colorScheme.error.toArgb()
    val content = MaterialTheme.colorScheme.onError.toArgb()
    SideEffect { DesktopTaskbar.setColors(container, content) }
}
