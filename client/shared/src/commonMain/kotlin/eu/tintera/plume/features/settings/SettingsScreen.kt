package eu.tintera.plume.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.notifications_24dp
import eu.tintera.plume.resources.open_in_new_24dp
import org.jetbrains.compose.resources.painterResource

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
) {
    val account by viewModel.account.collectAsStateWithLifecycle()
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsStateWithLifecycle()
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

    // Uživatel se sem vrací ze systémového nastavení, kde mohl notifikace přepnout.
    LifecycleResumeEffect(Unit) {
        viewModel.refreshNotificationState()
        onPauseOrDispose { }
    }

    Scaffold(
        modifier = modifier,
        topBar = { LargeFlexibleTopAppBar(title = { Text("Nastavení") }) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(Modifier.fillMaxWidth()) {
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
                    }
                }
            }

            // Stav oprávnění se ukazuje vždycky, ne jen když chybí — jinak by uživatel neměl kde
            // ověřit, že notifikace opravdu běží, a neměl by je kde vypnout.
            Card(Modifier.fillMaxWidth()) {
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    leadingContent = {
                        Icon(
                            painterResource(Res.drawable.notifications_24dp),
                            contentDescription = null,
                        )
                    },
                    headlineContent = { Text("Notifikace") },
                    supportingContent = {
                        Text(
                            if (notificationsEnabled) {
                                "Zapnuté"
                            } else {
                                "Vypnuté — o nových zprávách se dozvíš až v aplikaci"
                            },
                        )
                    },
                    trailingContent = {
                        Icon(
                            painterResource(Res.drawable.open_in_new_24dp),
                            contentDescription = null,
                        )
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { viewModel.openNotificationSettings() },
                )
            }

            // Destruktivní akce dostane error roli a potvrzení s konkrétním následkem.
            Button(
                onClick = { confirmLogOut = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
            ) {
                Text("Odhlásit se")
            }
        }
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
        title = { Text("Odhlásit se?") },
        text = {
            Text(
                "Zprávy a stažené soubory se z tohohle zařízení smažou. " +
                    "Účet i chaty na ostatních zařízeních zůstanou beze změny.",
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = "Odhlásit", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Zrušit") }
        },
    )
}
