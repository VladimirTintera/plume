package eu.tintera.plume.wear

import eu.tintera.plume.core.storage.AutoStorageCleaner
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.AlertDialogDefaults
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.di.APP_SCOPE
import eu.tintera.plume.core.telegram.TelegramSession
import eu.tintera.plume.core.ui.fileSizeText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.delete_24dp
import eu.tintera.plume.resources.wear_clear_cache
import eu.tintera.plume.resources.wear_clear_cache_done
import eu.tintera.plume.resources.wear_clear_cache_hint
import eu.tintera.plume.resources.wear_confirm_cleared
import eu.tintera.plume.resources.wear_clear_cache_running
import eu.tintera.plume.resources.logout_24dp
import eu.tintera.plume.resources.settings_title
import eu.tintera.plume.resources.wear_logout
import eu.tintera.plume.resources.wear_logout_text
import eu.tintera.plume.resources.wear_logout_title
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.core.qualifier.named

private val logger = Logger.withTag("WearSettings")

private const val CLEARING = -1L

/**
 * Nastavení hodinek, otevírá se tlačítkem pod nadpisem seznamu chatů. Hodinky nemají menu v liště
 * a konec dlouhého seznamu chatů nikoho nenapadne hledat (na konci původně bylo odhlášení).
 * Vyčištění mezipaměti a odhlášení; další volby hodinek patří sem.
 */
@Composable
fun SettingsScreen() {
    val state = rememberTransformingLazyColumnState()
    var confirmLogout by remember { mutableStateOf(false) }
    val cleaner = koinInject<AutoStorageCleaner>()
    val scope = rememberCoroutineScope()
    // null = ještě nečištěno, -1 = právě čistí, jinak uvolněné bajty.
    var freed by remember { mutableStateOf<Long?>(null) }
    var cleared by remember { mutableStateOf(false) }
    val spec = rememberTransformationSpec()
    ScreenScaffold(scrollState = state) { contentPadding ->
        TransformingLazyColumn(state = state, contentPadding = contentPadding) {
            item {
                ListHeader(
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                ) { Text(stringResource(Res.string.settings_title)) }
            }
            // Ruční vyčištění vedle automatického úklidu (AutoStorageCleaner): soubory zůstávají
            // na serveru, potvrzení proto netřeba.
            item {
                val current = freed
                Button(
                    onClick = {
                        if (current == CLEARING) return@Button
                        freed = CLEARING
                        scope.launch {
                            freed = cleaner.clearNow().getOrDefault(0L)
                            cleared = true
                        }
                    },
                    icon = { Icon(painterResource(Res.drawable.delete_24dp), contentDescription = null) },
                    label = { Text(stringResource(Res.string.wear_clear_cache)) },
                    secondaryLabel = {
                        Text(
                            when (current) {
                                null -> stringResource(Res.string.wear_clear_cache_hint)
                                CLEARING -> stringResource(Res.string.wear_clear_cache_running)
                                else -> stringResource(Res.string.wear_clear_cache_done, fileSizeText(current))
                            },
                        )
                    },
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                )
            }
            item {
                Button(
                    onClick = { confirmLogout = true },
                    icon = { Icon(painterResource(Res.drawable.logout_24dp), contentDescription = null) },
                    label = { Text(stringResource(Res.string.wear_logout)) },
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                )
            }
        }
    }
    LogoutDialog(visible = confirmLogout, onDismiss = { confirmLogout = false })
    // Potvrzení po vyčištění; kolik se uvolnilo, zůstává pod tlačítkem.
    SuccessConfirmation(visible = cleared, text = stringResource(Res.string.wear_confirm_cleared), onDismiss = { cleared = false })
}

/**
 * Potvrzení odhlášení. Odhlásí jen hodinky (vlastní sezení TDLib); po odhlášení automat přejde
 * k QR kódu sám a o zbytek (notifikace, dlaždice, zrcadlení z telefonu) se starají odběry stavu
 * přihlášení. Na telefonu se nic nemění.
 */
@Composable
private fun LogoutDialog(visible: Boolean, onDismiss: () -> Unit) {
    val session = koinInject<TelegramSession>()
    val appScope = koinInject<CoroutineScope>(named(APP_SCOPE))
    AlertDialog(
        visible = visible,
        onDismissRequest = onDismiss,
        icon = { Icon(painterResource(Res.drawable.logout_24dp), contentDescription = null) },
        title = { Text(stringResource(Res.string.wear_logout_title)) },
        text = { Text(stringResource(Res.string.wear_logout_text)) },
        confirmButton = {
            AlertDialogDefaults.ConfirmButton(
                onClick = {
                    onDismiss()
                    // APP_SCOPE: obrazovka po odhlášení zmizí a její scope by se zrušil uprostřed.
                    appScope.launch {
                        session.logOut().onFailure { logger.w(it) { "Odhlášení hodinek se nepodařilo." } }
                    }
                },
            )
        },
    )
}
