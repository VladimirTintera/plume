package eu.tintera.plume.core.calls

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import eu.tintera.plume.core.audio.RecordPermission
import eu.tintera.plume.core.audio.rememberRecordPermission
import eu.tintera.plume.core.camera.rememberVideoNotePermission

@Composable
actual fun rememberCallCameraPermission(): RecordPermission = rememberVideoNotePermission()

@Composable
actual fun rememberCallMicrophonePermission(): RecordPermission = rememberRecordPermission()

/** Při každém složení, ne `remember`: oprávnění může přibýt za běhu (dotaz u prvního hovoru). */
@Composable
actual fun rememberCallCameraGranted(): Boolean =
    ContextCompat.checkSelfPermission(LocalContext.current, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
