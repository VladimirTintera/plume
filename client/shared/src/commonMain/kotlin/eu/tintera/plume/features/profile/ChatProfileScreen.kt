package eu.tintera.plume.features.profile

import eu.tintera.plume.core.ui.MenuAction
import eu.tintera.plume.core.ui.ExpressiveMenu
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
import eu.tintera.plume.core.ui.grouped
import eu.tintera.plume.core.ui.RichText
import eu.tintera.plume.core.ui.chatAvatarKey
import eu.tintera.plume.core.ui.chatTitleKey
import eu.tintera.plume.core.ui.sharedNavElement
import eu.tintera.plume.core.ui.sharedNavText
import eu.tintera.plume.core.ui.describe
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.arrow_back_24dp
import eu.tintera.plume.resources.common_back
import eu.tintera.plume.resources.invite_members
import eu.tintera.plume.resources.invite_subscribers
import eu.tintera.plume.resources.notifications_24dp
import eu.tintera.plume.resources.notifications_off_24dp
import eu.tintera.plume.resources.profile_bio
import eu.tintera.plume.resources.profile_bot
import eu.tintera.plume.resources.profile_description
import eu.tintera.plume.resources.profile_info
import eu.tintera.plume.resources.profile_mute
import eu.tintera.plume.resources.profile_phone
import eu.tintera.plume.resources.profile_search
import eu.tintera.plume.resources.profile_unmute
import eu.tintera.plume.resources.profile_username
import eu.tintera.plume.resources.profile_username_value
import eu.tintera.plume.resources.search_24dp
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
    onOpenMedia: (messageId: Long) -> Unit,
    /** Chat je smazaný nebo opuštěný — zavřít profil i konverzaci. */
    onChatGone: () -> Unit,
    /** Úprava skupiny; nabídne se, jen když na ni uživatel má právo. */
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
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
        currentOnOpenMedia(target)
    }
    val currentOnChatGone by rememberUpdatedState(onChatGone)
    LaunchedEffect(state.chatGone) {
        if (state.chatGone) currentOnChatGone()
    }
    // Snackbar MIMO tělo efektu: `consumeError` mění klíč efektu a zrušil by ho dřív, než se
    // hláška ukáže — tak to bylo, a profil žádnou hlášku (ani chybu) nikdy neukázal.
    val snackbarScope = rememberCoroutineScope()
    LaunchedEffect(state.error) {
        val error = state.error ?: return@LaunchedEffect
        val text = getString(error.resource, *error.args.toTypedArray())
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
                            onSetBlocked = viewModel::setBlocked,
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
                )
                ProfileActions(muted = state.muted, onToggleMute = viewModel::toggleMute, onSearch = onSearch)
                val notifications = state.notifications
                if (profile.usernames.isNotEmpty() || profile.phone != null || profile.about.isNotBlank() || notifications != null) {
                    ProfileInfo(
                        profile,
                        notifications = notifications,
                        onLinkClick = viewModel::openLink,
                        onOpenNotifications = viewModel::openNotificationSheet,
                    )
                }
            }
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
            members = state.members,
            contentPadding = contentPadding,
            onOpen = viewModel::openMember,
        )

        is ProfileTab.Shared -> {
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
                    onOpen = viewModel::openDocument,
                    onLoadMore = { viewModel.loadMoreShared(tab.kind) },
                )

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
) {
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
            shape = when (profile.kind) {
                ProfileKind.Group -> ChatKind.Group
                ProfileKind.Channel -> ChatKind.Channel
                ProfileKind.User, ProfileKind.Bot, ProfileKind.SavedMessages -> ChatKind.Private
            }.avatarShape(),
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
                },
        )
        Text(
            text = profile.title,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(top = 8.dp)
                .sharedNavText(chatTitleKey(profile.chatId))
                .onPlaced { onTitlePlaced((it.positionInParent().y + it.size.height).roundToInt()) },
        )
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
    }
}

@Composable
private fun ProfileActions(muted: Boolean?, onToggleMute: () -> Unit, onSearch: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        if (muted != null) {
            ProfileAction(
                icon = if (muted) Res.drawable.notifications_24dp else Res.drawable.notifications_off_24dp,
                label = stringResource(if (muted) Res.string.profile_unmute else Res.string.profile_mute),
                onClick = onToggleMute,
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
    onOpenNotifications: () -> Unit,
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
    onSetBlocked: (Boolean) -> Unit,
    onDeleteOrLeave: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var confirmBlock by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val canBlock = profile.userId != null
    val canDeleteOrLeave = profile.isPrivate || profile.isMember
    // Úprava se nabízí, jen když je aspoň jedno z práv — co uvnitř ukáže, rozhoduje obrazovka.
    val canEdit = profile.editRights.any
    if (profile.link == null && !canBlock && !canDeleteOrLeave && !canEdit) return

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
                    profile.link?.let { MenuAction(stringResource(Res.string.profile_copy_link), Res.drawable.link_24dp, onClick = onCopyLink) },
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
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(if (profile.isPrivate) Res.string.chats_delete_title else Res.string.chats_leave_title)) },
            text = {
                Text(stringResource(if (profile.isPrivate) Res.string.chats_delete_body else Res.string.chats_leave_body, profile.title))
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDeleteOrLeave()
                }) {
                    Text(
                        stringResource(if (profile.isPrivate) Res.string.common_delete else Res.string.chats_action_leave),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(Res.string.common_cancel)) } },
        )
    }
}
