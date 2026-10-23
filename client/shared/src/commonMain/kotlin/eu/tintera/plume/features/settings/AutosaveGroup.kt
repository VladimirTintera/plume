package eu.tintera.plume.features.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.files.rememberSavePermission
import eu.tintera.plume.core.telegram.AUTOSAVE_DEFAULT_VIDEO_BYTES
import eu.tintera.plume.core.telegram.AUTOSAVE_VIDEO_SIZES
import eu.tintera.plume.core.telegram.AutosaveScope
import eu.tintera.plume.core.telegram.ChatKind
import eu.tintera.plume.core.telegram.AutosaveScopeSettings
import eu.tintera.plume.core.ui.Avatar
import eu.tintera.plume.core.ui.ConfirmDialog
import eu.tintera.plume.core.ui.GroupBreak
import eu.tintera.plume.resources.autosave_exceptions_clear
import eu.tintera.plume.resources.autosave_exceptions_clear_confirm
import eu.tintera.plume.resources.autosave_exceptions_clear_text
import eu.tintera.plume.resources.autosave_exceptions_clear_title
import eu.tintera.plume.resources.delete_sweep_24dp
import eu.tintera.plume.core.ui.avatarShape
import eu.tintera.plume.core.ui.chatTitle
import eu.tintera.plume.core.ui.fileSizeText
import eu.tintera.plume.core.ui.resolveNow
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.autosave_channels
import eu.tintera.plume.resources.autosave_done
import eu.tintera.plume.resources.autosave_exceptions
import eu.tintera.plume.resources.autosave_group
import eu.tintera.plume.resources.autosave_groups
import eu.tintera.plume.resources.autosave_hint
import eu.tintera.plume.resources.autosave_load_failed
import eu.tintera.plume.resources.autosave_max_video
import eu.tintera.plume.resources.autosave_off
import eu.tintera.plume.resources.autosave_photos
import eu.tintera.plume.resources.autosave_photos_switch
import eu.tintera.plume.resources.autosave_photos_videos_up_to
import eu.tintera.plume.resources.autosave_private
import eu.tintera.plume.resources.autosave_remove_exception
import eu.tintera.plume.resources.autosave_retry
import eu.tintera.plume.resources.autosave_videos_switch
import eu.tintera.plume.resources.autosave_videos_up_to
import eu.tintera.plume.resources.common_cancel
import eu.tintera.plume.resources.group_24dp
import eu.tintera.plume.resources.campaign_24dp
import eu.tintera.plume.resources.person_24dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * „Ukládání do galerie" v Úložišti: výchozí pravidla pro soukromé chaty, skupiny a kanály
 * a výjimky pro jednotlivé chaty (zakládají se v profilu chatu, tady se dají upravit
 * a odebrat). Změna jde hned na server — je to nastavení účtu.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun AutosaveGroup(viewModel: AutosaveViewModel, snackbarHostState: SnackbarHostState) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    LaunchedEffect(state.error) {
        val error = state.error ?: return@LaunchedEffect
        viewModel.consumeError()
        scope.launch { snackbarHostState.showSnackbar(error.resolveNow()) }
    }
    var editing by rememberSaveable(stateSaver = AutosaveScopeSaver) { mutableStateOf<AutosaveScope?>(null) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    if (confirmClear) {
        ConfirmDialog(
            title = stringResource(Res.string.autosave_exceptions_clear_title),
            text = stringResource(Res.string.autosave_exceptions_clear_text),
            confirmLabel = stringResource(Res.string.autosave_exceptions_clear_confirm),
            icon = Res.drawable.delete_sweep_24dp,
            destructive = true,
            onConfirm = {
                confirmClear = false
                viewModel.clearExceptions()
            },
            onDismiss = { confirmClear = false },
        )
    }
    val permission = rememberSavePermission()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingsGroup(Res.string.autosave_group) {
            val settings = state.settings
            when {
                settings != null -> {
                    ScopeRow(Res.string.autosave_private, Res.drawable.person_24dp, settings.privateChats) { editing = AutosaveScope.PrivateChats }
                    ScopeRow(Res.string.autosave_groups, Res.drawable.group_24dp, settings.groups) { editing = AutosaveScope.Groups }
                    ScopeRow(Res.string.autosave_channels, Res.drawable.campaign_24dp, settings.channels) { editing = AutosaveScope.Channels }
                }
                state.loadFailed -> ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text(stringResource(Res.string.autosave_load_failed), color = MaterialTheme.colorScheme.error) },
                    trailingContent = { TextButton(onClick = viewModel::load, shapes = ButtonDefaults.shapes()) { Text(stringResource(Res.string.autosave_retry)) } },
                )
                else -> Box(Modifier.padding(16.dp)) { LoadingIndicator() }
            }
        }
        val exceptions = state.settings?.exceptions.orEmpty()
        if (exceptions.isNotEmpty()) {
            SettingsGroup(Res.string.autosave_exceptions) {
                exceptions.forEach { exception ->
                    val chat = state.exceptionChats[exception.chatId]
                    val title = chatTitle(exception.chatId, chat?.title.orEmpty())
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        leadingContent = {
                            Avatar(
                                chatId = exception.chatId,
                                title = title,
                                avatarFileId = chat?.avatarFileId,
                                size = 40.dp,
                                shape = (chat?.kind ?: ChatKind.Private).avatarShape(),
                            )
                        },
                        headlineContent = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text(autosaveSummary(exception.settings)) },
                        modifier = Modifier.clickable { editing = AutosaveScope.Chat(exception.chatId) },
                    )
                }
                GroupBreak()
                SettingsActionRow(
                    icon = Res.drawable.delete_sweep_24dp,
                    title = stringResource(Res.string.autosave_exceptions_clear),
                    destructive = true,
                    onClick = { confirmClear = true },
                )
            }
        }
        Text(
            stringResource(Res.string.autosave_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }

    val settings = state.settings
    val target = editing
    if (settings != null && target != null) {
        val current = when (target) {
            AutosaveScope.PrivateChats -> settings.privateChats
            AutosaveScope.Groups -> settings.groups
            AutosaveScope.Channels -> settings.channels
            is AutosaveScope.Chat -> settings.exceptions.firstOrNull { it.chatId == target.chatId }?.settings
        }
        if (current == null) {
            // Výjimka mezitím zmizela (odebraná z jiného zařízení) — dialog nemá co ukázat.
            LaunchedEffect(target) { editing = null }
        } else {
            val title = when (target) {
                AutosaveScope.PrivateChats -> stringResource(Res.string.autosave_private)
                AutosaveScope.Groups -> stringResource(Res.string.autosave_groups)
                AutosaveScope.Channels -> stringResource(Res.string.autosave_channels)
                is AutosaveScope.Chat -> chatTitle(target.chatId, state.exceptionChats[target.chatId]?.title.orEmpty())
            }
            AutosaveScopeDialog(
                title = title,
                initial = current,
                canRemove = target is AutosaveScope.Chat,
                onConfirm = { changed ->
                    editing = null
                    if (changed != current) {
                        // Zapnutí potřebuje zápis do galerie: iOS se zeptá, Android do verze 9 taky.
                        // Nastavení účtu se změní tak jako tak — patří i jiným zařízením.
                        if (changed.enabled && !current.enabled) permission.request { }
                        viewModel.set(target, changed)
                    }
                },
                onRemove = {
                    editing = null
                    viewModel.set(target, null)
                },
                onDismiss = { editing = null },
            )
        }
    }
}

@Composable
private fun ScopeRow(title: StringResource, icon: DrawableResource, settings: AutosaveScopeSettings, onClick: () -> Unit) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { Icon(painterResource(icon), contentDescription = null) },
        headlineContent = { Text(stringResource(title)) },
        supportingContent = { Text(autosaveSummary(settings)) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/** „Vypnuto", „Fotky", „Videa do 100 MB", „Fotky a videa do 100 MB". */
@Composable
internal fun autosaveSummary(settings: AutosaveScopeSettings): String {
    val size = fileSizeText(settings.maxVideoBytes)
    return when {
        settings.photos && settings.videos -> stringResource(Res.string.autosave_photos_videos_up_to, size)
        settings.photos -> stringResource(Res.string.autosave_photos)
        settings.videos -> stringResource(Res.string.autosave_videos_up_to, size)
        else -> stringResource(Res.string.autosave_off)
    }
}

/**
 * Úprava jednoho pravidla: fotky, videa a největší video. Změna se pošle až „Hotovo" — tři
 * volání za sebou při každém ťuknutí by se na serveru předbíhala. Volá ho i profil chatu.
 *
 * @param hint věta nad volbami (v profilu: že jde o výjimku pro tenhle chat).
 */
@Composable
internal fun AutosaveScopeDialog(
    title: String,
    initial: AutosaveScopeSettings,
    canRemove: Boolean,
    onConfirm: (AutosaveScopeSettings) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
    hint: String? = null,
) {
    var photos by rememberSaveable { mutableStateOf(initial.photos) }
    var videos by rememberSaveable { mutableStateOf(initial.videos) }
    var maxVideo by rememberSaveable { mutableStateOf(initial.maxVideoBytes) }
    // Hodnota z jiného klienta, která mezi volbami není, zůstane vidět a vybraná — ale jen
    // u zapnutých videí: u vypnutých server vrací nejmenší povolenou (512 kB), a ta by v nabídce
    // jen mátla.
    val sizes = remember(initial) {
        (AUTOSAVE_VIDEO_SIZES + listOfNotNull(initial.maxVideoBytes.takeIf { initial.videos })).distinct().sorted()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.heightIn(max = DIALOG_MAX).verticalScroll(rememberScrollState())) {
                hint?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                SwitchRow(title = Res.string.autosave_photos_switch, checked = photos, onChange = { photos = it })
                SwitchRow(
                    title = Res.string.autosave_videos_switch,
                    checked = videos,
                    onChange = { on ->
                        videos = on
                        // Zapnutí s limitem, který mezi volbami není (512 kB od serveru), by ukládalo
                        // jen půlmegová videa — předvybere se rozumná velikost.
                        if (on && maxVideo !in sizes) maxVideo = AUTOSAVE_DEFAULT_VIDEO_BYTES
                    },
                )
                if (videos) {
                    Text(
                        stringResource(Res.string.autosave_max_video),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
                    )
                    sizes.forEach { size ->
                        val selected = size == maxVideo
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            leadingContent = { RadioButton(selected = selected, onClick = null) },
                            headlineContent = { Text(fileSizeText(size)) },
                            modifier = Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = { maxVideo = size }),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(AutosaveScopeSettings(photos = photos, videos = videos, maxVideoBytes = maxVideo)) }, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(Res.string.autosave_done))
            }
        },
        dismissButton = {
            Row {
                if (canRemove) {
                    TextButton(onClick = onRemove, shapes = ButtonDefaults.shapes()) {
                        Text(stringResource(Res.string.autosave_remove_exception), color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text(stringResource(Res.string.common_cancel)) }
            }
        },
    )
}

/** Rozpracovaná úprava přežije otočení: obor jako text („private", „chat:-100…"). */
private val AutosaveScopeSaver = androidx.compose.runtime.saveable.Saver<AutosaveScope?, String>(
    save = { scope ->
        when (scope) {
            null -> ""
            AutosaveScope.PrivateChats -> "private"
            AutosaveScope.Groups -> "groups"
            AutosaveScope.Channels -> "channels"
            is AutosaveScope.Chat -> "chat:${scope.chatId}"
        }
    },
    restore = { saved ->
        when {
            saved == "private" -> AutosaveScope.PrivateChats
            saved == "groups" -> AutosaveScope.Groups
            saved == "channels" -> AutosaveScope.Channels
            saved.startsWith("chat:") -> saved.removePrefix("chat:").toLongOrNull()?.let { AutosaveScope.Chat(it) }
            else -> null
        }
    },
)

private val DIALOG_MAX = 480.dp
