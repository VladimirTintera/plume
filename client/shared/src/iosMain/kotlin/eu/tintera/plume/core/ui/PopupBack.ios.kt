package eu.tintera.plume.core.ui

import androidx.compose.runtime.Composable

actual val popupDismissesOnBack: Boolean = true

@Composable
actual fun PopupBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit

@Composable
actual fun PopupBackAfterKeyboard(onBack: () -> Unit) = Unit
