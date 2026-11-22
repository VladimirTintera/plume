package eu.tintera.plume.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.navigation.SettingsSection
import eu.tintera.plume.features.devices.DevicesSettings
import eu.tintera.plume.resources.Res
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
    onOpenBlocked: () -> Unit,
    onOpenFolders: () -> Unit,
    onOpenStickerSets: () -> Unit,
    onOpenEmojiSets: () -> Unit,
    onOpenProxy: () -> Unit,
    onOpenAccountPrivacy: () -> Unit,
    onOpenTwoStep: () -> Unit,
    onOpenLoginEmail: () -> Unit,
    onOpenPasskeys: () -> Unit,
    onOpenNetworkUsage: () -> Unit,
    onOpenAppLock: () -> Unit,
    onOpenAiPlayground: () -> Unit,
    onOpenLicenses: () -> Unit,
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
            // Jen jedna větev se skládá, takže vzniknou jen ViewModely otevřené sekce.
            when (section) {
                SettingsSection.Privacy -> PrivacySettings(
                    blocked = koinViewModel(),
                    sensitive = koinViewModel(),
                    autoDelete = koinViewModel(),
                    twoStep = koinViewModel(),
                    onOpenBlocked = onOpenBlocked,
                    onOpenAccountPrivacy = onOpenAccountPrivacy,
                    onOpenTwoStep = onOpenTwoStep,
                    onOpenAppLock = onOpenAppLock,
                    onOpenLoginEmail = onOpenLoginEmail,
                    onOpenPasskeys = onOpenPasskeys,
                )
                SettingsSection.Notifications -> NotificationSettings(viewModel = koinViewModel(), snackbarHostState = snackbarHostState)
                SettingsSection.Chats -> ChatsSettings(
                    swipe = koinViewModel(),
                    quickReaction = koinViewModel(),
                    appearance = koinViewModel(),
                    onOpenFolders = onOpenFolders,
                    onOpenStickerSets = onOpenStickerSets,
                    onOpenEmojiSets = onOpenEmojiSets,
                )
                SettingsSection.Appearance -> AppearanceSettings(viewModel = koinViewModel())
                SettingsSection.Storage -> {
                    StorageSettings(viewModel = koinViewModel(), snackbarHostState = snackbarHostState, onOpenNetworkUsage = onOpenNetworkUsage)
                    ConnectionSettings(viewModel = koinViewModel(), onOpenProxy = onOpenProxy)
                }
                SettingsSection.Devices -> DevicesSettings(viewModel = koinViewModel(), snackbarHostState = snackbarHostState)
                SettingsSection.Ai -> OnDeviceAiSettings(viewModel = koinViewModel(), onOpenPlayground = onOpenAiPlayground)
                SettingsSection.WhatsNew -> WhatsNewSettings()
                SettingsSection.About -> AboutSettings(snackbarHostState = snackbarHostState, onOpenLicenses = onOpenLicenses)
            }
        }
    }
}
