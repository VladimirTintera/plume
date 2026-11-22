package eu.tintera.plume.features.settings

import eu.tintera.plume.core.ui.rememberRestoringScrollState
import androidx.compose.foundation.layout.Arrangement
import eu.tintera.plume.resources.history_toggle_off_24dp
import eu.tintera.plume.core.ui.ConfirmDialog
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.telegram.KindTraffic
import eu.tintera.plume.core.telegram.NetworkTraffic
import eu.tintera.plume.core.telegram.StorageCategory
import eu.tintera.plume.core.telegram.TrafficKind
import eu.tintera.plume.core.telegram.UsageNetwork
import eu.tintera.plume.core.ui.asDate
import eu.tintera.plume.core.ui.durationText
import eu.tintera.plume.core.ui.fileSizeText
import eu.tintera.plume.core.ui.resolveNow
import eu.tintera.plume.core.ui.tabularNumbers
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.call_24dp
import eu.tintera.plume.resources.chat_bubble_24dp
import eu.tintera.plume.resources.check_24dp
import eu.tintera.plume.resources.network_usage_calls
import eu.tintera.plume.resources.network_usage_calls_summary
import eu.tintera.plume.resources.network_usage_empty
import eu.tintera.plume.resources.network_usage_load_failed
import eu.tintera.plume.resources.network_usage_messages
import eu.tintera.plume.resources.network_usage_mobile
import eu.tintera.plume.resources.network_usage_mobile_short
import eu.tintera.plume.resources.network_usage_other
import eu.tintera.plume.resources.network_usage_reset
import eu.tintera.plume.resources.network_usage_reset_confirm
import eu.tintera.plume.resources.network_usage_reset_text
import eu.tintera.plume.resources.network_usage_reset_title
import eu.tintera.plume.resources.network_usage_retry
import eu.tintera.plume.resources.network_usage_roaming
import eu.tintera.plume.resources.network_usage_since
import eu.tintera.plume.resources.network_usage_summary
import eu.tintera.plume.resources.network_usage_title
import eu.tintera.plume.resources.network_usage_wifi
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import eu.tintera.plume.core.ui.TitledGroup

/**
 * Přenesená data: přepínač sítě (jen sítě, kterými něco teklo), velké číslo za síť a rozpis
 * podle druhu s podílem — tentýž jazyk jako přehled úložiště. Dole odkdy se počítá a vynulování.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NetworkUsageScreen(viewModel: NetworkUsageViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarScope = rememberCoroutineScope()
    // Snackbar mimo tělo efektu: vynulování chyby mění klíč a zrušilo by ho.
    LaunchedEffect(state.error) {
        val error = state.error ?: return@LaunchedEffect
        viewModel.consumeError()
        snackbarScope.launch { snackbarHostState.showSnackbar(error.resolveNow()) }
    }

    val scrollBehavior = settingsScrollBehavior()
    Scaffold(
        modifier = modifier.settingsScroll(scrollBehavior),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            SettingsTopBar(title = stringResource(Res.string.network_usage_title), onBack = onBack, scrollBehavior = scrollBehavior)
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .settingsContentPadding(padding)
                .verticalScroll(rememberRestoringScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = settingsContentBottom(padding, 16.dp)),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val usage = state.usage
            when {
                usage == null && state.loadFailed -> Column(Modifier.padding(horizontal = 16.dp)) {
                    Text(stringResource(Res.string.network_usage_load_failed), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = viewModel::load, shapes = ButtonDefaults.shapes()) { Text(stringResource(Res.string.network_usage_retry)) }
                }
                usage == null -> Box(Modifier.padding(horizontal = 16.dp)) { LoadingIndicator() }
                else -> {
                    val shown = usage.networks.firstOrNull { it.network == state.shown }
                    if (usage.networks.size > 1) {
                        NetworkSelector(usage.networks.map { it.network }, selected = state.shown, onSelect = viewModel::select)
                    }
                    if (shown == null) {
                        Text(
                            stringResource(Res.string.network_usage_empty),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    } else {
                        // Bez přepínače by nebylo poznat, o kterou síť jde — nadpis skupiny to řekne.
                        TitledGroup(shown.network.label) {
                            NetworkHeader(shown)
                            shown.kinds.forEach { KindRow(it, total = shown.sentBytes + shown.receivedBytes, callSeconds = shown.callSeconds) }
                        }
                    }
                    Text(
                        stringResource(Res.string.network_usage_since, usage.sinceDate.asDate()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    TextButton(
                        onClick = viewModel::requestReset,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        shapes = ButtonDefaults.shapes(),
                    ) { Text(stringResource(Res.string.network_usage_reset)) }
                }
            }
        }
    }

    if (state.confirmingReset) {
        ConfirmDialog(
            title = stringResource(Res.string.network_usage_reset_title),
            text = stringResource(Res.string.network_usage_reset_text),
            confirmLabel = stringResource(Res.string.network_usage_reset_confirm),
            onConfirm = viewModel::confirmReset,
            onDismiss = viewModel::dismissReset,
            icon = Res.drawable.history_toggle_off_24dp,
            destructive = true,
        )
    }
}

/** Sítě jako spojená přepínací tlačítka, jako volba typu chatu. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NetworkSelector(networks: List<UsageNetwork>, selected: UsageNetwork?, onSelect: (UsageNetwork) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
        networks.forEachIndexed { index, network ->
            val checked = network == selected
            ToggleButton(
                checked = checked,
                onCheckedChange = { if (!checked) onSelect(network) },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    networks.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                modifier = Modifier.weight(1f),
            ) {
                if (checked) {
                    Icon(
                        painterResource(Res.drawable.check_24dp),
                        contentDescription = null,
                        modifier = Modifier.padding(end = 4.dp).size(ButtonDefaults.IconSize),
                    )
                }
                Text(stringResource(network.shortLabel), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Velké číslo za síť, pod ním staženo a odesláno. */
@Composable
private fun NetworkHeader(traffic: NetworkTraffic) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(
            fileSizeText(traffic.sentBytes + traffic.receivedBytes),
            style = MaterialTheme.typography.headlineMediumEmphasized.tabularNumbers(),
        )
        Text(
            stringResource(Res.string.network_usage_summary, fileSizeText(traffic.receivedBytes), fileSizeText(traffic.sentBytes)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun KindRow(traffic: KindTraffic, total: Long, callSeconds: Long) {
    val summary = stringResource(Res.string.network_usage_summary, fileSizeText(traffic.receivedBytes), fileSizeText(traffic.sentBytes))
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { CategoryIcon(traffic.kind.icon) },
        supportingContent = {
            Column {
                Text(
                    if (traffic.kind == TrafficKind.Calls && callSeconds > 0) {
                        stringResource(Res.string.network_usage_calls_summary, durationText(callSeconds.toInt()), summary)
                    } else {
                        summary
                    },
                )
                LinearProgressIndicator(
                    progress = { if (total > 0) (traffic.totalBytes.toFloat() / total).coerceIn(0f, 1f) else 0f },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    drawStopIndicator = {},
                )
            }
        },
        trailingContent = {
            Text(fileSizeText(traffic.totalBytes), style = MaterialTheme.typography.labelLarge.tabularNumbers())
        },
    ) { Text(stringResource(traffic.kind.label)) }
}

/** Popisek tlačítka — „Mobilní data" se do čtvrtiny šířky nevešla (ověřeno na emulátoru). */
private val UsageNetwork.shortLabel: StringResource
    get() = if (this == UsageNetwork.Mobile) Res.string.network_usage_mobile_short else label

/** Nadpis skupiny s rozpisem. */
private val UsageNetwork.label: StringResource
    get() = when (this) {
        UsageNetwork.Mobile -> Res.string.network_usage_mobile
        UsageNetwork.WiFi -> Res.string.network_usage_wifi
        UsageNetwork.Roaming -> Res.string.network_usage_roaming
        UsageNetwork.Other -> Res.string.network_usage_other
    }

private val TrafficKind.label: StringResource
    get() = when (this) {
        TrafficKind.Messages -> Res.string.network_usage_messages
        TrafficKind.Calls -> Res.string.network_usage_calls
        TrafficKind.Photos -> StorageCategory.Photos.label
        TrafficKind.Videos -> StorageCategory.Videos.label
        TrafficKind.VoiceMessages -> StorageCategory.VoiceMessages.label
        TrafficKind.Music -> StorageCategory.Music.label
        TrafficKind.Files -> StorageCategory.Files.label
        TrafficKind.Gifs -> StorageCategory.Gifs.label
        TrafficKind.Stickers -> StorageCategory.Stickers.label
        TrafficKind.Other -> StorageCategory.Other.label
    }

private val TrafficKind.icon: DrawableResource
    get() = when (this) {
        TrafficKind.Messages -> Res.drawable.chat_bubble_24dp
        TrafficKind.Calls -> Res.drawable.call_24dp
        TrafficKind.Photos -> StorageCategory.Photos.icon
        TrafficKind.Videos -> StorageCategory.Videos.icon
        TrafficKind.VoiceMessages -> StorageCategory.VoiceMessages.icon
        TrafficKind.Music -> StorageCategory.Music.icon
        TrafficKind.Files -> StorageCategory.Files.icon
        TrafficKind.Gifs -> StorageCategory.Gifs.icon
        TrafficKind.Stickers -> StorageCategory.Stickers.icon
        TrafficKind.Other -> StorageCategory.Other.icon
    }
