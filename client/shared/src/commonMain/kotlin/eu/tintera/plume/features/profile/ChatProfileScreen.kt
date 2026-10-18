package eu.tintera.plume.features.profile

import eu.tintera.plume.resources.cake_24dp
import eu.tintera.plume.core.ui.ConfirmDialog
import eu.tintera.plume.core.ui.MediumButtonIcon
import eu.tintera.plume.core.ui.MediumButton
import eu.tintera.plume.resources.birthdate_suggest_confirm
import eu.tintera.plume.resources.birthdate_suggest
import eu.tintera.plume.features.settings.BirthdatePickerDialog
import eu.tintera.plume.resources.profile_tab_voice
import eu.tintera.plume.resources.mic_24dp
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.Layout
import androidx.compose.foundation.horizontalScroll
import eu.tintera.plume.resources.profile_tab_similar_channels
import eu.tintera.plume.resources.profile_tab_common_groups
import eu.tintera.plume.resources.profile_tab_video_notes
import eu.tintera.plume.resources.profile_tab_gifs
import eu.tintera.plume.resources.campaign_24dp
import eu.tintera.plume.resources.gif_box_24dp
import eu.tintera.plume.resources.profile_personal_channel
import eu.tintera.plume.resources.profile_personal_channel_subscribers
import eu.tintera.plume.core.telegram.Birthdate
import eu.tintera.plume.resources.add_photo_alternate_24dp
import eu.tintera.plume.resources.photo_suggest
import eu.tintera.plume.core.ui.PhotoCropOverlay
import eu.tintera.plume.core.ui.rememberAttachmentPicker
import eu.tintera.plume.core.ui.label
import eu.tintera.plume.resources.birthdate_age
import eu.tintera.plume.resources.birthdate_row
import eu.tintera.plume.resources.birthdate_with_age
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import androidx.compose.material3.IconButtonDefaults
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.files.opensInViewer
import eu.tintera.plume.core.ui.readString
import eu.tintera.plume.resources.profile_bookmarks_count
import eu.tintera.plume.resources.profile_bookmarks
import eu.tintera.plume.resources.share_24dp
import eu.tintera.plume.resources.contact_add
import eu.tintera.plume.resources.contact_edit
import eu.tintera.plume.resources.contact_note
import eu.tintera.plume.resources.person_remove_24dp
import eu.tintera.plume.resources.profile_remove_contact
import eu.tintera.plume.resources.profile_remove_contact_body
import eu.tintera.plume.resources.profile_remove_contact_title
import eu.tintera.plume.resources.profile_share_contact
import eu.tintera.plume.resources.profile_tab_music
import eu.tintera.plume.resources.profile_search_files
import eu.tintera.plume.resources.profile_search_links
import eu.tintera.plume.resources.profile_search_music
import eu.tintera.plume.resources.music_note_24dp
import eu.tintera.plume.resources.verification_fake_warning
import eu.tintera.plume.resources.verification_scam_warning
import eu.tintera.plume.resources.verification_verified
import eu.tintera.plume.core.telegram.Verification
import eu.tintera.plume.resources.verification_by_bot
import eu.tintera.plume.resources.verification_by_bot_named
import eu.tintera.plume.core.telegram.BotVerificationNote
import eu.tintera.plume.core.ui.resolveNow
import eu.tintera.plume.core.ui.durationText
import eu.tintera.plume.core.ui.VerificationBadge
import eu.tintera.plume.core.ui.EmojiStatusBadge
import androidx.compose.ui.platform.LocalHapticFeedback
import eu.tintera.plume.core.ui.MenuAction
import eu.tintera.plume.core.ui.ExpressiveMenu
import eu.tintera.plume.core.ui.toggle
import eu.tintera.plume.resources.common_delete
import eu.tintera.plume.resources.chats_leave_body
import eu.tintera.plume.resources.chats_leave_title
import eu.tintera.plume.resources.chats_delete_body
import eu.tintera.plume.resources.chats_delete_title
import eu.tintera.plume.resources.chats_action_leave
import eu.tintera.plume.resources.profile_block_bot_body
import eu.tintera.plume.resources.profile_block_body
import eu.tintera.plume.resources.profile_block_title
import eu.tintera.plume.resources.profile_unblock
import eu.tintera.plume.resources.profile_block
import eu.tintera.plume.resources.profile_copy_link
import eu.tintera.plume.resources.profile_more
import eu.tintera.plume.resources.edit_24dp
import eu.tintera.plume.resources.profile_edit
import eu.tintera.plume.resources.block_24dp
import eu.tintera.plume.resources.more_vert_24dp
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.clickable
import eu.tintera.plume.resources.profile_notif_title
import eu.tintera.plume.core.telegram.ChatNotificationPrefs
import eu.tintera.plume.core.telegram.ChatKind
import eu.tintera.plume.core.ui.avatarShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.background
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import eu.tintera.plume.core.telegram.SharedMediaKind
import eu.tintera.plume.resources.profile_tab_files
import eu.tintera.plume.resources.profile_tab_links
import eu.tintera.plume.resources.profile_tab_media
import eu.tintera.plume.resources.profile_tab_members
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import eu.tintera.plume.core.ui.confirm
import eu.tintera.plume.resources.description_filled_24dp
import eu.tintera.plume.resources.group_filled_24dp
import eu.tintera.plume.resources.link_24dp
import eu.tintera.plume.resources.perm_media_filled_24dp
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.telegram.ChatProfile
import eu.tintera.plume.core.telegram.ProfileKind
import eu.tintera.plume.core.ui.Avatar
import eu.tintera.plume.core.ui.GROUP_ITEM_SPACING
import eu.tintera.plume.core.ui.groupItemShape
import eu.tintera.plume.core.ui.grouped
import eu.tintera.plume.core.ui.RichText
import eu.tintera.plume.core.ui.chatAvatarKey
import eu.tintera.plume.core.ui.chatTitleKey
import eu.tintera.plume.core.ui.sharedNavElement
import eu.tintera.plume.core.ui.sharedNavText
import eu.tintera.plume.core.ui.describe
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.secret_chat_key_title
import eu.tintera.plume.resources.secret_chat_key_compare
import eu.tintera.plume.resources.lock_24dp
import eu.tintera.plume.resources.secret_chat_start
import eu.tintera.plume.resources.call_start
import eu.tintera.plume.resources.call_24dp
import eu.tintera.plume.resources.call_start_video
import eu.tintera.plume.resources.videocam_24dp
import eu.tintera.plume.features.calls.rememberStartCall
import eu.tintera.plume.features.calls.rememberVideoCallsSupported
import eu.tintera.plume.resources.chat_color_default
import eu.tintera.plume.resources.chat_color_title
import androidx.compose.foundation.shape.CircleShape
import eu.tintera.plume.core.settings.ThemeColor
import eu.tintera.plume.features.settings.label
import eu.tintera.plume.resources.add_members_title
import eu.tintera.plume.resources.add_subscribers_title
import eu.tintera.plume.resources.person_add_24dp
import eu.tintera.plume.resources.profile_open_app
import eu.tintera.plume.resources.apps_24dp
import eu.tintera.plume.resources.channel_direct_message
import eu.tintera.plume.resources.direct_topics_subtitle
import eu.tintera.plume.resources.mail_24dp
import eu.tintera.plume.resources.arrow_back_24dp
import eu.tintera.plume.resources.common_back
import eu.tintera.plume.resources.common_close
import eu.tintera.plume.resources.close_24dp
import eu.tintera.plume.resources.invite_members
import eu.tintera.plume.resources.invite_subscribers
import eu.tintera.plume.resources.notifications_24dp
import eu.tintera.plume.resources.notifications_off_24dp
import eu.tintera.plume.resources.profile_bio
import eu.tintera.plume.resources.profile_bot
import eu.tintera.plume.resources.profile_photos_open
import eu.tintera.plume.resources.profile_description
import eu.tintera.plume.resources.profile_info
import eu.tintera.plume.resources.profile_linked_channel
import eu.tintera.plume.resources.profile_linked_discussion
import eu.tintera.plume.resources.profile_mute
import eu.tintera.plume.resources.profile_phone
import eu.tintera.plume.resources.profile_search
import eu.tintera.plume.resources.profile_unmute
import eu.tintera.plume.resources.profile_username
import eu.tintera.plume.resources.profile_username_more
import eu.tintera.plume.resources.profile_username_value
import eu.tintera.plume.resources.search_24dp
import eu.tintera.plume.resources.auto_delete_title
import eu.tintera.plume.resources.auto_delete_off
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Profil chatu: velká fotka a jméno, řada akcí, údaje a u skupiny členové.
 *
 * - **Avatar a název přiletí z hlavičky konverzace** — tytéž sdílené klíče (`chatAvatarKey`,
 *   `chatTitleKey`) jako ze seznamu chatů do konverzace.
 * - **Údaje a členové jsou skupiny jako v Nastavení** (`SettingsGroup`): nadpis a karta s řádky.
 *   Aplikace tak má pro „seznam vlastností" jeden vzhled.
 * - **Akce jsou tónované dlaždice** s ikonou nad popiskem — velký cíl, a popisek řekne, co se stane
 *   (ztlumit / zapnout zvuk), ne jen stav.
 * - **Hledat zavře profil a otevře hledání v konverzaci** ([onSearch]); hledání patří konverzaci,
 *   profil by ho jen zdvojil.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ChatProfileScreen(
    viewModel: ChatProfileViewModel,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onOpenChat: (chatId: Long) -> Unit,
    /** Fotka nebo video ze záložky Média: otevře se prohlížeč, stejně jako z konverzace. */
    onOpenMedia: (messageId: Long, articleMedia: Int) -> Unit,
    /** Ťuknutí na velkou fotku: historie profilových fotek v prohlížeči. */
    onOpenPhotos: () -> Unit,
    /** Úprava skupiny; nabídne se, jen když na ni uživatel má právo. */
    onEdit: () -> Unit,
    /** ⋮ → Sdílet kontakt: výběr chatů, do kterých kontakt poslat. */
    onShareContact: (userId: Long) -> Unit = {},
    /** ⋮ → Přidat členy / odběratele: výběr lidí. */
    onAddMembers: () -> Unit = {},
    /** Práva správce ([admin] `true`), nebo omezení jednoho člena. */
    onOpenMemberRights: (userId: Long, admin: Boolean) -> Unit,
    /** Hlavní mini aplikace bota; dlaždice „Otevřít" se nabízí jen botovi, který ji má. */
    onOpenMainWebApp: () -> Unit = {},
    /** Záložka: zavřít profil a skočit v konverzaci na zprávu. */
    onOpenMessage: (messageId: Long) -> Unit = {},
    /**
     * ✕ místo šipky zpět: profil stojí jako panel vedle konverzace, takže se zavírá, ne vrací.
     * Obrazovky otevřené z profilu (úprava, práva člena) šipku zpět mají — vedou zpátky do něj.
     */
    closeIcon: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val bookmarks by viewModel.chatBookmarks.collectAsStateWithLifecycle()
    var showBookmarks by rememberSaveable { mutableStateOf(false) }
    val chatColor by viewModel.chatColor.collectAsStateWithLifecycle()
    var showChatColor by rememberSaveable { mutableStateOf(false) }
    var showSecretKey by rememberSaveable { mutableStateOf(false) }
    // Zvuk z Telegramu pro tenhle chat: tóny účtu a zápis má ViewModel zvuků z Nastavení.
    val sounds = rememberChatSounds(viewModel.chatId)
    val tones by sounds.tones.collectAsStateWithLifecycle()
    var showSound by rememberSaveable { mutableStateOf(false) }
    // Ukládání do galerie pro tenhle chat — vlastní ViewModel, ať profil nenese další závislost.
    val autosaveViewModel = rememberChatAutosaveViewModel(viewModel.chatId)
    val autosaveState = autosaveViewModel?.state?.collectAsStateWithLifecycle()?.value
    var showAutosave by rememberSaveable { mutableStateOf(false) }
    // Návrat z úpravy chatu (název, popis, veřejný odkaz): profil se tu skládá znovu, ale
    // ViewModel přežil a jinak by ukazoval, co přečetl při otevření.
    LaunchedEffect(Unit) { viewModel.refreshOnReturn() }
    val snackbarHostState = remember { SnackbarHostState() }
    val currentOnOpenChat by rememberUpdatedState(onOpenChat)

    LaunchedEffect(state.openChat) {
        val target = state.openChat ?: return@LaunchedEffect
        viewModel.consumeOpenChat()
        currentOnOpenChat(target)
    }
    val currentOnOpenMedia by rememberUpdatedState(onOpenMedia)
    LaunchedEffect(state.openMedia) {
        val target = state.openMedia ?: return@LaunchedEffect
        viewModel.consumeOpenMedia()
        currentOnOpenMedia(target.messageId, target.articleMedia)
    }
    val currentOnMemberRights by rememberUpdatedState(onOpenMemberRights)
    LaunchedEffect(state.openMemberRights) {
        val target = state.openMemberRights ?: return@LaunchedEffect
        viewModel.consumeOpenMemberRights()
        currentOnMemberRights(target.userId, target.admin)
    }

    state.mediaCalendar?.let { calendar ->
        MediaCalendarSheet(
            calendar = calendar,
            onPick = viewModel::pickCalendarDay,
            onLoadMore = viewModel::loadMoreCalendar,
            onDismiss = viewModel::closeMediaCalendar,
        )
    }

    state.memberSheet?.let { member ->
        MemberActionsSheet(
            member = member,
            onMessage = {
                viewModel.closeMemberActions()
                viewModel.openMember(member.userId)
            },
            onEditAdminRights = { viewModel.openMemberRights(admin = true) },
            onDismissAdmin = viewModel::dismissAdmin,
            onEditRestrictions = { viewModel.openMemberRights(admin = false) },
            onUnrestrict = viewModel::unrestrictMember,
            onEditTag = viewModel::editMemberTag,
            onBan = viewModel::confirmBan,
            onTransferOwnership = viewModel::startOwnershipTransfer,
            onDismiss = viewModel::closeMemberActions,
        )
    }
    state.memberBan?.let { member ->
        MemberBanDialog(member = member, onConfirm = viewModel::banMember, onDismiss = viewModel::cancelBan)
    }
    state.memberTag?.let { member ->
        MemberTagDialog(member = member, onConfirm = viewModel::setMemberTag, onDismiss = viewModel::cancelMemberTag)
    }
    state.memberTransfer?.let { member ->
        TransferOwnershipDialog(
            member = member,
            busy = state.transferBusy,
            onConfirm = viewModel::transferOwnership,
            onDismiss = viewModel::cancelOwnershipTransfer,
        )
    }
    // Snackbar MIMO tělo efektu: `consumeError` mění klíč efektu a zrušil by ho dřív, než se
    // hláška ukáže — tak to bylo, a profil žádnou hlášku (ani chybu) nikdy neukázal.
    val snackbarScope = rememberCoroutineScope()
    LaunchedEffect(state.error) {
        val error = state.error ?: return@LaunchedEffect
        val text = error.resolveNow()
        viewModel.consumeError()
        snackbarScope.launch { snackbarHostState.showSnackbar(text) }
    }

    // Jeden scroll pro celý profil: čte ho i hlavička obrazovky (název se v ní ukáže, až ten
    // velký odjede pod ni), takže žije tady, ne v obsahu.
    val scrollState = rememberScrollState()
    var titleBottom by remember { mutableIntStateOf(Int.MAX_VALUE) }
    val titleInBar by remember { derivedStateOf { scrollState.value > titleBottom } }
    val photoPicker = rememberAttachmentPicker(onPicked = viewModel::suggestPhotoFrom, onError = viewModel::reportError)
    val personalPhotoPicker = rememberAttachmentPicker(onPicked = viewModel::personalPhotoFrom, onError = viewModel::reportError)
    var editContact by rememberSaveable { mutableStateOf(false) }
    val contact = state.profile?.contact
    if (editContact && contact != null) {
        EditContactSheet(
            contact = contact,
            name = contact.firstName,
            onSave = viewModel::saveContact,
            onSetPhoto = if (viewModel.canSuggestPhoto) personalPhotoPicker::pickMedia else null,
            onRemovePhoto = viewModel::removePersonalPhoto,
            onDismiss = { editContact = false },
        )
    }

    // Box kvůli ořezu navrhované fotky: překryv přes celou obrazovku leží nad profilem.
    Box(Modifier.fillMaxSize()) {
        Scaffold(
            modifier = modifier,
            contentWindowInsets = ScaffoldDefaults.contentWindowInsets
                .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        AnimatedVisibility(
                            visible = titleInBar && state.profile != null,
                            enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()) +
                                slideInVertically(MaterialTheme.motionScheme.fastSpatialSpec()) { it / 2 },
                            exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
                                slideOutVertically(MaterialTheme.motionScheme.fastSpatialSpec()) { it / 2 },
                        ) {
                            Text(state.profile?.title.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                            if (closeIcon) {
                                Icon(painterResource(Res.drawable.close_24dp), contentDescription = stringResource(Res.string.common_close))
                            } else {
                                Icon(painterResource(Res.drawable.arrow_back_24dp), contentDescription = stringResource(Res.string.common_back))
                            }
                        }
                    },
                    actions = {
                        state.profile?.let { profile ->
                            ProfileMenu(
                                profile = profile,
                                onCopyLink = viewModel::copyLink,
                                onEdit = onEdit,
                                onShareContact = { profile.userId?.let(onShareContact) },
                                onAddMembers = onAddMembers,
                                onSetBlocked = viewModel::setBlocked,
                                onSetContact = viewModel::setContact,
                                onStartSecretChat = viewModel::startSecretChat,
                                onSuggestBirthdate = viewModel::suggestBirthdate,
                                onSuggestPhoto = if (viewModel.canSuggestPhoto) photoPicker::pickMedia else null,
                                onEditContact = { editContact = true },
                                onCall = rememberStartCall(profile.userId),
                            )
                        }
                    },
                )
            },
        ) { padding ->
            val profile = state.profile
            if (profile == null) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    if (state.loading) LoadingIndicator()
                }
                return@Scaffold
            }

            // Spodek (navigační lišta) jde do obsahu: poslední položka se nad ni doroluje.
            val bottom = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom).asPaddingValues().calculateBottomPadding()
            val header: @Composable () -> Unit = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 20.dp),
                ) {
                    ProfileHeader(
                        state = state,
                        profile = profile,
                        scroll = { scrollState.value },
                        onTitlePlaced = { titleBottom = it },
                        onOpenPhotos = onOpenPhotos,
                    )
                    if (state.offerContact && profile.kind == ProfileKind.User && profile.userId != null && !profile.isContact) {
                        AddContactButton(onClick = { viewModel.setContact(true) })
                    }
                    ProfileActions(
                        muted = state.muted,
                        onToggleMute = viewModel::toggleMute,
                        onSearch = onSearch,
                        onOpenApp = onOpenMainWebApp.takeIf { profile.hasMainWebApp },
                        directMessages = profile.directMessagesChatId?.let { profile.directMessagesAdmin },
                        onOpenDirectMessages = viewModel::openDirectMessages,
                    )
                    val notifications = state.notifications
                    val showsAutoDelete = profile.canSetAutoDelete || profile.autoDeleteTime > 0
                    val chatAutosave = autosaveChatType(profile.kind, profile.isSecretChat)
                        ?.let { type -> autosaveState?.settings?.chatAutosave(profile.chatId, type) }
                    if (profile.usernames.isNotEmpty() || profile.phone != null || profile.birthdate != null || profile.about.isNotBlank() ||
                        profile.personalChannel != null || profile.contact?.note?.isNotBlank() == true ||
                        notifications != null || showsAutoDelete || profile.linkedChat != null || bookmarks.isNotEmpty() ||
                        chatAutosave != null ||
                        profile.secretChatKey != null ||
                        viewModel.supportsChatColor
                    ) {
                        ProfileInfo(
                            profile,
                            notifications = notifications,
                            bookmarkCount = bookmarks.size,
                            onOpenBookmarks = { showBookmarks = true },
                            onLinkClick = viewModel::openLink,
                            onOpenLinkedChat = viewModel::openLinkedChat,
                            onOpenPersonalChannel = viewModel::openPersonalChannel,
                            onOpenNotifications = viewModel::openNotificationSheet,
                            autosave = chatAutosave,
                            onOpenAutosave = { showAutosave = true },
                            onOpenAutoDelete = viewModel::openAutoDeleteSheet.takeIf { profile.canSetAutoDelete },
                            onOpenSecretKey = { showSecretKey = true },
                            chatColor = chatColor,
                            onOpenChatColor = { showChatColor = true }.takeIf { viewModel.supportsChatColor },
                        )
                    }
                    // Hudba patří k člověku jako bio, ale je to seznam, ne údaj — vlastní blok
                    // pod údaji, před záložkami.
                    state.profileAudios?.takeIf { it.isNotEmpty() }?.let { audios ->
                        val playback by viewModel.playback.collectAsStateWithLifecycle()
                        ProfileMusic(
                            audios = audios,
                            chatId = profile.chatId,
                            playback = playback,
                            onToggle = viewModel::toggleProfileAudio,
                            // Vlastní hudba se spravuje v úpravě profilu; vlastní profil se tu neotevírá.
                            onRemove = null,
                        )
                    }
                }
            }

            val autosaveType = autosaveChatType(profile.kind, profile.isSecretChat)
            autosaveViewModel?.let {
                ChatAutosaveHost(
                    viewModel = it,
                    chatId = profile.chatId,
                    type = autosaveType,
                    open = showAutosave,
                    onClose = { showAutosave = false },
                    snackbarHostState = snackbarHostState,
                )
            }

            val secretKey = profile.secretChatKey
            if (showSecretKey && secretKey != null) {
                SecretChatKeySheet(key = secretKey, partnerName = profile.title, onDismiss = { showSecretKey = false })
            }

            if (state.autoDeleteSheet) {
                AutoDeleteSheet(
                    current = profile.autoDeleteTime,
                    secret = profile.isSecretChat,
                    onSelect = viewModel::setAutoDelete,
                    onDismiss = viewModel::closeAutoDeleteSheet,
                )
            }

            if (showChatColor) {
                ChatColorSheet(
                    current = chatColor,
                    onSelect = viewModel::setChatColor,
                    onDismiss = { showChatColor = false },
                )
            }

            val notifications = state.notifications
            if (showBookmarks && bookmarks.isNotEmpty()) {
                BookmarksSheet(
                    bookmarks = bookmarks,
                    onOpen = { bookmark ->
                        showBookmarks = false
                        onOpenMessage(bookmark.messageId)
                    },
                    onRemove = { viewModel.removeBookmark(it.messageId) },
                    onDismiss = { showBookmarks = false },
                )
            }
            if (state.notificationSheet && notifications != null) {
                ChatNotificationSheet(
                    prefs = notifications,
                    supportsConversationSettings = viewModel.supportsConversationSettings,
                    onMute = viewModel::mute,
                    onUnmute = viewModel::unmute,
                    onOption = viewModel::setNotificationOption,
                    onOpenConversationSettings = viewModel::openConversationSettings,
                    onReset = viewModel::resetNotifications,
                    onDismiss = viewModel::closeNotificationSheet,
                    soundLabel = chatSoundLabel(notifications, tones),
                    onPickSound = {
                        // Dva sheety přes sebe ne: upozornění se zavře, výběr zvuku otevře.
                        viewModel.closeNotificationSheet()
                        showSound = true
                    },
                )
            }
            ChatSoundHost(
                viewModel = sounds,
                chatId = profile.chatId,
                prefs = notifications,
                open = showSound,
                onClose = { showSound = false },
                snackbarHostState = snackbarHostState,
            )

            val tabs = state.tabs
            if (tabs.isEmpty()) {
                // Chat bez sdíleného obsahu a bez seznamu členů: žádné záložky, jen údaje.
                // Prázdná záložka by slibovala něco, co v chatu není.
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(top = padding.calculateTopPadding(), bottom = 24.dp + bottom),
                ) {
                    header()
                }
            } else {
                ProfileTabs(
                    state = state,
                    tabs = tabs,
                    viewModel = viewModel,
                    scrollState = scrollState,
                    header = header,
                    topPadding = padding.calculateTopPadding(),
                    bottomPadding = 24.dp + bottom,
                    onOpenMessage = onOpenMessage,
                )
            }
        }
        PhotoCropOverlay(
            path = viewModel.suggestCropping,
            onCancel = viewModel::cancelSuggestCrop,
            onConfirm = viewModel::confirmCrop,
            video = viewModel.suggestCroppingVideo,
        )
    }
}

/**
 * Hlavička, pod ní lepící záložky a pod nimi stránky — celé to je JEDNA scrollovatelná plocha.
 *
 * **Profil kolem záložek NENÍ `LazyColumn`.** Obsah záložky je sám svislý seznam a dva svislé
 * scrolly do sebe Compose nezměří, když vnitřní dostane nekonečnou výšku. Proto je vnější sloupec
 * `verticalScroll` a pager v něm má PEVNOU výšku (plocha bez záložek): spodek pageru dosedne na
 * spodek obrazovky přesně ve chvíli, kdy hlavička odjede, a záložky tím zůstanou přilepené nahoře.
 *
 * **Hlavička se musí dát táhnout.** Dřív byla nakreslená přes stránky a posouval ji jen
 * `NestedScrollConnection` ze seznamu pod ní — tah prstem přes fotku nebo údaje nedělal nic
 * a u krátké záložky nebylo čím hlavičku sbalit. Teď ji nese vnější scroll.
 *
 * **Pořadí při scrollu dolů obrací [collapseFirst]**: napřed se sbalí hlavička, teprve zbytek
 * dostane seznam. Nahoru to platí samo — vnější scroll dostane jen to, co seznam nespotřeboval,
 * tedy až když dojede na začátek; totéž u flingu.
 */
@Composable
private fun ProfileTabs(
    state: ChatProfileState,
    tabs: List<ProfileTab>,
    viewModel: ChatProfileViewModel,
    scrollState: ScrollState,
    header: @Composable () -> Unit,
    topPadding: Dp,
    bottomPadding: Dp,
    onOpenMessage: (messageId: Long) -> Unit,
) {
    /*
     * Počet stránek i klíč čtou TÝŽ stav. Pager je uvnitř `BoxWithConstraints`
     * (`SubcomposeLayout`), takže počet se zapíše hned v rekompozici, ale klíče se přepočítají
     * až v subkompozici při layoutu — a mezi tím by se nový počet potkal se starým seznamem.
     */
    val currentTabs by rememberUpdatedState(tabs)
    val pagerState = rememberPagerState { currentTabs.size }
    val density = LocalDensity.current
    var tabsHeight by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val collapseFirst = remember(scrollState) { collapseFirst(scrollState) }

    // Stránky začínají pod záložkami a mají vzduch od okrajů, jako skupiny v seznamu chatů.
    val contentPadding = PaddingValues(
        start = 16.dp,
        end = 16.dp,
        top = 8.dp,
        bottom = bottomPadding,
    )

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val pageHeight = (maxHeight - topPadding - with(density) { tabsHeight.toDp() }).coerceAtLeast(0.dp)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(top = topPadding),
        ) {
            header()
            ProfileTabSelector(
                tabs = currentTabs,
                selected = pagerState.currentPage,
                onSelect = { scope.launch { pagerState.animateScrollToPage(it) } },
                modifier = Modifier.onSizeChanged { tabsHeight = it.height },
            )
            HorizontalPager(
                state = pagerState,
                key = { currentTabs.getOrNull(it)?.key ?: it.toString() },
                // Stránky nesahají k sobě; při swipu je mezi bloky vidět mezera.
                pageSpacing = 16.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(pageHeight)
                    .nestedScroll(collapseFirst),
            ) { page ->
                val tab = currentTabs.getOrNull(page) ?: return@HorizontalPager
                // Stránka se načte, až když se poprvé ukáže; pager skládá jen viditelné.
                LaunchedEffect(tab) {
                    if (tab is ProfileTab.Shared) viewModel.loadShared(tab.kind)
                }
                TabContent(
                    tab = tab,
                    state = state,
                    viewModel = viewModel,
                    contentPadding = contentPadding,
                    onOpenMessage = onOpenMessage,
                )
            }
        }
    }
}

/**
 * Scroll dolů (obsah jede nahoru) si napřed vezme vnější plocha, dokud hlavička neodjede.
 *
 * Bez toho by se nejdřív scrolloval seznam v záložce a hlavička by zůstala stát, dokud by seznam
 * nedojel na konec — u dlouhé mřížky médií nikdy.
 */
private fun collapseFirst(outer: ScrollState) = object : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (available.y >= 0f) return Offset.Zero
        val consumed = outer.dispatchRawDelta(-available.y)
        return Offset(0f, -consumed)
    }
}

/**
 * Záložky jako spojená skupina přepínacích tlačítek (M3 Expressive connected button group).
 *
 * Klasický `PrimaryTabRow` s podtržením je vzhled z M2 a v profilu, kde je všechno ostatní
 * zaoblený blok, působil jako cizí prvek. Vybraná záložka je plná `primary` a zakulatí se celá,
 * takže se pozná tvarem i barvou. Pozadí má barvu stránky: přilepené nahoře překrývá nic.
 *
 * **Vybraná záložka si přianimuje ikonu a o ni se rozšíří** (váha 1 → [SELECTED_TAB_WEIGHT]).
 * Se stejnou šířkou všech by se ikona do čtvrtiny řádku vešla jen na úkor popisku („Čle…").
 * Ikona i šířka jedou jednou pružinou (`defaultSpatialSpec`), aby se text nepřeskládal skokem.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ProfileTabSelector(
    tabs: List<ProfileTab>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (tabs.size <= MAX_FITTING_TABS) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
            modifier = modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = TABS_PADDING, vertical = 8.dp)
                .selectableGroup(),
        ) {
            tabs.forEachIndexed { index, tab ->
                val weight by animateFloatAsState(
                    targetValue = if (index == selected && tabs.size > 1) SELECTED_TAB_WEIGHT else 1f,
                    animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                    label = "profile-tab-weight",
                )
                ProfileTabButton(tabs, index, tab, selected, onSelect, Modifier.weight(weight))
            }
        }
        return
    }

    /*
     * Víc než čtyři záložky: každé tlačítko má svoji šířku. Když se vejdou, rozdělí se zbylé
     * místo rovným dílem (řada s mezerou vpravo vypadala nedodělaně); když ne, řada se posouvá
     * do strany a vybraná se po změně — i tahem pageru — posune do viditelné části.
     */
    val rowScroll = rememberScrollState()
    val tabStarts = remember { mutableMapOf<Int, Int>() }
    val margin = with(LocalDensity.current) { TABS_PADDING.roundToPx() }
    LaunchedEffect(selected) {
        tabStarts[selected]?.let { rowScroll.animateScrollTo((it - margin).coerceAtLeast(0)) }
    }
    BoxWithConstraints(modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        val viewport = constraints.maxWidth
        Layout(
            content = {
                tabs.forEachIndexed { index, tab -> ProfileTabButton(tabs, index, tab, selected, onSelect) }
            },
            modifier = Modifier
                .horizontalScroll(rowScroll)
                .padding(horizontal = TABS_PADDING, vertical = 8.dp)
                .selectableGroup(),
        ) { measurables, _ ->
            val gap = ButtonGroupDefaults.ConnectedSpaceBetween.roundToPx()
            val natural = measurables.map { it.maxIntrinsicWidth(Constraints.Infinity) }
            val available = viewport - 2 * TABS_PADDING.roundToPx() - gap * (measurables.size - 1)
            val extra = ((available - natural.sum()) / measurables.size).coerceAtLeast(0)
            val placeables = measurables.mapIndexed { index, measurable ->
                val width = natural[index] + extra
                measurable.measure(Constraints(minWidth = width, maxWidth = width))
            }
            val width = placeables.sumOf { it.width } + gap * (placeables.size - 1)
            layout(width, placeables.maxOf { it.height }) {
                var x = 0
                placeables.forEachIndexed { index, placeable ->
                    tabStarts[index] = x
                    placeable.placeRelative(x, 0)
                    x += placeable.width + gap
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ProfileTabButton(
    tabs: List<ProfileTab>,
    index: Int,
    tab: ProfileTab,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val checked = index == selected
    ToggleButton(
        checked = checked,
        onCheckedChange = { onSelect(index) },
        shapes = when {
            tabs.size == 1 -> ToggleButtonDefaults.shapes()
            index == 0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
            index == tabs.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
        },
        // Čtyři popisky se na telefon vejdou jen s užším vnitřním odsazením.
        contentPadding = PaddingValues(horizontal = 8.dp),
        modifier = modifier.semantics { role = Role.Tab },
    ) {
        AnimatedVisibility(
            visible = checked,
            enter = expandHorizontally(MaterialTheme.motionScheme.defaultSpatialSpec()) +
                scaleIn(MaterialTheme.motionScheme.defaultSpatialSpec()) +
                fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
            exit = shrinkHorizontally(MaterialTheme.motionScheme.fastSpatialSpec()) +
                fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
        ) {
            Icon(
                painterResource(tab.icon()),
                contentDescription = null,
                modifier = Modifier.padding(end = ButtonDefaults.IconSpacing).size(ButtonDefaults.IconSize),
            )
        }
        Text(stringResource(tab.label()), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun TabContent(
    tab: ProfileTab,
    state: ChatProfileState,
    viewModel: ChatProfileViewModel,
    contentPadding: PaddingValues,
    /** GIF a kolečko: zavřít profil a skočit na zprávu, prohlížeč je neumí. */
    onOpenMessage: (messageId: Long) -> Unit,
) {
    when (tab) {
        ProfileTab.CommonGroups -> RelatedChatsList(
            chats = state.commonGroups.orEmpty(),
            contentPadding = contentPadding,
            onOpen = viewModel::openRelatedChat,
        )

        ProfileTab.SimilarChannels -> RelatedChatsList(
            chats = state.similarChannels.orEmpty(),
            contentPadding = contentPadding,
            onOpen = viewModel::openRelatedChat,
        )

        ProfileTab.Members -> ProfileMembersList(
            list = state.members,
            contentPadding = contentPadding,
            onOpen = viewModel::openMember,
            onManage = viewModel::openMemberActions,
            onLoadMore = viewModel::loadMoreMembers,
            hidden = state.profile?.membersHidden == true,
            search = if (state.memberSearchAvailable) {
                MemberSearch(query = state.memberQuery, onQueryChange = viewModel::searchMembers)
            } else {
                null
            },
        )

        is ProfileTab.Shared -> SharedTabFrame(
            search = if (state.sharedSearchAvailable(tab.kind)) {
                SharedSearch(
                    query = state.sharedQueries[tab.kind].orEmpty(),
                    placeholder = stringResource(tab.kind.searchPlaceholder()),
                    onQueryChange = { viewModel.searchShared(tab.kind, it) },
                )
            } else {
                null
            },
            nothingFound = state.shared[tab.kind]?.let { it.query.isNotEmpty() && it.messages.isEmpty() && !it.loading } == true,
            contentPadding = contentPadding,
            onOpenCalendar = viewModel::openMediaCalendar.takeIf {
                tab.kind == SharedMediaKind.Media && offersMediaCalendar(state.counts?.media ?: 0)
            },
        ) {
            val data = state.shared[tab.kind]
            val messages = data?.messages.orEmpty()
            val loading = data?.loading != false
            when (tab.kind) {
                SharedMediaKind.Media -> SharedMediaGrid(
                    messages = messages,
                    loading = loading,
                    contentPadding = contentPadding,
                    onOpen = viewModel::openMedia,
                    onLoadMore = { viewModel.loadMoreShared(tab.kind) },
                    positions = state.mediaPositions,
                    onJump = { target -> viewModel.jumpShared(tab.kind, target.messageId, target.position) },
                    scrollTo = data?.scrollTo,
                    onScrolledTo = { viewModel.consumeSharedScroll(tab.kind) },
                    onLoadNewer = { viewModel.loadNewerShared(tab.kind) },
                    canLoadNewer = data?.canLoadNewer == true,
                )

                SharedMediaKind.Files -> SharedFilesList(
                    messages = messages,
                    loading = loading,
                    downloads = state.documentDownloads,
                    contentPadding = contentPadding,
                    // Obrázek a video, které prohlížeč umí, se otevřou v něm, jako v konverzaci;
                    // ostatní soubory (a co prohlížeč neumí, třeba HEIC na iOS) dostane systém.
                    onOpen = { messageId ->
                        val document = messages.firstOrNull { it.id == messageId }?.media as? MessageMedia.Document
                        if (document != null && opensInViewer(document.fileName, document.mimeType)) {
                            viewModel.openMedia(messageId)
                        } else {
                            viewModel.openDocument(messageId)
                        }
                    },
                    onLoadMore = { viewModel.loadMoreShared(tab.kind) },
                )

                SharedMediaKind.Music -> {
                    val playback by viewModel.playback.collectAsStateWithLifecycle()
                    SharedMusicList(
                        messages = messages,
                        loading = loading,
                        playback = playback,
                        chatId = state.profile?.chatId ?: 0L,
                        contentPadding = contentPadding,
                        onToggle = viewModel::toggleMusic,
                        onLoadMore = { viewModel.loadMoreShared(tab.kind) },
                    )
                }

                SharedMediaKind.Links -> SharedLinksList(
                    messages = messages,
                    loading = loading,
                    contentPadding = contentPadding,
                    onOpen = viewModel::openLink,
                    onLoadMore = { viewModel.loadMoreShared(tab.kind) },
                )

                SharedMediaKind.Voice -> {
                    val playback by viewModel.playback.collectAsStateWithLifecycle()
                    SharedVoiceList(
                        messages = messages,
                        loading = loading,
                        playback = playback,
                        chatId = state.profile?.chatId ?: 0L,
                        contentPadding = contentPadding,
                        onToggle = viewModel::toggleVoice,
                        onLoadMore = { viewModel.loadMoreShared(tab.kind) },
                    )
                }

                SharedMediaKind.Gifs, SharedMediaKind.VideoNotes -> SharedClipsGrid(
                    messages = messages,
                    loading = loading,
                    contentPadding = contentPadding,
                    onOpen = onOpenMessage,
                    onLoadMore = { viewModel.loadMoreShared(tab.kind) },
                )
            }
        }
    }
}

/** Nápověda v poli hledání záložky; média hledání nemají (viz [offersSharedSearch]). */
private fun SharedMediaKind.searchPlaceholder(): StringResource = when (this) {
    SharedMediaKind.Media, SharedMediaKind.Files, SharedMediaKind.Gifs, SharedMediaKind.VideoNotes, SharedMediaKind.Voice ->
        Res.string.profile_search_files
    SharedMediaKind.Music -> Res.string.profile_search_music
    SharedMediaKind.Links -> Res.string.profile_search_links
}

/** Klíč stránky pageru; podle indexu by se stránkám po změně záložek rozsypal scroll. */
private val ProfileTab.key: String
    get() = when (this) {
        ProfileTab.Members -> "members"
        is ProfileTab.Shared -> kind.name
        ProfileTab.CommonGroups -> "common-groups"
        ProfileTab.SimilarChannels -> "similar-channels"
    }

/** Ikona vybrané záložky; vyplněná varianta tam, kde ji Material Symbols má. */
private fun ProfileTab.icon(): DrawableResource = when (this) {
    ProfileTab.Members -> Res.drawable.group_filled_24dp
    is ProfileTab.Shared -> when (kind) {
        SharedMediaKind.Media -> Res.drawable.perm_media_filled_24dp
        SharedMediaKind.Files -> Res.drawable.description_filled_24dp
        SharedMediaKind.Music -> Res.drawable.music_note_24dp
        SharedMediaKind.Links -> Res.drawable.link_24dp
        SharedMediaKind.Gifs -> Res.drawable.gif_box_24dp
        SharedMediaKind.VideoNotes -> Res.drawable.videocam_24dp
        SharedMediaKind.Voice -> Res.drawable.mic_24dp
    }
    ProfileTab.CommonGroups -> Res.drawable.group_filled_24dp
    ProfileTab.SimilarChannels -> Res.drawable.campaign_24dp
}

/** Kolik záložek se vejde do řady na telefon; s víc má každá svou šířku a řada se může posouvat. */
private const val MAX_FITTING_TABS = 4

/** Odsazení řady záložek od kraje, stejné jako u stránek pod ní. */
private val TABS_PADDING = 16.dp

/** O kolik je vybraná záložka širší než ostatní — přesně tolik, aby se vešla ikona. */
private const val SELECTED_TAB_WEIGHT = 1.4f

private fun ProfileTab.label(): StringResource = when (this) {
    ProfileTab.Members -> Res.string.profile_tab_members
    is ProfileTab.Shared -> when (kind) {
        SharedMediaKind.Media -> Res.string.profile_tab_media
        SharedMediaKind.Files -> Res.string.profile_tab_files
        SharedMediaKind.Music -> Res.string.profile_tab_music
        SharedMediaKind.Links -> Res.string.profile_tab_links
        SharedMediaKind.Gifs -> Res.string.profile_tab_gifs
        SharedMediaKind.VideoNotes -> Res.string.profile_tab_video_notes
        SharedMediaKind.Voice -> Res.string.profile_tab_voice
    }
    ProfileTab.CommonGroups -> Res.string.profile_tab_common_groups
    ProfileTab.SimilarChannels -> Res.string.profile_tab_similar_channels
}

@Composable
private fun ProfileHeader(
    state: ChatProfileState,
    profile: ChatProfile,
    /** Posun profilu v px; čte se až při kreslení avataru, scroll nic nerekomponuje. */
    scroll: () -> Int,
    /** Spodek jména v px od vršku hlavičky — odtud výš ho nese lišta. */
    onTitlePlaced: (Int) -> Unit,
    onOpenPhotos: () -> Unit,
) {
    val avatarShape = when (profile.kind) {
        ProfileKind.Group -> ChatKind.Group
        ProfileKind.Channel -> ChatKind.Channel
        ProfileKind.User, ProfileKind.Bot, ProfileKind.SavedMessages -> ChatKind.Private
    }.avatarShape()
    // Bez fotky (iniciály) není co prohlížet; Uložené zprávy profil nemají.
    val hasPhoto = (profile.photoFileId ?: profile.photoThumbnailFileId) != null &&
        profile.kind != ProfileKind.SavedMessages
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Avatar(
            chatId = profile.chatId,
            title = profile.title,
            avatarFileId = profile.photoFileId ?: profile.photoThumbnailFileId,
            size = HEADER_AVATAR_SIZE,
            // Stejný tvar jako v seznamu a hlavičce konverzace, odkud avatar přiletěl.
            shape = avatarShape,
            modifier = Modifier
                .sharedNavElement(chatAvatarKey(profile.chatId))
                .graphicsLayer {
                    /*
                     * Avatar se při sbalování zmenšuje do jména pod sebou a mizí, místo aby odjel
                     * pod lištu useknutý její hranou. Průběh je podíl výšky avataru, o který
                     * profil odjel. Zmenšuje se ke SPODKU a právě o tolik, o kolik odjel: vršek
                     * tím stojí těsně pod lištou a spodek jede se jménem. S menším zmenšením
                     * (zkoušeno 0,5) vršek dál utíkal pod lištu a byl useknutý. Posun (parallax)
                     * ne — avatar by najel na jméno, které jede plnou rychlostí.
                     */
                    val progress = (scroll() / size.height).coerceIn(0f, 1f)
                    val scale = 1f - progress
                    scaleX = scale
                    scaleY = scale
                    alpha = 1f - progress
                    transformOrigin = TransformOrigin(0.5f, 1f)
                }
                // Ořez před klikáním, jinak by vlnka přetekla přes tvar avataru do čtverce.
                .clip(avatarShape)
                .clickable(
                    enabled = hasPhoto,
                    onClickLabel = stringResource(Res.string.profile_photos_open),
                    onClick = onOpenPhotos,
                ),
        )
        // Řádek se jménem a statusem; kde končí, hlásí řádek, ne text — text v něm má pozici
        // vůči řádku, ne vůči hlavičce.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            modifier = Modifier
                .padding(top = 8.dp)
                .onPlaced { onTitlePlaced((it.positionInParent().y + it.size.height).roundToInt()) },
        ) {
            Text(
                text = profile.title,
                style = MaterialTheme.typography.headlineMediumEmphasized,
                textAlign = TextAlign.Center,
                // Váha bez vyplnění: dlouhé jméno se zalomí, status za ním zůstane vidět.
                modifier = Modifier.weight(1f, fill = false).sharedNavText(chatTitleKey(profile.chatId)),
            )
            profile.emojiStatus?.let { EmojiStatusBadge(it, size = PROFILE_EMOJI_STATUS_SIZE) }
        }
        val subtitle = when (profile.kind) {
            ProfileKind.User -> state.presence?.describe()
            ProfileKind.Bot -> stringResource(Res.string.profile_bot)
            ProfileKind.Group -> profile.memberCount?.let { pluralStringResource(Res.plurals.invite_members, it, it.grouped()) }
            ProfileKind.Channel -> profile.memberCount?.let { pluralStringResource(Res.plurals.invite_subscribers, it, it.grouped()) }
            ProfileKind.SavedMessages -> null
        }
        subtitle?.let {
            Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        profile.verification?.let { ProfileVerification(it, profile.botVerification) }
    }
}

/**
 * Ověření v profilu: štítek a u podvodu a falešného účtu i věta, CO to znamená. V seznamu
 * a v hlavičce na vysvětlení není místo; tady ano, a kdo účet zkoumá, je právě tady.
 */
/** Status za velkým jménem profilu — velký jako jeho písmo (`headlineMedium`). */
private val PROFILE_EMOJI_STATUS_SIZE = 28.dp

@Composable
private fun VerifiedLine(badge: Verification, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        VerificationBadge(badge)
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ProfileVerification(verification: Verification, botVerification: BotVerificationNote?) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(top = 8.dp, start = 24.dp, end = 24.dp),
    ) {
        if (verification is Verification.Verified) {
            /*
             * Dva řádky, každý se svou značkou: fajfka Telegramu a značka ověřovacího bota
             * s tím, kdo a proč ověřil. Na jednom řádku by značka od bota stála vedle věty
             * „Ověřeno Telegramem", jako by ji udělil Telegram.
             */
            if (verification.official) {
                VerifiedLine(Verification.Verified(), stringResource(Res.string.verification_verified))
            }
            val bot = verification.botIconEmojiId
            if (bot != null) {
                VerifiedLine(
                    Verification.Verified(official = false, botIconEmojiId = bot),
                    botVerification?.let { it.description ?: stringResource(Res.string.verification_by_bot_named, it.botName) }
                        ?: stringResource(Res.string.verification_by_bot),
                )
            }
        } else {
            VerificationBadge(verification)
            Text(
                stringResource(
                    if (verification == Verification.Scam) Res.string.verification_scam_warning
                    else Res.string.verification_fake_warning,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * „Přidat do kontaktů" jako HLAVNÍ akce profilu otevřeného z kontaktního odkazu nebo QR kódu
 * (`ChatProfileState.offerContact`). Kvůli tomu si lidé kód ukazují; v ⋮ by ho nikdo nehledal.
 * Plné tlačítko přes celou šířku, ne další dlaždice: dlaždice jsou pro to, co se v profilu dělá
 * pořád, a do třetiny šířky by se „Přidat do kontaktů" nevešlo. Po přidání zmizí (snackbar ho
 * potvrdí); odebrat jde dál v ⋮.
 */
@Composable
private fun AddContactButton(onClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    MediumButton(
        onClick = {
            haptics.confirm()
            onClick()
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        MediumButtonIcon(Res.drawable.person_add_24dp)
        Text(stringResource(Res.string.contact_add))
    }
}

@Composable
private fun ProfileActions(
    muted: Boolean?,
    onToggleMute: () -> Unit,
    onSearch: () -> Unit,
    /**
     * Hlavní mini aplikace bota. Je PRVNÍ: u bota s aplikací je to to, kvůli čemu do profilu
     * přišel — oficiální klienti ji mají jako velké tlačítko „Open App".
     */
    onOpenApp: (() -> Unit)?,
    /**
     * Kanál má přímé zprávy: `false` = odběratel („Napsat"), `true` = správce („Přímé zprávy",
     * vlákna po odběratelích); `null` = kanál je nemá. Je první jako aplikace bota — u kanálu
     * je to jediná akce, která vede někam dál.
     */
    directMessages: Boolean? = null,
    onOpenDirectMessages: () -> Unit = {},
) {
    val haptics = LocalHapticFeedback.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        if (onOpenApp != null) {
            ProfileAction(
                icon = Res.drawable.apps_24dp,
                label = stringResource(Res.string.profile_open_app),
                onClick = onOpenApp,
                modifier = Modifier.weight(1f),
            )
        }
        if (directMessages != null) {
            ProfileAction(
                icon = Res.drawable.mail_24dp,
                label = stringResource(if (directMessages) Res.string.direct_topics_subtitle else Res.string.channel_direct_message),
                onClick = onOpenDirectMessages,
                modifier = Modifier.weight(1f),
            )
        }
        if (muted != null) {
            ProfileAction(
                icon = if (muted) Res.drawable.notifications_24dp else Res.drawable.notifications_off_24dp,
                label = stringResource(if (muted) Res.string.profile_unmute else Res.string.profile_mute),
                // Ztlumení je přepínač, i když vypadá jako dlaždice — Hledat vedle něj stav
                // nemá a haptiku proto nedostane.
                onClick = {
                    haptics.toggle(on = !muted)
                    onToggleMute()
                },
                checked = muted,
                modifier = Modifier.weight(1f),
            )
        }
        ProfileAction(
            icon = Res.drawable.search_24dp,
            label = stringResource(Res.string.profile_search),
            onClick = onSearch,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Akce jako tónovaná dlaždice: ikona nad popiskem.
 *
 * - **Stisk dlaždici zhranatí** ([ACTION_PRESSED_CORNER]), jako u tlačítek M3 Expressive —
 *   zpětná vazba tvarem, ne jen vlnkou.
 * - **Přepínač (ztlumení) nese stav tvarem a barvou** ([checked]): zapnutý je celý zakulacený
 *   a v `tertiaryContainer`, stejně jako vybraná záložka pod ním je celá kulatá. Dřív se stav
 *   poznal jen z popisku, který navíc říká opak („Zapnout zvuk" = teď je ztlumeno).
 *   Hledat stav nemá, a proto ho tvarem nenese — tvar jen tam, kde se mění stav.
 * - **Ikona a popisek se vymění přechodem**, ne skokem: změna přijde až s potvrzením serveru,
 *   o chvilku po ťuknutí, a skoková výměna se dala přehlédnout.
 */
@Composable
private fun ProfileAction(
    icon: DrawableResource,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    checked: Boolean? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val corner by animateDpAsState(
        targetValue = when {
            pressed -> ACTION_PRESSED_CORNER
            checked == true -> ACTION_CHECKED_CORNER
            else -> ACTION_CORNER
        },
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "profile-action-corner",
    )
    val container by animateColorAsState(
        if (checked == true) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "profile-action-container",
    )
    val content by animateColorAsState(
        if (checked == true) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "profile-action-content",
    )
    Surface(
        onClick = onClick,
        // Pružina přestřelí; záporné zaoblení `RoundedCornerShape` odmítne.
        shape = RoundedCornerShape(corner.coerceAtLeast(0.dp)),
        color = container,
        contentColor = content,
        interactionSource = interactionSource,
        modifier = modifier,
    ) {
        val enterFade = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
        val enterScale = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
        val exitFade = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
        AnimatedContent(
            targetState = icon to label,
            transitionSpec = {
                (fadeIn(enterFade) + scaleIn(enterScale, initialScale = 0.6f)).togetherWith(fadeOut(exitFade))
            },
            contentAlignment = Alignment.Center,
            label = "profile-action-content",
        ) { (shownIcon, shownLabel) ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
            ) {
                Icon(painterResource(shownIcon), contentDescription = null)
                Text(shownLabel, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private val ACTION_CORNER = 20.dp
private val ACTION_PRESSED_CORNER = 12.dp

/** Víc než polovina výšky dlaždice; `RoundedCornerShape` zaoblení na polovinu stáhne sám. */
private val ACTION_CHECKED_CORNER = 48.dp

/**
 * Údaje jako blok zaoblených řádků — stejný jazyk jako skupiny v seznamu chatů a obsah záložek
 * pod ním. Karta `SettingsGroup` (výplň `surfaceContainerHighest`) byla vedle světlých řádků
 * záložek tmavší plocha a profil z toho měl dva různé vzhledy pro „seznam věcí".
 */
@Composable
private fun ProfileInfo(
    profile: ChatProfile,
    notifications: ChatNotificationPrefs?,
    bookmarkCount: Int,
    onOpenBookmarks: () -> Unit,
    onLinkClick: (String) -> Unit,
    onOpenLinkedChat: () -> Unit,
    onOpenPersonalChannel: () -> Unit,
    onOpenNotifications: () -> Unit,
    /** Ukládání do galerie v tomhle chatu; `null` = řádek se neukazuje (desktop, tajný chat, Uložené). */
    autosave: ChatAutosave?,
    onOpenAutosave: () -> Unit,
    /** `null` = mazání měnit nesmí; řádek se pak jen ukáže, když je zapnuté. */
    onOpenAutoDelete: (() -> Unit)?,
    onOpenSecretKey: () -> Unit,
    /** Barva konverzace; `null` = barvy aplikace. */
    chatColor: ThemeColor?,
    /** `null` = barvu nastavit nejde a řádek se neukazuje. */
    onOpenChatColor: (() -> Unit)?,
) {
    val rows = buildList<@Composable (Shape) -> Unit> {
        profile.usernames.firstOrNull()?.let { username ->
            add { shape ->
                InfoRow(label = stringResource(Res.string.profile_username), shape = shape) {
                    Column {
                        Text(stringResource(Res.string.profile_username_value, username), style = MaterialTheme.typography.bodyLarge)
                        // Další zapnutá jména (sběratelská z Fragmentu) pod hlavním — najdou ho podle všech.
                        val others = profile.usernames.drop(1)
                        if (others.isNotEmpty()) {
                            val joined = others.map { stringResource(Res.string.profile_username_value, it) }.joinToString(", ")
                            Text(
                                stringResource(Res.string.profile_username_more, joined),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        profile.phone?.let { phone ->
            add { shape ->
                InfoRow(label = stringResource(Res.string.profile_phone), shape = shape) {
                    Text(phone, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        profile.birthdate?.let { birthdate ->
            add { shape ->
                InfoRow(label = stringResource(Res.string.birthdate_row), shape = shape) {
                    Text(birthdateWithAge(birthdate), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        if (profile.about.isNotBlank()) {
            val isPerson = profile.kind == ProfileKind.User || profile.kind == ProfileKind.Bot
            add { shape ->
                InfoRow(
                    label = stringResource(if (isPerson) Res.string.profile_bio else Res.string.profile_description),
                    shape = shape,
                ) {
                    RichText(
                        text = profile.about,
                        entities = profile.aboutEntities,
                        style = MaterialTheme.typography.bodyLarge,
                        onLinkClick = onLinkClick,
                        // Bio i popis skupiny můžou nést citaci; řádek stojí na
                        // `surfaceContainerLowest`, takže blok musí být o stupeň výš.
                        quoteBackground = MaterialTheme.colorScheme.surfaceContainerHigh,
                    )
                }
            }
        }
        // Poznámka ke kontaktu (vidí ji jen uživatel) patří k člověku jako bio — hned za ním.
        profile.contact?.takeIf { it.note.isNotBlank() }?.let { contact ->
            add { shape ->
                InfoRow(label = stringResource(Res.string.contact_note), shape = shape) {
                    RichText(
                        text = contact.note,
                        entities = contact.noteEntities,
                        style = MaterialTheme.typography.bodyLarge,
                        onLinkClick = onLinkClick,
                    )
                }
            }
        }
        // Osobní kanál patří k člověku jako bio — hned za ním. Ťuknutí kanál otevře.
        profile.personalChannel?.let { channel ->
            add { shape ->
                InfoRow(
                    label = channel.subscribers?.let { count ->
                        stringResource(
                            Res.string.profile_personal_channel_subscribers,
                            pluralStringResource(Res.plurals.invite_subscribers, count, count.grouped()),
                        )
                    } ?: stringResource(Res.string.profile_personal_channel),
                    shape = shape,
                    onClick = onOpenPersonalChannel,
                    trailing = {
                        Avatar(
                            chatId = channel.chatId,
                            title = channel.title,
                            avatarFileId = channel.photoFileId,
                            size = 40.dp,
                            shape = ChatKind.Channel.avatarShape(),
                        )
                    },
                ) {
                    Text(channel.title, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        // Propojený chat je údaj o chatu jako popis — za ním, před nastaveními. Ťuknutí ho otevře.
        profile.linkedChat?.let { linked ->
            add { shape ->
                InfoRow(
                    label = stringResource(
                        if (profile.kind == ProfileKind.Channel) Res.string.profile_linked_discussion else Res.string.profile_linked_channel,
                    ),
                    shape = shape,
                    onClick = onOpenLinkedChat,
                ) {
                    Text(linked.title, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        // Šifrovací klíč tajného chatu je údaj o chatu — za údaji o člověku, před nastaveními.
        profile.secretChatKey?.let { key ->
            add { shape ->
                InfoRow(
                    label = stringResource(Res.string.secret_chat_key_title),
                    shape = shape,
                    onClick = onOpenSecretKey,
                    trailing = { SecretChatIdenticon(key, size = 40.dp) },
                ) {
                    Text(stringResource(Res.string.secret_chat_key_compare), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        // Automatické mazání je nastavení chatu jako upozornění, takže stojí u nich — před nimi,
        // protože se týká všech v chatu, upozornění jen mě.
        if (onOpenAutoDelete != null || profile.autoDeleteTime > 0) {
            add { shape ->
                InfoRow(
                    label = stringResource(Res.string.auto_delete_title),
                    shape = shape,
                    onClick = onOpenAutoDelete,
                ) {
                    Text(
                        text = profile.autoDeleteTime.takeIf { it > 0 }?.let { durationText(it) }
                            ?: stringResource(Res.string.auto_delete_off),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
        // Záložky jsou moje, ne údaj o chatu — za údaji, před nastaveními. Jen když nějaké jsou:
        // přidávají se v nabídce nad zprávou a prázdný řádek by nic neřekl.
        if (bookmarkCount > 0) {
            add { shape ->
                InfoRow(label = stringResource(Res.string.profile_bookmarks), shape = shape, onClick = onOpenBookmarks) {
                    Text(
                        pluralStringResource(Res.plurals.profile_bookmarks_count, bookmarkCount, bookmarkCount),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
        // Upozornění poslední: údaje o chatu napřed, jeho nastavení až za nimi. Řádek vede do
        // sheetu s dobou ztlumení a volbami; dlaždice Ztlumit nahoře zůstává rychlým přepínačem.
        notifications?.let { prefs ->
            add { shape ->
                InfoRow(
                    label = stringResource(Res.string.profile_notif_title),
                    shape = shape,
                    onClick = onOpenNotifications,
                ) {
                    Text(prefs.summary(), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        // Ukládání do galerie je nastavení účtu pro tenhle chat — vedle upozornění. Ťuknutí
        // výjimku založí, upraví nebo odebere (v Úložišti jde jen upravit a odebrat).
        autosave?.let { current ->
            add { shape ->
                InfoRow(label = chatAutosaveLabel(), shape = shape, onClick = onOpenAutosave) {
                    Text(chatAutosaveText(current), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        // Barva je čistě moje a jen vzhled — až za upozorněními. Puntík nese barvu, jakou chat
        // má: profil sám leží v barvě chatu, takže je to jeho `primary`.
        onOpenChatColor?.let { onClick ->
            add { shape ->
                InfoRow(
                    label = stringResource(Res.string.chat_color_title),
                    shape = shape,
                    onClick = onClick,
                    trailing = chatColor?.let {
                        {
                            Box(Modifier.size(CHAT_COLOR_DOT).background(MaterialTheme.colorScheme.primary, CircleShape))
                        }
                    },
                ) {
                    Text(
                        stringResource(chatColor?.label ?: Res.string.chat_color_default),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(Res.string.profile_info),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(GROUP_ITEM_SPACING)) {
            rows.forEachIndexed { index, row -> row(groupItemShape(index, rows.size)) }
        }
    }
}

/** Údaj: hodnota a pod ní, co to je — jako v kontaktech. */
@Composable
private fun InfoRow(
    label: String,
    shape: Shape,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    value: @Composable () -> Unit,
) {
    ListItem(
        headlineContent = value,
        supportingContent = { Text(label) },
        trailingContent = trailing,
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        modifier = Modifier.clip(shape).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    )
}

private val HEADER_AVATAR_SIZE = 112.dp
private val CHAT_COLOR_DOT = 24.dp

/**
 * Méně častá akce nad člověkem nebo chatem za ⋮: úprava, odkaz, kontakt, hovor, tajný chat,
 * blokování.
 *
 * Nahoře v liště, ne jako další dlaždice: dlaždice (Ztlumit, Hledat) jsou to, kvůli čemu se do
 * profilu chodí; tohle se dělá jednou za čas. Nabídka (`ExpressiveMenu`) tu nevadí — past
 * s odstupem od okraje (viz „Vzhled konverzace") je jen u spodku obrazovky. Zablokování má
 * potvrzení s konkrétním následkem.
 *
 * Nahlášení, smazání / odchod a ukončení tajného chatu jsou v ⋮ konverzace (`ConversationMenu`):
 * týkají se konverzace, ne člověka. Hovor zůstává i tady — profil člena jde otevřít z jeho
 * zprávy ve skupině, bez konverzace s ním.
 */
@Composable
private fun ProfileMenu(
    profile: ChatProfile,
    onCopyLink: () -> Unit,
    onEdit: () -> Unit,
    onShareContact: () -> Unit,
    onAddMembers: () -> Unit,
    onSetBlocked: (Boolean) -> Unit,
    onSetContact: (Boolean) -> Unit,
    onStartSecretChat: () -> Unit,
    /** Navrhnout člověku datum narození (`suggestUserBirthdate`). */
    onSuggestBirthdate: (Birthdate) -> Unit,
    /** Navrhnout člověku profilovou fotku: výběr fotky, pak ořez (`null` = nejde). */
    onSuggestPhoto: (() -> Unit)?,
    /** Upravit kontakt: jméno, poznámka, vlastní fotka. Nabízí se jen u kontaktu. */
    onEditContact: () -> Unit,
    /** Zavolat (`null` = hovory tu nejsou, viz `CallRepository.supported`). */
    onCall: ((video: Boolean) -> Unit)?,
) {
    val videoCalls = rememberVideoCallsSupported()
    var expanded by remember { mutableStateOf(false) }
    var confirmBlock by rememberSaveable { mutableStateOf(false) }
    var confirmRemoveContact by rememberSaveable { mutableStateOf(false) }
    var pickBirthdate by rememberSaveable { mutableStateOf(false) }
    val canBlock = profile.userId != null
    // Úprava se nabízí, jen když je aspoň jedno z práv — co uvnitř ukáže, rozhoduje obrazovka.
    val canEdit = profile.editRights.any
    // Kontakt se posílá s číslem; kdo ho skrývá (nebo bot, ten žádné nemá), poslat nejde.
    val canShareContact = profile.kind == ProfileKind.User && profile.userId != null && !profile.phone.isNullOrBlank()
    // Do kontaktů jde člověk, ne bot a ne vlastní účet (ten je `SavedMessages`).
    val canManageContact = profile.kind == ProfileKind.User && profile.userId != null
    // Tajný chat jde začít s člověkem (ne s botem, ne se sebou — to je `SavedMessages`), a to
    // i z profilu jiného tajného chatu: každý je vázaný na jedno zařízení.
    val canStartSecret = profile.kind == ProfileKind.User && profile.userId != null
    if (
        profile.link == null && !canBlock && !canEdit && !canShareContact &&
        !profile.canAddMembers && !canManageContact && !canStartSecret
    ) {
        return
    }

    Box {
        IconButton(onClick = { expanded = true }, shapes = IconButtonDefaults.shapes()) {
            Icon(painterResource(Res.drawable.more_vert_24dp), contentDescription = stringResource(Res.string.profile_more))
        }
        ExpressiveMenu(
            expanded = expanded,
            onDismiss = { expanded = false },
            groups = listOf(
                listOfNotNull(
                    if (canEdit) {
                        MenuAction(stringResource(Res.string.profile_edit), Res.drawable.edit_24dp, onClick = onEdit)
                    } else {
                        null
                    },
                    if (profile.canAddMembers) {
                        MenuAction(
                            stringResource(
                                if (profile.kind == ProfileKind.Channel) Res.string.add_subscribers_title else Res.string.add_members_title,
                            ),
                            Res.drawable.person_add_24dp,
                            onClick = onAddMembers,
                        )
                    } else {
                        null
                    },
                    profile.link?.let { MenuAction(stringResource(Res.string.profile_copy_link), Res.drawable.link_24dp, onClick = onCopyLink) },
                    if (canShareContact) {
                        MenuAction(stringResource(Res.string.profile_share_contact), Res.drawable.share_24dp, onClick = onShareContact)
                    } else {
                        null
                    },
                    if (canStartSecret && onCall != null) {
                        MenuAction(stringResource(Res.string.call_start), Res.drawable.call_24dp, onClick = { onCall(false) })
                    } else {
                        null
                    },
                    if (canStartSecret && onCall != null && videoCalls) {
                        MenuAction(stringResource(Res.string.call_start_video), Res.drawable.videocam_24dp, onClick = { onCall(true) })
                    } else {
                        null
                    },
                    if (canStartSecret) {
                        MenuAction(stringResource(Res.string.secret_chat_start), Res.drawable.lock_24dp, onClick = onStartSecretChat)
                    } else {
                        null
                    },
                    // Návrh fotky dostane jen člověk; vlastní fotka je v úpravě profilu.
                    if (canStartSecret && onSuggestPhoto != null) {
                        MenuAction(stringResource(Res.string.photo_suggest), Res.drawable.add_photo_alternate_24dp, onClick = onSuggestPhoto)
                    } else {
                        null
                    },
                    // Návrh data narození dostane jen člověk; vlastní datum je v úpravě profilu.
                    if (canStartSecret && profile.birthdate == null) {
                        MenuAction(stringResource(Res.string.birthdate_suggest), Res.drawable.cake_24dp, onClick = { pickBirthdate = true })
                    } else {
                        null
                    },
                    if (canManageContact && profile.contact != null) {
                        MenuAction(stringResource(Res.string.contact_edit), Res.drawable.edit_24dp, onClick = onEditContact)
                    } else {
                        null
                    },
                    if (canManageContact) {
                        if (profile.isContact) {
                            // Odebrání smaže jméno uložené v kontaktech na všech zařízeních — s potvrzením.
                            MenuAction(
                                stringResource(Res.string.profile_remove_contact),
                                Res.drawable.person_remove_24dp,
                                onClick = { confirmRemoveContact = true },
                            )
                        } else {
                            MenuAction(stringResource(Res.string.contact_add), Res.drawable.person_add_24dp, onClick = { onSetContact(true) })
                        }
                    } else {
                        null
                    },
                    if (canBlock) {
                        MenuAction(
                            stringResource(if (profile.isBlocked) Res.string.profile_unblock else Res.string.profile_block),
                            Res.drawable.block_24dp,
                            // Odblokovat je vratné a nic neodřízne — bez potvrzení.
                            onClick = { if (profile.isBlocked) onSetBlocked(false) else confirmBlock = true },
                        )
                    } else {
                        null
                    },
                ),
            ),
        )
    }

    if (confirmBlock) {
        ConfirmDialog(
            title = stringResource(Res.string.profile_block_title, profile.title),
            text = stringResource(if (profile.kind == ProfileKind.Bot) Res.string.profile_block_bot_body else Res.string.profile_block_body),
            confirmLabel = stringResource(Res.string.profile_block),
            onConfirm = {
                confirmBlock = false
                onSetBlocked(true)
            },
            onDismiss = { confirmBlock = false },
            icon = Res.drawable.block_24dp,
            destructive = true,
        )
    }
    if (pickBirthdate) {
        BirthdatePickerDialog(
            initial = null,
            onConfirm = { picked ->
                pickBirthdate = false
                picked?.let(onSuggestBirthdate)
            },
            onDismiss = { pickBirthdate = false },
            confirmLabel = Res.string.birthdate_suggest_confirm,
        )
    }
    if (confirmRemoveContact) {
        ConfirmDialog(
            title = stringResource(Res.string.profile_remove_contact_title, profile.title),
            text = stringResource(Res.string.profile_remove_contact_body),
            confirmLabel = stringResource(Res.string.profile_remove_contact),
            onConfirm = {
                confirmRemoveContact = false
                onSetContact(false)
            },
            onDismiss = { confirmRemoveContact = false },
            icon = Res.drawable.person_remove_24dp,
            destructive = true,
        )
    }
}

/** „12. října 1990 (35 let)", bez roku jen datum. */
@Composable
private fun birthdateWithAge(birthdate: Birthdate): String {
    val today = remember { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date }
    val label = birthdate.label()
    val age = birthdate.ageOn(today) ?: return label
    return stringResource(Res.string.birthdate_with_age, label, pluralStringResource(Res.plurals.birthdate_age, age, age))
}
