package eu.tintera.plume.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.navigation.SettingsSection
import eu.tintera.plume.features.devices.DevicesSettings
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.arrow_back_24dp
import eu.tintera.plume.resources.common_back
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Obsah jedné sekce nastavení — v list-detail rozvržení pravý panel, na telefonu obrazovka.
 *
 * [onBack] je `null` v dvoupanelu: tam sekce panel jen vymění a šipka zpět by neměla kam vést
 * (seznam sekcí je pořád vidět vedle). Ze stejného důvodu se sekce v dvoupanelu vždycky NAHRAZUJE,
 * nevrší se na zásobník.
 */
@Composable
fun SettingsSectionScreen(
    section: SettingsSection,
    onBack: (() -> Unit)?,
    onEditProfile: () -> Unit,
    onOpenBlocked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Hlášky sekcí (odhlášené zařízení, výsledek QR přihlášení, neuložené nastavení notifikací).
    val snackbarHostState = remember { SnackbarHostState() }
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(section.title)) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                painterResource(Res.drawable.arrow_back_24dp),
                                contentDescription = stringResource(Res.string.common_back),
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Jen jedna větev se skládá, takže vznikne právě jeden ViewModel — ten druhý
            // by si jinak vyžádal účet ze serveru zbytečně.
            when (section) {
                SettingsSection.General -> GeneralSettings(viewModel = koinViewModel(), onEditProfile = onEditProfile, onOpenBlocked = onOpenBlocked)
                SettingsSection.Notifications -> NotificationSettings(viewModel = koinViewModel(), snackbarHostState = snackbarHostState)
                SettingsSection.Appearance -> AppearanceSettings(viewModel = koinViewModel())
                SettingsSection.Devices -> DevicesSettings(viewModel = koinViewModel(), snackbarHostState = snackbarHostState)
            }
        }
    }
}

/**
 * Skupina uvnitř sekce: nadpis a pod ním blok položek.
 *
 * Nadpis a karta jsou JEDEN kus, ne dva sousedi ve sloupci sekce — s rozestupem sekce by nadpis
 * visel stejně daleko od svojí karty jako od té předchozí a nebylo by poznat, k čemu patří.
 * Skupiny jsou to, co sekci udrží čitelnou, až jich v ní bude víc.
 */
@Composable
internal fun SettingsGroup(
    title: StringResource,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp),
        )
        Card(Modifier.fillMaxWidth()) {
            Column(content = content)
        }
    }
}
