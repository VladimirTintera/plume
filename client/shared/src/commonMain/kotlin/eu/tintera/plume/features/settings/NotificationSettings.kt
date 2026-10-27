package eu.tintera.plume.features.settings

import eu.tintera.plume.resources.settings_notif_story_poster_summary
import eu.tintera.plume.resources.settings_notif_story_poster
import eu.tintera.plume.resources.settings_notif_stories_none
import eu.tintera.plume.resources.settings_notif_stories_all
import eu.tintera.plume.resources.settings_notif_stories_top
import eu.tintera.plume.resources.settings_notif_stories
import eu.tintera.plume.core.ui.ChoiceRow
import eu.tintera.plume.core.ui.ConfirmDialog
import eu.tintera.plume.core.ui.GroupBreak
import eu.tintera.plume.resources.refresh_24dp
import eu.tintera.plume.resources.settings_notif_reset_all
import eu.tintera.plume.resources.settings_notif_reset_all_confirm
import eu.tintera.plume.resources.settings_notif_reset_all_confirm_text
import eu.tintera.plume.resources.settings_notif_reset_all_confirm_title
import eu.tintera.plume.core.telegram.StoryAlerts
import eu.tintera.plume.resources.settings_background_data_blocked_summary
import eu.tintera.plume.resources.settings_background_data_blocked
import eu.tintera.plume.resources.data_usage_24dp
import eu.tintera.plume.resources.settings_full_screen_calls_on
import eu.tintera.plume.resources.settings_full_screen_calls_off
import eu.tintera.plume.resources.settings_full_screen_calls
import eu.tintera.plume.resources.call_24dp
import eu.tintera.plume.resources.undo_24dp
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.itemsIndexed
import eu.tintera.plume.core.ui.WithTooltip
import eu.tintera.plume.core.ui.groupedItem
import eu.tintera.plume.core.ui.rememberExpandedSheetState
import eu.tintera.plume.core.ui.SheetTitle
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
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import eu.tintera.plume.core.ui.avatarShape
import eu.tintera.plume.core.ui.resolve
import eu.tintera.plume.core.ui.tabularNumbers
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
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import eu.tintera.plume.core.ui.SwitchListItem
import eu.tintera.plume.core.ui.TitledGroup

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
    var confirmReset by rememberSaveable { mutableStateOf(false) }

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
    if (viewModel.hasSystemPermission) TitledGroup(Res.string.settings_notif_system) {
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
        // Bez celoobrazovkové výzvy zamčený telefon při hovoru jen zvoní a nic neukáže —
        // Plume z Play ji od Androidu 14 nemusí mít (`docs/poznatky/notifikace.md`).
        state.fullScreenCalls?.let { allowed ->
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                leadingContent = { Icon(painterResource(Res.drawable.call_24dp), contentDescription = null) },
                headlineContent = { Text(stringResource(Res.string.settings_full_screen_calls)) },
                supportingContent = {
                    Text(
                        stringResource(if (allowed) Res.string.settings_full_screen_calls_on else Res.string.settings_full_screen_calls_off),
                        color = if (allowed) Color.Unspecified else MaterialTheme.colorScheme.error,
                    )
                },
                trailingContent = { Icon(painterResource(Res.drawable.open_in_new_24dp), contentDescription = null) },
                modifier = Modifier.clickable(onClick = viewModel::openFullScreenCallSettings),
            )
        }
        // Jen když to push zjistil — jinak stav neznáme (`NotificationPermission.backgroundDataBlocked`).
        if (state.backgroundDataBlocked) {
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                leadingContent = { Icon(painterResource(Res.drawable.data_usage_24dp), contentDescription = null) },
                headlineContent = { Text(stringResource(Res.string.settings_background_data_blocked)) },
                supportingContent = {
                    Text(stringResource(Res.string.settings_background_data_blocked_summary), color = MaterialTheme.colorScheme.error)
                },
                trailingContent = { Icon(painterResource(Res.drawable.open_in_new_24dp), contentDescription = null) },
                modifier = Modifier.clickable(onClick = viewModel::openBackgroundDataSettings),
            )
        }
    }

    // Desktop: oznámení chodí, jen když aplikace běží (push tam není) — hned pod povolením,
    // protože bez běžící aplikace nezmění nic ani ono.
    state.launchAtLogin?.let { enabled ->
        TitledGroup(Res.string.settings_notif_background) {
            SwitchListItem(
                headline = stringResource(Res.string.settings_launch_at_login),
                supporting = stringResource(Res.string.settings_launch_at_login_summary),
                checked = enabled,
                onCheckedChange = viewModel::setLaunchAtLogin,
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

    TitledGroup(Res.string.settings_notif_other) {
        state.contactJoined?.let { enabled ->
            SwitchListItem(
                headline = stringResource(Res.string.settings_notif_contact_joined),
                checked = enabled,
                onCheckedChange = viewModel::setContactJoined,
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
        // Účtové nastavení (rozsahy a chaty), ne zařízení: kanály a tiché hodiny zůstanou.
        GroupBreak()
        SettingsActionRow(
            icon = Res.drawable.refresh_24dp,
            title = stringResource(Res.string.settings_notif_reset_all),
            destructive = true,
            onClick = { confirmReset = true },
        )
    }

    if (confirmReset) {
        ConfirmDialog(
            title = stringResource(Res.string.settings_notif_reset_all_confirm_title),
            text = stringResource(Res.string.settings_notif_reset_all_confirm_text),
            confirmLabel = stringResource(Res.string.settings_notif_reset_all_confirm),
            icon = Res.drawable.refresh_24dp,
            destructive = true,
            onConfirm = {
                confirmReset = false
                viewModel.resetAll()
            },
            onDismiss = { confirmReset = false },
        )
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
    TitledGroup(scope.title) {
        if (settings == null) {
            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                LoadingIndicator()
            }
            return@TitledGroup
        }

        SwitchListItem(
            headline = stringResource(Res.string.settings_notif_enabled),
            checked = settings.enabled,
            onCheckedChange = { value -> onUpdate { it.copy(enabled = value) } },
        )
        // Náhled bez upozornění nemá kde být vidět. Zmínky a připnuté ne: zmínky projdou
        // i ztlumeným chatem, a to je přesně jejich smysl.
        SwitchListItem(
            headline = stringResource(Res.string.settings_notif_preview),
            supporting = stringResource(Res.string.settings_notif_preview_summary),
            checked = settings.showPreview,
            enabled = settings.enabled,
            onCheckedChange = { value -> onUpdate { it.copy(showPreview = value) } },
        )
        SwitchListItem(
            headline = stringResource(Res.string.settings_notif_pinned),
            supporting = stringResource(Res.string.settings_notif_pinned_summary),
            checked = settings.pinnedMessages,
            onCheckedChange = { value -> onUpdate { it.copy(pinnedMessages = value) } },
        )
        // Zmínit se dá jen ve skupině. V soukromém chatu je každá zpráva „pro mě" a v kanálu
        // píše jen kanál.
        if (scope == NotificationScope.Groups) {
            SwitchListItem(
                headline = stringResource(Res.string.settings_notif_mentions),
                supporting = stringResource(Res.string.settings_notif_mentions_summary),
                checked = settings.mentions,
                onCheckedChange = { value -> onUpdate { it.copy(mentions = value) } },
            )
        }
        // Příběhy zveřejňují hlavně lidé, nastavení je proto u soukromých chatů (jako
        // v oficiálních aplikacích). Platí pro celý účet.
        if (scope == NotificationScope.Private) {
            ChoiceRow(
                headline = stringResource(Res.string.settings_notif_stories),
                options = StoryAlerts.entries,
                selected = settings.storyAlerts,
                label = { stringResource(it.label()) },
                onSelect = { value -> onUpdate { it.copy(storyAlerts = value) } },
            )
            SwitchListItem(
                headline = stringResource(Res.string.settings_notif_story_poster),
                supporting = stringResource(Res.string.settings_notif_story_poster_summary),
                checked = settings.showStoryPoster,
                enabled = settings.storyAlerts != StoryAlerts.None,
                onCheckedChange = { value -> onUpdate { it.copy(showStoryPoster = value) } },
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

    TitledGroup(Res.string.quiet_hours_title) {
        // Nadpis skupiny už „Tiché hodiny" říká; řádek říká, co přepínač dělá.
        SwitchListItem(
            headline = stringResource(Res.string.quiet_hours_switch),
            supporting = stringResource(Res.string.quiet_hours_summary),
            checked = quietHours.enabled,
            onCheckedChange = onEnabledChange,
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
    val sheetState = rememberExpandedSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column {
            SheetTitle(stringResource(scope.title), modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp))
            Text(
                stringResource(Res.string.settings_notif_exceptions_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 4.dp, bottom = 16.dp),
            )
            // Chaty v bloku zaoblených řádků; „Obnovit" je ikonové tlačítko s bublinou — textové
            // tlačítko v každém řádku dělalo ze seznamu sloupec odkazů.
            LazyColumn(Modifier.navigationBarsPadding(), contentPadding = PaddingValues(bottom = 16.dp)) {
                itemsIndexed(exceptions, key = { _, it -> it.chatId }) { index, exception ->
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
                            val reset = stringResource(Res.string.settings_notif_reset)
                            WithTooltip(reset) {
                                IconButton(onClick = { onReset(exception.chatId) }, shapes = IconButtonDefaults.shapes()) {
                                    Icon(painterResource(Res.drawable.undo_24dp), contentDescription = reset)
                                }
                            }
                        },
                        modifier = Modifier
                            .animateItem()
                            .groupedItem(index, exceptions.size, MaterialTheme.colorScheme.surfaceContainerLowest, pressable = false),
                    )
                }
            }
        }
    }
}

internal val NotificationScope.title: StringResource
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
internal fun soundLabel(soundId: Long?, tones: List<NotificationTone>): String = when {
    soundId == null || soundId == DEFAULT_SOUND_ID -> stringResource(Res.string.notif_sound_default)
    soundId == NO_SOUND_ID -> stringResource(Res.string.notif_sound_none)
    else -> tones.firstOrNull { it.id == soundId }?.title ?: stringResource(Res.string.notif_sound_unknown)
}

private fun StoryAlerts.label(): StringResource = when (this) {
    StoryAlerts.TopContacts -> Res.string.settings_notif_stories_top
    StoryAlerts.All -> Res.string.settings_notif_stories_all
    StoryAlerts.None -> Res.string.settings_notif_stories_none
}
