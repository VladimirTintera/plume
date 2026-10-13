package eu.tintera.plume.core.calls

import androidx.compose.runtime.Composable

/** Hovory tu nejsou, okénko taky ne. */
@Composable
actual fun rememberCallPictureInPicture(
    enabled: Boolean,
    aspectRatio: Float,
    muted: Boolean,
    onToggleMute: () -> Unit,
    onHangUp: () -> Unit,
): CallPictureInPicture = NoCallPictureInPicture

private object NoCallPictureInPicture : CallPictureInPicture {
    override val active: Boolean = false
}
