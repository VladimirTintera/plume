package eu.tintera.plume.features.profile

import eu.tintera.plume.resources.edit_chat_forum_help
import eu.tintera.plume.resources.edit_chat_forum
import eu.tintera.plume.resources.how_to_reg_24dp
import eu.tintera.plume.resources.link_24dp
import eu.tintera.plume.resources.edit_chat_join_requests_none
import eu.tintera.plume.resources.edit_chat_join_requests_count
import eu.tintera.plume.resources.edit_chat_join_requests
import eu.tintera.plume.resources.edit_chat_invite_links_help
import eu.tintera.plume.resources.edit_chat_invite_links
import eu.tintera.plume.resources.edit_chat_invites
import org.jetbrains.compose.resources.pluralStringResource
import eu.tintera.plume.core.ui.grouped
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.ChatKind
import eu.tintera.plume.core.telegram.GroupPermissions
import eu.tintera.plume.core.ui.Avatar
import eu.tintera.plume.core.ui.ChatTypeSection
import eu.tintera.plume.core.ui.avatarShape
import eu.tintera.plume.core.ui.rememberAttachmentPicker
import eu.tintera.plume.core.ui.resolve
import eu.tintera.plume.core.ui.toggle
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.arrow_back_24dp
import eu.tintera.plume.resources.common_back
import eu.tintera.plume.resources.common_cancel
import eu.tintera.plume.resources.edit_chat_delete_photo
import eu.tintera.plume.resources.edit_chat_delete_photo_body
import eu.tintera.plume.resources.edit_chat_description
import eu.tintera.plume.resources.edit_chat_description_help
import eu.tintera.plume.resources.edit_chat_permissions
import eu.tintera.plume.resources.edit_chat_perm_change_info
import eu.tintera.plume.resources.edit_chat_perm_invite
import eu.tintera.plume.resources.edit_chat_perm_pin
import eu.tintera.plume.resources.edit_chat_perm_react
import eu.tintera.plume.resources.edit_chat_perm_send_documents
import eu.tintera.plume.resources.edit_chat_perm_send_messages
import eu.tintera.plume.resources.edit_chat_perm_send_photos
import eu.tintera.plume.resources.edit_chat_perm_send_stickers
import eu.tintera.plume.resources.edit_chat_perm_send_videos
import eu.tintera.plume.resources.edit_chat_perm_send_voice
import eu.tintera.plume.resources.edit_chat_title
import eu.tintera.plume.resources.edit_chat_title_channel
import eu.tintera.plume.resources.edit_chat_name
import eu.tintera.plume.resources.edit_chat_name_required
import eu.tintera.plume.resources.edit_profile_change_photo
import eu.tintera.plume.resources.edit_profile_delete_photo_confirm
import eu.tintera.plume.resources.edit_profile_save
import eu.tintera.plume.resources.photo_camera_24dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Úprava skupiny nebo kanálu z profilu: fotka, název, popis, veřejný odkaz a výchozí práva členů.
 *
 * **Ukazuje se jen to, na co uživatel má právo.** Název, popis a fotka stojí na `canChangeInfo`,
 * práva členů na `canRestrictMembers` — správce může mít jen jedno z nich, takže se o každém
 * bloku rozhoduje zvlášť. Obrazovku samotnou nabídne profil, jen když je aspoň jedno z nich.
 */
@Composable
fun EditChatScreen(
    viewModel: EditChatViewModel,
    onBack: () -> Unit,
    onOpenInviteLinks: () -> Unit,
    onOpenJoinRequests: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val currentOnBack by rememberUpdatedState(onBack)
    var confirmDeletePhoto by rememberSaveable { mutableStateOf(false) }

    val notice = viewModel.notice
    val noticeText = notice?.resolve()
    // Snackbar mimo tělo efektu: vynulování hlášky mění klíč a zrušilo by ho (viz „Vrátit").
    LaunchedEffect(notice) {
        if (noticeText != null) {
            viewModel.noticeShown()
            scope.launch { snackbarHostState.showSnackbar(noticeText) }
        }
    }
    LaunchedEffect(viewModel.saved) {
        if (viewModel.saved) currentOnBack()
    }

    val picker = rememberAttachmentPicker(onPicked = viewModel::setPhoto, onError = viewModel::reportError)

    if (confirmDeletePhoto) {
        AlertDialog(
            onDismissRequest = { confirmDeletePhoto = false },
            title = { Text(stringResource(Res.string.edit_chat_delete_photo)) },
            text = { Text(stringResource(Res.string.edit_chat_delete_photo_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeletePhoto = false
                    viewModel.deletePhoto()
                }) {
                    Text(stringResource(Res.string.edit_profile_delete_photo_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeletePhoto = false }) { Text(stringResource(Res.string.common_cancel)) }
            },
        )
    }

    val chat = viewModel.original

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (chat?.isChannel == true) Res.string.edit_chat_title_channel else Res.string.edit_chat_title,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(Res.drawable.arrow_back_24dp), contentDescription = stringResource(Res.string.common_back))
                    }
                },
                actions = {
                    // Uložit se nabízí, jen když je co ukládat — fotka a práva jdou na server hned.
                    if (chat?.rights?.canChangeInfo == true) {
                        TextButton(onClick = viewModel::save, enabled = viewModel.canSave) {
                            Text(stringResource(Res.string.edit_profile_save))
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (chat == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                if (!viewModel.loadFailed) LoadingIndicator()
            }
            return@Scaffold
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (chat.rights.canChangeInfo) {
                Box(contentAlignment = Alignment.Center) {
                    Avatar(
                        chatId = chat.chatId,
                        title = viewModel.title,
                        avatarFileId = chat.photoFileId ?: chat.photoThumbnailFileId,
                        size = AVATAR_SIZE,
                        // Tentýž tvar jako v seznamu a v hlavičce — skupina sušenka, kanál čtverec.
                        shape = (if (chat.isChannel) ChatKind.Channel else ChatKind.Group).avatarShape(),
                    )
                    // Nahrávání fotky trvá: kolečko přes avatar, jako průběh nahrávání přílohy.
                    if (viewModel.photoBusy) CircularWavyProgressIndicator(Modifier.size(AVATAR_SIZE))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = picker::pickMedia, enabled = !viewModel.photoBusy) {
                        Icon(painterResource(Res.drawable.photo_camera_24dp), contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                        Text(stringResource(Res.string.edit_profile_change_photo))
                    }
                    if (chat.photoFileId != null) {
                        TextButton(onClick = { confirmDeletePhoto = true }, enabled = !viewModel.photoBusy) {
                            Text(stringResource(Res.string.edit_chat_delete_photo))
                        }
                    }
                }

                OutlinedTextField(
                    value = viewModel.title,
                    onValueChange = { viewModel.title = it.take(TITLE_MAX_LENGTH) },
                    label = { Text(stringResource(Res.string.edit_chat_name)) },
                    singleLine = true,
                    isError = viewModel.title.isBlank(),
                    supportingText = if (viewModel.title.isBlank()) {
                        { Text(stringResource(Res.string.edit_chat_name_required)) }
                    } else null,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = viewModel.description,
                    onValueChange = { viewModel.description = it.take(DESCRIPTION_MAX_LENGTH) },
                    label = { Text(stringResource(Res.string.edit_chat_description)) },
                    minLines = 2,
                    supportingText = { Text(stringResource(Res.string.edit_chat_description_help)) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Veřejný odkaz mění jen vlastník supergroupy nebo kanálu (`EditableChat.canChangeUsername`).
            if (chat.rights.canChangeInfo && chat.canChangeUsername) {
                ChatTypeSection(
                    channel = chat.isChannel,
                    isPublic = viewModel.isPublic,
                    onPublicChange = { viewModel.isPublic = it },
                    username = viewModel.username,
                    onUsernameChange = { viewModel.username = it },
                    status = viewModel.usernameStatus,
                    enabled = !viewModel.saving,
                    original = chat.username,
                )
            }

            // Témata zapíná jen vlastník supergroupy (viz `EditableChat.canToggleForum`).
            if (chat.canToggleForum) {
                Card(Modifier.fillMaxWidth()) {
                    Column {
                        SwitchRow(Res.string.edit_chat_forum, viewModel.isForum, viewModel::toggleForum)
                        Text(
                            stringResource(Res.string.edit_chat_forum_help),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                        )
                    }
                }
            }

            /*
             * Práva členů jen ve skupině a jen s právem omezovat (`setChatPermissions` ho
             * vyžaduje). Kanál výchozí práva nemá — nikdo do něj nepíše, takže není co omezovat.
             */
            val permissions = viewModel.permissions
            if (chat.rights.canRestrictMembers && !chat.isChannel && permissions != null) {
                PermissionsGroup(permissions, viewModel::setPermission)
            }

            // Pozvánky: vlastník a správce s právem zvát (`ChatEditRights.canManageInvites`).
            if (chat.rights.canManageInvites) {
                val pending by viewModel.pendingJoinRequests.collectAsStateWithLifecycle()
                InvitesGroup(pending = pending, onOpenInviteLinks = onOpenInviteLinks, onOpenJoinRequests = onOpenJoinRequests)
            }
        }
    }
}

/**
 * Výchozí práva členů. Přepínače se ukládají hned — čekat na potvrzení by znamenalo přepínač,
 * který se po ťuknutí nehýbe (stejně jako v nastavení archivu).
 */
@Composable
private fun PermissionsGroup(
    permissions: GroupPermissions,
    onChange: ((GroupPermissions) -> GroupPermissions) -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Nadpis a karta jsou jeden kus, jako skupina v Nastavení — jinak by nadpis visel
        // stejně daleko od svojí karty jako od pole nad ní.
        Text(
            stringResource(Res.string.edit_chat_permissions),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp),
        )
        Card(Modifier.fillMaxWidth()) {
            Column {
                SwitchRow(Res.string.edit_chat_perm_send_messages, permissions.sendMessages) { value ->
                    onChange { it.copy(sendMessages = value) }
                }
                SwitchRow(Res.string.edit_chat_perm_send_photos, permissions.sendPhotos) { value ->
                    onChange { it.copy(sendPhotos = value) }
                }
                SwitchRow(Res.string.edit_chat_perm_send_videos, permissions.sendVideos) { value ->
                    onChange { it.copy(sendVideos = value) }
                }
                SwitchRow(Res.string.edit_chat_perm_send_documents, permissions.sendDocuments) { value ->
                    onChange { it.copy(sendDocuments = value) }
                }
                SwitchRow(Res.string.edit_chat_perm_send_voice, permissions.sendVoiceNotes) { value ->
                    onChange { it.copy(sendVoiceNotes = value) }
                }
                SwitchRow(Res.string.edit_chat_perm_send_stickers, permissions.sendStickers) { value ->
                    onChange { it.copy(sendStickers = value) }
                }
                SwitchRow(Res.string.edit_chat_perm_react, permissions.react) { value ->
                    onChange { it.copy(react = value) }
                }
                SwitchRow(Res.string.edit_chat_perm_invite, permissions.inviteUsers) { value ->
                    onChange { it.copy(inviteUsers = value) }
                }
                SwitchRow(Res.string.edit_chat_perm_pin, permissions.pinMessages) { value ->
                    onChange { it.copy(pinMessages = value) }
                }
                SwitchRow(Res.string.edit_chat_perm_change_info, permissions.changeInfo) { value ->
                    onChange { it.copy(changeInfo = value) }
                }
            }
        }
    }
}

/**
 * Pozvánky a žádosti o vstup — dva řádky, každý na vlastní obrazovku. Žádosti mají v podtitulu
 * počet čekajících, aby bylo vidět, že je co vyřizovat, ještě než se na ně ťukne.
 */
@Composable
private fun InvitesGroup(pending: Int, onOpenInviteLinks: () -> Unit, onOpenJoinRequests: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(Res.string.edit_chat_invites),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp),
        )
        Card(Modifier.fillMaxWidth()) {
            Column {
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text(stringResource(Res.string.edit_chat_invite_links)) },
                    supportingContent = { Text(stringResource(Res.string.edit_chat_invite_links_help)) },
                    leadingContent = { Icon(painterResource(Res.drawable.link_24dp), contentDescription = null) },
                    modifier = Modifier.clickable(onClick = onOpenInviteLinks),
                )
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text(stringResource(Res.string.edit_chat_join_requests)) },
                    supportingContent = {
                        Text(
                            if (pending > 0) {
                                pluralStringResource(Res.plurals.edit_chat_join_requests_count, pending, pending.grouped())
                            } else {
                                stringResource(Res.string.edit_chat_join_requests_none)
                            },
                        )
                    },
                    leadingContent = { Icon(painterResource(Res.drawable.how_to_reg_24dp), contentDescription = null) },
                    modifier = Modifier.clickable(onClick = onOpenJoinRequests),
                )
            }
        }
    }
}

/** Řádek nese klikání celý, přepínač má `onCheckedChange = null` — jako v Nastavení. */
@Composable
internal fun SwitchRow(title: StringResource, checked: Boolean, onChange: (Boolean) -> Unit) {
    val haptics = LocalHapticFeedback.current
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = { Text(stringResource(title)) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = { on ->
            haptics.toggle(on)
            onChange(on)
        }),
    )
}

private const val TITLE_MAX_LENGTH = 128
private const val DESCRIPTION_MAX_LENGTH = 255
private val AVATAR_SIZE = 96.dp
