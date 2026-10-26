package eu.tintera.plume.features.profile

import androidx.compose.foundation.layout.FlowRow
import eu.tintera.plume.resources.mood_24dp
import eu.tintera.plume.resources.emoji_photo_open
import eu.tintera.plume.resources.campaign_24dp
import eu.tintera.plume.resources.broadcast_group_confirm
import eu.tintera.plume.resources.broadcast_group_body
import eu.tintera.plume.resources.broadcast_group_title
import eu.tintera.plume.resources.broadcast_group_row_help
import eu.tintera.plume.resources.broadcast_group_row
import eu.tintera.plume.resources.celebration_24dp
import eu.tintera.plume.resources.welcome_messages_row_help
import eu.tintera.plume.resources.welcome_messages_title
import eu.tintera.plume.resources.group_24dp
import eu.tintera.plume.resources.history_24dp
import eu.tintera.plume.core.ui.SaveActionButton
import eu.tintera.plume.core.ui.GroupedColumn
import eu.tintera.plume.core.ui.FormTextField
import eu.tintera.plume.core.ui.ConfirmDialog
import eu.tintera.plume.resources.event_log_row_help
import eu.tintera.plume.resources.event_log_row
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ButtonDefaults
import eu.tintera.plume.core.ui.IconSwitch
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
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import eu.tintera.plume.core.telegram.StickerSetSummary
import eu.tintera.plume.core.telegram.ChatKind
import eu.tintera.plume.core.telegram.GroupPermissions
import eu.tintera.plume.core.telegram.LinkedChat
import eu.tintera.plume.resources.edit_chat_discussion
import eu.tintera.plume.resources.edit_chat_sticker_set
import eu.tintera.plume.resources.edit_chat_sticker_set_none
import eu.tintera.plume.resources.edit_chat_sticker_set_off
import eu.tintera.plume.resources.sticker_24dp
import eu.tintera.plume.resources.stickers_count
import eu.tintera.plume.resources.edit_chat_discussion_none
import eu.tintera.plume.resources.edit_chat_discussion_off
import eu.tintera.plume.resources.edit_chat_discussion_on
import eu.tintera.plume.resources.forum_24dp
import eu.tintera.plume.core.ui.Avatar
import eu.tintera.plume.core.ui.ChatTypeSection
import eu.tintera.plume.features.channellimit.PublicLinkLimitSheet
import eu.tintera.plume.core.ui.avatarShape
import eu.tintera.plume.core.ui.PhotoCropOverlay
import eu.tintera.plume.core.ui.rememberAttachmentPicker
import eu.tintera.plume.core.ui.resolve
import eu.tintera.plume.core.ui.toggle
import eu.tintera.plume.core.telegram.MemberModeration
import eu.tintera.plume.resources.edit_chat_moderation
import eu.tintera.plume.resources.edit_chat_restricted
import eu.tintera.plume.resources.edit_chat_removed
import eu.tintera.plume.resources.edit_chat_nobody
import eu.tintera.plume.resources.edit_chat_people_count
import eu.tintera.plume.resources.lock_24dp
import eu.tintera.plume.resources.person_remove_24dp
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.rocket_launch_24dp
import eu.tintera.plume.resources.boosts_row_help
import eu.tintera.plume.resources.boosts_title
import eu.tintera.plume.resources.bar_chart_24dp
import eu.tintera.plume.resources.statistics_row_help
import eu.tintera.plume.resources.statistics_title
import eu.tintera.plume.resources.delete_24dp
import eu.tintera.plume.resources.edit_chat_delete_group_body
import eu.tintera.plume.resources.edit_chat_delete_channel_body
import eu.tintera.plume.resources.edit_chat_delete_group_title
import eu.tintera.plume.resources.edit_chat_delete_channel_title
import eu.tintera.plume.resources.edit_chat_delete_group
import eu.tintera.plume.resources.edit_chat_delete_channel
import eu.tintera.plume.resources.edit_chat_upgrade_confirm
import eu.tintera.plume.resources.edit_chat_upgrade_topics
import eu.tintera.plume.resources.edit_chat_upgrade_link
import eu.tintera.plume.resources.edit_chat_upgrade_title
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
import eu.tintera.plume.resources.edit_chat_perm_send_polls
import eu.tintera.plume.resources.edit_chat_perm_send_stickers
import eu.tintera.plume.resources.edit_chat_perm_send_video_notes
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
    /** Seznam vyhozených ([banned] `true`), nebo omezených členů. */
    onOpenRemoved: (banned: Boolean) -> Unit,
    /** Poslední akce ve skupině; nabízí se jen správcům ([EditableChat.canViewEventLog]). */
    onOpenEventLog: () -> Unit,
    /** Statistiky kanálu nebo skupiny; nabízí se jen tam, kde je server vydá ([EditableChat.canGetStatistics]). */
    onOpenStatistics: () -> Unit,
    /** Chat je smazaný pro všechny: zavřít všechno, co ho ukazuje. */
    onDeleted: () -> Unit,
    /** Základní skupina se převedla na supergroupu: (staré id, nové id). */
    onUpgraded: (Long, Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val currentOnBack by rememberUpdatedState(onBack)
    var confirmDeletePhoto by rememberSaveable { mutableStateOf(false) }
    var showEmojiPhoto by rememberSaveable { mutableStateOf(false) }
    var showDiscussion by rememberSaveable { mutableStateOf(false) }
    var showStickerSet by rememberSaveable { mutableStateOf(false) }
    var showSlowMode by rememberSaveable { mutableStateOf(false) }
    var showReactions by rememberSaveable { mutableStateOf(false) }
    var confirmDeleteChat by rememberSaveable { mutableStateOf(false) }
    var confirmBroadcastGroup by rememberSaveable { mutableStateOf(false) }
    val currentOnDeleted by rememberUpdatedState(onDeleted)
    LaunchedEffect(viewModel.deleted) {
        if (viewModel.deleted) currentOnDeleted()
    }

    // Návrat ze seznamu omezených / vyhozených: obrazovka se skládá znovu a počty se přečtou.
    LaunchedEffect(Unit) { viewModel.refreshOnReturn() }
    viewModel.welcome?.let { sheet ->
        WelcomeMessagesSheet(
            sheet = sheet,
            onAdd = viewModel::addWelcomeMessage,
            onDelete = viewModel::deleteWelcomeMessage,
            onDismiss = viewModel::closeWelcomeMessages,
        )
    }
    viewModel.boosts?.let { sheet ->
        BoostStatusSheet(
            sheet = sheet,
            onCopy = viewModel::copyBoostLink,
            onShare = viewModel::shareBoostLink,
            onLoadMore = viewModel::loadMoreBoosts,
            onDismiss = viewModel::closeBoosts,
        )
    }
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
    val currentOnUpgraded by rememberUpdatedState(onUpgraded)
    LaunchedEffect(viewModel.upgraded) {
        viewModel.upgraded?.let { (old, new) ->
            viewModel.upgradeHandled()
            currentOnUpgraded(old, new)
        }
    }

    if (viewModel.publicLimit.reached) {
        PublicLinkLimitSheet(
            onDismiss = { viewModel.publicLimitClosed(released = false) },
            onReleased = { viewModel.publicLimitClosed(released = true) },
        )
    }

    viewModel.upgradeAsked?.let { reason ->
        ConfirmDialog(
            title = stringResource(Res.string.edit_chat_upgrade_title),
            text = stringResource(
                when (reason) {
                    GroupUpgradeReason.PublicLink -> Res.string.edit_chat_upgrade_link
                    GroupUpgradeReason.Topics -> Res.string.edit_chat_upgrade_topics
                },
            ),
            confirmLabel = stringResource(Res.string.edit_chat_upgrade_confirm),
            onConfirm = viewModel::confirmUpgrade,
            onDismiss = viewModel::cancelUpgrade,
            icon = Res.drawable.group_24dp,
        )
    }

    val picker = rememberAttachmentPicker(onPicked = viewModel::setPhoto, onError = viewModel::reportError)

    if (confirmDeletePhoto) {
        ConfirmDialog(
            title = stringResource(Res.string.edit_chat_delete_photo),
            text = stringResource(Res.string.edit_chat_delete_photo_body),
            confirmLabel = stringResource(Res.string.edit_profile_delete_photo_confirm),
            onConfirm = {
                confirmDeletePhoto = false
                viewModel.deletePhoto()
            },
            onDismiss = { confirmDeletePhoto = false },
            icon = Res.drawable.delete_24dp,
            destructive = true,
        )
    }

    val emojiChat = viewModel.original
    if (showEmojiPhoto && emojiChat != null) {
        EmojiPhotoSheet(
            forChat = true,
            previewShape = (if (emojiChat.isChannel) ChatKind.Channel else ChatKind.Group).avatarShape(),
            onSet = { photo ->
                showEmojiPhoto = false
                viewModel.setEmojiPhoto(photo)
            },
            onDismiss = { showEmojiPhoto = false },
        )
    }

    if (showDiscussion) {
        DiscussionSheet(
            current = viewModel.linkedChat,
            candidates = viewModel.discussionCandidates,
            onSelect = { group ->
                showDiscussion = false
                viewModel.setDiscussion(group)
            },
            onDismiss = { showDiscussion = false },
        )
    }

    if (showStickerSet) {
        GroupStickerSetSheet(
            current = viewModel.stickerSet,
            candidates = viewModel.stickerSetCandidates,
            onSelect = { set ->
                showStickerSet = false
                viewModel.chooseStickerSet(set)
            },
            onDismiss = { showStickerSet = false },
        )
    }

    val editable = viewModel.original
    if (confirmDeleteChat && editable != null) {
        val channel = editable.isChannel
        ConfirmDialog(
            title = stringResource(if (channel) Res.string.edit_chat_delete_channel_title else Res.string.edit_chat_delete_group_title),
            text = stringResource(if (channel) Res.string.edit_chat_delete_channel_body else Res.string.edit_chat_delete_group_body),
            confirmLabel = stringResource(if (channel) Res.string.edit_chat_delete_channel else Res.string.edit_chat_delete_group),
            onConfirm = {
                confirmDeleteChat = false
                viewModel.deleteChatForAll()
            },
            onDismiss = { confirmDeleteChat = false },
            icon = Res.drawable.delete_24dp,
            destructive = true,
        )
    }

    if (confirmBroadcastGroup) {
        // Nevratné, ale nic nemaže — proto potvrzení bez chybové barvy, ikona říká, co se stane.
        ConfirmDialog(
            title = stringResource(Res.string.broadcast_group_title),
            text = stringResource(Res.string.broadcast_group_body),
            confirmLabel = stringResource(Res.string.broadcast_group_confirm),
            onConfirm = {
                confirmBroadcastGroup = false
                viewModel.convertToBroadcastGroup()
            },
            onDismiss = { confirmBroadcastGroup = false },
            icon = Res.drawable.campaign_24dp,
        )
    }

    val settings = viewModel.settings
    if (showSlowMode) {
        settings.slowModeDelay?.let { current ->
            SlowModeSheet(
                current = current,
                onSelect = { seconds ->
                    showSlowMode = false
                    viewModel.setSlowMode(seconds)
                },
                onDismiss = { showSlowMode = false },
            )
        }
    }
    if (showReactions) {
        settings.reactions?.let { current ->
            val choices by viewModel.reactionChoices.collectAsStateWithLifecycle()
            ChatReactionsSheet(
                current = current,
                choices = choices,
                onSave = { reactions ->
                    showReactions = false
                    viewModel.setReactions(reactions)
                },
                onDismiss = { showReactions = false },
            )
        }
    }

    val chat = viewModel.original

    // Ořez fotky leží přes celou obrazovku, nad Scaffoldem.
    Box(Modifier.fillMaxSize()) {
        val subpageScroll = profileSubpageScrollBehavior()
        Scaffold(
            modifier = modifier.profileSubpageScroll(subpageScroll),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                ProfileSubpageTopBar(
                    title = stringResource(if (chat?.isChannel == true) Res.string.edit_chat_title_channel else Res.string.edit_chat_title),
                    subtitle = null,
                    onBack = onBack,
                    scrollBehavior = subpageScroll,
                    actions = {
                        // Uložit se nabízí, jen když je co ukládat — fotka a práva jdou na server hned.
                        if (chat?.rights?.canChangeInfo == true) {
                            SaveActionButton(onClick = viewModel::save, enabled = viewModel.canSave) {
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
                    // Tři tlačítka se na úzký telefon nevejdou vedle sebe — co přeteče, jde pod.
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                        TextButton(onClick = picker::pickMedia, enabled = !viewModel.photoBusy, shapes = ButtonDefaults.shapes()) {
                            Icon(painterResource(Res.drawable.photo_camera_24dp), contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                            Text(stringResource(Res.string.edit_profile_change_photo))
                        }
                        TextButton(onClick = { showEmojiPhoto = true }, enabled = !viewModel.photoBusy, shapes = ButtonDefaults.shapes()) {
                            Icon(painterResource(Res.drawable.mood_24dp), contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                            Text(stringResource(Res.string.emoji_photo_open))
                        }
                        if (chat.photoFileId != null) {
                            TextButton(onClick = { confirmDeletePhoto = true }, enabled = !viewModel.photoBusy, shapes = ButtonDefaults.shapes()) {
                                Text(stringResource(Res.string.edit_chat_delete_photo))
                            }
                        }
                    }

                    FormTextField(
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

                    FormTextField(
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
                        onPublicChange = viewModel::choosePublic,
                        username = viewModel.username,
                        onUsernameChange = { viewModel.username = it },
                        status = viewModel.usernameStatus,
                        enabled = !viewModel.saving,
                        original = chat.username,
                    )
                }

                // Diskuse ke kanálu — skupina pro komentáře pod příspěvky (`EditableChat.canSetDiscussion`).
                if (chat.canSetDiscussion) {
                    DiscussionGroup(
                        linked = viewModel.linkedChat,
                        busy = viewModel.discussionBusy,
                        onClick = {
                            viewModel.loadDiscussionCandidates()
                            showDiscussion = true
                        },
                    )
                }

                // Sada samolepek skupiny — dovoluje ji server, ne práva (`EditableChat.canSetStickerSet`).
                if (chat.canSetStickerSet) {
                    StickerSetGroup(
                        set = viewModel.stickerSet,
                        busy = viewModel.stickerSetBusy,
                        onClick = {
                            viewModel.loadStickerSetCandidates()
                            showStickerSet = true
                        },
                    )
                }

                // Témata zapíná jen vlastník supergroupy (viz `EditableChat.canToggleForum`).
                if (chat.canToggleForum) {
                    GroupedColumn(Modifier.fillMaxWidth()) {
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

                // Pomalý režim, reakce a přepínače — jen co dovolí práva (`groupSettingsOffer`).
                if (settings.any) {
                    GroupSettingsCard(
                        channel = chat.isChannel,
                        settings = settings,
                        onOpenSlowMode = { showSlowMode = true },
                        onOpenReactions = { showReactions = true },
                        onToggle = viewModel::setToggle,
                        onUnrestrictBoosts = viewModel::setUnrestrictBoosts,
                        onSponsoredMessages = viewModel::setSponsoredMessages,
                        onMainTab = viewModel::setMainTab,
                    )
                }

                // Pozvánky: vlastník a správce s právem zvát (`ChatEditRights.canManageInvites`).
                if (chat.rights.canManageInvites) {
                    val pending by viewModel.pendingJoinRequests.collectAsStateWithLifecycle()
                    InvitesGroup(pending = pending, onOpenInviteLinks = onOpenInviteLinks, onOpenJoinRequests = onOpenJoinRequests)
                }

                // Omezení a vyhození: supergroup a kanál, vlastník a správce s právem omezovat.
                chat.moderation?.let { ModerationGroup(it, onOpenRemoved) }

                // Uvítací zprávy pro nové členy: vlastník a správce s právem je spravovat.
                if (chat.rights.canManageWelcomeMessages) {
                    GroupedColumn(Modifier.fillMaxWidth()) {
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            headlineContent = { Text(stringResource(Res.string.welcome_messages_title)) },
                            supportingContent = { Text(stringResource(Res.string.welcome_messages_row_help)) },
                            leadingContent = { Icon(painterResource(Res.drawable.celebration_24dp), contentDescription = null) },
                            modifier = Modifier.clickable(onClick = viewModel::openWelcomeMessages),
                        )
                    }
                }

                // Statistiky: jen kde je server vydá (kanál zhruba od 500 odběratelů, větší skupina).
                if (chat.canGetStatistics) {
                    GroupedColumn(Modifier.fillMaxWidth()) {
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            headlineContent = { Text(stringResource(Res.string.statistics_title)) },
                            supportingContent = { Text(stringResource(Res.string.statistics_row_help)) },
                            leadingContent = { Icon(painterResource(Res.drawable.bar_chart_24dp), contentDescription = null) },
                            modifier = Modifier.clickable(onClick = onOpenStatistics),
                        )
                    }
                }

                // Boosty: správce supergroupy a kanálu (stejná podmínka jako záznam akcí).
                if (chat.canViewEventLog) {
                    GroupedColumn(Modifier.fillMaxWidth()) {
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            headlineContent = { Text(stringResource(Res.string.boosts_title)) },
                            supportingContent = { Text(stringResource(Res.string.boosts_row_help)) },
                            leadingContent = { Icon(painterResource(Res.drawable.rocket_launch_24dp), contentDescription = null) },
                            modifier = Modifier.clickable(onClick = viewModel::openBoosts),
                        )
                    }
                }

                if (chat.canViewEventLog) {
                    GroupedColumn(Modifier.fillMaxWidth()) {
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            headlineContent = { Text(stringResource(Res.string.event_log_row)) },
                            supportingContent = { Text(stringResource(Res.string.event_log_row_help)) },
                            leadingContent = { Icon(painterResource(Res.drawable.history_24dp), contentDescription = null) },
                            modifier = Modifier.clickable(onClick = onOpenEventLog),
                        )
                    }
                }

                // Gigaskupina: jen vlastníkovi, kterému ji Telegram doporučil (`offersBroadcastGroup`).
                if (viewModel.offersBroadcastGroup) {
                    GroupedColumn(Modifier.fillMaxWidth()) {
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            headlineContent = { Text(stringResource(Res.string.broadcast_group_row)) },
                            supportingContent = { Text(stringResource(Res.string.broadcast_group_row_help)) },
                            leadingContent = { Icon(painterResource(Res.drawable.campaign_24dp), contentDescription = null) },
                            modifier = Modifier.clickable(enabled = !viewModel.convertingToBroadcast) { confirmBroadcastGroup = true },
                        )
                    }
                }

                // Nevratné, proto poslední a v chybové barvě, s potvrzením — jako oficiální klienti.
                if (chat.canDeleteForAll) {
                    GroupedColumn(Modifier.fillMaxWidth()) {
                        ListItem(
                            colors = ListItemDefaults.colors(
                                containerColor = Color.Transparent,
                                headlineColor = MaterialTheme.colorScheme.error,
                                leadingIconColor = MaterialTheme.colorScheme.error,
                            ),
                            headlineContent = {
                                Text(
                                    stringResource(
                                        if (chat.isChannel) Res.string.edit_chat_delete_channel else Res.string.edit_chat_delete_group,
                                    ),
                                )
                            },
                            leadingContent = { Icon(painterResource(Res.drawable.delete_24dp), contentDescription = null) },
                            trailingContent = if (viewModel.deleting) {
                                { LoadingIndicator(Modifier.size(32.dp)) }
                            } else {
                                null
                            },
                            modifier = Modifier.clickable(enabled = !viewModel.deleting) { confirmDeleteChat = true },
                        )
                    }
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
        // Nadpis a blok jsou jeden kus, jako skupina v Nastavení — jinak by nadpis visel
        // stejně daleko od svého bloku jako od pole nad ním.
        Text(
            stringResource(Res.string.edit_chat_permissions),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp),
        )
        GroupedColumn(Modifier.fillMaxWidth()) {
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
            SwitchRow(Res.string.edit_chat_perm_send_video_notes, permissions.sendVideoNotes) { value ->
                onChange { it.copy(sendVideoNotes = value) }
            }
            SwitchRow(Res.string.edit_chat_perm_send_stickers, permissions.sendStickers) { value ->
                onChange { it.copy(sendStickers = value) }
            }
            SwitchRow(Res.string.edit_chat_perm_send_polls, permissions.sendPolls) { value ->
                onChange { it.copy(sendPolls = value) }
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

/**
 * Sada samolepek skupiny: blok s řádkem, který otevře výběr sady. Podtitul říká, k čemu sada je,
 * dokud žádná není; jinak počet samolepek.
 */
@Composable
private fun StickerSetGroup(set: StickerSetSummary?, busy: Boolean, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(Res.string.edit_chat_sticker_set),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp),
        )
        GroupedColumn(Modifier.fillMaxWidth()) {
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = { Text(set?.title ?: stringResource(Res.string.edit_chat_sticker_set_none)) },
                supportingContent = {
                    Text(
                        if (set != null && set.size > 0) {
                            pluralStringResource(Res.plurals.stickers_count, set.size, set.size)
                        } else {
                            stringResource(Res.string.edit_chat_sticker_set_off)
                        },
                    )
                },
                leadingContent = { Icon(painterResource(Res.drawable.sticker_24dp), contentDescription = null) },
                trailingContent = if (busy) {
                    { LoadingIndicator(Modifier.size(32.dp)) }
                } else {
                    null
                },
                modifier = Modifier.clickable(enabled = !busy, onClick = onClick),
            )
        }
    }
}

/**
 * Diskuse ke kanálu: jeden blok s řádkem, který otevře výběr skupiny. Podtitul říká, co platí
 * teď — připojená skupina, nebo že komentáře vypnuté.
 */
@Composable
private fun DiscussionGroup(linked: LinkedChat?, busy: Boolean, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(Res.string.edit_chat_discussion),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp),
        )
        GroupedColumn(Modifier.fillMaxWidth()) {
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = {
                    Text(linked?.title ?: stringResource(Res.string.edit_chat_discussion_none))
                },
                supportingContent = {
                    Text(
                        stringResource(
                            if (linked != null) Res.string.edit_chat_discussion_on else Res.string.edit_chat_discussion_off,
                        ),
                    )
                },
                leadingContent = { Icon(painterResource(Res.drawable.forum_24dp), contentDescription = null) },
                trailingContent = if (busy) {
                    { LoadingIndicator(Modifier.size(32.dp)) }
                } else {
                    null
                },
                modifier = Modifier.clickable(enabled = !busy, onClick = onClick),
            )
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
        GroupedColumn(Modifier.fillMaxWidth()) {
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

/**
 * Omezení a vyhození členové — každý řádek na vlastní seznam, v podtitulu kolik jich je. Kanál
 * má jen vyhozené: nikdo do něj nepíše, takže není co omezovat.
 */
@Composable
private fun ModerationGroup(moderation: MemberModeration, onOpen: (banned: Boolean) -> Unit) {
    @Composable
    fun count(value: Int): String = if (value > 0) {
        pluralStringResource(Res.plurals.edit_chat_people_count, value, value.grouped())
    } else {
        stringResource(Res.string.edit_chat_nobody)
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(Res.string.edit_chat_moderation),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp),
        )
        GroupedColumn(Modifier.fillMaxWidth()) {
            moderation.restrictedCount?.let { restricted ->
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text(stringResource(Res.string.edit_chat_restricted)) },
                    supportingContent = { Text(count(restricted)) },
                    leadingContent = { Icon(painterResource(Res.drawable.lock_24dp), contentDescription = null) },
                    modifier = Modifier.clickable { onOpen(false) },
                )
            }
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = { Text(stringResource(Res.string.edit_chat_removed)) },
                supportingContent = { Text(count(moderation.bannedCount)) },
                leadingContent = { Icon(painterResource(Res.drawable.person_remove_24dp), contentDescription = null) },
                modifier = Modifier.clickable { onOpen(true) },
            )
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
        trailingContent = { IconSwitch(checked = checked, onCheckedChange = null) },
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = { on ->
            haptics.toggle(on)
            onChange(on)
        }),
    )
}

private const val TITLE_MAX_LENGTH = 128
private const val DESCRIPTION_MAX_LENGTH = 255
private val AVATAR_SIZE = 96.dp
