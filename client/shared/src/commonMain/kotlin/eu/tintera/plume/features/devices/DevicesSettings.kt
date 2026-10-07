package eu.tintera.plume.features.devices

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.telegram.ConnectedWebsite
import eu.tintera.plume.core.telegram.Device
import eu.tintera.plume.core.telegram.DeviceKind
import eu.tintera.plume.core.telegram.INACTIVE_TTL_OPTIONS
import eu.tintera.plume.core.ui.IconSwitch
import eu.tintera.plume.core.ui.QrScanFailure
import eu.tintera.plume.core.ui.asDayLabel
import eu.tintera.plume.core.ui.asRelativeTime
import eu.tintera.plume.core.ui.asTimeOfDay
import eu.tintera.plume.core.ui.rememberQrScanner
import eu.tintera.plume.core.ui.resolve
import eu.tintera.plume.core.ui.toggle
import eu.tintera.plume.features.settings.SettingsGroup
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.common_cancel
import eu.tintera.plume.resources.common_try_again
import eu.tintera.plume.resources.computer_24dp
import eu.tintera.plume.resources.devices_24dp
import eu.tintera.plume.resources.devices_accept
import eu.tintera.plume.resources.devices_accept_calls
import eu.tintera.plume.resources.devices_accept_secret_chats
import eu.tintera.plume.resources.devices_active
import eu.tintera.plume.resources.devices_active_now
import eu.tintera.plume.resources.devices_auto_terminate
import eu.tintera.plume.resources.devices_auto_terminate_row
import eu.tintera.plume.resources.devices_date_time
import eu.tintera.plume.resources.devices_detail_app
import eu.tintera.plume.resources.devices_detail_ip
import eu.tintera.plume.resources.devices_detail_last_active
import eu.tintera.plume.resources.devices_detail_location
import eu.tintera.plume.resources.devices_detail_login
import eu.tintera.plume.resources.devices_detail_system
import eu.tintera.plume.resources.devices_error_load
import eu.tintera.plume.resources.devices_link
import eu.tintera.plume.resources.devices_none_other
import eu.tintera.plume.resources.devices_password_pending
import eu.tintera.plume.resources.devices_qr_permission
import eu.tintera.plume.resources.devices_qr_unavailable
import eu.tintera.plume.resources.devices_terminate
import eu.tintera.plume.resources.devices_terminate_body
import eu.tintera.plume.resources.devices_terminate_confirm
import eu.tintera.plume.resources.devices_terminate_others
import eu.tintera.plume.resources.devices_terminate_others_body
import eu.tintera.plume.resources.devices_terminate_others_title
import eu.tintera.plume.resources.devices_terminate_title
import eu.tintera.plume.resources.devices_this_device
import eu.tintera.plume.resources.devices_ttl_3months
import eu.tintera.plume.resources.devices_ttl_6months
import eu.tintera.plume.resources.devices_ttl_days
import eu.tintera.plume.resources.devices_ttl_month
import eu.tintera.plume.resources.devices_ttl_week
import eu.tintera.plume.resources.devices_ttl_year
import eu.tintera.plume.resources.devices_website_disconnect_body
import eu.tintera.plume.resources.devices_website_disconnect_title
import eu.tintera.plume.resources.devices_websites
import eu.tintera.plume.resources.devices_websites_disconnect_all
import eu.tintera.plume.resources.devices_websites_disconnect_all_body
import eu.tintera.plume.resources.devices_websites_disconnect_all_title
import eu.tintera.plume.resources.language_24dp
import eu.tintera.plume.resources.qr_code_scanner_24dp
import eu.tintera.plume.resources.smartphone_24dp
import eu.tintera.plume.resources.tablet_android_24dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Sekce „Zařízení": kde je účet přihlášený, odhlašování, weby přihlášené přes Telegram,
 * automatické odhlášení nečinných a přihlášení jiného zařízení QR kódem.
 *
 * Obsah do sloupce sekce, ne celá obrazovka — hlavičku a scrollování drží `SettingsSectionScreen`.
 * Snackbar je její, sem jen přichází jeho stav.
 */
@Composable
fun DevicesSettings(viewModel: DevicesViewModel, snackbarHostState: SnackbarHostState) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val unconfirmed by viewModel.unconfirmed.collectAsStateWithLifecycle()

    // Dialogy a sheet se pamatují podle id — obsah se bere ze stavu, takže po odhlášení zařízení
    // sheet sám zmizí a přepínače ukazují, co potvrdil server.
    var detailId by rememberSaveable { mutableStateOf<Long?>(null) }
    var terminateId by rememberSaveable { mutableStateOf<Long?>(null) }
    var confirmTerminateOthers by rememberSaveable { mutableStateOf(false) }
    var disconnectWebsiteId by rememberSaveable { mutableStateOf<Long?>(null) }
    var confirmDisconnectAll by rememberSaveable { mutableStateOf(false) }
    var pickTtl by rememberSaveable { mutableStateOf(false) }

    val notice = state.notice
    val scope = rememberCoroutineScope()
    val noticeText = notice?.resolve()
    // Snackbar běží mimo tělo efektu: vynulování hlášky mění klíč efektu a zrušilo by ho
    // (tatáž past jako „Vrátit" u připnutých zpráv).
    LaunchedEffect(notice) {
        if (noticeText != null) {
            viewModel.noticeShown()
            scope.launch { snackbarHostState.showSnackbar(noticeText) }
        }
    }

    // Na jiném zařízení se mezitím mohl někdo přihlásit nebo odhlásit.
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    val unavailableText = stringResource(Res.string.devices_qr_unavailable)
    val permissionText = stringResource(Res.string.devices_qr_permission)
    val startScan = rememberQrScanner(
        onScanned = viewModel::onQrScanned,
        onFailure = { failure ->
            val text = when (failure) {
                QrScanFailure.Unavailable -> unavailableText
                QrScanFailure.PermissionDenied -> permissionText
            }
            scope.launch { snackbarHostState.showSnackbar(text) }
        },
    )

    unconfirmed?.let { login ->
        UnconfirmedLoginCard(
            device = login,
            enabled = !state.busy,
            onConfirm = { viewModel.confirm(login.sessionId) },
            onTerminate = { viewModel.terminate(login.sessionId) },
        )
    }

    // Hlavní akce sekce, proto tlačítko a ne řádek v seznamu. Tonální: je častá, ale nic nemaže.
    FilledTonalButton(
        onClick = startScan,
        enabled = !state.busy,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(
            painterResource(Res.drawable.qr_code_scanner_24dp),
            contentDescription = null,
            modifier = Modifier.padding(end = 8.dp).size(ButtonDefaults.IconSize),
        )
        Text(stringResource(Res.string.devices_link))
    }

    val devices = state.devices
    when {
        devices == null && state.loadFailed -> Column(
            Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(Res.string.devices_error_load), color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = viewModel::refresh) { Text(stringResource(Res.string.common_try_again)) }
        }

        devices == null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
            LoadingIndicator()
        }

        else -> {
            devices.current?.let { current ->
                SettingsGroup(Res.string.devices_this_device) {
                    DeviceRow(current, onClick = { detailId = current.id })
                    // Odhlášení ostatních patří k tomuto zařízení: je to „nech jen mě".
                    if (devices.others.isNotEmpty()) {
                        DestructiveRow(
                            text = stringResource(Res.string.devices_terminate_others),
                            enabled = !state.busy,
                            onClick = { confirmTerminateOthers = true },
                        )
                    }
                }
            }

            SettingsGroup(Res.string.devices_active) {
                if (devices.others.isEmpty()) {
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        headlineContent = { Text(stringResource(Res.string.devices_none_other)) },
                    )
                }
                devices.others.forEach { device ->
                    DeviceRow(device, onClick = { detailId = device.id })
                }
            }

            // Weby jen když nějaké jsou — prázdná skupina by jen vysvětlovala funkci, kterou skoro
            // nikdo nepoužívá.
            if (state.websites.isNotEmpty()) {
                SettingsGroup(Res.string.devices_websites) {
                    state.websites.forEach { website ->
                        WebsiteRow(website, onClick = { disconnectWebsiteId = website.id })
                    }
                    DestructiveRow(
                        text = stringResource(Res.string.devices_websites_disconnect_all),
                        enabled = !state.busy,
                        onClick = { confirmDisconnectAll = true },
                    )
                }
            }

            SettingsGroup(Res.string.devices_auto_terminate) {
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text(stringResource(Res.string.devices_auto_terminate_row)) },
                    supportingContent = { Text(ttlLabel(devices.inactiveTtlDays)) },
                    modifier = Modifier.clickable(enabled = !state.busy) { pickTtl = true },
                )
            }
        }
    }

    val allDevices = devices?.let { listOfNotNull(it.current) + it.others }.orEmpty()

    allDevices.firstOrNull { it.id == detailId }?.let { device ->
        DeviceDetailSheet(
            device = device,
            busy = state.busy,
            onCanAcceptCalls = { viewModel.setCanAcceptCalls(device.id, it) },
            onCanAcceptSecretChats = { viewModel.setCanAcceptSecretChats(device.id, it) },
            onTerminate = { terminateId = device.id },
            onDismiss = { detailId = null },
        )
    }

    allDevices.firstOrNull { it.id == terminateId }?.let { device ->
        ConfirmDialog(
            title = stringResource(Res.string.devices_terminate_title, device.title),
            text = stringResource(Res.string.devices_terminate_body),
            confirm = stringResource(Res.string.devices_terminate_confirm),
            onConfirm = {
                terminateId = null
                detailId = null
                viewModel.terminate(device.id)
            },
            onDismiss = { terminateId = null },
        )
    }

    if (confirmTerminateOthers) {
        ConfirmDialog(
            title = stringResource(Res.string.devices_terminate_others_title),
            text = stringResource(Res.string.devices_terminate_others_body),
            confirm = stringResource(Res.string.devices_terminate_confirm),
            onConfirm = {
                confirmTerminateOthers = false
                viewModel.terminateOthers()
            },
            onDismiss = { confirmTerminateOthers = false },
        )
    }

    state.websites.firstOrNull { it.id == disconnectWebsiteId }?.let { website ->
        ConfirmDialog(
            title = stringResource(Res.string.devices_website_disconnect_title, website.domain),
            text = stringResource(Res.string.devices_website_disconnect_body),
            confirm = stringResource(Res.string.devices_terminate_confirm),
            onConfirm = {
                disconnectWebsiteId = null
                viewModel.disconnectWebsite(website.id)
            },
            onDismiss = { disconnectWebsiteId = null },
        )
    }

    if (confirmDisconnectAll) {
        ConfirmDialog(
            title = stringResource(Res.string.devices_websites_disconnect_all_title),
            text = stringResource(Res.string.devices_websites_disconnect_all_body),
            confirm = stringResource(Res.string.devices_terminate_confirm),
            onConfirm = {
                confirmDisconnectAll = false
                viewModel.disconnectAllWebsites()
            },
            onDismiss = { confirmDisconnectAll = false },
        )
    }

    if (pickTtl && devices != null) {
        TtlDialog(
            selected = devices.inactiveTtlDays,
            onSelect = { days ->
                pickTtl = false
                if (days != devices.inactiveTtlDays) viewModel.setInactiveTtl(days)
            },
            onDismiss = { pickTtl = false },
        )
    }
}

/** Co zařízení je: model, bez něj platforma. Aplikace je v podtitulu. */
private val Device.title: String
    get() = deviceModel.ifBlank { platform }.ifBlank { applicationName }

private val Device.appLine: String
    get() = listOf(applicationName, applicationVersion).filter { it.isNotBlank() }.joinToString(" ")

@Composable
private fun DeviceRow(device: Device, onClick: () -> Unit) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { DeviceIcon(device.kind.icon) },
        headlineContent = { Text(device.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Column {
                Text(device.appLine, maxLines = 1, overflow = TextOverflow.Ellipsis)
                // Čekající heslo je důležitější než poloha: zařízení k účtu zatím přístup nemá.
                Text(
                    if (device.isPasswordPending) {
                        stringResource(Res.string.devices_password_pending)
                    } else {
                        device.location
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        trailingContent = {
            Text(
                if (device.isCurrent) stringResource(Res.string.devices_active_now) else device.lastActiveDate.asRelativeTime(),
                style = MaterialTheme.typography.labelMedium,
            )
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun WebsiteRow(website: ConnectedWebsite, onClick: () -> Unit) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { DeviceIcon(Res.drawable.language_24dp) },
        headlineContent = { Text(website.domain, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(
                listOf(website.browser, website.platform, website.location).filter { it.isNotBlank() }.joinToString(" · "),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = { Text(website.lastActiveDate.asRelativeTime(), style = MaterialTheme.typography.labelMedium) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/** Ikona v tónovaném kruhu, jako avatar v seznamu chatů — řádky se tím srovnají pod sebe. */
@Composable
private fun DeviceIcon(icon: DrawableResource) {
    Box(
        Modifier.size(40.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

/** Nevratná akce ve skupině: text v chybové roli, potvrzení až v dialogu. */
@Composable
private fun DestructiveRow(text: String, enabled: Boolean, onClick: () -> Unit) {
    ListItem(
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.error,
        ),
        headlineContent = { Text(text) },
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
    )
}

private val DeviceKind.icon: DrawableResource
    get() = when (this) {
        DeviceKind.Phone -> Res.drawable.smartphone_24dp
        DeviceKind.Tablet -> Res.drawable.tablet_android_24dp
        DeviceKind.Desktop -> Res.drawable.computer_24dp
        DeviceKind.Browser -> Res.drawable.language_24dp
        DeviceKind.Unknown -> Res.drawable.devices_24dp
    }

/**
 * Podrobnosti zařízení: odkud a čím je přihlášené, co na něm smí přijímat, a odhlášení.
 * Sheet, ne obrazovka — je to pohled na jednu položku, ze kterého se vrací do seznamu.
 */
@Composable
private fun DeviceDetailSheet(
    device: Device,
    busy: Boolean,
    onCanAcceptCalls: (Boolean) -> Unit,
    onCanAcceptSecretChats: (Boolean) -> Unit,
    onTerminate: () -> Unit,
    onDismiss: () -> Unit,
) {
    // Rovnou celý: napůl rozbalený sheet schoval odhlášení pod spodní okraj a vypadalo to,
    // že jednotlivé zařízení odhlásit nejde (stejná past jako „Odebrat sadu" u samolepek).
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                leadingContent = { DeviceIcon(device.kind.icon) },
                headlineContent = { Text(device.title, style = MaterialTheme.typography.titleLarge) },
            )

            // Odhlášení hned pod hlavičkou, ne až za údaji: je to to, kvůli čemu se sem chodí,
            // a na menším telefonu by pod údaji a přepínači skončilo pod okrajem.
            // Jen u CIZÍHO zařízení — odhlášení tohoto je v Obecných (maže lokální data).
            if (!device.isCurrent) {
                Button(
                    onClick = onTerminate,
                    enabled = !busy,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(stringResource(Res.string.devices_terminate))
                }
            }

            DetailRow(Res.string.devices_detail_app, device.appLine)
            DetailRow(
                Res.string.devices_detail_system,
                listOf(device.platform, device.systemVersion).filter { it.isNotBlank() }.joinToString(" "),
            )
            DetailRow(Res.string.devices_detail_login, device.logInDate.asDateTime())
            if (!device.isCurrent) {
                DetailRow(Res.string.devices_detail_last_active, device.lastActiveDate.asDateTime())
            }
            DetailRow(Res.string.devices_detail_ip, device.ipAddress)
            DetailRow(Res.string.devices_detail_location, device.location)

            // Přepínače jen u cizího zařízení: o tomhle rozhoduje systémové nastavení.
            if (!device.isCurrent) {
                Text(
                    stringResource(Res.string.devices_accept),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                )
                SwitchRow(Res.string.devices_accept_calls, device.canAcceptCalls, !busy, onCanAcceptCalls)
                SwitchRow(Res.string.devices_accept_secret_chats, device.canAcceptSecretChats, !busy, onCanAcceptSecretChats)
            }
        }
    }
}

@Composable
private fun DetailRow(label: StringResource, value: String) {
    if (value.isBlank()) return
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        overlineContent = { Text(stringResource(label)) },
        headlineContent = { Text(value) },
    )
}

@Composable
private fun SwitchRow(
    label: StringResource,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    // Klikání nese celý řádek, přepínač má `onCheckedChange = null` — jako v sekci Vzhled.
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = { Text(stringResource(label)) },
        trailingContent = { IconSwitch(checked = checked, onCheckedChange = null, enabled = enabled) },
        modifier = Modifier.toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = { on ->
            haptics.toggle(on)
            onChange(on)
        }),
    )
}

@Composable
private fun Int.asDateTime(): String = stringResource(Res.string.devices_date_time, asDayLabel(), asTimeOfDay())

/** Volby jako v oficiálním klientu; jinou hodnotu (z jiného klienta) vypíše počtem dní. */
@Composable
private fun ttlLabel(days: Int): String = when (days) {
    7 -> stringResource(Res.string.devices_ttl_week)
    30 -> stringResource(Res.string.devices_ttl_month)
    90 -> stringResource(Res.string.devices_ttl_3months)
    180, 183 -> stringResource(Res.string.devices_ttl_6months)
    365 -> stringResource(Res.string.devices_ttl_year)
    else -> pluralStringResource(Res.plurals.devices_ttl_days, days, days)
}

@Composable
private fun TtlDialog(selected: Int, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    // Hodnota z jiného klienta, která mezi volbami není, zůstane vidět a vybraná.
    val options = (INACTIVE_TTL_OPTIONS + selected).distinct().sorted()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.devices_auto_terminate_row)) },
        text = {
            Column {
                options.forEach { days ->
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        headlineContent = { Text(ttlLabel(days)) },
                        leadingContent = { RadioButton(selected = days == selected, onClick = null) },
                        modifier = Modifier.selectable(
                            selected = days == selected,
                            role = Role.RadioButton,
                            onClick = { onSelect(days) },
                        ),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.common_cancel)) } },
    )
}

/** Potvrzení nevratné akce; pojmenovává následek, ne „opravdu?". */
@Composable
private fun ConfirmDialog(
    title: String,
    text: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirm, color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.common_cancel)) } },
    )
}
