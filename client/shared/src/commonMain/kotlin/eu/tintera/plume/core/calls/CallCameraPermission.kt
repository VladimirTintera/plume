package eu.tintera.plume.core.calls

import androidx.compose.runtime.Composable
import eu.tintera.plume.core.audio.RecordPermission

/**
 * Oprávnění ke kameře pro videohovor. Na telefonech totéž jako u videokoleček; desktop kolečka
 * nenatáčí (jejich oprávnění tam vždycky odmítne), kameru hovoru ale má.
 */
@Composable
expect fun rememberCallCameraPermission(): RecordPermission
