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
import org.jetbrains.compose.resources.StringResource
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
    onOpenFolders: () -> Unit,
    onOpenProxy: () -> Unit,
    onOpenAccountPrivacy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Hlášky sekcí (odhlášené zařízení, výsledek QR přihlášení, neuložené nastavení notifikací).
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = settingsScrollBehavior()
    Scaffold(
        modifier = modifier.settingsScroll(scrollBehavior),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            SettingsTopBar(title = stringResource(section.title), onBack = onBack, scrollBehavior = scrollBehavior)
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .settingsContentPadding(padding)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = settingsContentBottom(padding, 8.dp)),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Jen jedna větev se skládá, takže vznikne právě jeden ViewModel — ten druhý
            // by si jinak vyžádal účet ze serveru zbytečně.
            when (section) {
                SettingsSection.General -> GeneralSettings(viewModel = koinViewModel(), onEditProfile = onEditProfile, onOpenBlocked = onOpenBlocked, onOpenFolders = onOpenFolders, onOpenProxy = onOpenProxy, onOpenAccountPrivacy = onOpenAccountPrivacy)
                SettingsSection.Notifications -> NotificationSettings(viewModel = koinViewModel(), snackbarHostState = snackbarHostState)
                SettingsSection.Appearance -> AppearanceSettings(viewModel = koinViewModel())
                SettingsSection.Devices -> DevicesSettings(viewModel = koinViewModel(), snackbarHostState = snackbarHostState)
                SettingsSection.WhatsNew -> WhatsNewSettings()
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
) = SettingsGroup(stringResource(title), content)

/** Skupina s nadpisem, který není pevný text (verze a datum vydání v „Co je nového"). */
@Composable
internal fun SettingsGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp),
        )
        Card(Modifier.fillMaxWidth()) {
            Column(content = content)
        }
    }
}
