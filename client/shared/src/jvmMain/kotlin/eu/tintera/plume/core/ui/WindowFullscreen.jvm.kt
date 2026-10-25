package eu.tintera.plume.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import eu.tintera.plume.core.platform.DesktopWindow

/** Okno přepíná vstupní bod (`WindowPlacement.Fullscreen`), sem jen hlásí, jak je. */
@Composable
actual fun rememberWindowFullscreen(): WindowFullscreen? {
    val active = DesktopWindow.fullscreen.collectAsState()
    return remember(active) { DesktopWindowFullscreen(active) }
}

private class DesktopWindowFullscreen(private val state: State<Boolean>) : WindowFullscreen {
    override val active: Boolean get() = state.value

    override fun set(on: Boolean) = DesktopWindow.requestFullscreen(on)
}
