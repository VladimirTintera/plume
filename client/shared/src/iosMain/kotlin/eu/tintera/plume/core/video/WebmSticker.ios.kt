package eu.tintera.plume.core.video

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** WebM iOS neumí (AVFoundation kontejner nepřečte); samolepka zůstává náhledem. */
actual val webmStickersSupported: Boolean = false

@Composable
actual fun WebmSticker(
    path: String,
    playing: Boolean,
    contentDescription: String?,
    modifier: Modifier,
    placeholder: @Composable () -> Unit,
) {
    Box(modifier) { placeholder() }
}
