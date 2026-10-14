package eu.tintera.plume.core.calls

import android.app.Activity
import android.media.projection.MediaProjectionManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import eu.tintera.plume.core.audio.RecordPermission

/**
 * Systémový dialog „Sdílet obrazovku" (`createScreenCaptureIntent`); výsledek si převezme
 * knihovna hovoru přes [ScreenCapture.permission]. Odmítnutí = `false`, sdílení se nezapne.
 */
@Composable
actual fun rememberScreenSharePermission(): RecordPermission? {
    val context = LocalContext.current
    val pending = remember { arrayOfNulls<(Boolean) -> Unit>(1) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data.takeIf { result.resultCode == Activity.RESULT_OK }
        if (data != null) ScreenCapture.permission = data
        pending[0]?.invoke(data != null)
        pending[0] = null
    }
    return remember(context, launcher) {
        object : RecordPermission {
            override fun request(onResult: (granted: Boolean) -> Unit) {
                val manager = context.getSystemService(MediaProjectionManager::class.java) ?: return onResult(false)
                pending[0] = onResult
                launcher.launch(manager.createScreenCaptureIntent())
            }
        }
    }
}
