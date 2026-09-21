package eu.tintera.plume.core.ui

import androidx.compose.runtime.Composable

/** iOS klávesnice GIFy do pole nevkládá, sdílí je přes schránku nebo rozšíření. */
@Composable
actual fun KeyboardContentReceiver(
    acceptAnimations: Boolean,
    acceptImages: Boolean,
    onContent: (KeyboardContent) -> Unit,
    onError: (UiText) -> Unit,
    content: @Composable () -> Unit,
) = content()
