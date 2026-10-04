package eu.tintera.plume.features.conversation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.LoginUrlPrompt
import eu.tintera.plume.resources.bot_login_confirm
import eu.tintera.plume.resources.bot_login_text
import eu.tintera.plume.resources.bot_login_title
import eu.tintera.plume.resources.bot_login_write_access
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
import eu.tintera.plume.resources.webapp_consent_open
import eu.tintera.plume.resources.webapp_consent_text
import eu.tintera.plume.resources.webapp_consent_title
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

/**
 * Souhlas před prvním otevřením mini aplikace u bota. TDLib ho předepisuje: mini aplikace
 * dostane údaje o účtu a co v ní uživatel udělá, může dostat i bot. Ptá se jednou na bota.
 */
@Composable
internal fun WebAppConsentDialog(botName: String, onOpen: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.webapp_consent_title)) },
        text = { Text(stringResource(Res.string.webapp_consent_text, botName)) },
        confirmButton = { TextButton(onClick = onOpen) { Text(stringResource(Res.string.webapp_consent_open)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.common_cancel)) } },
    )
}

/**
 * Přihlášení na web z tlačítka bota. Doména je v nadpisu — o ni jde, popisek tlačítka píše bot.
 * Věta říká, co web dostane; zaškrtávátko (jen když se bot ptá) je ve výchozím stavu zapnuté,
 * jako u oficiálních klientů — bez něj by přihlášení u většiny webů postrádalo smysl.
 */
@Composable
internal fun LoginUrlDialog(prompt: LoginUrlPrompt.Confirm, onConfirm: (allowWriteAccess: Boolean) -> Unit, onDismiss: () -> Unit) {
    var allowWrite by rememberSaveable { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.bot_login_title, prompt.domain)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(Res.string.bot_login_text, prompt.domain))
                if (prompt.requestWriteAccess) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(value = allowWrite, role = Role.Checkbox, onValueChange = { allowWrite = it }),
                    ) {
                        Checkbox(checked = allowWrite, onCheckedChange = null)
                        Text(
                            stringResource(Res.string.bot_login_write_access, prompt.botName),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(prompt.requestWriteAccess && allowWrite) }) {
                Text(stringResource(Res.string.bot_login_confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.common_cancel)) } },
    )
}
