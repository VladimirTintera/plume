package eu.tintera.plume.core.calls

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import eu.tintera.plume.core.audio.RecordPermission

/**
 * Desktop se předem neptá: na macOS si o kameru řekne systém sám, když ji knihovna hovoru
 * poprvé otevře (`NSCameraUsageDescription` v Info.plist), a odmítnutá kamera jen nedá obraz.
 * Windows a Linux se aplikace na kameru neptají vůbec.
 */
@Composable
actual fun rememberCallCameraPermission(): RecordPermission = remember {
    object : RecordPermission {
        override fun request(onResult: (granted: Boolean) -> Unit) = onResult(true)
    }
}
