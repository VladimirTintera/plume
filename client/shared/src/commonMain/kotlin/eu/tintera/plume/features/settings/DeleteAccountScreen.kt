package eu.tintera.plume.features.settings

import androidx.compose.foundation.layout.Arrangement
import eu.tintera.plume.core.ui.MediumButton
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.ui.BlockTextField
import eu.tintera.plume.core.ui.PasswordField
import eu.tintera.plume.core.ui.resolve
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.common_cancel
import eu.tintera.plume.resources.delete_account_away
import eu.tintera.plume.resources.delete_account_button
import eu.tintera.plume.resources.delete_account_confirm
import eu.tintera.plume.resources.delete_account_confirm_text
import eu.tintera.plume.resources.delete_account_confirm_title
import eu.tintera.plume.resources.delete_account_intro
import eu.tintera.plume.resources.delete_account_password
import eu.tintera.plume.resources.delete_account_reason
import eu.tintera.plume.resources.delete_account_retry
import eu.tintera.plume.resources.delete_account_status_failed
import eu.tintera.plume.resources.delete_account_title
import eu.tintera.plume.resources.delete_account_undo
import org.jetbrains.compose.resources.stringResource

/**
 * Smazání účtu: co se stane, nepovinný důvod, heslo (jen s dvoufázovým ověřením) a tlačítko
 * s posledním potvrzením. Po smazání se TDLib klient zavře a aplikace přejde na přihlášení
 * sama — stejnou cestou jako po odhlášení.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DeleteAccountScreen(viewModel: DeleteAccountViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current

    val scrollBehavior = settingsScrollBehavior()
    Scaffold(
        modifier = modifier.settingsScroll(scrollBehavior),
        topBar = {
            SettingsTopBar(title = stringResource(Res.string.delete_account_title), onBack = onBack, scrollBehavior = scrollBehavior)
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .settingsContentPadding(padding)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = settingsContentBottom(padding, 16.dp)),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(Res.string.delete_account_intro), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(Res.string.delete_account_undo),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
            )
            Text(
                stringResource(Res.string.delete_account_away),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            BlockTextField(
                value = viewModel.reason,
                onValueChange = viewModel::onReasonChange,
                placeholder = Res.string.delete_account_reason,
                isError = false,
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Done,
                onSubmit = { focusManager.clearFocus() },
            )

            when (state.needsPassword) {
                true -> {
                    Text(
                        stringResource(Res.string.delete_account_password),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    PasswordField(
                        value = viewModel.password,
                        isError = state.error != null,
                        onValueChange = viewModel::onPasswordChange,
                        onSubmit = viewModel::requestDelete,
                    )
                }
                false -> Unit
                null -> if (state.statusFailed) {
                    Text(
                        stringResource(Res.string.delete_account_status_failed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(onClick = viewModel::loadStatus, shapes = ButtonDefaults.shapes()) { Text(stringResource(Res.string.delete_account_retry)) }
                } else {
                    LoadingIndicator()
                }
            }

            state.error?.let {
                Text(it.resolve(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            // Nevratná akce: chybová role a potvrzení s následkem, jako odhlášení.
            MediumButton(
                onClick = {
                    focusManager.clearFocus()
                    viewModel.requestDelete()
                },
                enabled = viewModel.canDelete,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                if (state.busy) {
                    LoadingIndicator(Modifier.size(24.dp), color = MaterialTheme.colorScheme.onError)
                } else {
                    Text(stringResource(Res.string.delete_account_button))
                }
            }
        }
    }

    if (state.confirming) {
        AlertDialog(
            onDismissRequest = viewModel::dismissConfirm,
            title = { Text(stringResource(Res.string.delete_account_confirm_title)) },
            text = { Text(stringResource(Res.string.delete_account_confirm_text)) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDelete, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(Res.string.delete_account_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissConfirm, shapes = ButtonDefaults.shapes()) { Text(stringResource(Res.string.common_cancel)) }
            },
        )
    }
}
