package eu.tintera.plume.core.notifications

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.koin.compose.koinInject

@Composable
actual fun NotificationPermissionPrompt() {
    // Před Androidem 13 se o nic nežádá.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

    val permission = koinInject<NotificationPermission>()
    var showRationale by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* Odmítnutí nic nerozbije; zapnout to jde v Nastavení. */ }

    LaunchedEffect(Unit) {
        // Ptáme se jen jednou. Když uživatel řekne „Teď ne", další cesta vede přes Nastavení —
        // opakované vyskakování dialogu by bylo otravné a stejně by skončilo odmítnutím.
        if (!permission.isGranted() && !permission.wasAsked()) showRationale = true
    }

    if (!showRationale) return

    NotificationRationaleDialog(
        onEnable = {
            permission.markAsked()
            showRationale = false
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        },
        onDismiss = {
            permission.markAsked()
            showRationale = false
        },
    )
}
