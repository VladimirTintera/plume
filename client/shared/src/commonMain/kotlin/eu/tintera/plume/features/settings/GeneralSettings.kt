package eu.tintera.plume.features.settings

import eu.tintera.plume.resources.settings_edit_profile
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.common_cancel
import eu.tintera.plume.resources.settings_sign_out
import eu.tintera.plume.resources.settings_sign_out_body
import eu.tintera.plume.resources.settings_sign_out_confirm
import eu.tintera.plume.resources.settings_sign_out_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Sekce „Obecné": kdo je přihlášený, blokovaní, AI v zařízení a odhlášení. Stav notifikací je v sekci Notifikace.
 *
 * Je to obsah do sloupce sekce, ne celá obrazovka — hlavičku a scrollování drží
 * [SettingsSectionScreen].
 */
@Composable
fun GeneralSettings(viewModel: SettingsViewModel, onEditProfile: () -> Unit, onOpenBlocked: () -> Unit, onOpenFolders: () -> Unit, onOpenProxy: () -> Unit) {
    val account by viewModel.account.collectAsStateWithLifecycle()
    var confirmLogOut by rememberSaveable { mutableStateOf(false) }

    if (confirmLogOut) {
        LogOutDialog(
            onConfirm = {
                confirmLogOut = false
                viewModel.logOut()
            },
            onDismiss = { confirmLogOut = false },
        )
    }

    // Karta účtu vede do úpravy profilu — tam, kde je vidět, co se upravuje.
    Card(onClick = onEditProfile, modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val current = account
            if (current == null) {
                LoadingIndicator()
            } else {
                Text(current.displayName, style = MaterialTheme.typography.titleLarge)
                Text(
                    current.phoneNumber.let { if (it.startsWith("+")) it else "+$it" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                current.username?.let {
                    Text(
                        "@$it",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    stringResource(Res.string.settings_edit_profile),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }

    // Chaty: složky. Vlastní sekce by měla jednu položku.
    ChatsSettings(onOpenFolders = onOpenFolders)

    // Soukromí: blokovaní a citlivý obsah. V Obecných, samostatná sekce by měla dvě položky.
    PrivacySettings(
        blocked = koinViewModel(),
        sensitive = koinViewModel(),
        autoDelete = koinViewModel(),
        onOpenBlocked = onOpenBlocked,
    )

    ConnectionSettings(onOpenProxy = onOpenProxy)

    // Experimentální funkce s modelem v zařízení. Nad odhlášením, které má zůstat poslední.
    OnDeviceAiSettings(viewModel = koinViewModel())

    // Destruktivní akce dostane error roli a potvrzení s konkrétním následkem.
    Button(
        onClick = { confirmLogOut = true },
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Text(stringResource(Res.string.settings_sign_out))
    }
}

/**
 * Potvrzení odhlášení. Pojmenovává následek: odhlášení maže lokální data TDLib, takže se po
 * novém přihlášení všechno stahuje znovu. Účtu samotnému ani ostatním zařízením se nic nestane.
 */
@Composable
private fun LogOutDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.settings_sign_out_title)) },
        text = {
            Text(
                stringResource(Res.string.settings_sign_out_body),
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = stringResource(Res.string.settings_sign_out_confirm), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.common_cancel)) }
        },
    )
}
