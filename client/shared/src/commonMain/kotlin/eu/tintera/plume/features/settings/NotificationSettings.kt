package eu.tintera.plume.features.settings

import org.koin.compose.viewmodel.koinViewModel
import eu.tintera.plume.resources.notif_sound_unknown
import eu.tintera.plume.resources.notif_sound_row
import eu.tintera.plume.resources.notif_sound_none
import eu.tintera.plume.resources.notif_sound_default
import eu.tintera.plume.core.ui.resolveNow
import eu.tintera.plume.core.telegram.NotificationTone
import eu.tintera.plume.core.telegram.NO_SOUND_ID
import eu.tintera.plume.core.telegram.DEFAULT_SOUND_ID
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.settings.QuietHours
import eu.tintera.plume.core.telegram.ExceptionState
import eu.tintera.plume.core.telegram.NotificationChannelKind
import eu.tintera.plume.core.telegram.NotificationException
import eu.tintera.plume.core.telegram.NotificationScope
import eu.tintera.plume.core.telegram.ScopeSettings
import eu.tintera.plume.core.ui.Avatar
import eu.tintera.plume.core.ui.IconSwitch
import eu.tintera.plume.core.ui.avatarShape
import eu.tintera.plume.core.ui.resolve
import eu.tintera.plume.core.ui.tabularNumbers
import eu.tintera.plume.core.ui.toggle
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.notification_channel_channels
import eu.tintera.plume.resources.notification_channel_groups
import eu.tintera.plume.resources.notification_channel_private
import eu.tintera.plume.resources.bedtime_24dp
import eu.tintera.plume.resources.notifications_24dp
import eu.tintera.plume.resources.notifications_off_24dp
import eu.tintera.plume.resources.open_in_new_24dp
import eu.tintera.plume.resources.settings_notif_contact_joined
import eu.tintera.plume.resources.settings_notif_enabled
import eu.tintera.plume.resources.settings_notif_exceptions
import eu.tintera.plume.resources.settings_notif_exceptions_count
import eu.tintera.plume.resources.settings_notif_exceptions_summary
import eu.tintera.plume.resources.settings_notif_mentions
import eu.tintera.plume.resources.settings_notif_mentions_sound_summary
import eu.tintera.plume.resources.settings_notif_mentions_summary
import eu.tintera.plume.resources.settings_notif_other
import eu.tintera.plume.resources.settings_notif_pinned
import eu.tintera.plume.resources.settings_notif_pinned_summary
import eu.tintera.plume.resources.settings_notif_preview
import eu.tintera.plume.resources.settings_notif_preview_summary
import eu.tintera.plume.resources.settings_notif_reset
import eu.tintera.plume.resources.settings_notif_sound
import eu.tintera.plume.resources.settings_notif_sound_summary
import eu.tintera.plume.resources.settings_notif_state_custom
import eu.tintera.plume.resources.settings_notif_state_enabled
import eu.tintera.plume.resources.settings_notif_state_muted
import eu.tintera.plume.resources.settings_launch_at_login
import eu.tintera.plume.resources.settings_launch_at_login_summary
import eu.tintera.plume.resources.settings_notif_background
import eu.tintera.plume.resources.settings_notif_system
import eu.tintera.plume.resources.settings_notifications_off
import eu.tintera.plume.resources.settings_notifications_on
import eu.tintera.plume.resources.quiet_hours_from
import eu.tintera.plume.resources.quiet_hours_summary
import eu.tintera.plume.resources.quiet_hours_switch
import eu.tintera.plume.resources.quiet_hours_title
import eu.tintera.plume.resources.quiet_hours_until
import eu.tintera.plume.features.conversation.TimeOfDayDialog
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Sekce „Notifikace": co TDLib upozorní a jak (po rozsazích soukromé chaty / skupiny / kanály),
 * výjimky a odkazy do systémových kanálů na zvuk a vibrace.
 *
 * Volby jdou přes TDLib, takže platí i na ostatních zařízeních. Zvuk vlastní volbou není: na
 * Androidu patří kanálu a po jeho založení ho aplikace nepřepíše.
 */
@Composable
fun NotificationSettings(
    viewModel: NotificationSettingsViewModel,
    snackbarHostState: SnackbarHostState,
    sounds: NotificationSoundsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val soundsState by sounds.state.collectAsStateWithLifecycle()
    val tones by sounds.tones.collectAsStateWithLifecycle()

    val notice = state.notice
    val noticeText = notice?.resolve()
    val scope = rememberCoroutineScope()
    // Snackbar mimo tělo efektu: vynulování hlášky mění klíč a zrušilo by ho (viz „Vrátit").
    LaunchedEffect(notice) {
        if (noticeText != null) {
            viewModel.noticeShown()
            scope.launch { snackbarHostState.showSnackbar(noticeText) }
        }
    }
    LaunchedEffect(soundsState.error) {
        val error = soundsState.error ?: return@LaunchedEffect
        sounds.consumeError()
        scope.launch { snackbarHostState.showSnackbar(error.resolveNow()) }
    }

    // Návrat ze systémového nastavení nebo z chatu, kde se mohlo přepnout ztlumení.
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    // Stav oprávnění se ukazuje vždycky, ne jen když chybí — jinak by nebylo kde ověřit, že
    // notifikace opravdu běží. Bez něj ostatní volby nic nezmění, proto je nahoře. Linux
    // povolení nemá (viz `NotificationPermission.hasSystemPermission`).
    if (viewModel.hasSystemPermission) SettingsGroup(Res.string.settings_notif_system) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = {
                Icon(
                    painterResource(if (state.permissionGranted) Res.drawable.notifications_24dp else Res.drawable.notifications_off_24dp),
                    contentDescription = null,
                )
            },
            headlineContent = {
                Text(
                    stringResource(
                        if (state.permissionGranted) Res.string.settings_notifications_on else Res.string.settings_notifications_off,
                    ),
                )
            },
            trailingContent = { Icon(painterResource(Res.drawable.open_in_new_24dp), contentDescription = null) },
            modifier = Modifier.clickable(onClick = viewModel::openSystemSettings),
        )
    }

    // Desktop: oznámení chodí, jen když aplikace běží (push tam není) — hned pod povolením,
    // protože bez běžící aplikace nezmění nic ani ono.
    state.launchAtLogin?.let { enabled ->
        SettingsGroup(Res.string.settings_notif_background) {
            SwitchRow(
                title = Res.string.settings_launch_at_login,
                summary = Res.string.settings_launch_at_login_summary,
                checked = enabled,
                onChange = viewModel::setLaunchAtLogin,
            )
        }
    }

    NotificationScope.entries.forEach { notificationScope ->
        ScopeGroup(
            scope = notificationScope,
            settings = state.scopes[notificationScope],
            exceptions = state.exceptions[notificationScope].orEmpty(),
            supportsChannelSettings = viewModel.supportsChannelSettings,
            onUpdate = { transform -> viewModel.update(notificationScope, transform) },
            onOpenExceptions = { viewModel.openExceptions(notificationScope) },
            onOpenChannel = { viewModel.openChannelSettings(notificationScope.channel, state.scopes[notificationScope]?.soundId ?: DEFAULT_SOUND_ID) },
            soundLabel = soundLabel(state.scopes[notificationScope]?.soundId, tones),
            onPickSound = { sounds.open(notificationScope) },
        )
    }

    soundsState.picking?.let { picking ->
        NotificationSoundSheet(
            title = picking.title,
            selected = state.scopes[picking]?.soundId ?: DEFAULT_SOUND_ID,
            tones = tones,
            limits = soundsState.limits,
            uploading = soundsState.uploading,
            onSelect = { soundId -> sounds.select(picking, soundId) },
            onUpload = sounds::upload,
            onDelete = sounds::delete,
            tonePath = sounds::tonePath,
            onError = { message -> scope.launch { snackbarHostState.showSnackbar(message.resolveNow()) } },
            onDismiss = sounds::close,
        )
    }

    if (viewModel.supportsQuietHours) {
        val quietHours by viewModel.quietHours.collectAsStateWithLifecycle()
        QuietHoursGroup(
            quietHours = quietHours,
            onEnabledChange = viewModel::setQuietHoursEnabled,
            onStartChange = viewModel::setQuietHoursStart,
            onEndChange = viewModel::setQuietHoursEnd,
        )
    }

    SettingsGroup(Res.string.settings_notif_other) {
        state.contactJoined?.let { enabled ->
            SwitchRow(
                title = Res.string.settings_notif_contact_joined,
                checked = enabled,
                onChange = viewModel::setContactJoined,
            )
        }
        // Zmínky mají vlastní kanál — v tiché skupině jsou to jediné, co má zvonit, a zvuk
        // se jim proto nastavuje zvlášť.
        if (viewModel.supportsChannelSettings) {
            LinkRow(
                title = Res.string.settings_notif_mentions,
                summary = Res.string.settings_notif_mentions_sound_summary,
                onClick = { viewModel.openChannelSettings(NotificationChannelKind.Mentions) },
            )
        }
    }

    state.openExceptions?.let { open ->
        ExceptionsSheet(
            scope = open,
            exceptions = state.exceptions[open].orEmpty(),
            onReset = { chatId -> viewModel.resetException(open, chatId) },
            onDismiss = viewModel::closeExceptions,
        )
    }
}

@Composable
private fun ScopeGroup(
    scope: NotificationScope,
    settings: ScopeSettings?,
    exceptions: List<NotificationException>,
    supportsChannelSettings: Boolean,
    onUpdate: ((ScopeSettings) -> ScopeSettings) -> Unit,
    onOpenExceptions: () -> Unit,
    onOpenChannel: () -> Unit,
    soundLabel: String,
    onPickSound: () -> Unit,
) {
    SettingsGroup(scope.title) {
        if (settings == null) {
            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                LoadingIndicator()
            }
            return@SettingsGroup
        }

        SwitchRow(
            title = Res.string.settings_notif_enabled,
            checked = settings.enabled,
            onChange = { value -> onUpdate { it.copy(enabled = value) } },
        )
        // Náhled bez upozornění nemá kde být vidět. Zmínky a připnuté ne: zmínky projdou
        // i ztlumeným chatem, a to je přesně jejich smysl.
        SwitchRow(
            title = Res.string.settings_notif_preview,
            summary = Res.string.settings_notif_preview_summary,
            checked = settings.showPreview,
            enabled = settings.enabled,
            onChange = { value -> onUpdate { it.copy(showPreview = value) } },
        )
        SwitchRow(
            title = Res.string.settings_notif_pinned,
            summary = Res.string.settings_notif_pinned_summary,
            checked = settings.pinnedMessages,
            onChange = { value -> onUpdate { it.copy(pinnedMessages = value) } },
        )
        // Zmínit se dá jen ve skupině. V soukromém chatu je každá zpráva „pro mě" a v kanálu
        // píše jen kanál.
        if (scope == NotificationScope.Groups) {
            SwitchRow(
                title = Res.string.settings_notif_mentions,
                summary = Res.string.settings_notif_mentions_summary,
                checked = settings.mentions,
                onChange = { value -> onUpdate { it.copy(mentions = value) } },
            )
        }
        if (exceptions.isNotEmpty()) {
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = { Text(stringResource(Res.string.settings_notif_exceptions)) },
                supportingContent = {
                    Text(pluralStringResource(Res.plurals.settings_notif_exceptions_count, exceptions.size, exceptions.size))
                },
                modifier = Modifier.clickable(onClick = onOpenExceptions),
            )
        }
        // Zvuk z Telegramu — nastavení účtu, platí i v oficiálních aplikacích. Na Androidu vybírá
        // kanál (výchozí, tichý, s tónem); vibrace a ostatní dál v systémovém kanálu níž.
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            headlineContent = { Text(stringResource(Res.string.notif_sound_row)) },
            supportingContent = { Text(soundLabel) },
            modifier = Modifier.clickable(onClick = onPickSound),
        )
        if (supportsChannelSettings) {
            LinkRow(
                title = Res.string.settings_notif_sound,
                summary = Res.string.settings_notif_sound_summary,
                onClick = onOpenChannel,
            )
        }
    }
}

/** Řádek nese klikání celý, přepínač má `onClick = null` (viz „Obrazovka nastavení"). */
@Composable
internal fun SwitchRow(
    title: StringResource,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    summary: StringResource? = null,
    enabled: Boolean = true,
    /** Podtitul je hláška o selhání — chybovou barvou. */
    summaryIsError: Boolean = false,
    icon: DrawableResource? = null,
) {
    val haptics = LocalHapticFeedback.current
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = icon?.let { { Icon(painterResource(it), contentDescription = null) } },
        headlineContent = { Text(stringResource(title)) },
        supportingContent = summary?.let {
            {
                Text(
                    stringResource(it),
                    color = if (summaryIsError) MaterialTheme.colorScheme.error else Color.Unspecified,
                )
            }
        },
        trailingContent = { IconSwitch(checked = checked, onCheckedChange = null, enabled = enabled) },
        modifier = Modifier.toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Switch,
            onValueChange = { on ->
                haptics.toggle(on)
                onChange(on)
            },
        ),
    )
}

/**
 * Tiché hodiny: přepínač a pod ním začátek a konec. Časy jsou vidět jen se zapnutým přepínačem —
 * vypnuté hodiny nic nedělají a dva řádky navíc by jen odváděly pozornost.
 */
@Composable
private fun QuietHoursGroup(
    quietHours: QuietHours,
    onEnabledChange: (Boolean) -> Unit,
    onStartChange: (Int) -> Unit,
    onEndChange: (Int) -> Unit,
) {
    var picking by rememberSaveable { mutableStateOf<QuietHoursEdge?>(null) }

    SettingsGroup(Res.string.quiet_hours_title) {
        // Nadpis skupiny už „Tiché hodiny" říká; řádek říká, co přepínač dělá.
        SwitchRow(
            title = Res.string.quiet_hours_switch,
            summary = Res.string.quiet_hours_summary,
            checked = quietHours.enabled,
            onChange = onEnabledChange,
            icon = Res.drawable.bedtime_24dp,
        )
        AnimatedVisibility(visible = quietHours.enabled) {
            Column {
                QuietHoursEdge.entries.forEach { edge ->
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        headlineContent = { Text(stringResource(edge.title)) },
                        trailingContent = {
                            Text(edge.of(quietHours).asClockText(), style = MaterialTheme.typography.bodyLarge.tabularNumbers())
                        },
                        modifier = Modifier.clickable { picking = edge },
                    )
                }
            }
        }
    }

    picking?.let { edge ->
        val minute = edge.of(quietHours)
        TimeOfDayDialog(
            title = stringResource(edge.title),
            hour = minute / 60,
            minute = minute % 60,
            onPick = { hour, min ->
                val picked = hour * 60 + min
                when (edge) {
                    QuietHoursEdge.Start -> onStartChange(picked)
                    QuietHoursEdge.End -> onEndChange(picked)
                }
                picking = null
            },
            onDismiss = { picking = null },
        )
    }
}

private enum class QuietHoursEdge(val title: StringResource) {
    Start(Res.string.quiet_hours_from),
    End(Res.string.quiet_hours_until),
    ;

    fun of(quietHours: QuietHours): Int = when (this) {
        Start -> quietHours.start
        End -> quietHours.end
    }
}

/** Minuta dne jako „22:00" — 24hodinově, jako ostatní časy v aplikaci ([eu.tintera.plume.core.ui.asTimeOfDay]). */
private fun Int.asClockText(): String =
    "${(this / 60).toString().padStart(2, '0')}:${(this % 60).toString().padStart(2, '0')}"

/** Odkaz ven z aplikace — ikona říká, že se otevře systémové nastavení. */
@Composable
private fun LinkRow(title: StringResource, summary: StringResource, onClick: () -> Unit) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = { Text(stringResource(title)) },
        supportingContent = { Text(stringResource(summary)) },
        trailingContent = { Icon(painterResource(Res.drawable.open_in_new_24dp), contentDescription = null) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun ExceptionsSheet(
    scope: NotificationScope,
    exceptions: List<NotificationException>,
    onReset: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    // Rovnou celý: seznam bývá delší než půl obrazovky (stejná úvaha jako u detailu zařízení).
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column {
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = { Text(stringResource(scope.title), style = MaterialTheme.typography.titleLarge) },
                supportingContent = { Text(stringResource(Res.string.settings_notif_exceptions_summary)) },
            )
            LazyColumn(Modifier.navigationBarsPadding()) {
                items(exceptions, key = { it.chatId }) { exception ->
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        leadingContent = {
                            Avatar(
                                chatId = exception.chatId,
                                title = exception.title,
                                avatarFileId = exception.avatarFileId,
                                size = 40.dp,
                                shape = exception.kind.avatarShape(),
                            )
                        },
                        headlineContent = { Text(exception.title, maxLines = 1) },
                        supportingContent = { Text(stringResource(exception.state.label)) },
                        trailingContent = {
                            TextButton(onClick = { onReset(exception.chatId) }) {
                                Text(stringResource(Res.string.settings_notif_reset))
                            }
                        },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

private val NotificationScope.title: StringResource
    get() = when (this) {
        NotificationScope.Private -> Res.string.notification_channel_private
        NotificationScope.Groups -> Res.string.notification_channel_groups
        NotificationScope.Channels -> Res.string.notification_channel_channels
    }

private val ExceptionState.label: StringResource
    get() = when (this) {
        ExceptionState.Muted -> Res.string.settings_notif_state_muted
        ExceptionState.Enabled -> Res.string.settings_notif_state_enabled
        ExceptionState.Custom -> Res.string.settings_notif_state_custom
    }

/** „Výchozí", „Bez zvuku", název tónu — a tón, který účet nemá, jako „Vlastní zvuk". */
@Composable
private fun soundLabel(soundId: Long?, tones: List<NotificationTone>): String = when {
    soundId == null || soundId == DEFAULT_SOUND_ID -> stringResource(Res.string.notif_sound_default)
    soundId == NO_SOUND_ID -> stringResource(Res.string.notif_sound_none)
    else -> tones.firstOrNull { it.id == soundId }?.title ?: stringResource(Res.string.notif_sound_unknown)
}
