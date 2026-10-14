package eu.tintera.plume.features.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import eu.tintera.plume.core.telegram.ChatKind
import eu.tintera.plume.core.telegram.PrivacyBase
import eu.tintera.plume.core.telegram.PrivacyChoice
import eu.tintera.plume.core.telegram.PrivacyMember
import eu.tintera.plume.core.telegram.PrivacySetting
import eu.tintera.plume.core.ui.Avatar
import eu.tintera.plume.core.ui.ChatPicker
import eu.tintera.plume.core.ui.PickerSection
import eu.tintera.plume.core.ui.avatarShape
import eu.tintera.plume.core.ui.resolveNow
import eu.tintera.plume.core.ui.toggle
import androidx.compose.ui.platform.LocalHapticFeedback
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.add_24dp
import eu.tintera.plume.resources.cake_24dp
import eu.tintera.plume.resources.call_24dp
import eu.tintera.plume.resources.chats_search_section_chats
import eu.tintera.plume.resources.check_24dp
import eu.tintera.plume.resources.close_24dp
import eu.tintera.plume.resources.done_all_24dp
import eu.tintera.plume.resources.forward_24dp
import eu.tintera.plume.resources.group_24dp
import eu.tintera.plume.resources.info_24dp
import eu.tintera.plume.resources.lan_24dp
import eu.tintera.plume.resources.mic_24dp
import eu.tintera.plume.resources.new_chat_contacts
import eu.tintera.plume.resources.person_search_24dp
import eu.tintera.plume.resources.privacy_add_exception
import eu.tintera.plume.resources.privacy_always
import eu.tintera.plume.resources.privacy_bio
import eu.tintera.plume.resources.privacy_birthdate
import eu.tintera.plume.resources.privacy_calls
import eu.tintera.plume.resources.privacy_chat_invites
import eu.tintera.plume.resources.privacy_choice_title
import eu.tintera.plume.resources.privacy_contacts
import eu.tintera.plume.resources.privacy_everybody
import eu.tintera.plume.resources.privacy_exception_count
import eu.tintera.plume.resources.privacy_exceptions_explanation
import eu.tintera.plume.resources.privacy_find_by_phone
import eu.tintera.plume.resources.privacy_find_by_phone_explanation
import eu.tintera.plume.resources.privacy_forward_link
import eu.tintera.plume.resources.privacy_forward_link_explanation
import eu.tintera.plume.resources.privacy_group_members
import eu.tintera.plume.resources.privacy_last_seen
import eu.tintera.plume.resources.privacy_last_seen_explanation
import eu.tintera.plume.resources.privacy_load_failed
import eu.tintera.plume.resources.privacy_never
import eu.tintera.plume.resources.privacy_nobody
import eu.tintera.plume.resources.privacy_peer_to_peer
import eu.tintera.plume.resources.privacy_peer_to_peer_explanation
import eu.tintera.plume.resources.privacy_phone
import eu.tintera.plume.resources.privacy_phone_explanation
import eu.tintera.plume.resources.privacy_picker_add
import eu.tintera.plume.resources.privacy_picker_groups
import eu.tintera.plume.resources.privacy_picker_search
import eu.tintera.plume.resources.privacy_premium_only
import eu.tintera.plume.resources.privacy_profile_photo
import eu.tintera.plume.resources.privacy_read_date
import eu.tintera.plume.resources.privacy_read_date_summary
import eu.tintera.plume.resources.privacy_remove_exception
import eu.tintera.plume.resources.privacy_save_failed
import eu.tintera.plume.resources.privacy_section_allowed
import eu.tintera.plume.resources.privacy_section_seen
import eu.tintera.plume.resources.privacy_summary
import eu.tintera.plume.resources.privacy_voice_messages
import eu.tintera.plume.resources.privacy_voice_messages_premium
import eu.tintera.plume.resources.schedule_24dp
import eu.tintera.plume.resources.settings_account_privacy
import eu.tintera.plume.resources.settings_blocked_deleted
import eu.tintera.plume.resources.smartphone_24dp
import eu.tintera.plume.resources.add_photo_alternate_24dp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import eu.tintera.plume.resources.common_cancel
import eu.tintera.plume.resources.delete_24dp
import eu.tintera.plume.resources.devices_ttl_days
import eu.tintera.plume.resources.duration_months
import eu.tintera.plume.resources.privacy_account_ttl
import eu.tintera.plume.resources.privacy_account_ttl_text
import eu.tintera.plume.resources.privacy_delete_now
import eu.tintera.plume.resources.privacy_section_delete
import eu.tintera.plume.resources.timer_24dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** Název řádku a detailu. */
internal val PrivacySetting.title: StringResource
    get() = when (this) {
        PrivacySetting.LastSeen -> Res.string.privacy_last_seen
        PrivacySetting.PhoneNumber -> Res.string.privacy_phone
        PrivacySetting.FindByPhone -> Res.string.privacy_find_by_phone
        PrivacySetting.ProfilePhoto -> Res.string.privacy_profile_photo
        PrivacySetting.Bio -> Res.string.privacy_bio
        PrivacySetting.Birthdate -> Res.string.privacy_birthdate
        PrivacySetting.ForwardLink -> Res.string.privacy_forward_link
        PrivacySetting.Calls -> Res.string.privacy_calls
        PrivacySetting.PeerToPeer -> Res.string.privacy_peer_to_peer
        PrivacySetting.ChatInvites -> Res.string.privacy_chat_invites
        PrivacySetting.VoiceMessages -> Res.string.privacy_voice_messages
    }

private val PrivacySetting.icon: DrawableResource
    get() = when (this) {
        PrivacySetting.LastSeen -> Res.drawable.schedule_24dp
        PrivacySetting.PhoneNumber -> Res.drawable.smartphone_24dp
        PrivacySetting.FindByPhone -> Res.drawable.person_search_24dp
        PrivacySetting.ProfilePhoto -> Res.drawable.add_photo_alternate_24dp
        PrivacySetting.Bio -> Res.drawable.info_24dp
        PrivacySetting.Birthdate -> Res.drawable.cake_24dp
        PrivacySetting.ForwardLink -> Res.drawable.forward_24dp
        PrivacySetting.Calls -> Res.drawable.call_24dp
        PrivacySetting.PeerToPeer -> Res.drawable.lan_24dp
        PrivacySetting.ChatInvites -> Res.drawable.group_24dp
        PrivacySetting.VoiceMessages -> Res.drawable.mic_24dp
    }

/** Věta pod volbou v detailu — jen tam, kde má volba následek, který z názvu není vidět. */
private val PrivacySetting.explanation: StringResource?
    get() = when (this) {
        PrivacySetting.LastSeen -> Res.string.privacy_last_seen_explanation
        PrivacySetting.PhoneNumber -> Res.string.privacy_phone_explanation
        PrivacySetting.FindByPhone -> Res.string.privacy_find_by_phone_explanation
        PrivacySetting.ForwardLink -> Res.string.privacy_forward_link_explanation
        PrivacySetting.PeerToPeer -> Res.string.privacy_peer_to_peer_explanation
        else -> null
    }

private val PrivacyBase.title: StringResource
    get() = when (this) {
        PrivacyBase.Everybody -> Res.string.privacy_everybody
        PrivacyBase.Contacts -> Res.string.privacy_contacts
        PrivacyBase.Nobody -> Res.string.privacy_nobody
    }

/** Skupiny řádků na obrazovce: co ostatní vidí a co smějí. */
private val SEEN = listOf(
    PrivacySetting.LastSeen,
    PrivacySetting.PhoneNumber,
    PrivacySetting.FindByPhone,
    PrivacySetting.ProfilePhoto,
    PrivacySetting.Bio,
    PrivacySetting.Birthdate,
    PrivacySetting.ForwardLink,
)
private val ALLOWED = listOf(
    PrivacySetting.Calls,
    PrivacySetting.PeerToPeer,
    PrivacySetting.ChatInvites,
    PrivacySetting.VoiceMessages,
)

/**
 * „Kdo mě najde podle čísla" má smysl, jen když číslo nevidí všichni — kdo ho vidí, ten ho zná.
 * Tak to dělají i oficiální klienti; dokud nastavení telefonu nedorazí, řádek se neukazuje.
 */
internal fun privacyRowShown(setting: PrivacySetting, choices: Map<PrivacySetting, PrivacyChoice>): Boolean =
    setting != PrivacySetting.FindByPhone ||
        choices[PrivacySetting.PhoneNumber]?.let { it.base != PrivacyBase.Everybody } == true

/**
 * „Soukromí účtu": seznam nastavení se stavem, ťuknutí otevře detail ([PrivacySettingScreen]).
 * Vrší se nad sekci Soukromí jako seznam blokovaných.
 */
@Composable
fun AccountPrivacyScreen(
    viewModel: AccountPrivacyViewModel,
    onBack: () -> Unit,
    onOpen: (PrivacySetting) -> Unit,
    onDeleteAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var pickTtl by rememberSaveable { mutableStateOf(false) }
    val ttl = state.accountTtlDays
    if (pickTtl && ttl != null) {
        AccountTtlDialog(
            selected = ttl,
            onSelect = { days ->
                pickTtl = false
                viewModel.setAccountTtl(days)
            },
            onDismiss = { pickTtl = false },
        )
    }
    val scrollBehavior = settingsScrollBehavior()
    Scaffold(
        modifier = modifier.settingsScroll(scrollBehavior),
        topBar = {
            SettingsTopBar(title = stringResource(Res.string.settings_account_privacy), onBack = onBack, scrollBehavior = scrollBehavior)
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
            SettingsGroup(Res.string.privacy_section_seen) {
                for (setting in SEEN) {
                    if (!privacyRowShown(setting, state.choices)) continue
                    PrivacyRow(setting, state, onOpen)
                    // Čas přečtení patří k „naposledy viděn" — je to tatáž otázka „kdo ví, kdy jsem tu byl".
                    if (setting == PrivacySetting.LastSeen) ReadDateRow(state, viewModel::setShowReadDate)
                }
            }
            SettingsGroup(Res.string.privacy_section_allowed) {
                for (setting in ALLOWED) PrivacyRow(setting, state, onOpen)
            }
            // Jako v oficiálních klientech na konci soukromí: samosmazání po neaktivitě
            // a smazání hned, obojí o témž.
            SettingsGroup(Res.string.privacy_section_delete) {
                AccountTtlRow(state, onClick = { pickTtl = true })
                ListItem(
                    colors = ListItemDefaults.colors(
                        containerColor = Color.Transparent,
                        headlineColor = MaterialTheme.colorScheme.error,
                        leadingIconColor = MaterialTheme.colorScheme.error,
                    ),
                    leadingContent = { Icon(painterResource(Res.drawable.delete_24dp), contentDescription = null) },
                    headlineContent = { Text(stringResource(Res.string.privacy_delete_now)) },
                    modifier = Modifier.clickable(onClick = onDeleteAccount),
                )
            }
        }
    }
}

@Composable
private fun PrivacyRow(setting: PrivacySetting, state: AccountPrivacyState, onOpen: (PrivacySetting) -> Unit) {
    val choice = state.choices[setting]
    val failed = setting in state.failed
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { Icon(painterResource(setting.icon), contentDescription = null) },
        headlineContent = { Text(stringResource(setting.title)) },
        supportingContent = when {
            // Dokud stav nedorazí, řádek nic netvrdí — „Všichni" by mohla být lež.
            choice != null -> {
                { Text(privacySummary(setting, choice, state.isPremium)) }
            }
            failed -> {
                { Text(stringResource(Res.string.privacy_load_failed), color = MaterialTheme.colorScheme.error) }
            }
            else -> null
        },
        modifier = Modifier.clickable(onClick = { onOpen(setting) }),
    )
}

/** „Kontakty", „Nikdo · 2 výjimky", u hlasovek bez Premium i „jen s Telegram Premium". */
@Composable
private fun privacySummary(setting: PrivacySetting, choice: PrivacyChoice, isPremium: Boolean): String {
    var text = stringResource(choice.base.title)
    val count = if (setting.exceptions) choice.exceptionCount else 0
    if (count > 0) {
        text = stringResource(Res.string.privacy_summary, text, pluralStringResource(Res.plurals.privacy_exception_count, count, count))
    }
    if (setting == PrivacySetting.VoiceMessages && !isPremium) {
        text = stringResource(Res.string.privacy_summary, text, stringResource(Res.string.privacy_premium_only))
    }
    return text
}

@Composable
private fun ReadDateRow(state: AccountPrivacyState, onChange: (Boolean) -> Unit) {
    val show = state.readDate
    if (show == null) {
        if (state.readDateFailed) {
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                leadingContent = { Icon(painterResource(Res.drawable.done_all_24dp), contentDescription = null) },
                headlineContent = { Text(stringResource(Res.string.privacy_read_date)) },
                supportingContent = { Text(stringResource(Res.string.privacy_load_failed), color = MaterialTheme.colorScheme.error) },
            )
        }
        return
    }
    SwitchRow(
        title = Res.string.privacy_read_date,
        summary = if (state.readDateFailed) Res.string.privacy_save_failed else Res.string.privacy_read_date_summary,
        summaryIsError = state.readDateFailed,
        checked = show,
        onChange = onChange,
        icon = Res.drawable.done_all_24dp,
    )
}

/** Řádek samosmazání: dokud hodnota nedorazí, nic netvrdí; selhání čtení i zápisu řekne větou. */
@Composable
private fun AccountTtlRow(state: AccountPrivacyState, onClick: () -> Unit) {
    val days = state.accountTtlDays
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { Icon(painterResource(Res.drawable.timer_24dp), contentDescription = null) },
        headlineContent = { Text(stringResource(Res.string.privacy_account_ttl)) },
        supportingContent = when {
            days != null && state.accountTtlFailed -> {
                { Text(stringResource(Res.string.privacy_save_failed), color = MaterialTheme.colorScheme.error) }
            }
            days != null -> {
                { Text(accountTtlLabel(days)) }
            }
            state.accountTtlFailed -> {
                { Text(stringResource(Res.string.privacy_load_failed), color = MaterialTheme.colorScheme.error) }
            }
            else -> null
        },
        modifier = Modifier.clickable(enabled = days != null, onClick = onClick),
    )
}

/** „6 měsíců"; hodnotu, která žádné volbě neodpovídá (jiný klient), vypíše počtem dní. */
@Composable
private fun accountTtlLabel(days: Int): String {
    val months = accountTtlMonths(days)
    return if (months != null) {
        pluralStringResource(Res.plurals.duration_months, months, months)
    } else {
        pluralStringResource(Res.plurals.devices_ttl_days, days, days)
    }
}

@Composable
private fun AccountTtlDialog(selected: Int, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    // Volba se stejným počtem měsíců zastoupí hodnotu ze serveru (180 → 182); hodnota z jiného
    // klienta, která mezi volbami není, zůstane vidět a vybraná.
    val selectedMonths = accountTtlMonths(selected)
    val options = if (selectedMonths != null) ACCOUNT_TTL_OPTIONS else (ACCOUNT_TTL_OPTIONS + selected).sorted()
    val haptics = LocalHapticFeedback.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.privacy_account_ttl)) },
        text = {
            Column {
                Text(
                    stringResource(Res.string.privacy_account_ttl_text),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                for (days in options) {
                    val isSelected = days == selected || (selectedMonths != null && accountTtlMonths(days) == selectedMonths)
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        headlineContent = { Text(accountTtlLabel(days)) },
                        leadingContent = { RadioButton(selected = isSelected, onClick = null) },
                        modifier = Modifier.selectable(
                            selected = isSelected,
                            role = Role.RadioButton,
                            onClick = {
                                if (!isSelected) haptics.toggle(true)
                                onSelect(days)
                            },
                        ),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.common_cancel)) } },
    )
}

/**
 * Detail jednoho nastavení: Všichni / Kontakty / Nikdo a pod tím výjimky.
 *
 * Výjimky se ukazují jen ty, které k volbě patří: u „Všichni" jen „Nikdy nepovolit", u „Nikdo"
 * jen „Vždy povolit". Ty druhé se ale nezahazují (viz [PrivacyChoice]) — přepnutí tam a zpátky
 * je vrátí.
 */
@Composable
fun PrivacySettingScreen(
    viewModel: PrivacySettingViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarScope = rememberCoroutineScope()
    // Snackbar mimo tělo efektu: vynulování chyby mění klíč a zrušilo by ho (viz „Vrátit").
    LaunchedEffect(state.error) {
        val error = state.error ?: return@LaunchedEffect
        viewModel.consumeError()
        snackbarScope.launch { snackbarHostState.showSnackbar(error.resolveNow()) }
    }

    val picker = state.picker
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = picker != null,
        onBackCompleted = viewModel::closePicker,
    )
    if (picker != null) {
        ExceptionPickerScreen(viewModel, picker, modifier)
        return
    }

    val setting = state.setting
    val scrollBehavior = settingsScrollBehavior()
    Scaffold(
        modifier = modifier.settingsScroll(scrollBehavior),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            SettingsTopBar(title = stringResource(setting.title), onBack = onBack, scrollBehavior = scrollBehavior)
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
            val choice = state.choice
            if (choice == null) {
                if (state.loadFailed) {
                    Text(
                        stringResource(Res.string.privacy_load_failed),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                return@Column
            }
            if (!state.editable) Note(Res.string.privacy_voice_messages_premium)

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingsGroup(Res.string.privacy_choice_title) {
                    for (base in setting.bases) {
                        BaseRow(base, selected = choice.base == base, enabled = state.editable) { viewModel.chooseBase(base) }
                    }
                }
                setting.explanation?.let { Note(it) }
            }

            if (setting.exceptions) {
                val deleted = stringResource(Res.string.settings_blocked_deleted)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (choice.alwaysApplies) {
                        ExceptionGroup(
                            title = Res.string.privacy_always,
                            members = membersOf(choice.alwaysUsers, choice.alwaysChats, state),
                            deleted = deleted,
                            editable = state.editable,
                            onAdd = { viewModel.openPicker(always = true) },
                            onRemove = { viewModel.removeException(always = true, member = it) },
                        )
                    }
                    if (choice.neverApplies) {
                        ExceptionGroup(
                            title = Res.string.privacy_never,
                            members = membersOf(choice.neverUsers, choice.neverChats, state),
                            deleted = deleted,
                            editable = state.editable,
                            onAdd = { viewModel.openPicker(always = false) },
                            onRemove = { viewModel.removeException(always = false, member = it) },
                        )
                    }
                    Note(Res.string.privacy_exceptions_explanation)
                }
            }
        }
    }
}

/** Výjimky v pořadí ze stavu; kdo ještě nemá jméno, dostane prázdné a UI dosadí „Smazaný účet". */
private fun membersOf(users: List<Long>, chats: List<Long>, state: PrivacySettingState): List<PrivacyMember> =
    users.map { state.members[MemberKey(it, false)] ?: PrivacyMember(it, false, "", null, ChatKind.Private) } +
        chats.map { state.members[MemberKey(it, true)] ?: PrivacyMember(it, true, "", null, ChatKind.Group) }

@Composable
private fun Note(text: StringResource) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

/** Řádek nese klikání celý, kolečko má `onClick = null` (viz „Obrazovka nastavení"). */
@Composable
private fun BaseRow(base: PrivacyBase, selected: Boolean, enabled: Boolean, onSelect: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = { Text(stringResource(base.title)) },
        leadingContent = { RadioButton(selected = selected, onClick = null, enabled = enabled) },
        modifier = Modifier.selectable(
            selected = selected,
            enabled = enabled,
            role = Role.RadioButton,
            onClick = {
                if (!selected) haptics.toggle(true)
                onSelect()
            },
        ),
    )
}

@Composable
private fun ExceptionGroup(
    title: StringResource,
    members: List<PrivacyMember>,
    deleted: String,
    editable: Boolean,
    onAdd: () -> Unit,
    onRemove: (PrivacyMember) -> Unit,
) {
    SettingsGroup(title) {
        for (member in members) {
            val name = member.title.ifBlank { deleted }
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                leadingContent = {
                    Avatar(
                        chatId = member.id,
                        title = name,
                        avatarFileId = member.avatarFileId,
                        size = 40.dp,
                        shape = member.kind.avatarShape(),
                    )
                },
                headlineContent = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = if (member.isGroup) {
                    { Text(stringResource(Res.string.privacy_group_members)) }
                } else {
                    null
                },
                trailingContent = if (editable) {
                    {
                        IconButton(onClick = { onRemove(member) }, shapes = IconButtonDefaults.shapes()) {
                            Icon(painterResource(Res.drawable.close_24dp), contentDescription = stringResource(Res.string.privacy_remove_exception))
                        }
                    }
                } else {
                    null
                },
            )
        }
        if (editable) {
            ListItem(
                colors = ListItemDefaults.colors(
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.primary,
                    leadingContentColor = MaterialTheme.colorScheme.primary,
                ),
                leadingContent = { Icon(painterResource(Res.drawable.add_24dp), contentDescription = null) },
                headlineContent = { Text(stringResource(Res.string.privacy_add_exception)) },
                modifier = Modifier.clickable(onClick = onAdd),
            )
        }
    }
}

/**
 * Výběr výjimek: tentýž `ChatPicker` jako výběr chatů do složky, víc naráz. Přes celou plochu
 * detailu, ne routa — výběr patří rozdělanému detailu (stejně jako u editoru složky).
 */
@Composable
private fun ExceptionPickerScreen(viewModel: PrivacySettingViewModel, picker: ExceptionPicker, modifier: Modifier) {
    val candidates by viewModel.candidates.collectAsStateWithLifecycle()
    val (contacts, groups, others) = candidates
    ChatPicker(
        title = stringResource(if (picker.always) Res.string.privacy_always else Res.string.privacy_never),
        query = picker.query,
        onQueryChange = viewModel::onPickerQuery,
        searchPlaceholder = stringResource(Res.string.privacy_picker_search),
        loading = false,
        sections = listOf(
            PickerSection(key = "contacts", chats = contacts, title = stringResource(Res.string.new_chat_contacts)),
            PickerSection(key = "groups", chats = groups, title = stringResource(Res.string.privacy_picker_groups)),
            PickerSection(key = "others", chats = others, title = stringResource(Res.string.chats_search_section_chats)),
        ),
        enabled = !picker.adding,
        onPick = viewModel::togglePicked,
        onBack = viewModel::closePicker,
        selected = picker.selected,
        onConfirm = viewModel::confirmPicker,
        confirmLabel = Res.string.privacy_picker_add,
        confirmIcon = Res.drawable.check_24dp,
        modifier = modifier,
    )
}

