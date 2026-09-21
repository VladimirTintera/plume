package eu.tintera.plume.features.profile

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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.unit.IntOffset
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import eu.tintera.plume.features.settings.SettingsGroup
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
    LaunchedEffect(state.error) {
        val error = state.error ?: return@LaunchedEffect
        viewModel.consumeError()
        snackbarHostState.showSnackbar(getString(error.resource, *error.args.toTypedArray()))
    }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets
            .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(Res.drawable.arrow_back_24dp), contentDescription = stringResource(Res.string.common_back))
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
                ProfileHeader(state, profile)
                ProfileActions(muted = state.muted, onToggleMute = viewModel::toggleMute, onSearch = onSearch)
                if (profile.usernames.isNotEmpty() || profile.phone != null || profile.about.isNotBlank()) {
                    ProfileInfo(profile, onLinkClick = viewModel::openLink)
                }
            }
        }

        val tabs = state.tabs
        if (tabs.isEmpty()) {
            // Chat bez sdíleného obsahu a bez seznamu členů: žádné záložky, jen údaje.
            // Prázdná záložka by slibovala něco, co v chatu není.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(top = padding.calculateTopPadding(), bottom = 24.dp + bottom),
            ) {
                header()
            }
        } else {
            ProfileTabs(
                state = state,
                tabs = tabs,
                viewModel = viewModel,
                header = header,
                topPadding = padding.calculateTopPadding(),
                bottomPadding = 24.dp + bottom,
            )
        }
    }
}

/**
 * Hlavička, pod ní lepící záložky a pod nimi stránky.
 *
 * **Profil kolem záložek NENÍ `LazyColumn`.** Obsah záložky je sám svislý seznam a dva svislé
 * scrolly do sebe Compose nezměří (`Vertically scrollable component was measured with an infinity
 * maximum height constraints`). Místo toho stránky vyplní celou plochu, hlavička je kreslená nad
 * nimi a posouvá ji `NestedScrollConnection`: při scrollu dolů se nejdřív sbalí hlavička, teprve
 * pak se hýbe seznam; při scrollu nahoru naopak až poté, co seznam dojede na začátek.
 *
 * **Záložky se tím přilepí samy** — jsou ve stejném sloupci pod hlavičkou, takže jakmile se
 * hlavička odsune o svou výšku, zůstanou nahoře. Žádný `stickyHeader` není potřeba.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileTabs(
    state: ChatProfileState,
    tabs: List<ProfileTab>,
    viewModel: ChatProfileViewModel,
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
    var headerHeight by remember { mutableIntStateOf(0) }
    var tabsHeight by remember { mutableIntStateOf(0) }
    val offset = remember { mutableFloatStateOf(0f) }

    // Hlavička se smí schovat celá; záložky zůstávají vidět vždycky.
    val maxOffset = -headerHeight.toFloat()
    val connection = remember(maxOffset) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Scroll dolů: napřed se sbalí hlavička, zbytek dostane seznam.
                val delta = available.y
                if (delta >= 0f) return Offset.Zero
                val target = (offset.floatValue + delta).coerceAtLeast(maxOffset)
                val consumed = target - offset.floatValue
                offset.floatValue = target
                return Offset(0f, consumed)
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                // Scroll nahoru: hlavička se vrací, až když seznam nemá kam dál (zbylo `available`).
                val delta = available.y
                if (delta <= 0f) return Offset.Zero
                val target = (offset.floatValue + delta).coerceAtMost(0f)
                val gained = target - offset.floatValue
                offset.floatValue = target
                return Offset(0f, gained)
            }
        }
    }

    // Obsah stránek začíná pod hlavičkou a záložkami — ty ho překrývají, ne odsouvají.
    val headerDp = with(density) { headerHeight.toDp() }
    val contentPadding = PaddingValues(
        top = topPadding + headerDp + with(density) { tabsHeight.toDp() },
        bottom = bottomPadding,
    )

    BoxWithConstraints(Modifier.fillMaxSize().nestedScroll(connection)) {
        /*
         * Pager jede s hlavičkou a je o její výšku VYŠŠÍ, než je plocha.
         *
         * Dokud se posouvala jen hlavička, zůstal seznam stát na nule i s celým horním
         * `contentPadding` — mezi přilepenými záložkami a obsahem zela mezera o výšku hlavičky.
         * Zmenšovat `contentPadding` podle offsetu by znamenalo přepočet layoutu seznamu
         * v každém snímku scrollu; takhle se jen posune hotová vrstva a spodek pageru dosedne
         * přesně na spodek plochy, jakmile je hlavička schovaná.
         */
        val pageHeight = maxHeight + headerDp
        HorizontalPager(
            state = pagerState,
            key = { currentTabs.getOrNull(it)?.key ?: it.toString() },
            modifier = Modifier
                .fillMaxWidth()
                .height(pageHeight)
                .offset { IntOffset(0, offset.floatValue.roundToInt()) },
        ) { page ->
            val tab = currentTabs.getOrNull(page) ?: return@HorizontalPager
            // Stránka se načte, až když se poprvé ukáže; pager skládá jen viditelné.
            LaunchedEffect(tab) {
                if (tab is ProfileTab.Shared) viewModel.loadShared(tab.kind)
            }
            TabContent(tab = tab, state = state, viewModel = viewModel, contentPadding = contentPadding)
        }

        Column(
            modifier = Modifier
                .offset { IntOffset(0, offset.floatValue.roundToInt()) }
                .padding(top = topPadding)
                // Bez pozadí by obsah stránky prosvítal skrz hlavičku, kterou překrývá.
                .background(MaterialTheme.colorScheme.surface),
        ) {
            Box(Modifier.onSizeChanged { headerHeight = it.height }) { header() }
            PrimaryTabRow(
                selectedTabIndex = pagerState.currentPage,
                modifier = Modifier.onSizeChanged { tabsHeight = it.height },
            ) {
                val scope = rememberCoroutineScope()
                currentTabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = { Text(stringResource(tab.label()), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    )
                }
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

private fun ProfileTab.label(): StringResource = when (this) {
    ProfileTab.Members -> Res.string.profile_tab_members
    is ProfileTab.Shared -> when (kind) {
        SharedMediaKind.Media -> Res.string.profile_tab_media
        SharedMediaKind.Files -> Res.string.profile_tab_files
        SharedMediaKind.Links -> Res.string.profile_tab_links
    }
}

@Composable
private fun ProfileHeader(state: ChatProfileState, profile: ChatProfile) {
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
            modifier = Modifier.sharedNavElement(chatAvatarKey(profile.chatId)),
        )
        Text(
            text = profile.title,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp).sharedNavText(chatTitleKey(profile.chatId)),
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

/** Akce jako tónovaná dlaždice: ikona nad popiskem. */
@Composable
private fun ProfileAction(icon: DrawableResource, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(vertical = 14.dp),
        ) {
            Icon(painterResource(icon), contentDescription = null)
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ProfileInfo(profile: ChatProfile, onLinkClick: (String) -> Unit) {
    SettingsGroup(title = Res.string.profile_info) {
        profile.usernames.firstOrNull()?.let { username ->
            InfoRow(label = stringResource(Res.string.profile_username)) {
                Text(stringResource(Res.string.profile_username_value, username), style = MaterialTheme.typography.bodyLarge)
            }
        }
        profile.phone?.let { phone ->
            InfoRow(label = stringResource(Res.string.profile_phone)) {
                Text(phone, style = MaterialTheme.typography.bodyLarge)
            }
        }
        if (profile.about.isNotBlank()) {
            val isPerson = profile.kind == ProfileKind.User || profile.kind == ProfileKind.Bot
            InfoRow(label = stringResource(if (isPerson) Res.string.profile_bio else Res.string.profile_description)) {
                RichText(
                    text = profile.about,
                    entities = profile.aboutEntities,
                    style = MaterialTheme.typography.bodyLarge,
                    onLinkClick = onLinkClick,
                )
            }
        }
    }
}

/** Údaj: hodnota a pod ní, co to je — jako v kontaktech. */
@Composable
private fun InfoRow(label: String, value: @Composable () -> Unit) {
    ListItem(
        headlineContent = value,
        supportingContent = { Text(label) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

private val HEADER_AVATAR_SIZE = 112.dp
