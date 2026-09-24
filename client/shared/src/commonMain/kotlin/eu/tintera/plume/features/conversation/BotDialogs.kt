package eu.tintera.plume.features.conversation

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.bot_alert_ok
import eu.tintera.plume.resources.bot_link_open
import eu.tintera.plume.resources.bot_link_title
import eu.tintera.plume.resources.bot_share_phone_confirm
import eu.tintera.plume.resources.bot_share_phone_text
import eu.tintera.plume.resources.bot_share_phone_title
import eu.tintera.plume.resources.common_cancel
import org.jetbrains.compose.resources.stringResource

/**
 * Odkaz z tlačítka bota. Adresa se ukáže celá — na tlačítku vidět nebyla a právě o ni jde.
 */
@Composable
internal fun OpenLinkDialog(url: String, onOpen: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.bot_link_title)) },
        text = { Text(url) },
        confirmButton = { TextButton(onClick = onOpen) { Text(stringResource(Res.string.bot_link_open)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.common_cancel)) } },
    )
}

/** Upozornění od bota (`showAlert`): bot si přeje, aby ho uživatel potvrdil, ne jen zahlédl. */
@Composable
internal fun BotAlertDialog(text: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.bot_alert_ok)) } },
    )
}

/**
 * Tlačítko bota chce telefonní číslo. Je to údaj o uživateli, takže se neposílá na jedno
 * ťuknutí — dialog říká, co přesně bot dostane.
 */
@Composable
internal fun SharePhoneDialog(onShare: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.bot_share_phone_title)) },
        text = { Text(stringResource(Res.string.bot_share_phone_text)) },
        confirmButton = { TextButton(onClick = onShare) { Text(stringResource(Res.string.bot_share_phone_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.common_cancel)) } },
    )
}
