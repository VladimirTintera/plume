package eu.tintera.plume.features.settings

import eu.tintera.plume.resources.error_24dp
import androidx.compose.material3.FilledTonalButton
import androidx.compose.foundation.clickable
import eu.tintera.plume.core.ui.ConfirmDialog
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.telegram.AccountRepository
import eu.tintera.plume.core.telegram.LoginPasskey
import eu.tintera.plume.core.ui.CustomEmojiIcon
import eu.tintera.plume.core.ui.asDayLabel
import eu.tintera.plume.core.ui.resolveNow
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.delete_24dp
import eu.tintera.plume.resources.passkey_24dp
import eu.tintera.plume.resources.passkey_added
import eu.tintera.plume.resources.passkey_never_used
import eu.tintera.plume.resources.passkey_remove
import eu.tintera.plume.resources.passkey_remove_confirm
import eu.tintera.plume.resources.passkey_remove_text
import eu.tintera.plume.resources.passkey_remove_title
import eu.tintera.plume.resources.passkey_summary
import eu.tintera.plume.resources.passkey_unnamed
import eu.tintera.plume.resources.passkey_used
import eu.tintera.plume.resources.passkeys_empty
import eu.tintera.plume.resources.passkeys_intro
import eu.tintera.plume.resources.passkeys_limitation
import eu.tintera.plume.resources.passkeys_load_failed
import eu.tintera.plume.resources.passkeys_retry
import eu.tintera.plume.resources.passkeys_section
import eu.tintera.plume.resources.settings_passkeys
import eu.tintera.plume.resources.settings_passkeys_count
import eu.tintera.plume.resources.settings_passkeys_none
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Řádek v Soukromí s počtem klíčů. Čte se při každém zobrazení sekce — update o změně seznamu
 * TDLib neposílá. Bez klíčů řádek zůstává: řekne, kde se klíč přidává, a obrazovka proč ne tady.
 */
@Composable
internal fun PasskeysRow(onOpen: () -> Unit) {
    val accounts = koinInject<AccountRepository>()
    val count by produceState<Int?>(initialValue = null) { value = accounts.loginPasskeys().getOrNull()?.size }
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { Icon(painterResource(Res.drawable.passkey_24dp), contentDescription = null) },
        headlineContent = { Text(stringResource(Res.string.settings_passkeys)) },
        supportingContent = count?.let { n ->
            {
                Text(
                    if (n == 0) {
                        stringResource(Res.string.settings_passkeys_none)
                    } else {
                        pluralStringResource(Res.plurals.settings_passkeys_count, n, n)
                    },
                )
            }
        },
        modifier = Modifier.clickable(onClick = onOpen),
    )
}

/**
 * Přístupové klíče: co to je, proč je Plume jen ukazuje, seznam a odebrání. Vrší se nad sekci
 * Soukromí jako dvoufázové ověření.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PasskeysScreen(viewModel: PasskeysViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarScope = rememberCoroutineScope()
    // Snackbar mimo tělo efektu: vynulování chyby mění klíč a zrušilo by ho.
    LaunchedEffect(state.error) {
        val error = state.error ?: return@LaunchedEffect
        viewModel.consumeError()
        snackbarScope.launch { snackbarHostState.showSnackbar(error.resolveNow()) }
    }

    SettingsPage(
        title = stringResource(Res.string.settings_passkeys),
        onBack = onBack,
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) {
        SettingsPageHeader(icon = Res.drawable.passkey_24dp, title = null, text = stringResource(Res.string.passkeys_intro))

        val passkeys = state.passkeys
        when {
            passkeys != null -> SettingsGroup(Res.string.passkeys_section) {
                if (passkeys.isEmpty()) {
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        headlineContent = { Text(stringResource(Res.string.passkeys_empty)) },
                    )
                }
                for (passkey in passkeys) {
                    PasskeyRow(
                        passkey = passkey,
                        removing = state.removing == passkey.id,
                        enabled = state.removing == null,
                        onRemove = { viewModel.requestRemove(passkey) },
                    )
                }
            }
            // Chyba na místě seznamu, ve stejné sekci: řádek s ikonou a tónovým „Zkusit znovu"
            // (pravidlo chybových stavů v CLAUDE.md). Dřív červený text a `TextButton` pod
            // nadpisem stránky, bez bloku — vypadalo to jako rozbitá obrazovka.
            state.loadFailed -> SettingsGroup(Res.string.passkeys_section) {
                ListItem(
                    colors = ListItemDefaults.colors(
                        containerColor = Color.Transparent,
                        leadingIconColor = MaterialTheme.colorScheme.error,
                    ),
                    leadingContent = { Icon(painterResource(Res.drawable.error_24dp), contentDescription = null) },
                    headlineContent = { Text(stringResource(Res.string.passkeys_load_failed)) },
                    trailingContent = {
                        FilledTonalButton(onClick = viewModel::load, shapes = ButtonDefaults.shapes()) {
                            Text(stringResource(Res.string.passkeys_retry))
                        }
                    },
                )
            }
            else -> SettingsPageLoading()
        }

        Text(
            stringResource(Res.string.passkeys_limitation),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }

    state.confirming?.let { passkey ->
        ConfirmDialog(
            title = stringResource(Res.string.passkey_remove_title, passkey.displayName()),
            text = stringResource(Res.string.passkey_remove_text),
            confirmLabel = stringResource(Res.string.passkey_remove_confirm),
            onConfirm = viewModel::confirmRemove,
            onDismiss = viewModel::dismissRemove,
            icon = Res.drawable.delete_24dp,
            destructive = true,
        )
    }
}

@Composable
private fun LoginPasskey.displayName(): String = name.ifBlank { stringResource(Res.string.passkey_unnamed) }

/** Název, kdy přidán a kdy naposledy použit; ikona správce hesel, když ji server zná. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PasskeyRow(passkey: LoginPasskey, removing: Boolean, enabled: Boolean, onRemove: () -> Unit) {
    val added = stringResource(Res.string.passkey_added, passkey.addedAt.asDayLabel())
    val used = passkey.lastUsedAt?.let { stringResource(Res.string.passkey_used, it.asDayLabel()) }
        ?: stringResource(Res.string.passkey_never_used)
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = {
            val icon = passkey.iconCustomEmojiId
            if (icon != null) {
                CustomEmojiIcon(customEmojiId = icon, size = 24.dp)
            } else {
                Icon(painterResource(Res.drawable.passkey_24dp), contentDescription = null)
            }
        },
        headlineContent = { Text(passkey.displayName(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(stringResource(Res.string.passkey_summary, added, used)) },
        trailingContent = {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                if (removing) {
                    LoadingIndicator(Modifier.size(24.dp))
                } else {
                    IconButton(onClick = onRemove, enabled = enabled, shapes = IconButtonDefaults.shapes()) {
                        Icon(painterResource(Res.drawable.delete_24dp), contentDescription = stringResource(Res.string.passkey_remove))
                    }
                }
            }
        },
    )
}
