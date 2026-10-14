package eu.tintera.plume.core.calls

import androidx.compose.runtime.Composable
import eu.tintera.plume.core.audio.RecordPermission

/**
 * Souhlas se snímáním obrazovky pro sdílení v hovoru (Android: systémový dialog
 * MediaProjection). Platí na jedno sdílení, každé další se ptá znovu. `null` = platforma
 * obrazovku sdílet neumí.
 */
@Composable
expect fun rememberScreenSharePermission(): RecordPermission?
