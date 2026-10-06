package eu.tintera.plume.features.profile

import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.files.opensInViewer
import eu.tintera.plume.resources.share_24dp
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
import eu.tintera.plume.resources.common_cancel
import eu.tintera.plume.resources.common_delete
import eu.tintera.plume.resources.chats_leave_body
import eu.tintera.plume.resources.chats_leave_title
import eu.tintera.plume.resources.chats_delete_body
import eu.tintera.plume.resources.chats_delete_title
import eu.tintera.plume.resources.chats_action_leave
import eu.tintera.plume.resources.chats_action_delete
import eu.tintera.plume.resources.profile_leave_group
import eu.tintera.plume.resources.profile_leave_channel
import eu.tintera.plume.resources.profile_block_bot_body
import eu.tintera.plume.resources.profile_block_body
import eu.tintera.plume.resources.profile_block_title
import eu.tintera.plume.resources.profile_unblock
import eu.tintera.plume.resources.profile_block
import eu.tintera.plume.resources.profile_copy_link
import eu.tintera.plume.resources.profile_more
import eu.tintera.plume.resources.logout_24dp
import eu.tintera.plume.resources.delete_24dp
import eu.tintera.plume.resources.flag_24dp
import eu.tintera.plume.resources.chats_action_report
import eu.tintera.plume.resources.chats_delete_report
import eu.tintera.plume.core.telegram.SpamActions
import eu.tintera.plume.features.chats.ChatDeleteSheet
import eu.tintera.plume.features.chats.SpamCleanup
import eu.tintera.plume.features.report.ReportHost
import eu.tintera.plume.resources.edit_24dp
import eu.tintera.plume.resources.profile_edit
import eu.tintera.plume.resources.block_24dp
import eu.tintera.plume.resources.more_vert_24dp
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.ButtonDefaults
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
import eu.tintera.plume.resources.add_members_title
import eu.tintera.plume.resources.add_subscribers_title
import eu.tintera.plume.resources.person_add_24dp
import eu.tintera.plume.resources.profile_open_app
import eu.tintera.plume.resources.apps_24dp
import eu.tintera.plume.resources.arrow_back_24dp
import eu.tintera.plume.resources.common_back
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
import eu.tintera.plume.resources.profile_username_value
import eu.tintera.plume.resources.search_24dp
import eu.tintera.plume.resources.auto_delete_title
import eu.tintera.plume.resources.auto_delete_off
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.getString
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
    /** Chat je smazaný nebo opuštěný — zavřít profil i konverzaci. */
    onChatGone: () -> Unit,
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
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Návrat z úpravy chatu (název, popis, veřejný odkaz): profil se tu skládá znovu, ale
    // ViewModel přežil a jinak by ukazoval, co přečetl při otevření.
    LaunchedEffect(Unit) { viewModel.refreshOnReturn() }
    val snackbarHostState = remember { SnackbarHostState() }
    ReportHost(viewModel.reportFlow, snackbarHostState)
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
    val currentOnChatGone by rememberUpdatedState(onChatGone)
    LaunchedEffect(state.chatGone) {
        if (state.chatGone) currentOnChatGone()
    }
    val currentOnMemberRights by rememberUpdatedState(onOpenMemberRights)
    LaunchedEffect(state.openMemberRights) {
        val target = state.openMemberRights ?: return@LaunchedEffect
        viewModel.consumeOpenMemberRights()
        currentOnMemberRights(target.userId, target.admin)
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
                    IconButton(onClick = onBack) {
                        Icon(painterResource(Res.drawable.arrow_back_24dp), contentDescription = stringResource(Res.string.common_back))
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
                            spam = state.spam?.copy(isBlocked = profile.isBlocked),
                            onSetBlocked = viewModel::setBlocked,
                            onReport = viewModel::report,
                            onDeleteOrLeave = viewModel::deleteOrLeave,
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
                ProfileActions(
                    muted = state.muted,
                    onToggleMute = viewModel::toggleMute,
                    onSearch = onSearch,
                    onOpenApp = onOpenMainWebApp.takeIf { profile.hasMainWebApp },
                )
                val notifications = state.notifications
                val showsAutoDelete = profile.canSetAutoDelete || profile.autoDeleteTime > 0
                if (profile.usernames.isNotEmpty() || profile.phone != null || profile.about.isNotBlank() ||
                    notifications != null || showsAutoDelete || profile.linkedChat != null
                ) {
                    ProfileInfo(
                        profile,
                        notifications = notifications,
                        onLinkClick = viewModel::openLink,
                        onOpenLinkedChat = viewModel::openLinkedChat,
                        onOpenNotifications = viewModel::openNotificationSheet,
                        onOpenAutoDelete = viewModel::openAutoDeleteSheet.takeIf { profile.canSetAutoDelete },
                    )
                }
            }
        }

        if (state.autoDeleteSheet) {
            AutoDeleteSheet(
                current = profile.autoDeleteTime,
                onSelect = viewModel::setAutoDelete,
                onDismiss = viewModel::closeAutoDeleteSheet,
            )
        }

        val notifications = state.notifications
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
            )
        }

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
            )
        }
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
                TabContent(tab = tab, state = state, viewModel = viewModel, contentPadding = contentPadding)
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
    Row(
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .selectableGroup(),
    ) {
        tabs.forEachIndexed { index, tab ->
            val checked = index == selected
            val weight by animateFloatAsState(
                targetValue = if (checked && tabs.size > 1) SELECTED_TAB_WEIGHT else 1f,
                animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                label = "profile-tab-weight",
            )
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
                modifier = Modifier.weight(weight).semantics { role = Role.Tab },
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
    }
}

@Composable
private fun TabContent(
    tab: ProfileTab,
    state: ChatProfileState,
    viewModel: ChatProfileViewModel,
    contentPadding: PaddingValues,
) {
    when (tab) {
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
            }
        }
    }
}

/** Nápověda v poli hledání záložky; média hledání nemají (viz [offersSharedSearch]). */
private fun SharedMediaKind.searchPlaceholder(): StringResource = when (this) {
    SharedMediaKind.Media, SharedMediaKind.Files -> Res.string.profile_search_files
    SharedMediaKind.Music -> Res.string.profile_search_music
    SharedMediaKind.Links -> Res.string.profile_search_links
}

/** Klíč stránky pageru; podle indexu by se stránkám po změně záložek rozsypal scroll. */
private val ProfileTab.key: String
    get() = when (this) {
        ProfileTab.Members -> "members"
        is ProfileTab.Shared -> kind.name
    }

/** Ikona vybrané záložky; vyplněná varianta tam, kde ji Material Symbols má. */
private fun ProfileTab.icon(): DrawableResource = when (this) {
    ProfileTab.Members -> Res.drawable.group_filled_24dp
    is ProfileTab.Shared -> when (kind) {
        SharedMediaKind.Media -> Res.drawable.perm_media_filled_24dp
        SharedMediaKind.Files -> Res.drawable.description_filled_24dp
        SharedMediaKind.Music -> Res.drawable.music_note_24dp
        SharedMediaKind.Links -> Res.drawable.link_24dp
    }
}

/** O kolik je vybraná záložka širší než ostatní — přesně tolik, aby se vešla ikona. */
private const val SELECTED_TAB_WEIGHT = 1.4f

private fun ProfileTab.label(): StringResource = when (this) {
    ProfileTab.Members -> Res.string.profile_tab_members
    is ProfileTab.Shared -> when (kind) {
        SharedMediaKind.Media -> Res.string.profile_tab_media
        SharedMediaKind.Files -> Res.string.profile_tab_files
        SharedMediaKind.Music -> Res.string.profile_tab_music
        SharedMediaKind.Links -> Res.string.profile_tab_links
    }
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
                style = MaterialTheme.typography.headlineMedium,
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
    onLinkClick: (String) -> Unit,
    onOpenLinkedChat: () -> Unit,
    onOpenNotifications: () -> Unit,
    /** `null` = mazání měnit nesmí; řádek se pak jen ukáže, když je zapnuté. */
    onOpenAutoDelete: (() -> Unit)?,
) {
    val rows = buildList<@Composable (Shape) -> Unit> {
        profile.usernames.firstOrNull()?.let { username ->
            add { shape ->
                InfoRow(label = stringResource(Res.string.profile_username), shape = shape) {
                    Text(stringResource(Res.string.profile_username_value, username), style = MaterialTheme.typography.bodyLarge)
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
private fun InfoRow(label: String, shape: Shape, onClick: (() -> Unit)? = null, value: @Composable () -> Unit) {
    ListItem(
        headlineContent = value,
        supportingContent = { Text(label) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        modifier = Modifier.clip(shape).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    )
}

private val HEADER_AVATAR_SIZE = 112.dp

/**
 * Méně častá akce nad chatem za ⋮: odkaz, blokování, smazání / odchod.
 *
 * Nahoře v liště, ne jako další dlaždice: dlaždice (Ztlumit, Hledat) jsou to, kvůli čemu se do
 * profilu chodí; tohle se dělá jednou za čas a dvě z toho jsou nevratné. Nabídka (`ExpressiveMenu`) tu
 * nevadí — past s odstupem od okraje (viz „Vzhled konverzace") je jen u spodku obrazovky.
 * Nevratné akce mají potvrzení s konkrétním následkem, stejné texty jako v seznamu chatů.
 */
@Composable
private fun ProfileMenu(
    profile: ChatProfile,
    onCopyLink: () -> Unit,
    onEdit: () -> Unit,
    onShareContact: () -> Unit,
    onAddMembers: () -> Unit,
    /** Co jde nahlásit a zablokovat; `null`, dokud to nedorazí. */
    spam: SpamActions?,
    onSetBlocked: (Boolean) -> Unit,
    onReport: () -> Unit,
    onDeleteOrLeave: (SpamCleanup) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var confirmBlock by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val canBlock = profile.userId != null
    val canDeleteOrLeave = profile.isPrivate || profile.isMember
    // Úprava se nabízí, jen když je aspoň jedno z práv — co uvnitř ukáže, rozhoduje obrazovka.
    val canEdit = profile.editRights.any
    val canReport = spam?.canReport == true
    // Kontakt se posílá s číslem; kdo ho skrývá (nebo bot, ten žádné nemá), poslat nejde.
    val canShareContact = profile.kind == ProfileKind.User && profile.userId != null && !profile.phone.isNullOrBlank()
    if (
        profile.link == null && !canBlock && !canDeleteOrLeave && !canEdit && !canReport && !canShareContact &&
        !profile.canAddMembers
    ) {
        return
    }

    Box {
        IconButton(onClick = { expanded = true }) {
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
                    // Nahlášení se ptá na důvod (průvodce), proto „…". Jen u spamového action
                    // baru ho server vezme rovnou jako spam, a pak to popisek řekne.
                    if (canReport) {
                        MenuAction(
                            stringResource(
                                if (spam?.reportsSpamDirectly == true) Res.string.chats_delete_report else Res.string.chats_action_report,
                            ),
                            Res.drawable.flag_24dp,
                            onClick = onReport,
                        )
                    } else {
                        null
                    },
                ),
                // Nevratné zvlášť, ve vlastní skupině a chybovou barvou.
                listOfNotNull(
                    if (canDeleteOrLeave) {
                        MenuAction(
                            stringResource(
                                when (profile.kind) {
                                    ProfileKind.Channel -> Res.string.profile_leave_channel
                                    ProfileKind.Group -> Res.string.profile_leave_group
                                    else -> Res.string.chats_action_delete
                                },
                            ),
                            if (profile.isPrivate) Res.drawable.delete_24dp else Res.drawable.logout_24dp,
                            destructive = true,
                            onClick = { confirmDelete = true },
                        )
                    } else {
                        null
                    },
                ),
            ),
        )
    }

    if (confirmBlock) {
        AlertDialog(
            onDismissRequest = { confirmBlock = false },
            title = { Text(stringResource(Res.string.profile_block_title, profile.title)) },
            text = {
                Text(stringResource(if (profile.kind == ProfileKind.Bot) Res.string.profile_block_bot_body else Res.string.profile_block_body))
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmBlock = false
                    onSetBlocked(true)
                }) { Text(stringResource(Res.string.profile_block), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmBlock = false }) { Text(stringResource(Res.string.common_cancel)) } },
        )
    }
    /*
     * Smazání je sheet s podmenu, ne dialog — tentýž jako v nabídce nad chatem v seznamu, i se
     * zablokováním a nahlášením. Dvě podoby téhož kroku by se rozešly.
     */
    if (confirmDelete) {
        ChatDeleteSheet(
            isPrivate = profile.isPrivate,
            title = profile.title,
            spam = spam,
            onConfirm = { cleanup ->
                confirmDelete = false
                onDeleteOrLeave(cleanup)
            },
            onDismiss = { confirmDelete = false },
        )
    }
}
