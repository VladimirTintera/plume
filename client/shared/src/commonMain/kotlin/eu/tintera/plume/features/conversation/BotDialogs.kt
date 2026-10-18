package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.ui.DialogCheckRow
import eu.tintera.plume.resources.how_to_reg_24dp
import eu.tintera.plume.resources.location_on_24dp
import eu.tintera.plume.resources.smartphone_24dp
import eu.tintera.plume.resources.open_in_new_24dp
import eu.tintera.plume.core.ui.ConfirmDialog
import eu.tintera.plume.core.telegram.LoginLinkKind
import eu.tintera.plume.resources.login_link_code_text
import eu.tintera.plume.resources.login_link_code_title
import eu.tintera.plume.resources.login_link_qr_text
import eu.tintera.plume.resources.login_link_qr_title
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import eu.tintera.plume.resources.bot_share_location_confirm
import eu.tintera.plume.resources.bot_share_location_text
import eu.tintera.plume.resources.bot_share_location_title
import org.jetbrains.compose.resources.stringResource

/**
 * Odkaz z tlačítka bota. Adresa se ukáže celá — na tlačítku vidět nebyla a právě o ni jde.
 */
@Composable
internal fun OpenLinkDialog(url: String, onOpen: () -> Unit, onDismiss: () -> Unit) {
    ConfirmDialog(
        title = stringResource(Res.string.bot_link_title),
        text = url,
        confirmLabel = stringResource(Res.string.bot_link_open),
        onConfirm = onOpen,
        onDismiss = onDismiss,
        icon = Res.drawable.open_in_new_24dp,
    )
}

/** Upozornění od bota (`showAlert`): bot si přeje, aby ho uživatel potvrdil, ne jen zahlédl. */
@Composable
internal fun BotAlertDialog(text: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text(stringResource(Res.string.bot_alert_ok)) } },
    )
}

/**
 * Tlačítko bota chce telefonní číslo. Je to údaj o uživateli, takže se neposílá na jedno
 * ťuknutí — dialog říká, co přesně bot dostane.
 */
@Composable
internal fun SharePhoneDialog(onShare: () -> Unit, onDismiss: () -> Unit) {
    ConfirmDialog(
        title = stringResource(Res.string.bot_share_phone_title),
        text = stringResource(Res.string.bot_share_phone_text),
        confirmLabel = stringResource(Res.string.bot_share_phone_confirm),
        onConfirm = onShare,
        onDismiss = onDismiss,
        icon = Res.drawable.smartphone_24dp,
    )
}

/**
 * Poloha pro tlačítko klávesnice bota — údaj o uživateli, takže se jako číslo nejdřív potvrzuje.
 * Věta říká, že jde o polohu TEĎ, ne o její sledování.
 */
@Composable
internal fun ShareLocationDialog(onShare: () -> Unit, onDismiss: () -> Unit) {
    ConfirmDialog(
        title = stringResource(Res.string.bot_share_location_title),
        text = stringResource(Res.string.bot_share_location_text),
        confirmLabel = stringResource(Res.string.bot_share_location_confirm),
        onConfirm = onShare,
        onDismiss = onDismiss,
        icon = Res.drawable.location_on_24dp,
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
    ConfirmDialog(
        title = stringResource(Res.string.bot_login_title, prompt.domain),
        text = stringResource(Res.string.bot_login_text, prompt.domain),
        confirmLabel = stringResource(Res.string.bot_login_confirm),
        onConfirm = { onConfirm(prompt.requestWriteAccess && allowWrite) },
        onDismiss = onDismiss,
        icon = Res.drawable.how_to_reg_24dp,
        content = if (prompt.requestWriteAccess) {
            {
                DialogCheckRow(
                    label = stringResource(Res.string.bot_login_write_access, prompt.botName),
                    checked = allowWrite,
                    onChange = { allowWrite = it },
                )
            }
        } else {
            null
        },
    )
}

/**
 * Přihlašovací odkaz, který se z ťuknutí neotevírá (`InternalLink.LoginWarning`). Říká proč
 * a kudy vede bezpečná cesta — u QR kódu to TDLib výslovně předepisuje.
 */
@Composable
internal fun LoginLinkWarningDialog(kind: LoginLinkKind, onDismiss: () -> Unit) {
    val (title, text) = when (kind) {
        LoginLinkKind.Code -> Res.string.login_link_code_title to Res.string.login_link_code_text
        LoginLinkKind.QrCode -> Res.string.login_link_qr_title to Res.string.login_link_qr_text
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = { Text(stringResource(text)) },
        confirmButton = { TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text(stringResource(Res.string.bot_alert_ok)) } },
    )
}
