package eu.tintera.plume.core.calls

import androidx.compose.runtime.Composable
import eu.tintera.plume.core.audio.RecordPermission
import eu.tintera.plume.core.camera.rememberVideoNotePermission

@Composable
actual fun rememberCallCameraPermission(): RecordPermission = rememberVideoNotePermission()
