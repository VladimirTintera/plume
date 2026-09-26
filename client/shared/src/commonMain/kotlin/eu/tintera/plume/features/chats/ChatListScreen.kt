package eu.tintera.plume.features.chats

import androidx.compose.material3.LocalContentColor
import eu.tintera.plume.core.ui.VerificationBadge
import eu.tintera.plume.core.ui.MenuAction
import eu.tintera.plume.core.ui.ExpressiveMenu
import eu.tintera.plume.core.ui.threshold
import eu.tintera.plume.resources.saved_messages
import eu.tintera.plume.resources.bookmark_24dp
import eu.tintera.plume.resources.favorite_filled_24dp
import eu.tintera.plume.resources.alternate_email_24dp
import eu.tintera.plume.resources.chats_unread_reaction
import eu.tintera.plume.resources.chats_unread_mention
import eu.tintera.plume.core.ui.isSavedMessages
import eu.tintera.plume.core.ui.chatTitle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material3.IconButton
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import eu.tintera.plume.core.ui.OnlineDot
import eu.tintera.plume.core.ui.rememberConnectionIndicator
import eu.tintera.plume.core.ui.rememberShownConnection
import eu.tintera.plume.core.telegram.ConnectionState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.abs
import kotlin.math.roundToInt
import eu.tintera.plume.features.devices.UnconfirmedLoginCard
import eu.tintera.plume.core.update.rememberStartUpdate
import eu.tintera.plume.core.telegram.ChatFolder
import eu.tintera.plume.core.telegram.ChatListState
import eu.tintera.plume.features.report.ReportHost
import eu.tintera.plume.core.telegram.ChatSummary
import eu.tintera.plume.core.telegram.Presence
import eu.tintera.plume.core.ui.Avatar
import eu.tintera.plume.core.ui.accentOn
import eu.tintera.plume.core.ui.asRelativeTime
import eu.tintera.plume.core.ui.chatAvatarKey
import eu.tintera.plume.core.ui.chatTitleKey
import eu.tintera.plume.core.ui.label
import eu.tintera.plume.core.ui.resolve
import eu.tintera.plume.core.ui.sharedNavElement
import eu.tintera.plume.core.ui.sharedNavText
import eu.tintera.plume.core.ui.tabularNumbers
import eu.tintera.plume.core.telegram.ArchiveUnread
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.archive_24dp
import eu.tintera.plume.resources.chats_action_archive
import eu.tintera.plume.resources.chats_action_unarchive
import eu.tintera.plume.resources.chats_archive_unread
import eu.tintera.plume.resources.unarchive_24dp
import eu.tintera.plume.resources.chats_archive
import eu.tintera.plume.resources.chats_archived
import eu.tintera.plume.resources.chats_new
import eu.tintera.plume.resources.chats_undo
import eu.tintera.plume.resources.chats_blocked
import eu.tintera.plume.resources.chats_unblocked
import eu.tintera.plume.resources.chats_unarchived
import eu.tintera.plume.resources.chats_draft_prefix
import eu.tintera.plume.resources.chats_empty
import eu.tintera.plume.resources.chats_muted
import eu.tintera.plume.resources.chats_section_pinned
import eu.tintera.plume.resources.common_try_again
import eu.tintera.plume.resources.edit_24dp
import eu.tintera.plume.resources.notifications_off_24dp
import eu.tintera.plume.resources.push_pin_filled_24dp
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import eu.tintera.plume.core.telegram.AccountInfo
import eu.tintera.plume.core.ui.IntroEmptyState
import eu.tintera.plume.core.ui.avatarShape
import eu.tintera.plume.resources.campaign_24dp
import eu.tintera.plume.resources.campaign_filled_24dp
import eu.tintera.plume.resources.chats_account
import eu.tintera.plume.resources.chats_filter_all
import eu.tintera.plume.resources.chats_filter_channels
import eu.tintera.plume.resources.chats_filter_empty
import eu.tintera.plume.resources.chats_filter_empty_title
import eu.tintera.plume.resources.chats_filter_unread_empty
import eu.tintera.plume.resources.chats_filter_unread_empty_title
import eu.tintera.plume.resources.done_all_24dp
import eu.tintera.plume.resources.chats_filter_groups
import eu.tintera.plume.resources.chats_filter_private
import eu.tintera.plume.resources.chats_filter_unread
import eu.tintera.plume.resources.forum_24dp
import eu.tintera.plume.resources.forum_filled_24dp
import eu.tintera.plume.resources.group_24dp
import eu.tintera.plume.resources.group_filled_24dp
import eu.tintera.plume.resources.mail_24dp
import eu.tintera.plume.resources.mail_filled_24dp
import eu.tintera.plume.resources.nav_settings
import eu.tintera.plume.resources.person_24dp
import eu.tintera.plume.resources.person_filled_24dp
import eu.tintera.plume.resources.settings_24dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun ChatListScreen(
    viewModel: ChatsViewModel,
    selectedChatId: Long?,
    onChatClick: (Long) -> Unit,
    /** Nalezená zpráva: otevřít její chat a skočit na ni. */
    onMessageClick: (chatId: Long, messageId: Long) -> Unit,
    /** Dotaz, se kterým otevřít hledání (hashtag z konverzace); po otevření [onSearchRequestHandled]. */
    searchRequest: String?,
    onSearchRequestHandled: () -> Unit,
    onOpenArchive: () -> Unit,
    onNewChat: () -> Unit = {},
    onChatArchived: ((ArchiveChange) -> Unit)? = null,
    /** Menu účtu → Nastavení. Na telefonu jediný vstup, spodní lišta není. */
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val search by viewModel.search.state.collectAsStateWithLifecycle()
    val connection by viewModel.connection.collectAsStateWithLifecycle()
    val shownConnection = rememberShownConnection(connection)
    val savedMessagesChatId by viewModel.savedMessagesChatId.collectAsStateWithLifecycle()
    val updateCard by viewModel.updateCard.collectAsStateWithLifecycle()
    val unconfirmedLogin by viewModel.unconfirmedLogin.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val chatList by viewModel.state.collectAsStateWithLifecycle()
    val readyChats = (chatList as? ChatListState.Ready)?.chats
    val filters = remember(readyChats, filter, savedMessagesChatId) {
        availableFilters(readyChats, filter, savedMessagesChatId)
    }
    val account by viewModel.account.collectAsStateWithLifecycle()
    val archiveUnread by viewModel.archiveUnread.collectAsStateWithLifecycle()
    val startUpdate = rememberStartUpdate(viewModel.appUpdates)
    // Hledání při scrollu dolů odjede a při scrollu nahoru se vrátí.
    val scrollBehavior = SearchBarDefaults.enterAlwaysSearchBarScrollBehavior()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val listState = rememberLazyListState()
    // Toolbar s filtry: rozbalený nahoře a při scrollu nahoru, při scrollu dolů z něj zbude FAB.
    var toolbarExpanded by remember { mutableStateOf(true) }
    var previousIndex by remember { mutableIntStateOf(0) }
    var previousOffset by remember { mutableIntStateOf(0) }

    LaunchedEffect(listState) {
        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }.collect { (index, offset) ->
            if (index == 0 && offset == 0) {
                toolbarExpanded = true
            } else if (index < previousIndex || (index == previousIndex && offset < previousOffset)) {
                // Scroll nahoru -> rozbalit
                toolbarExpanded = true
            } else if (index > previousIndex || (index == previousIndex && offset > previousOffset)) {
                // Scroll dolů -> sbalit
                toolbarExpanded = false
            }
            previousIndex = index
            previousOffset = offset
        }
    }

    ChatActionsHost(viewModel, snackbarHostState, onArchived = onChatArchived)

    /*
     * Chat vybraný v hledání, na který má seznam dojet. Ukládá se (`rememberSaveable`): na
     * telefonu konverzace seznam překryje a ten se posune, až se na něj uživatel vrátí.
     */
    var scrollToChat by rememberSaveable { mutableStateOf<Long?>(null) }

    // Obchod se ptá při otevření seznamu i při návratu do popředí — tak se po restartu připomene
    // i stažená, ale nenainstalovaná aktualizace.
    LifecycleResumeEffect(Unit) {
        viewModel.checkForUpdate()
        onPauseOrDispose { }
    }

    /*
     * FAB a snackbar nejsou ve slotech Scaffoldu: FAB je součástí plovoucího toolbaru dole
     * a snackbar musí stát NAD ním — Scaffold by FAB dal do rohu a snackbar pod toolbar.
     */
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            ChatSearchBar(
                connection = shownConnection?.label(),
                search = search,
                savedMessagesChatId = savedMessagesChatId,
                scrollBehavior = scrollBehavior,
                onQueryChange = viewModel.search::setQuery,
                onExpandedChange = viewModel.search::setSuggestionsWanted,
                onChatClick = { chatId ->
                    viewModel.search.recordFound(chatId)
                    scrollToChat = chatId
                    onChatClick(chatId)
                },
                onMessageClick = { chatId, messageId ->
                    scrollToChat = chatId
                    onMessageClick(chatId, messageId)
                },
                onLoadMoreMessages = viewModel.search::loadMoreMessages,
                onClearRecent = viewModel.search::clearRecent,
                onFilterChange = viewModel.search::setFilter,
                request = searchRequest,
                onRequestHandled = onSearchRequestHandled,
                trailing = {
                    AccountMenu(
                        account = account,
                        archiveUnread = archiveUnread,
                        connection = shownConnection,
                        onSavedMessages = { viewModel.openSavedMessages(onChatClick) },
                        onArchive = onOpenArchive,
                        onSettings = onOpenSettings,
                    )
                },
            )
        },
    ) { scaffoldPadding ->
        // Poslední chat se musí dát odscrollovat nad toolbar, jinak by pod ním zůstal schovaný.
        val padding = PaddingValues(
            top = scaffoldPadding.calculateTopPadding(),
            bottom = scaffoldPadding.calculateBottomPadding() + TOOLBAR_CLEARANCE,
        )
        Box(Modifier.fillMaxSize()) {
            ChatListContent(
                viewModel = viewModel,
                selectedChatId = selectedChatId,
                onChatClick = onChatClick,
                padding = padding,
                scrollToChat = scrollToChat,
                onScrolledToChat = { scrollToChat = null },
                listState = listState,
                filter = filter,
                // Položky hlavičky: nové přihlášení a karta s aktualizací.
                headerItemCount = listOfNotNull(unconfirmedLogin, updateCard).size,
                empty = {
                    Text(
                        stringResource(Res.string.chats_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    )
                },
            ) {
                // Nad chaty, jako vlastní bloky se stejnou mezerou jako mezi skupinami. Přihlášení
                // napřed: týká se bezpečnosti účtu, aktualizace počká.
                unconfirmedLogin?.let { login ->
                    item(key = UNCONFIRMED_LOGIN_KEY, contentType = UNCONFIRMED_LOGIN_KEY) {
                        UnconfirmedLoginCard(
                            device = login,
                            enabled = true,
                            onConfirm = { viewModel.confirmLogin(login.sessionId) },
                            onTerminate = { viewModel.terminateLogin(login.sessionId) },
                            modifier = Modifier.animateItem().padding(bottom = GROUP_SPACING),
                        )
                    }
                }
                updateCard?.let { update ->
                    item(key = UPDATE_CARD_KEY, contentType = UPDATE_CARD_KEY) {
                        UpdateCard(
                            state = update,
                            onUpdate = startUpdate,
                            onDismiss = viewModel::dismissUpdate,
                            onRestart = viewModel::completeUpdate,
                            modifier = Modifier.animateItem().padding(bottom = GROUP_SPACING),
                        )
                    }
                }
            }

            /*
             * Závoj pod stavovou lištou. Odjetý bar ji odkryje a chaty by pod ní jezdily bez
             * pozadí, přes hodiny a ikony. Leží nad seznamem, ale pod barem — Scaffold kreslí
             * `topBar` přes obsah.
             *
             * Přechod do průhledna, ne plná barva: s plným pásem končily chaty o výšku lišty
             * pod horním okrajem ostrou hranou, jako useknuté. Takhle jedou až nahoru a jen
             * se pod lištou vytrácejí; u horního okraje, kde jsou ikony, je závoj skoro plný.
             */
            val surface = MaterialTheme.colorScheme.surface
            Box(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsTopHeight(WindowInsets.statusBars)
                    .background(
                        Brush.verticalGradient(
                            0f to surface,
                            0.6f to surface.copy(alpha = 0.85f),
                            1f to surface.copy(alpha = 0f),
                        ),
                    ),
            )

            /*
             * Nad navigační lištou A nad klávesnicí. Scaffold dává do paddingu jen systémové
             * lišty, takže vysunutá klávesnice (hledání, dvoupanel s psaním vpravo) toolbar
             * i snackbar překryla. Padding ze Scaffoldu se zkonzumuje a `imePadding` pak přidá
             * jen to, o co je klávesnice vyšší než lišta — jinak by se obojí sečetlo.
             */
            val bottomPadding = PaddingValues(bottom = scaffoldPadding.calculateBottomPadding())
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottomPadding)
                    .consumeWindowInsets(bottomPadding)
                    .imePadding(),
            ) {
                SnackbarHost(snackbarHostState)
                ChatListToolbar(
                    expanded = toolbarExpanded,
                    filters = filters,
                    filter = filter,
                    onFilterChange = { selected ->
                        viewModel.setFilter(selected)
                        // Nový filtr = jiný seznam; začít od jeho vršku, ne uprostřed.
                        scope.launch { listState.scrollToItem(0) }
                    },
                    onNewChat = onNewChat,
                    modifier = Modifier.padding(bottom = FloatingToolbarDefaults.ScreenOffset),
                )
            }
        }
    }
}

/**
 * Plovoucí toolbar dole: filtry seznamu a FAB „Nová zpráva" (M3 Expressive
 * `HorizontalFloatingToolbar` s FAB). Nahradil spodní navigační lištu — ta u dvou sekcí jen
 * zabírala místo, kdežto tady má spodek obrazovky na dosah palce skutečnou práci.
 *
 * Při scrollu dolů se toolbar sbalí a zbude jen FAB ([expanded]), takže nezakrývá chaty; nahoře
 * a při scrollu nahoru je zase celý.
 *
 * Filtry jsou ikony bez popisků (pět popisků by se na telefon nevešlo); název nese tooltip po
 * podržení a `contentDescription`. Vybraný filtr je `secondaryContainer` — výběr je navigační
 * stav, stejná role jako vybraná položka navigace v M3 — a tvarem se mění z kruhu na čtverec
 * (`toggleableShapes`), takže je poznat i bez barvy.
 */
@Composable
private fun ChatListToolbar(
    expanded: Boolean,
    /** Nabízené filtry ([availableFilters]); ostatní se schovají i s místem. */
    filters: List<ChatFilter>,
    filter: ChatFilter,
    onFilterChange: (ChatFilter) -> Unit,
    onNewChat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HorizontalFloatingToolbar(
        expanded = expanded,
        floatingActionButton = {
            FloatingToolbarDefaults.VibrantFloatingActionButton(onClick = onNewChat) {
                Icon(
                    painterResource(Res.drawable.edit_24dp),
                    contentDescription = stringResource(Res.string.chats_new),
                )
            }
        },
        colors = FloatingToolbarDefaults.standardFloatingToolbarColors(),
        modifier = modifier,
    ) {
        ChatFilter.entries.forEach { entry ->
            /*
             * Filtr, který nemá co ukázat, se schová i se svým místem (`shrinkHorizontally`),
             * takže se toolbar plynule zúží. Pořadí ostatních zůstává, ikony se jen dosunou.
             */
            AnimatedVisibility(
                visible = entry in filters,
                enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                    expandHorizontally(MaterialTheme.motionScheme.defaultSpatialSpec()),
                exit = fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                    shrinkHorizontally(MaterialTheme.motionScheme.defaultSpatialSpec()),
            ) {
                val label = stringResource(entry.label)
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                    tooltip = { PlainTooltip { Text(label) } },
                    state = rememberTooltipState(),
                ) {
                    val selected = entry == filter
                    IconToggleButton(
                        checked = selected,
                        onCheckedChange = { onFilterChange(entry) },
                        shapes = IconButtonDefaults.toggleableShapes(),
                        colors = IconButtonDefaults.iconToggleButtonColors(
                            checkedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            checkedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                    ) {
                        Icon(
                            painterResource(if (selected) entry.selectedIcon else entry.icon),
                            contentDescription = label,
                        )
                    }
                }
            }
        }
    }
}

private val ChatFilter.label: StringResource
    get() = when (this) {
        ChatFilter.All -> Res.string.chats_filter_all
        ChatFilter.Unread -> Res.string.chats_filter_unread
        ChatFilter.Private -> Res.string.chats_filter_private
        ChatFilter.Groups -> Res.string.chats_filter_groups
        ChatFilter.Channels -> Res.string.chats_filter_channels
    }

/** Nevyplněná varianta; vybraný filtr má vyplněnou ([selectedIcon]) — M3 konvence jako v navigaci. */
private val ChatFilter.icon: DrawableResource
    get() = when (this) {
        ChatFilter.All -> Res.drawable.forum_24dp
        ChatFilter.Unread -> Res.drawable.mail_24dp
        ChatFilter.Private -> Res.drawable.person_24dp
        ChatFilter.Groups -> Res.drawable.group_24dp
        ChatFilter.Channels -> Res.drawable.campaign_24dp
    }

private val ChatFilter.selectedIcon: DrawableResource
    get() = when (this) {
        ChatFilter.All -> Res.drawable.forum_filled_24dp
        ChatFilter.Unread -> Res.drawable.mail_filled_24dp
        ChatFilter.Private -> Res.drawable.person_filled_24dp
        ChatFilter.Groups -> Res.drawable.group_filled_24dp
        ChatFilter.Channels -> Res.drawable.campaign_filled_24dp
    }

/**
 * Fotka účtu na konci vyhledávací pilulky a pod ní menu: Uložené zprávy, Archiv, Nastavení.
 *
 * Vzor Gmailu a Google Messages. Nahradil ⋮ vedle pilulky, a hlavně spodní lištu: Nastavení
 * jsou na telefonu jen tady. Pilulka tím dostala celou šířku.
 *
 * Než se účet načte (nebo když se načíst nepodaří), je místo fotky ikona člověka — tlačítko
 * se nesmí objevit až později, menu by do té doby nebylo kde otevřít.
 *
 * [archiveUnread] se ukazuje dvakrát, protože samotné menu je zavřené: číslem u položky Archiv
 * a tečkou na tlačítku menu, jinak by se o nepřečtených v archivu nikdo nedozvěděl. Tečka je
 * ale jen od NEZTLUMENÝCH chatů — do archivu se ztlumené chaty ukládají, takže tečka od nich
 * by byla signál, který nikdy nezhasne. Číslo u položky počítá všechny; kdo menu otevřel, chce
 * vědět, kolik toho v archivu čeká.
 *
 * Bez spojení ([connection]) fotka naroste, zvlní se a po jejím okraji vyjede vlnitý
 * prstenec ([ConnectionIndicator]); při připojování se obojí točí. Text stavu je v nápovědě pilulky; čtečce obrazovky
 * ho říká i popis tlačítka, protože nápovědu čte jen u pole.
 */
@Composable
private fun AccountMenu(
    account: AccountInfo?,
    archiveUnread: ArchiveUnread,
    connection: ConnectionState?,
    onSavedMessages: () -> Unit,
    onArchive: () -> Unit,
    onSettings: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val accountLabel = stringResource(Res.string.chats_account)
    val connectionLabel = connection?.label()?.let { stringResource(it) }
    val label = if (connectionLabel == null) accountLabel else "$accountLabel, $connectionLabel"
    val indicator = rememberConnectionIndicator(connection, ACCOUNT_AVATAR_SIZE, ACCOUNT_AVATAR_GROWN)
    // Odznak je jen číslo — čtečka obrazovky potřebuje větu, ne „3".
    val unreadLabel = pluralStringResource(Res.plurals.chats_archive_unread, archiveUnread.chats, archiveUnread.chats)
    /*
     * Ztlumený archiv nese odznak v neutrální barvě: chaty tam nepřečtené jsou, ale nic si
     * nežádá pozornost — obarvit je akcentem by bylo falešné volání.
     */
    val accented = archiveUnread.unmutedChats > 0
    val archiveBadge: (@Composable () -> Unit)? = if (archiveUnread.chats == 0) null else {
        {
            Badge(
                containerColor = if (accented) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                },
                contentColor = if (accented) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            ) {
                Text(
                    text = archiveUnread.chats.toString(),
                    style = LocalTextStyle.current.tabularNumbers(),
                    modifier = Modifier.semantics { contentDescription = unreadLabel },
                )
            }
        }
    }
    /*
     * Vrstvy odspodu: fotka v tlačítku, prstenec po jejím okraji, tečka archivu. Prstenec
     * i tečka jsou mimo `IconButton`, který obsah ořezává na 40dp kruh; tečka nad prstencem,
     * jinak by ji přeškrtl. Tečka zůstává v rohu NEZVĚTŠENÉ fotky, takže sedí na prstenci
     * jako odznak.
     */
    Box(contentAlignment = Alignment.Center) {
        IconButton(
            onClick = { open = true },
            modifier = Modifier.semantics { contentDescription = label },
        ) {
            if (account != null) {
                Avatar(
                    chatId = account.id,
                    title = account.displayName,
                    avatarFileId = account.photoFileId,
                    size = ACCOUNT_AVATAR_SIZE,
                    modifier = with(indicator) { Modifier.avatar() },
                )
            } else {
                Icon(painterResource(Res.drawable.person_24dp), contentDescription = null)
            }
        }
        with(indicator) { Spacer(Modifier.size(ACCOUNT_AVATAR_SIZE).ring()) }
        if (archiveUnread.unmutedChats > 0) {
            Box(Modifier.size(ACCOUNT_AVATAR_SIZE)) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        // Obroučka barvou pilulky — bez ní je tečka na tmavé fotce jen skvrna,
                        // stejně jako u zelené tečky dostupnosti v řádku chatu.
                        .size(ACCOUNT_DOT)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
                        .padding(2.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                )
            }
        }
        // Kam se odtud jde: dvě místa v chatech, pak Nastavení — jiný druh cíle, vlastní skupina.
        ExpressiveMenu(
            expanded = open,
            onDismiss = { open = false },
            groups = listOf(
                listOf(
                    MenuAction(stringResource(Res.string.saved_messages), Res.drawable.bookmark_24dp, onClick = onSavedMessages),
                    MenuAction(
                        label = stringResource(Res.string.chats_archive),
                        icon = Res.drawable.archive_24dp,
                        trailing = archiveBadge,
                        onClick = onArchive,
                    ),
                ),
                listOf(MenuAction(stringResource(Res.string.nav_settings), Res.drawable.settings_24dp, onClick = onSettings)),
            ),
        )
    }
}

/**
 * Nabídka akcí po dlouhém stisku, průvodce nahlášením a snackbary (chyba, „Vrátit" po archivaci).
 * Společné pro hlavní seznam i archiv — liší se jen tím, co archivace znamená, a to ví chat sám
 * ([ChatSummary.folder]).
 */
@Composable
internal fun ChatActionsHost(
    viewModel: ChatsViewModel,
    snackbarHostState: SnackbarHostState,
    onArchived: ((ArchiveChange) -> Unit)? = null,
) {
    val actions by viewModel.actions.collectAsStateWithLifecycle()
    val savedMessagesChatId by viewModel.savedMessagesChatId.collectAsStateWithLifecycle()
    // Snackbar s „Vrátit" běží mimo efekt: ten se nulováním události restartuje a zrušil by ho
    // (stejná past jako „Vrátit" u odepnuté zprávy v konverzaci).
    val snackbarScope = rememberCoroutineScope()

    // Bez tohohle by neúspěšná akce (typicky překročený limit připnutých chatů) zmizela beze stopy
    // a vypadalo by to, že se nic nestalo.
    LaunchedEffect(actions.error) {
        val message = actions.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(getString(message.resource, *message.args.toTypedArray()))
        viewModel.dismissError()
    }

    LaunchedEffect(actions.archived) {
        val change = actions.archived ?: return@LaunchedEffect
        viewModel.consumeArchived()
        onArchived?.invoke(change)
        snackbarScope.launch {
            val result = snackbarHostState.showSnackbar(
                message = getString(if (change.archived) Res.string.chats_archived else Res.string.chats_unarchived),
                actionLabel = getString(Res.string.chats_undo),
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.undoArchive(change)
        }
    }

    /*
     * Zablokování v seznamu nic vidět nezmění (chat zůstává), takže je snackbar jediné potvrzení,
     * že se něco stalo — a zároveň nahrazuje potvrzovací dialog před vratnou akcí. Běží mimo efekt
     * ze stejného důvodu jako u archivace.
     */
    LaunchedEffect(actions.blocked) {
        val change = actions.blocked ?: return@LaunchedEffect
        viewModel.consumeBlocked()
        snackbarScope.launch {
            val result = snackbarHostState.showSnackbar(
                message = getString(if (change.blocked) Res.string.chats_blocked else Res.string.chats_unblocked),
                actionLabel = getString(Res.string.chats_undo),
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.undoBlock(change)
        }
    }

    actions.target?.let { chat ->
        ChatActionsSheet(
            chat = chat,
            spam = actions.spam,
            onTogglePinned = { viewModel.togglePinned(chat) },
            onMute = { duration -> viewModel.mute(chat, duration) },
            onUnmute = { viewModel.unmute(chat) },
            onToggleUnread = { viewModel.toggleUnread(chat) },
            // Uložené zprávy archivovat nejdou — TDLib by to odmítl.
            onToggleArchived = if (chat.id == savedMessagesChatId) null else {
                { viewModel.toggleArchived(chat) }
            },
            onSetBlocked = { blocked -> viewModel.setBlocked(chat, blocked) },
            onReport = { viewModel.report(chat) },
            onDelete = { cleanup -> viewModel.delete(chat, cleanup) },
            onDismiss = viewModel::hideActions,
        )
    }

    ReportHost(viewModel.reportFlow, snackbarHostState)
}

/**
 * Seznam chatů jedné složky ve skupinách, s dotahováním dalších a stavem načítání a chyby.
 * [empty] se kreslí místo prázdného seznamu (v ploše bez odsazení Scaffoldu), [header] jsou
 * položky nad chaty (karta s aktualizací) a [headerItemCount] jejich počet.
 *
 * [scrollToChat]: seznam dojede na tenhle chat (vybraný v hledání), pokud v něm je a není celý
 * vidět, a ohlásí to [onScrolledToChat].
 */
@Composable
internal fun ChatListContent(
    viewModel: ChatsViewModel,
    selectedChatId: Long?,
    onChatClick: (Long) -> Unit,
    padding: PaddingValues,
    empty: @Composable BoxScope.() -> Unit,
    scrollToChat: Long? = null,
    onScrolledToChat: () -> Unit = {},
    headerItemCount: Int = 0,
    listState: LazyListState = rememberLazyListState(),
    /** Filtr z toolbaru; archiv žádný nemá. */
    filter: ChatFilter = ChatFilter.All,
    header: LazyListScope.() -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val separateUnread by viewModel.separateUnreadChats.collectAsStateWithLifecycle()
    val savedMessagesChatId by viewModel.savedMessagesChatId.collectAsStateWithLifecycle()

    // Dotažení dalších chatů, jakmile se uživatel blíží ke konci. TDLib pak pošle updaty a seznam
    // se doplní sám — proto se tu nečeká na návratovou hodnotu.
    val shouldLoadMore by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            last >= info.totalItemsCount - LOAD_MORE_THRESHOLD
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { shouldLoadMore }
            .collect { if (it) viewModel.loadMore() }
    }


    val groups = (state as? ChatListState.Ready)?.chats?.let { chats ->
        remember(chats, filter, separateUnread, savedMessagesChatId) {
            chatGroups(chats.filteredBy(filter), separateUnread, savedMessagesChatId)
        }
    }.orEmpty()

    /*
     * Dojetí na chat vybraný v hledání. Index se počítá ze skupin, protože `LazyListState` klíč
     * položky mimo obrazovku na index nepřevede: kotva, hlavička, pak chaty skupinu po skupině.
     * Chat, který je celý vidět, se nehýbe — skok by jen zbytečně odsunul to, na co uživatel
     * kouká. Chat v seznamu není (archiv, mimo složky): požadavek se jen zahodí.
     */
    LaunchedEffect(scrollToChat, groups) {
        val chatId = scrollToChat ?: return@LaunchedEffect
        if (groups.isEmpty()) return@LaunchedEffect
        val position = groups.flatten().indexOfFirst { it.id == chatId }
        if (position >= 0) {
            val info = listState.layoutInfo
            val fullyVisible = info.visibleItemsInfo.any {
                it.key == chatId && it.offset >= info.viewportStartOffset + info.beforeContentPadding &&
                    it.offset + it.size <= info.viewportEndOffset - info.afterContentPadding
            }
            if (!fullyVisible) listState.animateScrollToItem(1 + headerItemCount + position)
        }
        onScrolledToChat()
    }

    when (val current = state) {
        ChatListState.Loading -> Box(
            Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            LoadingIndicator()
        }

        is ChatListState.Failed -> Box(
            Modifier.fillMaxSize().padding(padding).padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(current.message.resolve(), style = MaterialTheme.typography.bodyLarge)
                TextButton(onClick = viewModel::loadMore) { Text(stringResource(Res.string.common_try_again)) }
            }
        }

        is ChatListState.Ready -> if (current.chats.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), content = empty)
        } else if (groups.isEmpty() && filter != ChatFilter.All) {
            /*
             * Filtru nic neodpovídá. Jiný stav než prázdný seznam: chaty jsou, jen ne tyhle.
             * Tentýž animovaný prázdný stav jako archiv a placeholder detailu; u Nepřečtených
             * je to dobrá zpráva, ne slepá ulička, a tak to taky říká.
             */
            Box(Modifier.fillMaxSize().padding(padding)) {
                key(filter) {
                    if (filter == ChatFilter.Unread) {
                        IntroEmptyState(
                            icon = Res.drawable.done_all_24dp,
                            title = stringResource(Res.string.chats_filter_unread_empty_title),
                            text = stringResource(Res.string.chats_filter_unread_empty),
                        )
                    } else {
                        IntroEmptyState(
                            icon = filter.icon,
                            title = stringResource(Res.string.chats_filter_empty_title),
                            text = stringResource(Res.string.chats_filter_empty),
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    // Kotva i rozestup za ní jdou z horního odsazení, první chat tak
                    // zůstane tam, kde byl bez ní.
                    top = padding.calculateTopPadding() + 8.dp - TOP_ANCHOR_HEIGHT -
                        GROUP_ITEM_SPACING,
                    bottom = padding.calculateBottomPadding() + 8.dp,
                    start = 16.dp,
                    end = 16.dp,
                ),
                // Rozestup UVNITŘ skupiny. Mezera mezi skupinami se přidává u její první
                // položky — seznam o sousedech položky nic neví.
                verticalArrangement = Arrangement.spacedBy(GROUP_ITEM_SPACING),
            ) {
                /*
                 * Kotva nahoře. `LazyColumn` drží pozici podle klíče první viditelné
                 * položky; bez kotvy je to první chat, a když nad něj vyskočí jiný (nová
                 * zpráva, připnutí), seznam jde za tím původním a nový skončí nad horní
                 * hranou. S kotvou je první viditelná položka ona, takže seznam u vršku
                 * zůstane u vršku. Výška nesmí být nulová — položku bez výšky seznam
                 * za viditelnou nepovažuje.
                 */
                item(key = TOP_ANCHOR_KEY, contentType = TOP_ANCHOR_KEY) {
                    Spacer(Modifier.fillMaxWidth().height(TOP_ANCHOR_HEIGHT))
                }

                header()

                groups.forEachIndexed { groupIndex, group ->
                    itemsIndexed(group, key = { _, chat -> chat.id }) { index, chat ->
                        // Krajní položky skupiny nesou její zaoblení, prostřední se drží u sebe.
                        val shape = groupItemShape(
                            firstInGroup = index == 0,
                            lastInGroup = index == group.lastIndex,
                            selected = chat.id == selectedChatId,
                        )
                        SwipeToArchive(
                            archived = chat.folder == ChatFolder.Archive,
                            // Uložené zprávy archivovat nejdou, stejně jako v nabídce po dlouhém stisku.
                            enabled = chat.id != savedMessagesChatId,
                            shape = shape,
                            onSwipe = { viewModel.toggleArchived(chat) },
                            // Animace přeskládání při změně pořadí — bez toho by chaty
                            // při nové zprávě skákaly. `animateItem` musí být na kořeni
                            // položky, takže sedí na tahu, ne na řádku uvnitř.
                            modifier = Modifier
                                .animateItem()
                                .padding(
                                    top = if (index == 0 && groupIndex > 0) GROUP_SPACING
                                    else 0.dp,
                                ),
                        ) {
                            ChatRow(
                                chat = chat,
                                selected = chat.id == selectedChatId,
                                shape = shape,
                                onClick = { onChatClick(chat.id) },
                                onLongClick = { viewModel.showActions(chat) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Zaoblení položky v bloku: velké nahoře u první, velké dole u poslední, malé mezi nimi.
 * Animuje se, protože skupiny se přeskládávají za běhu — přečtený chat přeskočí z nepřečtených
 * mezi ostatní a tvary sousedů se tím mění.
 *
 * Počítá se mimo [ChatRow], protože tentýž tvar musí mít i podklad, který se odkrývá tahem —
 * jinak by pod zaoblenou položkou vykukovaly jeho hranaté rohy.
 */
@Composable
private fun groupItemShape(firstInGroup: Boolean, lastInGroup: Boolean, selected: Boolean): RoundedCornerShape {
    val topCorner by animateDpAsState(
        // Vybraná položka se z bloku vyčlení: zaoblená celá, jako skupina sama (M3 Expressive).
        targetValue = if (firstInGroup || selected) GROUP_CORNER else INNER_CORNER,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "chat-corner-top",
    )
    val bottomCorner by animateDpAsState(
        targetValue = if (lastInGroup || selected) GROUP_CORNER else INNER_CORNER,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "chat-corner-bottom",
    )
    return RoundedCornerShape(
        topStart = topCorner,
        topEnd = topCorner,
        bottomStart = bottomCorner,
        bottomEnd = bottomCorner,
    )
}

/**
 * Chat tahem do strany do archivu a zpátky.
 *
 * Táhnout jde na OBĚ strany a obě dělají totéž: druhá akce v seznamu není, takže by si uživatel
 * musel pamatovat směr pro nic. Nevratné to není — snackbar „Vrátit" je tentýž jako u archivace
 * z nabídky ([ChatActionsHost]), takže se tah nepotvrzuje.
 *
 * **Vlastní detektor, ne `SwipeToDismissBox`.** Ten stojí na `draggable`, které si tah vezme po
 * obyčejném touch slopu a je přitom směrově agnostické — palec jde na začátku scrollu obloukem do
 * strany, takže seznamu gesto sebral a chaty odlétaly do archivu při obyčejném scrollování.
 * A archivoval i ZKRATKOU PŘES RYCHLOST: tah rychlejší než 125 dp/s bral jako dokončený bez
 * ohledu na to, kam došel, a vypnout to zvenku nejde. Tady rozhoduje [archiveSwipeDecision]
 * (výrazně vodorovný tah, dvojnásobek slopu) a [archiveSwipeThreshold] (kus šířky řádku);
 * rychlost se nepočítá vůbec.
 *
 * [enabled] `false` u Uložených zpráv: ty archivovat nejdou a tah, po kterém přijde chyba, je
 * horší než tah, který se nechytne.
 */
@Composable
private fun SwipeToArchive(
    /** Chat je v archivu — tah ho tedy vrací mezi chaty. */
    archived: Boolean,
    enabled: Boolean,
    shape: RoundedCornerShape,
    onSwipe: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val thresholdCap = with(LocalDensity.current) { SWIPE_THRESHOLD_MAX.toPx() }

    /*
     * Posun drží `remember`, NE `rememberSaveable`: `LazyColumn` si stav položky pamatuje podle
     * jejího klíče i poté, co položka ze seznamu zmizí — a chat odtažený tahem právě mizí. Po
     * „Vrátit" by se tedy vrátil rovnou odtažený, tedy neviditelný.
     */
    val drag = remember { Animatable(0f) }
    /** Prah už byl v tomhle tahu překročen; hlídá, aby haptika odešla jen jednou za tah. */
    var armed by remember { mutableStateOf(false) }
    // Spec se musí vzít v kompozici — konec gesta je obyčejná suspend funkce, ne composable.
    val returnSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()

    /*
     * Strana, ze které se podklad odkrývá. Čte se hrubě přes `derivedStateOf`: `drag.value` se
     * mění každý snímek tahu a podklad se má překreslit jen při změně strany.
     */
    val side by remember {
        derivedStateOf {
            when {
                drag.value > 0f -> SwipeSide.Start
                drag.value < 0f -> SwipeSide.End
                else -> SwipeSide.None
            }
        }
    }

    Box(
        modifier.then(
            if (!enabled) {
                Modifier
            } else {
                Modifier.pointerInput(Unit) {
                    val maxDrag = { size.width.toFloat() }
                    val threshold = { archiveSwipeThreshold(size.width.toFloat(), thresholdCap) }
                    detectArchiveSwipe(
                        onDrag = { delta ->
                            coroutineScope.launch {
                                val next = (drag.value + delta).coerceIn(-maxDrag(), maxDrag())
                                drag.snapTo(next)
                                if (!armed && abs(next) >= threshold()) {
                                    armed = true
                                    haptics.threshold()
                                }
                            }
                        },
                        onEnd = { released ->
                            val committed = released && abs(drag.value) >= threshold()
                            val target = if (drag.value > 0f) maxDrag() else -maxDrag()
                            armed = false
                            coroutineScope.launch {
                                /*
                                 * Archivuje se při PUŠTĚNÍ, ne při překročení prahu — kdo si to
                                 * rozmyslí, vrátí prst zpátky a nic se nestane. Akce odchází
                                 * PŘED animací odjezdu: odscrollovaná položka se uklidí
                                 * z kompozice i s tímhle rozsahem a animace by ji vzala s sebou.
                                 */
                                if (committed) onSwipe()
                                drag.animateTo(if (committed) target else 0f, returnSpec)
                            }
                        },
                    )
                }
            },
        ),
    ) {
        /*
         * Bez tahu se podklad NEKRESLÍ VŮBEC, ani jeho plocha. Řádek nad ním má tentýž tvar,
         * jenže vyhlazené hrany (zaoblení, okraje na zlomcích pixelu) jsou poloprůhledné a
         * podklad jimi prosvítal: v tmavém tématu byl kolem každého chatu světlý lem
         * `primaryContainer` na skoro černém `surfaceContainerLowest`.
         */
        if (side != SwipeSide.None) {
            Box(
                Modifier
                    .matchParentSize()
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = if (side == SwipeSide.End) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                val icon = @Composable {
                    Icon(
                        painterResource(
                            if (archived) Res.drawable.unarchive_24dp else Res.drawable.archive_24dp,
                        ),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                val label = @Composable {
                    Text(
                        text = stringResource(
                            if (archived) Res.string.chats_action_unarchive else Res.string.chats_action_archive,
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Row(
                    Modifier.padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Ikona patří k hraně, ze které se podklad odkrývá — objeví se první
                    // a popisek ji jen doplní. Opačné pořadí ukáže na začátku tahu konec
                    // věty, ze kterého se nepozná, co se stane.
                    if (side == SwipeSide.End) {
                        label()
                        icon()
                    } else {
                        icon()
                        label()
                    }
                }
            }
        }
        // Posun se čte až v layoutu, takže tah nic nerekomponuje.
        Box(Modifier.offset { IntOffset(drag.value.roundToInt(), 0) }) {
            content()
        }
    }
}

/** Ze které hrany se odkrývá podklad pod taženým chatem. */
private enum class SwipeSide { None, Start, End }

/**
 * Strop prahu archivace. Na široké ploše (tablet, dvoupanel) by podíl šířky znamenal tah přes
 * půl obrazovky; na telefonu se neuplatní.
 */
private val SWIPE_THRESHOLD_MAX = 160.dp

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun ChatRow(
    chat: ChatSummary,
    selected: Boolean,
    /** Tvar bloku — počítá ho [groupItemShape] vedle, protože ho potřebuje i podklad pod tahem. */
    shape: RoundedCornerShape,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    /*
     * Blok je SVĚTLEJŠÍ než pozadí, ne tmavší.
     *
     * Původně to bylo obráceně — pozadí `surface` (nejsvětlejší role) a řádky `surfaceContainerHighest`
     * o tři stupně tmavší. Z položek pak byly šedé plochy na světlém a seznam působil těžce.
     * Takhle je to vztah „světlý panel na tónované stránce", což je i to, čím odstup dělá
     * Google Messages: jeho řádky samy žádnou výplň nemají.
     *
     * Výběr v list-detail je navigační stav, ne zvýraznění obsahu → `secondaryContainer`,
     * stejná role, jakou používá vybraná položka v nav railu.
     */
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerLowest
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    // Čas a ikony: `onSurfaceVariant` je počítaný na surface role. Na vybrané položce by v paletě
    // se zvýšeným kontrastem (světlý `secondaryContainer` v tmavém tématu) nebyl vidět.
    val secondaryColor = if (selected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    // `Card(onClick = …)` umí jen krátké ťuknutí. Dlouhý stisk potřebuje `combinedClickable`,
    // takže se klikání přesouvá na Surface — Card zůstává jen tvarem a barvou.
    Surface(
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box {
                Avatar(
                    chatId = chat.id,
                    title = chat.title,
                    avatarFileId = chat.avatarFileId,
                    size = AVATAR_SIZE,
                    isSavedMessages = isSavedMessages(chat.id),
                    // Druh chatu tvarem: člověk kruh, skupina sušenka, kanál čtverec.
                    shape = chat.kind.avatarShape(),
                    modifier = Modifier.sharedNavElement(chatAvatarKey(chat.id)),
                )

                // Tečka sedí NA avataru, ne vedle jména: v seznamu se očima jede po levém
                // sloupci, takže se dostupnost přečte spolu s tím, kdo to je.
                if (chat.presence is Presence.Online) {
                    OnlineDot(rim = containerColor, modifier = Modifier.align(Alignment.BottomEnd))
                }
            }

            Column(Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (chat.isPinned) {
                        Icon(
                            painterResource(Res.drawable.push_pin_filled_24dp),
                            contentDescription = stringResource(Res.string.chats_section_pinned),
                            modifier = Modifier.size(14.dp),
                            tint = secondaryColor,
                        )
                    }
                    // Jméno a štítek ověření vyplní zbytek řádku, takže čas sedí na konci.
                    // Jméno uvnitř má váhu BEZ vyplnění: dlouhé se zkrátí a štítek za ním
                    // zůstane vidět, krátké nechá štítek hned za sebou.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = chatTitle(chat.id, chat.title),
                            // `bodyLarge` místo `titleMedium`: stejná velikost, ale běžný řez.
                            // Polotučné jméno u KAŽDÉHO chatu znamená, že nevyčnívá žádný —
                            // tučností se proto odlišuje jen nepřečtený, jako v Google Messages.
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (chat.hasUnread) FontWeight.SemiBold else null,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false).sharedNavText(chatTitleKey(chat.id)),
                        )
                        chat.verification?.let {
                            VerificationBadge(
                                verification = it,
                                accent = accentOn(
                                    background = containerColor,
                                    accent = MaterialTheme.colorScheme.primary,
                                    fallback = LocalContentColor.current,
                                ),
                            )
                        }
                    }

                    if (chat.isMuted) {
                        Icon(
                            painterResource(Res.drawable.notifications_off_24dp),
                            contentDescription = stringResource(Res.string.chats_muted),
                            modifier = Modifier.size(14.dp),
                            tint = secondaryColor,
                        )
                    }

                    // Čas je zarovnaný doprava na konec řádku s názvem, ne pod něj — jinak by
                    // soupeřil o místo s náhledem zprávy, který bývá dlouhý.
                    // Koncept posouvá chat v seznamu nahoru, takže čas musí jít s ním — jinak by
                    // u chatu nahoře stálo datum starší než u těch pod ním.
                    listOfNotNull(chat.lastMessageTimestamp, chat.draft?.timestamp).maxOrNull()?.let { timestamp ->
                        Text(
                            text = timestamp.asRelativeTime(),
                            // Časy stojí pod sebou u pravého okraje — stejně široké číslice
                            // je drží v zákrytu.
                            style = MaterialTheme.typography.labelMedium.tabularNumbers(),
                            // U nepřečteného chatu je čas akcentem, stejně jako odznak pod ním —
                            // oči jdou po pravém sloupci a hledají, kde se něco stalo.
                            color = if (chat.hasUnread && !selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                secondaryColor
                            },
                            maxLines = 1,
                        )
                    }
                }
                /*
                 * Činnost > koncept > náhled poslední zprávy.
                 *
                 * „Píše…" je nejčerstvější informace. Koncept je to, co uživatel v chatu nechal
                 * rozdělané, a hledá ho spíš než poslední cizí zprávu. Obojí najednou se do
                 * řádku nevejde.
                 */
                val draftLabelColor = accentOn(containerColor, MaterialTheme.colorScheme.tertiary, contentColor)
                val subtitle: AnnotatedString? = chat.activity?.label()?.let { AnnotatedString(stringResource(it)) }
                    ?: chat.draft?.let { draft ->
                        buildAnnotatedString {
                            // Štítek jinou barvou než „píše…" (primary) — jde o náš text, ne o dění.
                            withStyle(SpanStyle(color = draftLabelColor)) { append(stringResource(Res.string.chats_draft_prefix)) }
                            // Zalomení by v jednořádkovém náhledu useklo text hned na začátku.
                            append(draft.text.replace('\n', ' '))
                        }
                    }
                    ?: chat.lastMessagePreview?.let(::AnnotatedString)

                subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        // Nepřečtený náhled se odliší barvou, ne řezem: s polotučným názvem nad
                        // ním by byly na jednom řádku dvě úrovně tučnosti a blok by ztěžkl.
                        color = when {
                            // Činnost se odliší barvou, aby bylo poznat, že to není text zprávy.
                            chat.activity != null -> accentOn(containerColor, MaterialTheme.colorScheme.primary, contentColor)
                            selected -> MaterialTheme.colorScheme.onSecondaryContainer
                            chat.hasUnread -> MaterialTheme.colorScheme.onSurface
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            UnreadBadges(chat)
        }
    }
}

/**
 * Odznaky vpravo v řádku: reakce, zmínka, počet nepřečtených — v tomhle pořadí, počet u kraje.
 *
 * Zmínka a reakce mají vlastní odznak, protože se v počtu ztratí: „@" ve skupině se stovkou
 * nepřečtených je to jediné, kvůli čemu ji uživatel otevře. Číslo nenesou (kolik jich je, řekne
 * tlačítko v konverzaci) — jde o to, ŽE nějaké jsou.
 */
@Composable
private fun UnreadBadges(chat: ChatSummary) {
    if (chat.unreadReactionCount == 0 && chat.unreadMentionCount == 0 && chat.unreadCount == 0) return
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (chat.unreadReactionCount > 0) {
            IconBadge(Res.drawable.favorite_filled_24dp, stringResource(Res.string.chats_unread_reaction))
        }
        if (chat.unreadMentionCount > 0) {
            IconBadge(Res.drawable.alternate_email_24dp, stringResource(Res.string.chats_unread_mention))
        }
        if (chat.unreadCount > 0) {
            // `Badge` je ve výchozím stavu chybově červený. Nepřečtená zpráva ale není
            // chyba — v odlehčeném seznamu byla ta červená jediná sytá barva na obrazovce
            // a přebíjela všechno ostatní.
            Badge(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Text(chat.unreadCount.toString(), style = LocalTextStyle.current.tabularNumbers())
            }
        }
    }
}

/** Kulatý odznak s ikonou, velký jako odznak s jednou číslicí. */
@Composable
private fun IconBadge(icon: DrawableResource, description: String) {
    Badge(
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.semantics { contentDescription = description },
    ) {
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(12.dp))
    }
}

/** Kolik položek před koncem už spustí dotažení další dávky. */
private const val LOAD_MORE_THRESHOLD = 5

/** Zaoblení na krajích skupiny. Nese ho i karta s aktualizací — je v seznamu další blok. */
internal val GROUP_CORNER = 20.dp

/** Zaoblení mezi sousedy uvnitř skupiny — tak akorát, aby byly rozeznatelné, ne oddělené. */
private val INNER_CORNER = 4.dp

/** Rozestup mezi položkami jedné skupiny. */
private val GROUP_ITEM_SPACING = 2.dp

/** Mezera mezi skupinami. */
internal val GROUP_SPACING = 14.dp

/** Klíč neviditelné kotvy nad prvním chatem. */
private const val TOP_ANCHOR_KEY = "top-anchor"

/** Klíč karty s aktualizací. */
private const val UPDATE_CARD_KEY = "update-card"
private const val UNCONFIRMED_LOGIN_KEY = "unconfirmed-login"

/** Výška kotvy — nejmenší, se kterou ji seznam ještě počítá za viditelnou. */
private val TOP_ANCHOR_HEIGHT = 1.dp

/** Avatar. Větší než výchozích 48 dp — v odlehčeném řádku nese hlavní váhu on, ne písmo. */
private val AVATAR_SIZE = 52.dp

/** Fotka účtu v pilulce — o kus menší než ikona, ať pilulku nevyboulí. */
private val ACCOUNT_AVATAR_SIZE = 30.dp

/** Fotka účtu při výpadku spojení: 38 dp + půl tahu prstence se vejde pod 40dp ořez `IconButton` i do pilulky. */
private val ACCOUNT_AVATAR_GROWN = 38.dp

/** Tečka „v archivu něco je" na fotce účtu, i s obroučkou. Menší než tečka dostupnosti — je jen signál, ne stav. */
private val ACCOUNT_DOT = 10.dp

/**
 * O kolik se prodlužuje spodek seznamu kvůli plovoucímu toolbaru: jeho výška, odsazení od
 * okraje a trochu vzduchu, aby poslední chat nekončil těsně pod ním.
 */
private val TOOLBAR_CLEARANCE = FloatingToolbarDefaults.ContainerSize + FloatingToolbarDefaults.ScreenOffset + 8.dp

