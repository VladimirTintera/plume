package eu.tintera.plume.core.ui

import androidx.compose.runtime.Composable

/**
 * Vkládání obrázků a GIFů ze schránky desktop zatím neumí — vstupní pole bere jen text
 * (viz docs/roadmap.md).
 */
@Composable
actual fun KeyboardContentReceiver(
    acceptAnimations: Boolean,
    acceptImages: Boolean,
    acceptFiles: Boolean,
    onContent: (KeyboardContent) -> Unit,
    onError: (UiText) -> Unit,
    content: @Composable () -> Unit,
) = content()

@Composable
actual fun ClipboardPasteSuggestion(
    active: Boolean,
    acceptAnimations: Boolean,
    acceptImages: Boolean,
    onContent: (KeyboardContent) -> Unit,
    onError: (UiText) -> Unit,
    container: @Composable (kind: ClipboardMediaKind, pasteButton: @Composable () -> Unit) -> Unit,
) = Unit
