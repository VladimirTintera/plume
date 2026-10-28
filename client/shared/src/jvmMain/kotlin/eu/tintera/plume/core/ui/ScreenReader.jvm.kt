package eu.tintera.plume.core.ui

import androidx.compose.runtime.Composable

/** Desktop běžící čtečku obrazovky nehlásí. */
@Composable
actual fun rememberScreenReaderEnabled(): Boolean = false
