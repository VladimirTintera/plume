package eu.tintera.plume.features.settings

import eu.tintera.plume.resources.settings_discreet_reading_summary
import eu.tintera.plume.resources.settings_discreet_reading
import eu.tintera.plume.resources.visibility_off_24dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import eu.tintera.plume.core.ui.reject
import eu.tintera.plume.core.ui.toggle
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
import eu.tintera.plume.resources.settings_contact_sync_summary
import eu.tintera.plume.resources.settings_contact_sync_denied
import eu.tintera.plume.resources.settings_contact_sync
import eu.tintera.plume.resources.contact_page_24dp
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.telegram.BlockedSender
import eu.tintera.plume.core.telegram.ChatSummary
import eu.tintera.plume.core.ui.Avatar
import eu.tintera.plume.core.ui.ChatPicker
import eu.tintera.plume.core.ui.GROUP_CORNER
import eu.tintera.plume.core.ui.GROUP_ITEM_SPACING
import eu.tintera.plume.core.ui.GROUP_SPACING
import eu.tintera.plume.core.ui.PickerSection
import eu.tintera.plume.core.ui.SheetActionRow
import eu.tintera.plume.core.ui.avatarShape
import eu.tintera.plume.core.ui.readString
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.settings_default_auto_delete_explanation
import eu.tintera.plume.resources.settings_default_auto_delete_failed
import eu.tintera.plume.resources.settings_default_auto_delete
import eu.tintera.plume.resources.auto_delete_off
import eu.tintera.plume.resources.timer_24dp
import eu.tintera.plume.core.ui.durationText
import eu.tintera.plume.features.profile.AutoDeleteSheet
import eu.tintera.plume.resources.add_24dp
import eu.tintera.plume.resources.block_24dp
import eu.tintera.plume.resources.settings_account_privacy
import eu.tintera.plume.resources.settings_account_privacy_summary
import eu.tintera.plume.resources.shield_person_24dp
import eu.tintera.plume.resources.lock_24dp
import eu.tintera.plume.resources.link_off_24dp
import eu.tintera.plume.resources.settings_app_lock
import eu.tintera.plume.resources.settings_app_lock_off
import eu.tintera.plume.resources.settings_app_lock_on
import eu.tintera.plume.resources.settings_strip_tracking_params
import eu.tintera.plume.resources.settings_strip_tracking_params_summary
import eu.tintera.plume.resources.chats_search_section_chats
import eu.tintera.plume.resources.common_cancel
import eu.tintera.plume.resources.new_chat_contacts
import eu.tintera.plume.resources.new_chat_empty
import eu.tintera.plume.resources.person_24dp
import eu.tintera.plume.resources.profile_block
import eu.tintera.plume.resources.profile_block_title
import eu.tintera.plume.resources.settings_block_body
import eu.tintera.plume.resources.settings_block_search
import eu.tintera.plume.resources.settings_block_user
import eu.tintera.plume.resources.chats_undo
import eu.tintera.plume.resources.chats_unblocked
import eu.tintera.plume.resources.profile_unblock
import eu.tintera.plume.resources.search_off_24dp
import eu.tintera.plume.resources.settings_blocked
import eu.tintera.plume.resources.settings_blocked_count
import eu.tintera.plume.resources.settings_blocked_deleted
import eu.tintera.plume.resources.settings_blocked_none
import eu.tintera.plume.resources.settings_blocked_search
import eu.tintera.plume.resources.settings_blocked_summary
import eu.tintera.plume.resources.settings_privacy
import eu.tintera.plume.resources.settings_discretion
import eu.tintera.plume.resources.settings_security
import eu.tintera.plume.resources.settings_sensitive_content
import eu.tintera.plume.resources.settings_sensitive_content_failed
import eu.tintera.plume.resources.settings_sensitive_content_summary
import eu.tintera.plume.resources.visibility_24dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Sekce Soukromí: kdo co vidí a s kým se mluví, co po sobě aplikace nechává (diskrétnost)
 * a čím je účet a aplikace chráněná (zabezpečení).
 */
@Composable
internal fun PrivacySettings(
    blocked: BlockedSendersViewModel,
    sensitive: SensitiveContentViewModel,
    autoDelete: DefaultAutoDeleteViewModel,
    twoStep: TwoStepViewModel,
    onOpenBlocked: () -> Unit,
    onOpenAccountPrivacy: () -> Unit,
    onOpenTwoStep: () -> Unit,
    onOpenAppLock: () -> Unit,
    onOpenLoginEmail: () -> Unit,
    onOpenPasskeys: () -> Unit,
) {
    SettingsGroup(Res.string.settings_privacy) {
        AccountPrivacyRow(onOpenAccountPrivacy)
        BlockedRow(blocked, onOpenBlocked)
        ContactSyncRow()
        DefaultAutoDeleteRow(autoDelete)
        SensitiveContentRow(sensitive)
    }
    SettingsGroup(Res.string.settings_discretion) {
        DiscreetReadingRow()
        StripTrackingParametersRow()
    }
    SettingsGroup(Res.string.settings_security) {
        TwoStepRow(twoStep, onOpenTwoStep)
        LoginEmailRow(twoStep, onOpenLoginEmail)
        PasskeysRow(onOpenPasskeys)
        AppLockRow(onOpenAppLock)
    }
}

/**
 * Synchronizace adresáře telefonu (`ContactSyncer`). Jen tam, kde ji platforma umí (Android).
 * Zapnutí se nejdřív zeptá na oprávnění ke kontaktům; bez něj zůstane vypnuté a řádek řekne
 * proč. Vypnutí smaže nahraná čísla ze serveru — věta pod řádkem to říká předem, i to, že
 * kontakty v Telegramu zůstanou.
 */
@Composable
private fun ContactSyncRow() {
    val syncer = org.koin.compose.getKoin().getOrNull<eu.tintera.plume.core.contacts.ContactSyncer>() ?: return
    val permission = eu.tintera.plume.core.contacts.rememberContactsPermission() ?: return
    val settings = org.koin.compose.koinInject<eu.tintera.plume.core.settings.AppSettings>()
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val enabled by settings.contactSyncEnabled.collectAsStateWithLifecycle()
    var denied by remember { androidx.compose.runtime.mutableStateOf(false) }
    val toggle: (Boolean) -> Unit = { on ->
        if (on) {
            permission.request { granted ->
                denied = !granted
                if (granted) {
                    haptics.toggle(true)
                    syncer.enable()
                } else {
                    haptics.reject()
                }
            }
        } else {
            haptics.toggle(false)
            syncer.disable()
        }
    }
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { Icon(painterResource(Res.drawable.contact_page_24dp), contentDescription = null) },
        headlineContent = { Text(stringResource(Res.string.settings_contact_sync)) },
        supportingContent = {
            Text(
                stringResource(if (denied && !enabled) Res.string.settings_contact_sync_denied else Res.string.settings_contact_sync_summary),
                color = if (denied && !enabled) MaterialTheme.colorScheme.error else Color.Unspecified,
            )
        },
        trailingContent = { eu.tintera.plume.core.ui.IconSwitch(checked = enabled, onCheckedChange = null) },
        modifier = Modifier.toggleable(value = enabled, role = Role.Switch, onValueChange = toggle),
    )
}

/** Diskrétní čtení (`AppSettings.discreetReading`): bez hlášení přečtení a „píše…". */
@Composable
private fun DiscreetReadingRow() {
    val settings = org.koin.compose.koinInject<eu.tintera.plume.core.settings.AppSettings>()
    val enabled by settings.discreetReading.collectAsStateWithLifecycle()
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { Icon(painterResource(Res.drawable.visibility_off_24dp), contentDescription = null) },
        headlineContent = { Text(stringResource(Res.string.settings_discreet_reading)) },
        supportingContent = { Text(stringResource(Res.string.settings_discreet_reading_summary)) },
        trailingContent = {
            eu.tintera.plume.core.ui.IconSwitch(
                checked = enabled,
                onCheckedChange = settings::setDiscreetReading,
            )
        },
        modifier = Modifier.clickable { settings.setDiscreetReading(!enabled) },
    )
}

@Composable
private fun StripTrackingParametersRow() {
    val settings = org.koin.compose.koinInject<eu.tintera.plume.core.settings.AppSettings>()
    val enabled by settings.stripTrackingParameters.collectAsStateWithLifecycle()
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { Icon(painterResource(Res.drawable.link_off_24dp), contentDescription = null) },
        headlineContent = { Text(stringResource(Res.string.settings_strip_tracking_params)) },
        supportingContent = { Text(stringResource(Res.string.settings_strip_tracking_params_summary)) },
        trailingContent = {
            eu.tintera.plume.core.ui.IconSwitch(
                checked = enabled,
                onCheckedChange = settings::setStripTrackingParameters,
            )
        },
        modifier = Modifier.clickable { settings.setStripTrackingParameters(!enabled) },
    )
}

@Composable
private fun AppLockRow(onOpen: () -> Unit) {
    val appLock = org.koin.compose.koinInject<eu.tintera.plume.core.lock.AppLock>()
    val settings by appLock.settings.collectAsStateWithLifecycle()
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { Icon(painterResource(Res.drawable.lock_24dp), contentDescription = null) },
        headlineContent = { Text(stringResource(Res.string.settings_app_lock)) },
        supportingContent = {
            if (settings != null) {
                Text(stringResource(Res.string.settings_app_lock_on))
            } else {
                Text(stringResource(Res.string.settings_app_lock_off))
            }
        },
        modifier = Modifier.clickable(onClick = onOpen),
    )
}

/**
 * „Automatické mazání v nových chatech": doba, která se nastaví v každém NOVĚ založeném chatu.
 * Výběr je tentýž sheet jako v profilu chatu, jen s větou o nových chatech — na chaty, které
 * už existují, volba nesahá, a bez té věty by se to dalo čekat.
 */
@Composable
private fun DefaultAutoDeleteRow(viewModel: DefaultAutoDeleteViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Dokud doba nedorazí, řádek nic netvrdí — „vypnuto" by mohla být lež.
    val seconds = state.seconds ?: return
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { Icon(painterResource(Res.drawable.timer_24dp), contentDescription = null) },
        headlineContent = { Text(stringResource(Res.string.settings_default_auto_delete)) },
        supportingContent = {
            when {
                state.failed -> Text(
                    stringResource(Res.string.settings_default_auto_delete_failed),
                    color = MaterialTheme.colorScheme.error,
                )
                seconds == 0 -> Text(stringResource(Res.string.auto_delete_off))
                else -> Text(durationText(seconds))
            }
        },
        modifier = Modifier.clickable(onClick = viewModel::openChooser),
    )
    if (state.choosing) {
        AutoDeleteSheet(
            current = seconds,
            onSelect = viewModel::choose,
            onDismiss = viewModel::closeChooser,
            explanation = stringResource(Res.string.settings_default_auto_delete_explanation),
        )
    }
}

/**
 * Řádek s počtem blokovaných, který otevře jejich seznam ([BlockedSendersScreen]).
 *
 * Počet se čte znovu při každém návratu na obrazovku — po odblokování v seznamu by jinak
 * zůstal starý.
 */
/** „Soukromí účtu": kdo vidí číslo, fotku a kdy byl uživatel naposledy online, kdo smí volat. */
@Composable
private fun AccountPrivacyRow(onOpen: () -> Unit) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { Icon(painterResource(Res.drawable.shield_person_24dp), contentDescription = null) },
        headlineContent = { Text(stringResource(Res.string.settings_account_privacy)) },
        supportingContent = { Text(stringResource(Res.string.settings_account_privacy_summary)) },
        modifier = Modifier.clickable(onClick = onOpen),
    )
}

@Composable
private fun BlockedRow(viewModel: BlockedSendersViewModel, onOpen: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.reload()
        onPauseOrDispose {}
    }

    run {
        val count = state.senders?.size
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = { Icon(painterResource(Res.drawable.block_24dp), contentDescription = null) },
            headlineContent = { Text(stringResource(Res.string.settings_blocked)) },
            supportingContent = {
                // Dokud počet nedorazí, řádek nic netvrdí — „nikoho" by mohla být lež.
                when (count) {
                    null -> Unit
                    0 -> Text(stringResource(Res.string.settings_blocked_none))
                    else -> Text(pluralStringResource(Res.plurals.settings_blocked_count, count, count))
                }
            },
            modifier = Modifier.clickable(onClick = onOpen),
        )
    }
}

/**
 * „Zobrazovat citlivý obsah": fotky a videa označená jako citlivá se ukážou bez rozostření.
 * Kde volbu server změnit nedovolí, řádek není vůbec ([SensitiveContentState.show] `null`).
 *
 * Už načtené zprávy se nepřekreslí — příznak se čte při převodu zprávy; projeví se po návratu
 * do chatu.
 */
@Composable
private fun SensitiveContentRow(viewModel: SensitiveContentViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val show = state.show ?: return
    SwitchRow(
        title = Res.string.settings_sensitive_content,
        summary = if (state.failed) Res.string.settings_sensitive_content_failed else Res.string.settings_sensitive_content_summary,
        summaryIsError = state.failed,
        checked = show,
        onChange = viewModel::setShow,
        icon = Res.drawable.visibility_24dp,
    )
}

/**
 * Seznam blokovaných lidí a chatů.
 *
 * **Je to tentýž `ChatPicker` jako kontakty, přeposílání a sdílení** — hlavička, hledání, bloky
 * zaoblených řádků, prázdný stav. První verze byla sheet se seznamem `ListItem`ů a tlačítkem
 * „Odblokovat" u každého řádku: vedle kontaktů vypadala jako jiná aplikace a měla jiné ovládání.
 * Akce nad položkou se tu dělá jako všude jinde — **ťuknutí otevře nabídku**, vratná akce má
 * snackbar s „Vrátit".
 */
@Composable
fun BlockedSendersScreen(
    viewModel: BlockedSendersViewModel,
    onBack: () -> Unit,
    /** „Zablokovat uživatele" — výběr ([BlockSenderScreen]), který se vrší nad seznam. */
    onBlockUser: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Seznam se čte při každém složení: obrazovka pod výběrem k zablokování složená není, takže
    // návrat z něj ji složí znovu — a zablokovaný v ní má být hned vidět.
    LaunchedEffect(viewModel) { viewModel.reload() }
    val snackbarHostState = remember { SnackbarHostState() }
    // Snackbar běží mimo efekt: vynulování události mění klíč efektu a zrušilo by ho
    // (stejná past jako „Vrátit" u archivace v seznamu chatů).
    val snackbarScope = rememberCoroutineScope()

    LaunchedEffect(state.unblocked) {
        val sender = state.unblocked ?: return@LaunchedEffect
        viewModel.consumeUnblocked()
        snackbarScope.launch {
            val result = snackbarHostState.showSnackbar(
                message = readString(Res.string.chats_unblocked),
                actionLabel = readString(Res.string.chats_undo),
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.undoUnblock(sender)
        }
    }

    val deleted = stringResource(Res.string.settings_blocked_deleted)
    val blankQuery = state.query.isBlank()
    ChatPicker(
        title = stringResource(Res.string.settings_blocked),
        query = state.query,
        onQueryChange = viewModel::onQueryChange,
        searchPlaceholder = stringResource(Res.string.settings_blocked_search),
        loading = state.senders == null,
        sections = listOf(PickerSection(key = "blocked", chats = state.visible.map { it.toSummary(deleted) })),
        enabled = true,
        onPick = viewModel::openActions,
        onBack = onBack,
        snackbarHostState = snackbarHostState,
        // Bez dotazu je prázdno „nikoho nemáš zablokovaného", s dotazem výchozí „nic nenalezeno".
        emptyIcon = if (blankQuery) Res.drawable.block_24dp else Res.drawable.search_off_24dp,
        emptyTitle = if (blankQuery) stringResource(Res.string.settings_blocked_none) else null,
        emptyText = if (blankQuery) stringResource(Res.string.settings_blocked_summary) else null,
        error = state.error,
        onErrorShown = viewModel::consumeError,
        header = { BlockUserRow(onClick = onBlockUser) },
        modifier = modifier,
    )

    state.target?.let { sender ->
        BlockedActionsSheet(
            sender = sender,
            title = sender.title.ifBlank { deleted },
            onUnblock = { viewModel.unblock(sender) },
            onDismiss = viewModel::dismissActions,
        )
    }
}

/**
 * „Zablokovat uživatele" nad seznamem. Je to akce, ne položka seznamu, proto samostatný blok
 * s ikonou v akcentu; stojí nad seznamem i v prázdném stavu, kdy je to jediné, co tu jde dělat.
 */
@Composable
private fun BlockUserRow(onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(GROUP_CORNER),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = GROUP_SPACING - GROUP_ITEM_SPACING),
    ) {
        ListItem(
            colors = ListItemDefaults.colors(
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.primary,
                leadingContentColor = MaterialTheme.colorScheme.primary,
            ),
            leadingContent = { Icon(painterResource(Res.drawable.add_24dp), contentDescription = null) },
            headlineContent = { Text(stringResource(Res.string.settings_block_user)) },
            modifier = Modifier.clickable(onClick = onClick),
        )
    }
}

/**
 * Výběr, koho zablokovat: kontakty, a při hledání i ostatní známé chaty. Tentýž `ChatPicker`
 * jako seznam blokovaných a nová konverzace. **Zablokování se potvrzuje dialogem**, jako
 * v profilu: ťuknutí vedle v dlouhém seznamu kontaktů by jinak zablokovalo někoho jiného
 * a „Vrátit" ve snackbaru tu není kde ukázat — obrazovka po zablokování odchází.
 */
@Composable
fun BlockSenderScreen(
    viewModel: BlockSenderViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (state.done) {
        // Jednorázově: obrazovka odchází a seznam blokovaných pod ní se přečte znovu.
        LaunchedEffect(Unit) { onBack() }
    }
    val blankQuery = state.query.isBlank()
    ChatPicker(
        title = stringResource(Res.string.settings_block_user),
        query = state.query,
        onQueryChange = viewModel::onQueryChange,
        searchPlaceholder = stringResource(Res.string.settings_block_search),
        loading = state.loading && state.contacts.isEmpty(),
        sections = listOf(
            PickerSection(key = "contacts", chats = state.contacts, title = stringResource(Res.string.new_chat_contacts)),
            PickerSection(key = "others", chats = state.others, title = stringResource(Res.string.chats_search_section_chats)),
        ),
        enabled = !state.blocking,
        onPick = viewModel::choose,
        onBack = onBack,
        emptyIcon = if (blankQuery) Res.drawable.person_24dp else Res.drawable.search_off_24dp,
        emptyTitle = if (blankQuery) stringResource(Res.string.new_chat_empty) else null,
        error = state.error,
        onErrorShown = viewModel::consumeError,
        modifier = modifier,
    )

    state.pending?.let { chat ->
        AlertDialog(
            onDismissRequest = viewModel::cancel,
            title = { Text(stringResource(Res.string.profile_block_title, chat.title)) },
            text = { Text(stringResource(Res.string.settings_block_body)) },
            confirmButton = {
                TextButton(onClick = viewModel::confirm) {
                    Text(stringResource(Res.string.profile_block), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = viewModel::cancel) { Text(stringResource(Res.string.common_cancel)) } },
        )
    }
}

/**
 * Nabídka nad blokovaným: kdo to je a „Odblokovat". Tentýž tvar jako nabídka nad chatem
 * v seznamu (`ChatActionsSheet`) — jazyk akcí nad položkou je v aplikaci jeden.
 */
@Composable
private fun BlockedActionsSheet(
    sender: BlockedSender,
    title: String,
    onUnblock: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        ) {
            Avatar(
                chatId = sender.id,
                title = title,
                avatarFileId = sender.avatarFileId,
                size = 40.dp,
                shape = sender.kind.avatarShape(),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 16.dp),
            )
        }
        Box(Modifier.padding(16.dp)) {
            SheetActionRow(
                icon = Res.drawable.block_24dp,
                label = stringResource(Res.string.profile_unblock),
                shape = RoundedCornerShape(20.dp),
                onClick = onUnblock,
            )
        }
    }
}

/**
 * Blokovaný jako položka výběru chatu. `ChatPicker` kreslí `ChatSummary`; použije se z něj
 * jméno, fotka a druh (tvar avataru), zbytek jsou výchozí hodnoty bez významu.
 */
private fun BlockedSender.toSummary(deletedTitle: String) = ChatSummary(
    id = id,
    title = title.ifBlank { deletedTitle },
    unreadCount = 0,
    lastMessagePreview = null,
    isPinned = false,
    order = 0,
    avatarFileId = avatarFileId,
    isMuted = false,
    isMarkedAsUnread = false,
    kind = kind,
    lastMessageTimestamp = null,
    presence = null,
    activity = null,
    draft = null,
)
