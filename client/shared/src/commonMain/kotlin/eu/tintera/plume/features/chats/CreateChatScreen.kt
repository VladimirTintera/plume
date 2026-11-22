package eu.tintera.plume.features.chats

import eu.tintera.plume.core.ui.rememberRestoringScrollState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.FlowRow
import eu.tintera.plume.core.telegram.ProfilePhotoFile
import eu.tintera.plume.features.profile.EmojiPhotoPreview
import eu.tintera.plume.features.profile.EmojiPhotoSheet
import eu.tintera.plume.resources.mood_24dp
import eu.tintera.plume.resources.emoji_photo_open
import androidx.compose.material3.IconButtonDefaults
import eu.tintera.plume.core.ui.SaveActionButton
import eu.tintera.plume.core.ui.FormTextField
import androidx.compose.material3.ButtonDefaults
import eu.tintera.plume.resources.add_members_refused_invite
import eu.tintera.plume.resources.add_members_refused_text
import eu.tintera.plume.resources.add_members_refused_text_no_link
import eu.tintera.plume.resources.add_members_refused_title
import eu.tintera.plume.resources.create_channel_username_failed
import eu.tintera.plume.resources.create_group_username_failed
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import eu.tintera.plume.core.telegram.ChatKind
import eu.tintera.plume.core.ui.ChatTypeSection
import eu.tintera.plume.features.channellimit.PublicLinkLimitSheet
import eu.tintera.plume.core.ui.localImage
import eu.tintera.plume.core.ui.avatarShape
import eu.tintera.plume.core.ui.grouped
import eu.tintera.plume.core.ui.PhotoCropOverlay
import eu.tintera.plume.core.ui.rememberAttachmentPicker
import eu.tintera.plume.core.ui.resolve
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.arrow_back_24dp
import eu.tintera.plume.resources.campaign_24dp
import eu.tintera.plume.resources.common_back
import eu.tintera.plume.resources.common_close
import eu.tintera.plume.resources.create_channel_created
import eu.tintera.plume.resources.create_channel_help
import eu.tintera.plume.resources.create_chat_add_photo
import eu.tintera.plume.resources.create_chat_create
import eu.tintera.plume.resources.create_chat_photo_failed
import eu.tintera.plume.resources.create_group_created
import eu.tintera.plume.resources.create_group_no_members
import eu.tintera.plume.resources.edit_chat_delete_photo
import eu.tintera.plume.resources.edit_chat_description
import eu.tintera.plume.resources.edit_chat_description_help
import eu.tintera.plume.resources.edit_chat_name
import eu.tintera.plume.resources.edit_profile_change_photo
import eu.tintera.plume.resources.group_24dp
import eu.tintera.plume.resources.invite_members
import eu.tintera.plume.resources.new_chat_new_channel
import eu.tintera.plume.resources.new_chat_new_group
import eu.tintera.plume.resources.photo_camera_24dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Druhý krok založení skupiny (první je výběr členů) a jediný krok založení kanálu: fotka,
 * název, u kanálu popis. Rozložením je to úprava chatu (`EditChatScreen`) — kdo pak skupinu
 * upravuje, vidí tentýž formulář.
 *
 * **Popis jen u kanálu.** Kanál se představuje lidem, kteří ho najdou a ještě v něm nejsou;
 * skupina vzniká pro lidi, které uživatel právě vybral. Doplnit ho jde v úpravě.
 */
@Composable
fun CreateChatScreen(
    viewModel: CreateChatViewModel,
    onBack: () -> Unit,
    onCreated: (chatId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val currentOnCreated by rememberUpdatedState(onCreated)

    val notice = viewModel.notice
    val noticeText = notice?.resolve()
    // Snackbar mimo tělo efektu: vynulování hlášky mění klíč a zrušilo by ho (viz „Vrátit").
    LaunchedEffect(notice) {
        if (noticeText != null) {
            viewModel.noticeShown()
            scope.launch { snackbarHostState.showSnackbar(noticeText) }
        }
    }
    LaunchedEffect(viewModel.created) {
        viewModel.created?.let { currentOnCreated(it) }
    }

    viewModel.report?.let { report ->
        CreatedReportDialog(
            report = report,
            channel = viewModel.channel,
            sending = viewModel.sendingInvites,
            onSendInvites = viewModel::sendInvites,
            onDismiss = { onCreated(report.chatId) },
        )
    }

    if (viewModel.publicLimit?.reached == true) {
        PublicLinkLimitSheet(
            onDismiss = { viewModel.publicLimitClosed(released = false) },
            onReleased = { viewModel.publicLimitClosed(released = true) },
        )
    }

    val picker = rememberAttachmentPicker(onPicked = viewModel::setPhoto, onError = viewModel::reportError)
    var showEmojiPhoto by rememberSaveable { mutableStateOf(false) }
    if (showEmojiPhoto) {
        EmojiPhotoSheet(
            forChat = true,
            previewShape = (if (viewModel.channel) ChatKind.Channel else ChatKind.Group).avatarShape(),
            onSet = { choice ->
                showEmojiPhoto = false
                viewModel.chooseEmojiPhoto(choice)
            },
            onDismiss = { showEmojiPhoto = false },
        )
    }
    val channel = viewModel.channel

    // Ořez fotky leží přes celou obrazovku, nad Scaffoldem.
    Box(Modifier.fillMaxSize()) {
        Scaffold(
            modifier = modifier,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(if (channel) Res.string.new_chat_new_channel else Res.string.new_chat_new_group)) },
                    navigationIcon = {
                        IconButton(
                            onClick = onBack,
                            enabled = !viewModel.creating,
                            shapes = IconButtonDefaults.shapes(),
                        ) {
                            Icon(painterResource(Res.drawable.arrow_back_24dp), contentDescription = stringResource(Res.string.common_back))
                        }
                    },
                    actions = {
                        if (viewModel.creating) {
                            LoadingIndicator(Modifier.padding(end = 12.dp).size(40.dp))
                        } else {
                            SaveActionButton(onClick = viewModel::create, enabled = viewModel.canCreate) {
                                Text(stringResource(Res.string.create_chat_create))
                            }
                        }
                    },
                )
            },
        ) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding()
                    .verticalScroll(rememberRestoringScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PhotoSlot(
                    path = viewModel.photo?.preview,
                    emoji = viewModel.emojiPhoto,
                    channel = channel,
                    busy = viewModel.photoBusy,
                    enabled = !viewModel.creating,
                    onPick = picker::pickMedia,
                )
                // Tři tlačítka se na úzký telefon nevejdou vedle sebe — co přeteče, jde pod.
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                    TextButton(onClick = picker::pickMedia, enabled = !viewModel.photoBusy && !viewModel.creating, shapes = ButtonDefaults.shapes()) {
                        Icon(painterResource(Res.drawable.photo_camera_24dp), contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                        Text(
                            stringResource(
                                if (!viewModel.hasPhoto) Res.string.create_chat_add_photo else Res.string.edit_profile_change_photo,
                            ),
                        )
                    }
                    TextButton(onClick = { showEmojiPhoto = true }, enabled = !viewModel.photoBusy && !viewModel.creating, shapes = ButtonDefaults.shapes()) {
                        Icon(painterResource(Res.drawable.mood_24dp), contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                        Text(stringResource(Res.string.emoji_photo_open))
                    }
                    if (viewModel.hasPhoto) {
                        TextButton(onClick = viewModel::removePhoto, enabled = !viewModel.creating, shapes = ButtonDefaults.shapes()) {
                            Text(stringResource(Res.string.edit_chat_delete_photo))
                        }
                    }
                }

                FormTextField(
                    value = viewModel.title,
                    onValueChange = { viewModel.title = it.take(TITLE_MAX_LENGTH) },
                    label = { Text(stringResource(Res.string.edit_chat_name)) },
                    singleLine = true,
                    enabled = !viewModel.creating,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = if (channel) ImeAction.Next else ImeAction.Done,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )

                if (channel) {
                    FormTextField(
                        value = viewModel.description,
                        onValueChange = { viewModel.description = it.take(DESCRIPTION_MAX_LENGTH) },
                        label = { Text(stringResource(Res.string.edit_chat_description)) },
                        minLines = 2,
                        enabled = !viewModel.creating,
                        supportingText = { Text(stringResource(Res.string.edit_chat_description_help)) },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                ChatTypeSection(
                    channel = channel,
                    isPublic = viewModel.isPublic,
                    onPublicChange = viewModel::choosePublic,
                    username = viewModel.username,
                    onUsernameChange = { viewModel.username = it },
                    status = viewModel.usernameStatus,
                    enabled = !viewModel.creating,
                )

                if (channel) {
                    Hint(stringResource(Res.string.create_channel_help))
                } else {
                    Members(count = viewModel.memberCount, names = viewModel.memberNames)
                }
            }
        }
        PhotoCropOverlay(
            path = viewModel.cropping,
            onCancel = viewModel::cancelCrop,
            onConfirm = viewModel::cropPhoto,
            video = viewModel.croppingVideo,
        )
    }
}

/** Fotka v tvaru, jaký bude mít chat v seznamu — skupina sušenka, kanál čtverec. */
@Composable
private fun PhotoSlot(path: String?, emoji: ProfilePhotoFile?, channel: Boolean, busy: Boolean, enabled: Boolean, onPick: () -> Unit) {
    val shape = (if (channel) ChatKind.Channel else ChatKind.Group).avatarShape()
    Box(contentAlignment = Alignment.Center) {
        Surface(
            shape = shape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(AVATAR_SIZE).clip(shape).clickable(enabled = enabled && !busy, onClick = onPick),
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (emoji != null) {
                    EmojiPhotoPreview(photo = emoji, size = AVATAR_SIZE, shape = shape)
                } else if (path != null) {
                    AsyncImage(
                        model = localImage(path),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        painterResource(if (channel) Res.drawable.campaign_24dp else Res.drawable.group_24dp),
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                    )
                }
            }
        }
        if (busy) CircularWavyProgressIndicator(Modifier.size(AVATAR_SIZE))
    }
}

@Composable
private fun Members(count: Int, names: List<String>) {
    if (count == 0) {
        Hint(stringResource(Res.string.create_group_no_members))
        return
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            pluralStringResource(Res.plurals.invite_members, count, count.grouped()),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() },
        )
        if (names.isNotEmpty()) {
            Text(
                names.joinToString(", "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
    )
}

/**
 * Chat stojí, ale něco se nepovedlo. Dialog, ne snackbar: obrazovka hned odchází do chatu
 * a snackbar by nebylo kde ukázat. **Komu server nedovolil přidání, tomu jde rovnou poslat
 * pozvánku** — tytéž věty a tatáž cesta jako při přidávání členů (`AddMembersScreen`).
 */
@Composable
private fun CreatedReportDialog(
    report: CreatedReport,
    channel: Boolean,
    sending: Boolean,
    onSendInvites: () -> Unit,
    onDismiss: () -> Unit,
) {
    val refused = report.refused
    val names = refused.map { it.name }.filter { it.isNotBlank() }.joinToString(", ")
    val canInvite = refused.isNotEmpty() && report.inviteLink != null
    AlertDialog(
        // Chat stojí, takže zavření dialogu = otevřít ho.
        onDismissRequest = { if (!sending) onDismiss() },
        title = {
            Text(
                if (refused.isNotEmpty()) {
                    pluralStringResource(Res.plurals.add_members_refused_title, refused.size, refused.size)
                } else {
                    stringResource(if (channel) Res.string.create_channel_created else Res.string.create_group_created)
                },
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (refused.isNotEmpty()) {
                    Text(
                        stringResource(
                            if (canInvite) Res.string.add_members_refused_text else Res.string.add_members_refused_text_no_link,
                            names,
                        ),
                    )
                }
                if (report.usernameFailed) {
                    Text(
                        stringResource(
                            if (channel) Res.string.create_channel_username_failed else Res.string.create_group_username_failed,
                        ),
                    )
                }
                if (report.photoFailed) Text(stringResource(Res.string.create_chat_photo_failed))
            }
        },
        confirmButton = {
            if (canInvite) {
                TextButton(onClick = onSendInvites, enabled = !sending, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(Res.string.add_members_refused_invite))
                }
            } else {
                TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text(stringResource(Res.string.common_close)) }
            }
        },
        dismissButton = if (canInvite) {
            { TextButton(onClick = onDismiss, enabled = !sending, shapes = ButtonDefaults.shapes()) { Text(stringResource(Res.string.common_close)) } }
        } else {
            null
        },
    )
}

private const val TITLE_MAX_LENGTH = 128
private const val DESCRIPTION_MAX_LENGTH = 255
private val AVATAR_SIZE = 96.dp
