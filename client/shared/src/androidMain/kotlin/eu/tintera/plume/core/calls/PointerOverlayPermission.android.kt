package eu.tintera.plume.core.calls

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** Nastavení „Zobrazení přes jiné aplikace" rovnou pro Plume; výsledek je stav po návratu. */
@Composable
actual fun rememberPointerOverlayPermission(): PointerOverlayPermission? {
    val context = LocalContext.current
    val pending = remember { arrayOfNulls<() -> Unit>(1) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        pending[0]?.invoke()
        pending[0] = null
    }
    return remember(context, launcher) {
        object : PointerOverlayPermission {
            override val granted: Boolean get() = Settings.canDrawOverlays(context)

            override fun request(onDone: () -> Unit) {
                pending[0] = onDone
                runCatching {
                    launcher.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
                }.onFailure {
                    pending[0] = null
                    onDone()
                }
            }
        }
    }
}
