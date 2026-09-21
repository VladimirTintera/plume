package eu.tintera.plume.core.ui

import androidx.compose.runtime.Composable

/** iOS snímky obrazovky zakázat neumí; viz `SecureScreen` v commonMain. */
@Composable
actual fun SecureScreen(enabled: Boolean) = Unit
