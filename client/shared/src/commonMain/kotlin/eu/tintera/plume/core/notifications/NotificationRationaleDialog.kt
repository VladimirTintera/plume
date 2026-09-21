package eu.tintera.plume.core.notifications

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.notification_permission_body
import eu.tintera.plume.resources.notification_permission_enable
import eu.tintera.plume.resources.notification_permission_later
import eu.tintera.plume.resources.notification_permission_title
import eu.tintera.plume.resources.notifications_24dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Vlastní vysvětlení před systémovým dotazem na notifikace — na Androidu i na iOS.
 *
 * Systémový dotaz dá obě platformy jen omezeně (Android dvakrát, iOS jednou napořád); vlastní
 * dialog žádný pokus nespotřebuje. [onDismiss] = „Teď ne" i zavření mimo dialog.
 */
@Composable
internal fun NotificationRationaleDialog(onEnable: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(painterResource(Res.drawable.notifications_24dp), contentDescription = null) },
        title = { Text(stringResource(Res.string.notification_permission_title)) },
        text = { Text(stringResource(Res.string.notification_permission_body)) },
        confirmButton = {
            // Afirmativní akce = filled Button (konvence M3).
            Button(onClick = onEnable) { Text(stringResource(Res.string.notification_permission_enable)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.notification_permission_later)) }
        },
    )
}
