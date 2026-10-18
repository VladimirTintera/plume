package eu.tintera.plume.features.settings

import eu.tintera.plume.core.ui.ChoiceRow
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.connectivity.detectsRoaming
import eu.tintera.plume.core.settings.AppSettings
import eu.tintera.plume.core.settings.AutoDownloadLevel
import eu.tintera.plume.core.settings.AutoDownloadSettings
import eu.tintera.plume.core.settings.AutoDownloadSync
import eu.tintera.plume.core.settings.HIGH_BYTES
import eu.tintera.plume.core.settings.LOW_VIDEO_BYTES
import eu.tintera.plume.core.settings.MEDIUM_BYTES
import eu.tintera.plume.core.ui.fileSizeText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.auto_download_body
import eu.tintera.plume.resources.auto_download_high
import eu.tintera.plume.resources.auto_download_high_summary
import eu.tintera.plume.resources.auto_download_low
import eu.tintera.plume.resources.auto_download_low_summary
import eu.tintera.plume.resources.auto_download_medium
import eu.tintera.plume.resources.auto_download_medium_summary
import eu.tintera.plume.resources.auto_download_mobile
import eu.tintera.plume.resources.auto_download_off
import eu.tintera.plume.resources.auto_download_off_summary
import eu.tintera.plume.resources.auto_download_roaming
import eu.tintera.plume.resources.auto_download_title
import eu.tintera.plume.resources.auto_download_wifi
import eu.tintera.plume.resources.signal_cellular_alt_24dp
import eu.tintera.plume.resources.language_24dp
import eu.tintera.plume.resources.wifi_24dp
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Automatické stahování (Úložiště). Zápis běží v rozsahu aplikace, ne ve `viewModelScope` —
 * volba těsně před odchodem z obrazovky se tak neztratí (viz [AppearanceViewModel]). Jde přes
 * [AutoDownloadSync], aby se dostala i na server pro další přihlášené zařízení.
 */
class AutoDownloadViewModel(
    settings: AppSettings,
    private val sync: AutoDownloadSync,
) : ViewModel() {

    val levels: StateFlow<AutoDownloadSettings> = settings.autoDownload

    fun set(network: AutoDownloadNetwork, level: AutoDownloadLevel) {
        val current = levels.value
        sync.change(
            when (network) {
                AutoDownloadNetwork.WiFi -> current.copy(wifi = level)
                AutoDownloadNetwork.Mobile -> current.copy(mobile = level)
                AutoDownloadNetwork.Roaming -> current.copy(roaming = level)
            },
        )
    }
}

/** Síť, pro kterou se úroveň volí. */
enum class AutoDownloadNetwork { WiFi, Mobile, Roaming }

/**
 * Skupina „Automatické stahování": úroveň pro každou síť. Roaming jen tam, kde ho platforma
 * pozná ([detectsRoaming]) — na iOS by to byla volba, která se nikdy neuplatní.
 */
@Composable
internal fun AutoDownloadGroup(viewModel: AutoDownloadViewModel) {
    val levels by viewModel.levels.collectAsStateWithLifecycle()

    SettingsGroup(Res.string.auto_download_title) {
        AutoDownloadNetwork.entries
            .filter { it != AutoDownloadNetwork.Roaming || detectsRoaming }
            .forEach { network ->
                // Každá úroveň říká, CO se stáhne samo, s čísly — „Běžně" samo o sobě nic neřekne.
                // Věta nad volbami vysvětluje, co se stane s tím, co se samo nestáhne, a spořič dat.
                ChoiceRow(
                    headline = stringResource(network.title),
                    icon = network.icon,
                    options = AutoDownloadLevel.entries,
                    selected = network.of(levels),
                    label = { stringResource(it.title) },
                    optionSupporting = { it.summary() },
                    note = stringResource(Res.string.auto_download_body),
                    onSelect = { level -> viewModel.set(network, level) },
                )
            }
    }
}

private fun AutoDownloadNetwork.of(levels: AutoDownloadSettings): AutoDownloadLevel = when (this) {
    AutoDownloadNetwork.WiFi -> levels.wifi
    AutoDownloadNetwork.Mobile -> levels.mobile
    AutoDownloadNetwork.Roaming -> levels.roaming
}

private val AutoDownloadNetwork.title: StringResource
    get() = when (this) {
        AutoDownloadNetwork.WiFi -> Res.string.auto_download_wifi
        AutoDownloadNetwork.Mobile -> Res.string.auto_download_mobile
        AutoDownloadNetwork.Roaming -> Res.string.auto_download_roaming
    }

private val AutoDownloadNetwork.icon: DrawableResource
    get() = when (this) {
        AutoDownloadNetwork.WiFi -> Res.drawable.wifi_24dp
        AutoDownloadNetwork.Mobile -> Res.drawable.signal_cellular_alt_24dp
        AutoDownloadNetwork.Roaming -> Res.drawable.language_24dp
    }

private val AutoDownloadLevel.title: StringResource
    get() = when (this) {
        AutoDownloadLevel.Off -> Res.string.auto_download_off
        AutoDownloadLevel.Low -> Res.string.auto_download_low
        AutoDownloadLevel.Medium -> Res.string.auto_download_medium
        AutoDownloadLevel.High -> Res.string.auto_download_high
    }

/** Co se na úrovni stáhne samo. Velikosti se berou z týchž konstant jako limity. */
@Composable
private fun AutoDownloadLevel.summary(): String = when (this) {
    AutoDownloadLevel.Off -> stringResource(Res.string.auto_download_off_summary)
    AutoDownloadLevel.Low -> stringResource(Res.string.auto_download_low_summary, fileSizeText(LOW_VIDEO_BYTES))
    AutoDownloadLevel.Medium -> stringResource(Res.string.auto_download_medium_summary, fileSizeText(MEDIUM_BYTES))
    AutoDownloadLevel.High -> stringResource(Res.string.auto_download_high_summary, fileSizeText(HIGH_BYTES))
}
