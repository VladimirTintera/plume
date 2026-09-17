package eu.tintera.plume.core.notifications

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.notification_permission_body
import eu.tintera.plume.resources.notification_permission_enable
import eu.tintera.plume.resources.notification_permission_later
import eu.tintera.plume.resources.notification_permission_title
import eu.tintera.plume.resources.notifications_24dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
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

    AlertDialog(
        onDismissRequest = {
            permission.markAsked()
            showRationale = false
        },
        icon = { Icon(painterResource(Res.drawable.notifications_24dp), contentDescription = null) },
        title = { Text(stringResource(Res.string.notification_permission_title)) },
        text = {
            Text(
                stringResource(Res.string.notification_permission_body),
            )
        },
        confirmButton = {
            // Afirmativní akce = filled Button (konvence M3).
            Button(
                onClick = {
                    permission.markAsked()
                    showRationale = false
                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                },
            ) {
                Text(stringResource(Res.string.notification_permission_enable))
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    permission.markAsked()
                    showRationale = false
                },
            ) {
                Text(stringResource(Res.string.notification_permission_later))
            }
        },
    )
}
