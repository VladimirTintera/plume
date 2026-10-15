package eu.tintera.plume.features.chats

import eu.tintera.plume.core.telegram.AppScreen
import eu.tintera.plume.core.telegram.SuggestedAction
import eu.tintera.plume.features.notices.BirthdaysCard
import eu.tintera.plume.features.notices.SuggestedActionCard
import eu.tintera.plume.features.notices.rememberNoticeLinkOpener
import eu.tintera.plume.features.notices.FrozenAccountCard
import eu.tintera.plume.core.settings.ThemeColor
import eu.tintera.plume.app.root.presentation.LocalIsDarkTheme
import eu.tintera.plume.app.root.presentation.theme.chatColorScheme
import eu.tintera.plume.app.root.presentation.theme.seed
import androidx.compose.material3.ColorScheme
import org.koin.compose.koinInject
import androidx.compose.ui.graphics.Color
import eu.tintera.plume.core.ui.asUpcomingTime
import eu.tintera.plume.resources.snooze_24dp
import eu.tintera.plume.resources.chats_snooze_reminder
import eu.tintera.plume.resources.chats_snoozed
import eu.tintera.plume.resources.chats_snoozed_until
import eu.tintera.plume.resources.chats_swipe_snooze
import eu.tintera.plume.resources.chats_swipe_unsnooze
import eu.tintera.plume.core.ui.neighbourChat
import eu.tintera.plume.core.ui.KeyboardShortcuts
import eu.tintera.plume.core.ui.KeyboardShortcut
import eu.tintera.plume.resources.chats_swipe_archive
import eu.tintera.plume.resources.chats_swipe_unarchive
import eu.tintera.plume.resources.chats_swipe_read
import eu.tintera.plume.resources.chats_swipe_unread
import eu.tintera.plume.resources.chats_swipe_mute
import eu.tintera.plume.resources.chats_swipe_unmute
import eu.tintera.plume.resources.chats_swipe_pin
import eu.tintera.plume.resources.chats_swipe_unpin
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds
import androidx.compose.runtime.rememberUpdatedState
import org.jetbrains.compose.resources.StringResource
import eu.tintera.plume.resources.keep_off_24dp
import eu.tintera.plume.resources.notifications_24dp
import eu.tintera.plume.resources.mark_chat_unread_24dp
import eu.tintera.plume.core.ui.SwipeThresholdBadge
import androidx.compose.foundation.clickable
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.lerp
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.traversalIndex
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import eu.tintera.plume.core.ui.RollingNumber
import eu.tintera.plume.core.adaptive.COLLAPSED_LIST_MAX_WIDTH
import eu.tintera.plume.core.adaptive.LIST_CONTENT_INSET
import eu.tintera.plume.core.adaptive.LocalExpandListPane
import eu.tintera.plume.core.ui.readPluralString
import eu.tintera.plume.core.ui.readString
import eu.tintera.plume.resources.campaign_24dp
import eu.tintera.plume.resources.chats_create
import eu.tintera.plume.resources.close_24dp
import eu.tintera.plume.resources.common_close
import eu.tintera.plume.resources.group_24dp
import eu.tintera.plume.resources.new_chat_new_channel
import eu.tintera.plume.resources.new_chat_new_group
import eu.tintera.plume.core.ui.ReportDrawnWhen
import eu.tintera.plume.core.ui.resolveNow
import eu.tintera.plume.core.ui.SwipeDirection
import eu.tintera.plume.core.ui.detectHorizontalSwipe
import androidx.compose.material3.LocalContentColor
import eu.tintera.plume.core.ui.SecretChatLock
import eu.tintera.plume.core.ui.VerificationBadge
import eu.tintera.plume.core.ui.EmojiStatusBadge
import eu.tintera.plume.core.ui.appendWithCustomEmoji
import eu.tintera.plume.core.ui.customEmojiInlineContent
import eu.tintera.plume.core.ui.MenuAction
import eu.tintera.plume.core.ui.ExpressiveMenu
import eu.tintera.plume.core.ui.threshold
import eu.tintera.plume.resources.saved_messages
import eu.tintera.plume.resources.bookmark_24dp
import eu.tintera.plume.resources.favorite_filled_24dp
import eu.tintera.plume.resources.alternate_email_24dp
import eu.tintera.plume.resources.chats_unread_reaction
import eu.tintera.plume.resources.ballot_24dp
import eu.tintera.plume.resources.chats_unread_poll_vote
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
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import eu.tintera.plume.core.ui.contextClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material3.ScaffoldDefaults
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
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import eu.tintera.plume.features.conversation.DockedPlayer
import eu.tintera.plume.features.conversation.GLASS_FALLBACK_ALPHA
import eu.tintera.plume.features.conversation.GLASS_TEXT_TINT_ALPHA
import eu.tintera.plume.features.conversation.PlayerDockBar
import eu.tintera.plume.features.conversation.glassStyle
import org.koin.compose.viewmodel.koinViewModel
import androidx.compose.ui.layout.onSizeChanged
import eu.tintera.plume.features.devices.UnconfirmedLoginCard
import eu.tintera.plume.core.update.rememberStartUpdate
import eu.tintera.plume.core.telegram.ChatListState
import eu.tintera.plume.features.report.ReportHost
import eu.tintera.plume.core.telegram.ChatSponsor
import eu.tintera.plume.core.telegram.ChatSummary
import eu.tintera.plume.core.telegram.Presence
import eu.tintera.plume.core.ui.Avatar
import eu.tintera.plume.core.ui.ReorderRow
import eu.tintera.plume.core.ui.ReorderTopBar
import eu.tintera.plume.resources.chats_reorder_title
import eu.tintera.plume.core.ui.draggedItem
import eu.tintera.plume.core.ui.groupItemShape
import eu.tintera.plume.core.ui.rememberDragReorderState
import eu.tintera.plume.resources.chats_reorder_hint
import eu.tintera.plume.core.ui.accentOn
import eu.tintera.plume.core.ui.asRelativeTime
import eu.tintera.plume.core.ui.chatAvatarKey
import eu.tintera.plume.core.ui.rememberChatContainerSource
import eu.tintera.plume.core.ui.chatTitleKey
import eu.tintera.plume.core.ui.label
import eu.tintera.plume.core.ui.resolve
import eu.tintera.plume.core.ui.sharedNavElement
import eu.tintera.plume.core.ui.sharedNavText
import eu.tintera.plume.core.ui.tabularNumbers
import eu.tintera.plume.core.telegram.ArchiveUnread
import eu.tintera.plume.core.telegram.opensAsTopics
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.chats_sponsor_announcement
import eu.tintera.plume.resources.chats_sponsor_proxy
import eu.tintera.plume.resources.archive_24dp
import eu.tintera.plume.resources.chats_action_archive
import eu.tintera.plume.resources.chats_action_unarchive
import eu.tintera.plume.resources.chats_archive_unread
import eu.tintera.plume.resources.unarchive_24dp
import eu.tintera.plume.resources.chats_archive
import eu.tintera.plume.resources.chats_archived
import eu.tintera.plume.resources.chats_archived_many
import eu.tintera.plume.resources.chats_unarchived_many
import eu.tintera.plume.resources.chats_new
import eu.tintera.plume.resources.chats_undo
import eu.tintera.plume.resources.chats_blocked
import eu.tintera.plume.resources.chats_unblocked
import eu.tintera.plume.resources.chats_unarchived
import eu.tintera.plume.resources.chats_draft_prefix
import eu.tintera.plume.resources.chats_drafts
import eu.tintera.plume.resources.chats_drafts_count
import eu.tintera.plume.resources.edit_note_24dp
import eu.tintera.plume.resources.chats_empty
import eu.tintera.plume.resources.chats_folder_empty
import eu.tintera.plume.resources.chats_folder_empty_title
import eu.tintera.plume.resources.chats_muted
import eu.tintera.plume.resources.chats_section_pinned
import eu.tintera.plume.resources.common_try_again
import eu.tintera.plume.resources.edit_24dp
import eu.tintera.plume.resources.notifications_off_24dp
import eu.tintera.plume.resources.push_pin_filled_24dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import eu.tintera.plume.core.telegram.AccountInfo
import eu.tintera.plume.core.ui.IntroEmptyState
import eu.tintera.plume.core.ui.folderIcon
import eu.tintera.plume.core.ui.avatarShape
import eu.tintera.plume.resources.chats_account
import eu.tintera.plume.resources.my_qr_title
import eu.tintera.plume.resources.qr_code_24dp
import eu.tintera.plume.features.settings.MyQrCodeSheet
import eu.tintera.plume.resources.nav_settings
import eu.tintera.plume.resources.person_24dp
import eu.tintera.plume.resources.settings_24dp
import org.jetbrains.compose.resources.DrawableResource
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun ChatListScreen(
    viewModel: ChatsViewModel,
    selectedChatId: Long?,
    onChatClick: (Long) -> Unit,
    /** Skupina s tématy: otevře se seznam témat, ne konverzace. */
    onForumClick: (Long) -> Unit,
    /** Téma skupiny (koncept v něm z přehledu konceptů); bez něj se otevře seznam témat. */
    onTopicClick: (chatId: Long, topicId: Int) -> Unit = { chatId, _ -> onForumClick(chatId) },
    /** Nalezená zpráva: otevřít její chat a skočit na ni. */
    onMessageClick: (chatId: Long, messageId: Long, topicId: Int?) -> Unit,
    /** Dotaz, se kterým otevřít hledání (hashtag z konverzace); po otevření [onSearchRequestHandled]. */
    searchRequest: String?,
    onSearchRequestHandled: () -> Unit,
    onOpenArchive: () -> Unit,
    onNewChat: () -> Unit = {},
    onNewGroup: () -> Unit = {},
    onNewChannel: () -> Unit = {},
    onChatArchived: ((ArchiveChange) -> Unit)? = null,
    /** Menu účtu → Nastavení. Na telefonu jediný vstup, spodní lišta není. */
    onOpenSettings: () -> Unit = {},
    onOpenProxySettings: () -> Unit = {},
    /** „+" v řádku složek → Nová složka; otevře editor složky v Nastavení. */
    onCreateFolder: () -> Unit = {},
    /** „+" v řádku složek → Spravovat složky. */
    onManageFolders: () -> Unit = {},
    /** Obrazovka aplikace z doporučené akce (dvoufázové ověření, úprava profilu). */
    onOpenScreen: (AppScreen) -> Unit = {},
    /** Vedle seznamu je otevřená konverzace (dvoupanel) — hudbu pak ukazuje její dock. */
    conversationOpen: Boolean = false,
    /** Hledání ze sbaleného sloupce; otevře se, až se seznam rozbalí (viz [CollapsedChatList]). */
    onOpenSearch: () -> Unit = {},
    nowPlayingViewModel: NowPlayingViewModel = koinViewModel(),
    modifier: Modifier = Modifier,
) {
    val search by viewModel.search.state.collectAsStateWithLifecycle()
    val naturalSearchAvailable by viewModel.naturalSearchAvailable.collectAsStateWithLifecycle()
    val connection by viewModel.connection.collectAsStateWithLifecycle()
    val shownConnection = rememberShownConnection(connection)
    val savedMessagesChatId by viewModel.savedMessagesChatId.collectAsStateWithLifecycle()
    val hiddenSavedMessagesChatId by viewModel.hiddenSavedMessagesChatId.collectAsStateWithLifecycle()
    val updateCard by viewModel.updateCard.collectAsStateWithLifecycle()
    val freeze by viewModel.freeze.collectAsStateWithLifecycle()
    val suggestion by viewModel.suggestion.collectAsStateWithLifecycle()
    val birthdays by viewModel.birthdays.collectAsStateWithLifecycle()
    val accountPhone by viewModel.accountPhone.collectAsStateWithLifecycle()
    val openNoticeLink = rememberNoticeLinkOpener()
    val unconfirmedLogin by viewModel.unconfirmedLogin.collectAsStateWithLifecycle()
    val newFolderChats by viewModel.newFolderChats.collectAsStateWithLifecycle()
    val account by viewModel.account.collectAsStateWithLifecycle()
    val archiveUnread by viewModel.archiveUnread.collectAsStateWithLifecycle()
    val drafts by viewModel.drafts.collectAsStateWithLifecycle()
    var showDrafts by rememberSaveable { mutableStateOf(false) }
    var showMyQrCode by rememberSaveable { mutableStateOf(false) }
    val folderTabs by viewModel.folderTabs.collectAsStateWithLifecycle()
    val folderSuggestions by viewModel.folderSuggestions.collectAsStateWithLifecycle()
    val reorder by viewModel.reorder.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val selectionActions by viewModel.selectionActions.collectAsStateWithLifecycle()
    // Vybraná složka Telegramu (ne celý seznam) — kvůli prázdnému stavu.
    val selectedFolder = folderTabs.firstOrNull { it.selected && it.id != null }
    val startUpdate = rememberStartUpdate(viewModel.appUpdates)
    // Hledání při scrollu dolů odjede a při scrollu nahoru se vrátí.
    val scrollBehavior = SearchBarDefaults.enterAlwaysSearchBarScrollBehavior()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    /*
     * Hudba, která hraje dál po odchodu z konverzace (viz `docs/poznatky/prohlizec-medii.md`, „Přehrávač nad seznamem
     * chatů"). Tentýž řádek jako dock v konverzaci; při zasouvání kreslí poslední skladbu.
     */
    val nowPlaying by nowPlayingViewModel.nowPlaying.collectAsStateWithLifecycle()
    val shownPlayer = nowPlaying?.takeIf { !conversationOpen }?.let {
        DockedPlayer(
            key = it.key,
            music = true,
            title = it.track.title,
            label = it.track.performer,
            coverFileId = it.track.coverFileId,
            hasNext = it.next != null,
        )
    }
    val lastPlayer = remember { arrayOf<DockedPlayer?>(null) }
    if (shownPlayer != null) lastPlayer[0] = shownPlayer
    var playerHeight by remember { mutableIntStateOf(0) }
    val listHaze = rememberHazeState()

    val listState = rememberLazyListState()
    /*
     * Lišta složek při scrollu dolů zajede do FAB a při scrollu nahoru (a na vršku seznamu) zase
     * vyjede — stejně jako pilulka hledání nahoře, aby nezakrývala chaty, které uživatel čte.
     */
    var folderToolbarHidden by remember { mutableStateOf(false) }
    LaunchedEffect(listState) {
        var previous = 0 to 0
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { position ->
                val (index, offset) = position
                folderToolbarHidden = when {
                    index == 0 && offset == 0 -> false
                    index < previous.first || (index == previous.first && offset < previous.second) -> false
                    index > previous.first || offset > previous.second -> true
                    else -> folderToolbarHidden
                }
                previous = position
            }
    }
    /*
     * FAB menu „Vytvořit" (viz `docs/poznatky/seznam-chatu.md`, „Složky místo filtrů a FAB"). Zpět ho napřed zavře,
     * teprve další zpět odejde z aplikace; `NavigationBackHandler`, ne `BackHandler` (zabil by
     * predictive back).
     */
    var fabMenuExpanded by rememberSaveable { mutableStateOf(false) }
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = fabMenuExpanded,
        onBackCompleted = { fabMenuExpanded = false },
    )

    ChatActionsHost(
        viewModel,
        snackbarHostState,
        onArchived = onChatArchived,
        onOpenProxySettings = onOpenProxySettings,
    )
    // Až s načtenými koncepty: obnovený sheet (po zániku procesu) by se jinak nad prázdným
    // seznamem hned zavřel.
    val loadedDrafts = drafts
    if (showDrafts && loadedDrafts != null) {
        val draftDeleted by viewModel.draftDeleted.collectAsStateWithLifecycle()
        DraftsSheet(
            drafts = loadedDrafts,
            deleted = draftDeleted,
            onOpen = { item ->
                showDrafts = false
                val topic = item.topic
                when {
                    topic != null -> onTopicClick(item.chat.id, topic.id)
                    item.chat.opensAsTopics -> onForumClick(item.chat.id)
                    else -> onChatClick(item.chat.id)
                }
            },
            onDelete = viewModel::deleteDraft,
            onDeletedShown = viewModel::consumeDraftDeleted,
            onUndo = viewModel::undoDeleteDraft,
            onDismiss = { showDrafts = false },
        )
    }
    if (showMyQrCode) MyQrCodeSheet(onDismiss = { showMyQrCode = false })
    newFolderChats?.takeIf { it.sheetOpen }?.let { folderChats ->
        NewFolderChatsSheet(
            state = folderChats,
            onToggle = viewModel::toggleNewFolderChat,
            onJoin = { viewModel.processNewFolderChats(join = true) },
            onDismiss = viewModel::closeNewFolderChats,
        )
    }
    folderSuggestions?.let { suggestions ->
        FolderSuggestionsSheet(
            state = suggestions,
            onAdd = { folder ->
                viewModel.addRecommendedFolder(folder)
                // Přidaná složka se rovnou vybere — začít od vršku jejího seznamu.
                scope.launch { listState.scrollToItem(0) }
            },
            onCreate = {
                viewModel.closeFolderSuggestions()
                onCreateFolder()
            },
            onManage = {
                viewModel.closeFolderSuggestions()
                onManageFolders()
            },
            onDismiss = viewModel::closeFolderSuggestions,
        )
    }

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

    val accountMenu: @Composable () -> Unit = {
        AccountMenu(
            account = account,
            archiveUnread = archiveUnread,
            connection = shownConnection,
            onSavedMessages = { viewModel.openSavedMessages(onChatClick) },
            onArchive = onOpenArchive,
            draftCount = drafts?.size ?: 0,
            onDrafts = { showDrafts = true },
            onMyQrCode = { showMyQrCode = true },
            onSettings = onOpenSettings,
        )
    }

    /*
     * Seznam sbalený táhlem na úzký sloupec. Rozhoduje šířka, ne zvolená kotva: během tahu se
     * prochází všemi šířkami a řádek s názvem by byl nečitelný dávno před sbalenou kotvou.
     * Sheety a hlášky výš zůstávají společné, takže se při přepnutí nezavřou.
     */
    val expandListPane = LocalExpandListPane.current
    BoxWithConstraints(modifier) {
        if (maxWidth < COLLAPSED_LIST_MAX_WIDTH) {
            CollapsedChatList(
                viewModel = viewModel,
                selectedChatId = selectedChatId,
                onChatClick = onChatClick,
                onForumClick = onForumClick,
                onSearch = {
                    expandListPane?.invoke()
                    onOpenSearch()
                },
                onNewChat = onNewChat,
                onNewGroup = onNewGroup,
                onNewChannel = onNewChannel,
                snackbarHostState = snackbarHostState,
                account = accountMenu,
                // V pořadí karet nad seznamem (níž, `header`): první čekající je nejdůležitější.
                pendingCard = when {
                    freeze != null -> PendingCard.Freeze
                    unconfirmedLogin != null -> PendingCard.Login
                    birthdays.isNotEmpty() -> PendingCard.Birthdays
                    suggestion != null -> PendingCard.Suggestion
                    newFolderChats != null -> PendingCard.FolderChats
                    updateCard != null -> PendingCard.Update
                    else -> null
                },
                onShowCards = {
                    expandListPane?.invoke()
                    // Karty jsou na vršku seznamu; ten se teprve složí, proto žádost, ne posun.
                    listState.requestScrollToItem(0)
                },
            )
            return@BoxWithConstraints
        }

        /*
         * FAB a snackbar nejsou ve slotech Scaffoldu: FAB je součástí plovoucího toolbaru dole
         * a snackbar musí stát NAD ním — Scaffold by FAB dal do rohu a snackbar pod toolbar.
         */
        Scaffold(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                reorder?.let { current ->
                    ReorderTopBar(
                        title = stringResource(Res.string.chats_reorder_title),
                        busy = current.busy,
                        onCancel = viewModel::cancelReorder,
                        onDone = viewModel::saveReorder,
                    )
                    return@Scaffold
                }
                selection?.let { current ->
                    ChatSelectionTopBar(count = current.count, onClose = viewModel::closeSelection)
                    return@Scaffold
                }
                // Hlavička pod otevřeným FAB menu ztlumená a neťukací, stejně jako seznam.
                Box {
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
                            // Výsledky hledání jsou položky seznamu, takže vědí, jestli se chat otevírá tématy.
                            val forum = with(search) { results + top + recent + global }.any { it.id == chatId && it.opensAsTopics }
                            if (forum) onForumClick(chatId) else onChatClick(chatId)
                        },
                        onMessageClick = { chatId, messageId, topicId ->
                            scrollToChat = chatId
                            onMessageClick(chatId, messageId, topicId)
                        },
                        onLoadMoreMessages = viewModel.search::loadMoreMessages,
                        suggestionActions = remember(viewModel.search) {
                            SuggestionActions(
                                onClear = viewModel.search::clearRecent,
                                onRemoveRecent = viewModel.search::removeRecent,
                                onRemoveTop = viewModel.search::removeTop,
                            )
                        },
                        onFilterChange = viewModel.search::setFilter,
                        aiSearch = viewModel::parseSearch.takeIf { naturalSearchAvailable },
                        request = searchRequest,
                        onRequestHandled = onSearchRequestHandled,
                        trailing = accountMenu,
                    )
                    FabMenuScrim(
                        visible = fabMenuExpanded,
                        onDismiss = { fabMenuExpanded = false },
                        modifier = Modifier.matchParentSize(),
                    )
                }
            },
        ) { scaffoldPadding ->
            // Poslední chat se musí dát odscrollovat nad FAB, jinak by pod ním zůstal schovaný.
            val padding = PaddingValues(
                top = scaffoldPadding.calculateTopPadding(),
                bottom = scaffoldPadding.calculateBottomPadding() +
                    (if (folderTabs.isEmpty()) FAB_CLEARANCE else FAB_CLEARANCE_WITH_FOLDERS) +
                    with(LocalDensity.current) { playerHeight.toDp() },
            )
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().hazeSource(listHaze)) {
                    ChatListContent(
                        viewModel = viewModel,
                        selectedChatId = selectedChatId,
                        onChatClick = onChatClick,
                        onForumClick = onForumClick,
                        padding = padding,
                        scrollToChat = scrollToChat,
                        onScrolledToChat = { scrollToChat = null },
                        listState = listState,
                        // Položky hlavičky: nové přihlášení a karta s aktualizací.
                        headerItemCount = listOfNotNull(freeze, unconfirmedLogin, birthdays.takeIf { it.isNotEmpty() }, suggestion, updateCard, newFolderChats).size,
                        empty = {
                            /*
                             * Prázdná SLOŽKA je jiný stav než prázdný účet: chaty jsou, jen ne tyhle (třeba
                             * „Nepřečtené" se vším přečteným). Tentýž animovaný stav jako archiv, s ikonou
                             * složky; klíč podle složky, aby se úvodní animace pustila u každé zvlášť.
                             */
                            if (selectedFolder != null) {
                                key(selectedFolder.id) {
                                    IntroEmptyState(
                                        icon = folderIcon(selectedFolder.iconName),
                                        title = stringResource(Res.string.chats_folder_empty_title),
                                        text = stringResource(Res.string.chats_folder_empty),
                                    )
                                }
                            } else {
                                Text(
                                    stringResource(Res.string.chats_empty),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                                )
                            }
                        },
                    ) {
                        // Nad chaty, jako vlastní bloky se stejnou mezerou jako mezi skupinami. Zmrazení
                        // a přihlášení napřed: týkají se účtu samotného, aktualizace počká.
                        freeze?.let { frozen ->
                            item(key = FROZEN_ACCOUNT_KEY, contentType = FROZEN_ACCOUNT_KEY) {
                                FrozenAccountCard(
                                    freeze = frozen,
                                    onAppeal = openNoticeLink,
                                    modifier = Modifier.animateItem().padding(bottom = GROUP_SPACING),
                                )
                            }
                        }
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
                        // Narozeniny před radou: platí jen dnes (a včera, zítra), rada počká.
                        if (birthdays.isNotEmpty()) {
                            item(key = BIRTHDAYS_KEY, contentType = BIRTHDAYS_KEY) {
                                BirthdaysCard(
                                    birthdays = birthdays,
                                    onOpen = { userId -> viewModel.openPrivateChat(userId, onChatClick) },
                                    onHide = viewModel::hideBirthdays,
                                    modifier = Modifier.animateItem().padding(bottom = GROUP_SPACING),
                                )
                            }
                        }
                        // Za přihlášením: rada od Telegramu je méně naléhavá než cizí zařízení v účtu.
                        suggestion?.let { action ->
                            item(key = SUGGESTION_KEY, contentType = SUGGESTION_KEY) {
                                SuggestedActionCard(
                                    action = action,
                                    phone = accountPhone,
                                    onPrimary = {
                                        when (action) {
                                            SuggestedAction.CheckPassword, SuggestedAction.CheckPhoneNumber ->
                                                viewModel.hideSuggestion(action)
                                            is SuggestedAction.SetPassword -> onOpenScreen(AppScreen.TwoStepVerification)
                                            is SuggestedAction.SetLoginEmail -> onOpenScreen(AppScreen.LoginEmail)
                                            SuggestedAction.SetProfilePhoto, SuggestedAction.SetBirthdate -> onOpenScreen(AppScreen.EditProfile)
                                            SuggestedAction.ArchiveUnknownChats -> viewModel.enableArchiveUnknownChats(action)
                                            is SuggestedAction.Custom -> openNoticeLink(action.url)
                                        }
                                    },
                                    onSecondary = {
                                        // „Zapomněl(a) jsem" vede k heslu (změna, obnova e-mailem), „Změnit" ke změně
                                        // čísla; jinde „Teď ne".
                                        when (action) {
                                            SuggestedAction.CheckPassword -> onOpenScreen(AppScreen.TwoStepVerification)
                                            SuggestedAction.CheckPhoneNumber -> onOpenScreen(AppScreen.ChangePhone)
                                            else -> viewModel.hideSuggestion(action)
                                        }
                                    },
                                    modifier = Modifier.animateItem().padding(bottom = GROUP_SPACING),
                                )
                            }
                        }
                        newFolderChats?.let { folderChats ->
                            item(key = NEW_FOLDER_CHATS_KEY, contentType = NEW_FOLDER_CHATS_KEY) {
                                NewFolderChatsCard(
                                    state = folderChats,
                                    onShow = viewModel::openNewFolderChats,
                                    onHide = { viewModel.processNewFolderChats(join = false) },
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

                FabMenuScrim(visible = fabMenuExpanded, onDismiss = { fabMenuExpanded = false })

                /*
                 * Nad navigační lištou A nad klávesnicí. Scaffold dává do paddingu jen systémové
                 * lišty, takže vysunutá klávesnice (hledání, dvoupanel s psaním vpravo) FAB
                 * i snackbar překryla. Sjednocení (`union`) bere větší z obou, jinak by se lišta
                 * a klávesnice sečetly.
                 *
                 * Insety, ne `padding(scaffoldPadding)` + `consumeWindowInsets(PaddingValues)`.
                 * Hodnota `PaddingValues` se mění s rozložením Foldu, a to ve chvíli, kdy NavDisplay
                 * přesouvá seznam do list-detail scény: `update` modifikátoru pak běží nad
                 * odpojeným uzlem a Compose 1.12 spadne („visitSubtreeIf called on an unattached
                 * node"). Insety jsou popis, ne čísla, takže se modifikátor při rozložení nemění.
                 *
                 * FAB a snackbar nejsou ve slotech Scaffoldu: Scaffold by je postavil jen nad
                 * systémové lišty, ne nad klávesnici.
                 */
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .windowInsetsPadding(
                            ScaffoldDefaults.contentWindowInsets
                                .only(WindowInsetsSides.Bottom)
                                .union(WindowInsets.ime),
                        ),
                ) {
                    SnackbarHost(snackbarHostState, Modifier.fillMaxWidth())
                    // Lišta výběru stojí MÍSTO FAB menu, uprostřed: zakládat nový chat uprostřed
                    // vybírání nedává smysl a obojí by se o spodek obrazovky přetahovalo.
                    ChatSelectionToolbar(
                        actions = selectionActions,
                        onArchive = viewModel::archiveSelected,
                        onMute = viewModel::muteSelected,
                        onRead = viewModel::readSelected,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                    /*
                     * Složky v plovoucí liště s FAB (viz `docs/poznatky/seznam-chatu.md`, „Složky
                     * chatů"). Celá lišta je „tlačítko" FAB menu a přepínací FAB sedí v jejím slotu:
                     * položky menu tak vyjedou nad lištou a FAB je pořád jeden. Při scrollu dolů
                     * a při otevřeném menu se lišta sbalí do FAB.
                     */
                    if (reorder == null && selection == null) NewChatFabMenu(
                        expanded = fabMenuExpanded,
                        onExpandedChange = { fabMenuExpanded = it },
                        onNewChat = onNewChat,
                        onNewGroup = onNewGroup,
                        onNewChannel = onNewChannel,
                        toolbar = if (folderTabs.isEmpty()) null else { fab ->
                            ChatFolderToolbar(
                                tabs = folderTabs,
                                expanded = !fabMenuExpanded && !folderToolbarHidden,
                                onSelect = { id ->
                                    viewModel.selectFolder(id)
                                    /*
                                     * Jiná složka = jiný seznam; začít od jeho vršku. V obsluze ťuknutí,
                                     * NE v efektu nad složkou: ten by se spustil i při každém návratu
                                     * seznamu do kompozice (z konverzace na telefonu) a odjel nahoru.
                                     */
                                    scope.launch { listState.scrollToItem(0) }
                                },
                                onAdd = viewModel::openFolderSuggestions,
                                onReadAll = viewModel::readFolder,
                                floatingActionButton = fab,
                            )
                        },
                    )
                    /*
                     * Pod FAB, u palce, jako u hudebních aplikací — a hlavně mimo lištu hledání, která
                     * při scrollu odjíždí. Seznam se odsazuje o jeho výšku, poslední chat je nad ním.
                     */
                    AnimatedVisibility(
                        visible = shownPlayer != null,
                        enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                        exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
                        modifier = Modifier.onSizeChanged { playerHeight = it.height },
                    ) {
                        lastPlayer[0]?.let { player ->
                            PlayerDockBar(
                                player = player,
                                playback = { nowPlayingViewModel.playback.value?.takeIf { it.key == player.key } },
                                reveal = { 1f },
                                onToggle = nowPlayingViewModel::togglePlaying,
                                onNext = nowPlayingViewModel::skipToNext,
                                onStop = nowPlayingViewModel::stop,
                                onShow = { onMessageClick(player.key.chatId, player.key.messageId, null) },
                                glass = Modifier.hazeBlur(
                                    input = HazeInput.Sources(listHaze),
                                    style = glassStyle(
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        alpha = GLASS_TEXT_TINT_ALPHA,
                                        fallbackAlpha = GLASS_FALLBACK_ALPHA,
                                    ),
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Plocha pod otevřeným FAB menu: ťuknutí kamkoli vedle ho zavře a nepropadne dál (otevřelo by
 * chat nebo hledání). Obsah pod položkami ztlumí, jinak by se pilulky menu slévaly s řádky.
 * Kreslí se dvakrát — přes seznam a přes hlavičku, kterou Scaffold kreslí nad obsahem.
 */
@Composable
private fun FabMenuScrim(visible: Boolean, onDismiss: () -> Unit, modifier: Modifier = Modifier.fillMaxSize()) {
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = FAB_MENU_SCRIM_ALPHA))
                .clickable(interactionSource = null, indication = null, onClick = onDismiss)
                .clearAndSetSemantics {},
        )
    }
}

/**
 * „Vytvořit" — FAB menu M3 Expressive: přepínací FAB (tužka → ✕, zaoblený čtverec → kruh)
 * a nad ním Nový kanál, Nová skupina a Nová zpráva. Nová zpráva je u palce, nejblíž FAB.
 *
 * Dřív to byl rozšířený FAB „Nová zpráva", který se při scrollu sbalil na ikonu; skupina
 * a kanál byly jen řádky na obrazovce Nové zprávy (jsou tam dál, pro odkazy a hledání).
 * Popisek nesou položky, přepínač je jen ikona — tak to M3 u FAB menu chce.
 *
 * Položka menu nejdřív menu zavře, teprve pak naviguje: po návratu do seznamu má být zavřené.
 *
 * Se složkami je přepínač ve slotu lišty ([toolbar] dostane FAB a postaví kolem něj
 * `ChatFolderToolbar`) a „tlačítkem" menu je celá lišta, takže položky vyjedou nad ní.
 */
@Composable
private fun NewChatFabMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onNewChat: () -> Unit,
    onNewGroup: () -> Unit,
    onNewChannel: () -> Unit,
    toolbar: (@Composable (fab: @Composable () -> Unit) -> Unit)? = null,
) {
    val createLabel = stringResource(Res.string.chats_create)
    val closeLabel = stringResource(Res.string.common_close)
    val closeAction = CustomAccessibilityAction(closeLabel) { onExpandedChange(false); true }
    val items = listOf(
        Triple(Res.drawable.campaign_24dp, Res.string.new_chat_new_channel, onNewChannel),
        Triple(Res.drawable.group_24dp, Res.string.new_chat_new_group, onNewGroup),
        Triple(Res.drawable.edit_24dp, Res.string.chats_new, onNewChat),
    )
    val fab = @Composable { containerSize: (Float) -> Dp ->
        ToggleFloatingActionButton(
            checked = expanded,
            onCheckedChange = onExpandedChange,
            containerSize = containerSize,
            modifier = Modifier.semantics {
                // Přepínač je v pořadí čtečky PŘED položkami nad ním, jako v ukázce M3.
                traversalIndex = -1f
                contentDescription = if (expanded) closeLabel else createLabel
            },
        ) {
            val close by remember { derivedStateOf { checkedProgress > 0.5f } }
            Icon(
                painterResource(if (close) Res.drawable.close_24dp else Res.drawable.edit_24dp),
                contentDescription = null,
                modifier = Modifier.animateIcon({ checkedProgress }),
            )
        }
    }
    FloatingActionButtonMenu(
        expanded = expanded,
        button = {
            if (toolbar == null) fab(ToggleFloatingActionButtonDefaults.containerSize()) else toolbar { ToolbarSlotFab(fab) }
        },
    ) {
        items.forEachIndexed { index, (icon, label, onClick) ->
            FloatingActionButtonMenuItem(
                onClick = {
                    onExpandedChange(false)
                    onClick()
                },
                icon = { Icon(painterResource(icon), contentDescription = null) },
                text = { Text(stringResource(label)) },
                modifier = Modifier.semantics {
                    isTraversalGroup = true
                    // Přepínač je ve čtečce před položkami; z poslední se menu zavře akcí.
                    if (index == items.lastIndex) customActions = listOf(closeAction)
                },
            )
        }
    }
}

/**
 * Přepínací FAB ve slotu lišty složek. Velikost slotu určuje lišta (56 dp rozbalená, 80 dp
 * sbalená) a FAB ji musí vyplnit; přepínač má ale velikost vlastní, z níž se při otevření menu
 * morfuje do kruhu „Zavřít". Výchozí velikost proto začíná na velikosti slotu: zavřený FAB
 * slot vyplní, otevřený se zmenší na zavírací kruh.
 *
 * Slot se čte z omezení při měření a do lambdy jde obyčejnou proměnnou, ne stavem: přepínač
 * velikost čte v měření a lišta mění slot právě přeměřením, takže změna dojde i bez stavu.
 */
@Composable
private fun ToolbarSlotFab(fab: @Composable (containerSize: (Float) -> Dp) -> Unit) {
    val slot = remember { floatArrayOf(TOOLBAR_FAB_EXPANDED_SIZE.value) }
    val closeSize = ToggleFloatingActionButtonDefaults.containerSize()(1f)
    Box(
        Modifier.layout { measurable, constraints ->
            slot[0] = constraints.maxWidth.toDp().value
            val placeable = measurable.measure(Constraints.fixed(constraints.maxWidth, constraints.maxHeight))
            layout(placeable.width, placeable.height) { placeable.place(0, 0) }
        },
        /*
         * Pevně na celý slot, jako `fillMaxSize` u FAB z ukázek M3. Lišta i `Box` dávají dětem
         * minimum 0, přepínač by si pak vzal svých 56 dp a v nafouknutém slotu by se jen
         * přilepil do rohu (ověřeno na emulátoru).
         */
        propagateMinConstraints = true,
    ) {
        fab { progress -> lerp(slot[0].dp, closeSize, progress) }
    }
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
    /** Kolik chatů má rozepsanou zprávu; bez konceptů položka v menu není. */
    draftCount: Int,
    onDrafts: () -> Unit,
    onMyQrCode: () -> Unit,
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
                RollingNumber(
                    value = archiveUnread.chats,
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
            // Bez tvaru při stisku: obsahem je kulatá fotka, hranatá vlnka by kolem ní trčela.
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
        // Kam se odtud jde: místa v chatech, pak vlastní účet (QR kód) a Nastavení — jiný druh
        // cíle, vlastní skupina.
        // Koncepty jen když nějaké jsou: prázdný přehled by byl položka, která nikam nevede.
        val draftsLabel = pluralStringResource(Res.plurals.chats_drafts_count, draftCount, draftCount)
        ExpressiveMenu(
            expanded = open,
            onDismiss = { open = false },
            groups = listOf(
                listOfNotNull(
                    MenuAction(stringResource(Res.string.saved_messages), Res.drawable.bookmark_24dp, onClick = onSavedMessages),
                    MenuAction(
                        label = stringResource(Res.string.chats_archive),
                        icon = Res.drawable.archive_24dp,
                        trailing = archiveBadge,
                        onClick = onArchive,
                    ),
                    // Počet v neutrální barvě: koncept je náš rozdělaný text, ne nic, co by
                    // si žádalo pozornost jako nepřečtené.
                    if (draftCount > 0) {
                        MenuAction(
                            label = stringResource(Res.string.chats_drafts),
                            icon = Res.drawable.edit_note_24dp,
                            trailing = {
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ) {
                                    RollingNumber(
                                        value = draftCount,
                                        style = LocalTextStyle.current.tabularNumbers(),
                                        modifier = Modifier.semantics { contentDescription = draftsLabel },
                                    )
                                }
                            },
                            onClick = onDrafts,
                        )
                    } else {
                        null
                    },
                ),
                listOf(
                    MenuAction(stringResource(Res.string.my_qr_title), Res.drawable.qr_code_24dp, onClick = onMyQrCode),
                    MenuAction(stringResource(Res.string.nav_settings), Res.drawable.settings_24dp, onClick = onSettings),
                ),
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
    /** Nabídka nad chatem od provozovatele proxy vede do nastavení proxy (jen hlavní seznam). */
    onOpenProxySettings: () -> Unit = {},
) {
    val actions by viewModel.actions.collectAsStateWithLifecycle()
    val savedMessagesChatId by viewModel.savedMessagesChatId.collectAsStateWithLifecycle()
    val chatFolders by viewModel.chatFolders.collectAsStateWithLifecycle()
    // Snackbar s „Vrátit" běží mimo efekt: ten se nulováním události restartuje a zrušil by ho
    // (stejná past jako „Vrátit" u odepnuté zprávy v konverzaci).
    val snackbarScope = rememberCoroutineScope()

    // Bez tohohle by neúspěšná akce (typicky překročený limit připnutých chatů) zmizela beze stopy
    // a vypadalo by to, že se nic nestalo.
    LaunchedEffect(actions.error) {
        val message = actions.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message.resolveNow())
        viewModel.dismissError()
    }

    LaunchedEffect(actions.archived) {
        val change = actions.archived ?: return@LaunchedEffect
        viewModel.consumeArchived()
        onArchived?.invoke(change)
        snackbarScope.launch {
            val result = snackbarHostState.showSnackbar(
                message = when {
                    change.chatIds.size > 1 -> readPluralString(
                        if (change.archived) Res.plurals.chats_archived_many else Res.plurals.chats_unarchived_many,
                        change.chatIds.size,
                        change.chatIds.size,
                    )
                    change.archived -> readString(Res.string.chats_archived)
                    else -> readString(Res.string.chats_unarchived)
                },
                actionLabel = readString(Res.string.chats_undo),
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
                message = readString(if (change.blocked) Res.string.chats_blocked else Res.string.chats_unblocked),
                actionLabel = readString(Res.string.chats_undo),
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.undoBlock(change)
        }
    }

    val listState by viewModel.state.collectAsStateWithLifecycle()
    val chats = (listState as? ChatListState.Ready)?.chats.orEmpty()

    val snoozeState by viewModel.snoozeState.collectAsStateWithLifecycle()
    // Doby odložení po tahu chatem.
    val snoozePicker by viewModel.snoozePicker.collectAsStateWithLifecycle()
    snoozePicker?.let { chat ->
        SnoozeSheet(
            chat = chat,
            onSnooze = { option -> viewModel.snooze(chat, option) },
            onDismiss = viewModel::closeSnoozePicker,
        )
    }
    actions.target?.let { chat ->
        val folderChoices = remember(actions.folders, chatFolders) {
            folderChoices(chatFolders, actions.folders)
        }
        ChatActionsSheet(
            chat = chat,
            spam = actions.spam,
            onOpenProxySettings = onOpenProxySettings,
            onTogglePinned = { viewModel.togglePinned(chat) },
            onReorderPinned = viewModel::startReorder.takeIf { canReorderPinned(chats) },
            onSelect = { viewModel.startSelection(chat) },
            onMute = { duration -> viewModel.mute(chat, duration) },
            onUnmute = { viewModel.unmute(chat) },
            snoozedUntil = snoozeState.sleeping[chat.id],
            onSnooze = if (viewModel.canSnooze) {
                { option -> viewModel.snooze(chat, option) }
            } else null,
            onUnsnooze = { viewModel.unsnooze(chat) },
            onToggleUnread = { viewModel.toggleUnread(chat) },
            // Uložené zprávy archivovat nejdou — TDLib by to odmítl.
            onToggleArchived = if (chat.id == savedMessagesChatId) null else {
                { viewModel.toggleArchived(chat) }
            },
            folders = folderChoices,
            onToggleFolder = { id -> viewModel.toggleFolder(chat, id) },
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
    onForumClick: (Long) -> Unit,
    padding: PaddingValues,
    empty: @Composable BoxScope.() -> Unit,
    scrollToChat: Long? = null,
    onScrolledToChat: () -> Unit = {},
    headerItemCount: Int = 0,
    listState: LazyListState = rememberLazyListState(),
    header: LazyListScope.() -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Start je hotový, až seznam ukazuje chaty (nebo že žádné nejsou), ne s prvním snímkem.
    ReportDrawnWhen { state !is ChatListState.Loading }
    val separateUnread by viewModel.separateUnreadChats.collectAsStateWithLifecycle()
    val savedMessagesChatId by viewModel.savedMessagesChatId.collectAsStateWithLifecycle()
    val swipeActions by viewModel.swipeActions.collectAsStateWithLifecycle()
    val hiddenSavedMessagesChatId by viewModel.hiddenSavedMessagesChatId.collectAsStateWithLifecycle()

    // Režim uspořádání připnutých: zpět ho zruší bez uložení, teprve další zpět odejde.
    val reorder by viewModel.reorder.collectAsStateWithLifecycle()
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = reorder != null,
        onBackCompleted = viewModel::cancelReorder,
    )
    // Výběr víc chatů: zpět ho ukončí, teprve další zpět odejde (jako výběr zpráv).
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = selection != null,
        onBackCompleted = viewModel::closeSelection,
    )
    val reorderIds = reorder?.chats?.map { it.id }.orEmpty()
    val dragState = rememberDragReorderState(
        listState = listState,
        canMove = { key -> key in reorderIds },
        onMove = { from, to -> viewModel.moveInReorder(from as Long, to as Long) },
    )

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


    val snoozes by viewModel.snoozeState.collectAsStateWithLifecycle()
    val chatColors by viewModel.chatColors.collectAsStateWithLifecycle()
    val chatAccents = rememberChatAccents(chatColors.values.toSet())
    val groups = (state as? ChatListState.Ready)?.chats?.let { chats ->
        remember(chats, separateUnread, hiddenSavedMessagesChatId, snoozes.due) {
            chatGroups(chats, separateUnread, hiddenSavedMessagesChatId, snoozes.due)
        }
    }.orEmpty()

    /*
     * Dojetí na chat vybraný v hledání. Index se počítá ze skupin, protože `LazyListState` klíč
     * položky mimo obrazovku na index nepřevede: kotva, hlavička, pak chaty skupinu po skupině.
     * Chat, který je celý vidět, se nehýbe — skok by jen zbytečně odsunul to, na co uživatel
     * kouká. Chat v seznamu není (archiv, mimo složky): požadavek se jen zahodí.
     */
    // Chat otevřený zkratkou z klávesnice: seznam za ním dojede stejně jako za chatem z hledání.
    var shortcutTarget by remember { mutableStateOf<Long?>(null) }
    ChatSwitchShortcuts(
        enabled = reorder == null && selection == null,
        chats = groups,
        selectedChatId = selectedChatId,
        onOpen = { chat ->
            shortcutTarget = chat.id
            if (chat.opensAsTopics) onForumClick(chat.id) else onChatClick(chat.id)
        },
    )

    LaunchedEffect(scrollToChat ?: shortcutTarget, groups) {
        val chatId = scrollToChat ?: shortcutTarget ?: return@LaunchedEffect
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
        if (scrollToChat != null) onScrolledToChat() else shortcutTarget = null
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
                    start = LIST_CONTENT_INSET,
                    end = LIST_CONTENT_INSET,
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

                /*
                 * Uspořádání ukazuje jen připnuté: ostatní chaty se přeskládat nedají a karty
                 * nad seznamem by jen odsouvaly to, s čím se pracuje. Stejně jako u témat.
                 */
                reorder?.let { current ->
                    item(key = REORDER_HINT_KEY, contentType = REORDER_HINT_KEY) {
                        Text(
                            stringResource(Res.string.chats_reorder_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
                        )
                    }
                    val pinned = current.chats
                    itemsIndexed(pinned, key = { _, chat -> chat.id }) { index, chat ->
                        val dragged = dragState.draggedKey == chat.id
                        ReorderRow(
                            key = chat.id,
                            title = chatTitle(chat.id, chat.title),
                            shape = groupItemShape(index, pinned.size),
                            dragState = dragState,
                            onMoveUp = pinned.getOrNull(index - 1)?.let { above -> { viewModel.moveInReorder(chat.id, above.id) } },
                            onMoveDown = pinned.getOrNull(index + 1)?.let { below -> { viewModel.moveInReorder(chat.id, below.id) } },
                            // Tažená položka jede pod prstem; animace umístění by se s tím prala.
                            modifier = Modifier
                                .then(if (dragged) Modifier else Modifier.animateItem())
                                .draggedItem(dragState, chat.id),
                        ) {
                            Avatar(
                                chatId = chat.avatarColorId,
                                title = chat.title,
                                avatarFileId = chat.avatarFileId,
                                size = REORDER_AVATAR_SIZE,
                                isSavedMessages = isSavedMessages(chat.id),
                                shape = chat.kind.avatarShape(),
                                backdrop = MaterialTheme.colorScheme.surfaceContainerLowest,
                            )
                        }
                    }
                    return@LazyColumn
                }

                header()

                groups.forEachIndexed { groupIndex, group ->
                    itemsIndexed(group, key = { _, chat -> chat.id }) { index, chat ->
                        // Ve výběru zvýrazňuje řádek výběr, ne chat otevřený v detailu vedle —
                        // dvě různé barvy pro „vybraný" by se v jednom seznamu pletly.
                        val selecting = selection != null
                        val picked = selection?.ids?.contains(chat.id) == true
                        val highlighted = if (selecting) picked else chat.id == selectedChatId
                        // Krajní položky skupiny nesou její zaoblení, prostřední se drží u sebe.
                        val shape = groupItemShape(
                            firstInGroup = index == 0,
                            lastInGroup = index == group.lastIndex,
                            selected = highlighted,
                        )
                        SwipeableChat(
                            // Ve výběru tah nejde: akce jdou lištou a tah by mířil jen na jeden chat.
                            // Co tah s chatem udělá (a jestli vůbec), řeší `chatSwipeEffect`.
                            right = chatSwipeEffect(swipeActions.right, chat, savedMessagesChatId, chat.id in snoozes.sleeping).takeUnless { selecting },
                            left = chatSwipeEffect(swipeActions.left, chat, savedMessagesChatId, chat.id in snoozes.sleeping).takeUnless { selecting },
                            shape = shape,
                            onSwipe = { effect -> viewModel.swipe(chat, effect) },
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
                                selected = highlighted,
                                selectionMark = picked.takeIf { selecting && chat.isSelectable() },
                                snooze = when {
                                    chat.id in snoozes.due -> RowSnooze.Due
                                    else -> snoozes.sleeping[chat.id]?.let(RowSnooze::Sleeping)
                                },
                                shape = shape,
                                accent = chatColors[chat.id]?.let(chatAccents::get),
                                onClick = {
                                    if (!selecting) viewModel.opened(chat.id)
                                    when {
                                        selecting -> viewModel.toggleSelected(chat)
                                        chat.opensAsTopics -> onForumClick(chat.id)
                                        else -> onChatClick(chat.id)
                                    }
                                },
                                // Ve výběru i dlouhý stisk jen přepíná — nabídka nad jedním chatem
                                // by z výběru vytrhla.
                                onLongClick = {
                                    if (selecting) viewModel.toggleSelected(chat) else viewModel.showActions(chat)
                                },
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
 * Chat tahem do strany: akce podle Nastavení, zvlášť pro tah doprava ([right]) a doleva
 * ([left]); `null` = na tu stranu tah nejde. Nevratné nic z toho není — archivace má snackbar
 * „Vrátit" jako z nabídky ([ChatActionsHost]) a ostatní akce se týmž tahem otočí, takže se tah
 * nepotvrzuje.
 *
 * **Vlastní detektor, ne `SwipeToDismissBox`.** Ten stojí na `draggable`, které si tah vezme po
 * obyčejném touch slopu a je přitom směrově agnostické — palec jde na začátku scrollu obloukem do
 * strany, takže seznamu gesto sebral a chaty odlétaly do archivu při obyčejném scrollování.
 * A archivoval i ZKRATKOU PŘES RYCHLOST: tah rychlejší než 125 dp/s bral jako dokončený bez
 * ohledu na to, kam došel, a vypnout to zvenku nejde. Tady rozhoduje [detectHorizontalSwipe]
 * (výrazně vodorovný tah, dvojnásobek slopu) a [chatSwipeThreshold] (kus šířky řádku);
 * rychlost se nepočítá vůbec.
 */
@Composable
private fun SwipeableChat(
    right: ChatSwipeEffect?,
    left: ChatSwipeEffect?,
    shape: RoundedCornerShape,
    onSwipe: (ChatSwipeEffect) -> Unit,
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
    /** Tah je právě za prahem; hlídá, aby se haptika ozvala jednou za každé překročení. */
    var armed by remember { mutableStateOf(false) }
    // Spec se musí vzít v kompozici — konec gesta je obyčejná suspend funkce, ne composable.
    val returnSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    // Detektor běží v `pointerInput(Unit)` přes celý život položky; akce se mění se stavem
    // chatu (ztlumený → odtlumit), takže se čtou čerstvé, ne ty z prvního snímku.
    val currentRight by rememberUpdatedState(right)
    val currentLeft by rememberUpdatedState(left)
    // Totéž pro akci: lambda drží chat z kompozice, ve které vznikla, a ten ze prvního snímku
    // by tvrdil, že nepřipnutý chat pořád není připnutý — druhý tah by ho připnul znovu.
    val currentOnSwipe by rememberUpdatedState(onSwipe)

    // Šířka řádku pro práh mimo `pointerInput` — podklad ji potřebuje ke zvýraznění ikony.
    var width by remember { mutableIntStateOf(0) }
    // Ikona ukazuje, jestli by se akce provedla PRÁVĚ TEĎ: vrácený prst ji vrátí do kolečka.
    val overThreshold by remember {
        derivedStateOf { width > 0 && abs(drag.value) >= chatSwipeThreshold(width.toFloat(), thresholdCap) }
    }

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

    // Klíč je směr, ne akce: změna akce (ztlumený chat) detektor nerestartuje, změna toho, kam
    // táhnout jde (Nastavení, vstup do výběru), ano — jinak by se chytal tah na zakázanou stranu.
    val direction = when {
        right != null && left != null -> SwipeDirection.Either
        right != null -> SwipeDirection.Right
        left != null -> SwipeDirection.Left
        else -> null
    }

    Box(
        modifier.onSizeChanged { width = it.width }.then(
            if (direction == null) {
                Modifier
            } else {
                Modifier.pointerInput(direction) {
                    val maxDrag = { size.width.toFloat() }
                    val threshold = { chatSwipeThreshold(size.width.toFloat(), thresholdCap) }
                    detectHorizontalSwipe(
                        direction = direction,
                        onDrag = { delta ->
                            coroutineScope.launch {
                                // Na stranu bez akce se řádek nepohne, ani když tah začal opačně.
                                val min = if (currentLeft != null) -maxDrag() else 0f
                                val max = if (currentRight != null) maxDrag() else 0f
                                val next = (drag.value + delta).coerceIn(min, max)
                                drag.snapTo(next)
                                // Vrácený prst práh zase odjistí: další překročení v témže tahu
                                // se musí ozvat znovu, stejně jako se znovu mění ikona.
                                val past = abs(next) >= threshold()
                                if (past && !armed) haptics.threshold()
                                armed = past
                            }
                        },
                        onEnd = { released ->
                            val committed = (if (drag.value > 0f) currentRight else currentLeft)
                                ?.takeIf { released && abs(drag.value) >= threshold() }
                            val target = if (drag.value > 0f) maxDrag() else -maxDrag()
                            armed = false
                            coroutineScope.launch {
                                /*
                                 * Akce se provede při PUŠTĚNÍ, ne při překročení prahu — kdo si to
                                 * rozmyslí, vrátí prst zpátky a nic se nestane. Akce odchází
                                 * PŘED animací odjezdu: odscrollovaná položka se uklidí
                                 * z kompozice i s tímhle rozsahem a animace by ji vzala s sebou.
                                 */
                                committed?.let(currentOnSwipe)
                                if (committed?.leavesList == true) {
                                    drag.animateTo(target, returnSpec)
                                    /*
                                     * Chat, který po archivaci ze seznamu odešel, se uklidí i s tímhle
                                     * rozsahem a sem se nedojde. Zůstal-li (složka s archivovanými
                                     * chaty, chyba od TDLib), vrátí se — jinak by v seznamu visel
                                     * neviditelný řádek, na který jde ťuknout.
                                     */
                                    delay(LEFT_ROW_RETURN_DELAY)
                                }
                                drag.animateTo(0f, returnSpec)
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
        val effect = when (side) {
            SwipeSide.Start -> right
            SwipeSide.End -> left
            SwipeSide.None -> null
        }
        if (effect != null) {
            Box(
                Modifier
                    .matchParentSize()
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = if (side == SwipeSide.End) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                val icon = @Composable {
                    // Za prahem kolečko naroste do sušenky a vyplní se — spolu s haptikou.
                    SwipeThresholdBadge(
                        armed = overThreshold,
                        painter = painterResource(effect.icon),
                        surface = MaterialTheme.colorScheme.primaryContainer,
                        idleFill = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = SWIPE_BADGE_IDLE_ALPHA),
                        idleIcon = MaterialTheme.colorScheme.onPrimaryContainer,
                        size = 40.dp,
                        iconSize = 24.dp,
                    )
                }
                val label = @Composable {
                    Text(
                        text = stringResource(effect.label),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Row(
                    // 16 + (40 − 24) / 2 = 24 dp: ikona stojí tam, kde stávala bez kolečka.
                    Modifier.padding(horizontal = 16.dp),
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

/**
 * Popisek akce na podkladu pod taženým chatem: říká, co se STANE, ne jaký je stav. Kratší než
 * věty z nabídky nad chatem — popisek leží v odkryté části a „Označit jako nepřečtené" by
 * u prahu (40 % šířky) zůstalo z půlky pod řádkem.
 */
private val ChatSwipeEffect.label: StringResource
    get() = when (this) {
        ChatSwipeEffect.Archive -> Res.string.chats_swipe_archive
        ChatSwipeEffect.Unarchive -> Res.string.chats_swipe_unarchive
        ChatSwipeEffect.MarkRead -> Res.string.chats_swipe_read
        ChatSwipeEffect.MarkUnread -> Res.string.chats_swipe_unread
        ChatSwipeEffect.Mute -> Res.string.chats_swipe_mute
        ChatSwipeEffect.Unmute -> Res.string.chats_swipe_unmute
        ChatSwipeEffect.Pin -> Res.string.chats_swipe_pin
        ChatSwipeEffect.Unpin -> Res.string.chats_swipe_unpin
        ChatSwipeEffect.Snooze -> Res.string.chats_swipe_snooze
        ChatSwipeEffect.Unsnooze -> Res.string.chats_swipe_unsnooze
    }

/** Ikony z nabídky nad chatem, ať tah a nabídka mluví stejně. */
private val ChatSwipeEffect.icon: DrawableResource
    get() = when (this) {
        ChatSwipeEffect.Archive -> Res.drawable.archive_24dp
        ChatSwipeEffect.Unarchive -> Res.drawable.unarchive_24dp
        ChatSwipeEffect.MarkRead, ChatSwipeEffect.MarkUnread -> Res.drawable.mark_chat_unread_24dp
        ChatSwipeEffect.Mute -> Res.drawable.notifications_off_24dp
        ChatSwipeEffect.Unmute -> Res.drawable.notifications_24dp
        ChatSwipeEffect.Pin -> Res.drawable.push_pin_filled_24dp
        ChatSwipeEffect.Unpin -> Res.drawable.keep_off_24dp
        ChatSwipeEffect.Snooze, ChatSwipeEffect.Unsnooze -> Res.drawable.snooze_24dp
    }

/** Podklad ikony akce do prahu: jemný tón barvy popředí na `primaryContainer`. */
private const val SWIPE_BADGE_IDLE_ALPHA = 0.12f

/**
 * Za jak dlouho po odjezdu se řádek vrátí, když chat v seznamu zůstal. Delší než odpověď TDLib
 * na archivaci a zmizení položky z obou seznamů, aby se odjetý chat nevracel do seznamu, ze
 * kterého právě odchází.
 */
private val LEFT_ROW_RETURN_DELAY = 1.seconds

/** Ze které hrany se odkrývá podklad pod taženým chatem. */
private enum class SwipeSide { None, Start, End }

/**
 * Strop prahu akce tahem. Na široké ploše (tablet, dvoupanel) by podíl šířky znamenal tah přes
 * půl obrazovky; na telefonu se neuplatní.
 */
private val SWIPE_THRESHOLD_MAX = 160.dp

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun ChatRow(
    chat: ChatSummary,
    selected: Boolean,
    /** Značka výběru víc chatů; `null` = výběr neběží (nebo chat vybrat nejde). */
    selectionMark: Boolean?,
    /** Odložení chatu; `null` = neodložený. */
    snooze: RowSnooze?,
    /** Tvar bloku — počítá ho [groupItemShape] vedle, protože ho potřebuje i podklad pod tahem. */
    shape: RoundedCornerShape,
    /** Paleta z barvy konverzace (odznaky nepřečtených); `null` = barvy aplikace. */
    accent: ColorScheme?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    /*
     * Spící odložený chat se tváří jako přečtený: jméno bez tučnosti, čas bez akcentu, odznaky
     * v neutrální barvě a místo náhledu věta, do kdy spí. Ze seznamu nezmizí — ztratit chat
     * z očí i z hledání pohledem by bylo víc, než si uživatel řekl.
     */
    val sleeping = snooze is RowSnooze.Sleeping
    val showUnread = chat.hasUnread && !sleeping
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

    // Řádek se při otevření chatu rozroste v jeho obrazovku (container transform).
    val containerSource = rememberChatContainerSource(chat.id)

    // `Card(onClick = …)` umí jen krátké ťuknutí. Dlouhý stisk potřebuje `combinedClickable`,
    // takže se klikání přesouvá na Surface — Card zůstává jen tvarem a barvou.
    Surface(
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
        modifier = modifier
            .then(containerSource.container)
            .fillMaxWidth()
            .clip(shape)
            .contextClickable(onClick = onClick, onLongClick = onLongClick)
            .then(if (selectionMark != null) Modifier.semantics { this.selected = selectionMark } else Modifier),
    ) {
        Row(
            containerSource.content.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box {
                Avatar(
                    chatId = chat.avatarColorId,
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
                // Ve výběru má roh avataru značka výběru; tečka dostupnosti by se s ní prala.
                if (selectionMark != null) {
                    SelectionMark(
                        selected = selectionMark,
                        rim = containerColor,
                        contentColor = contentColor,
                        modifier = Modifier.align(Alignment.BottomEnd).offset(x = 4.dp, y = 4.dp),
                    )
                } else if (chat.presence is Presence.Online) {
                    OnlineDot(rim = containerColor, modifier = Modifier.align(Alignment.BottomEnd), chatId = chat.id)
                }
            }

            Column(Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // Vynořená připomínka: ikona na místě připínáčku a v akcentu, ať je poznat,
                    // proč chat stojí nahoře.
                    if (snooze == RowSnooze.Due) {
                        Icon(
                            painterResource(Res.drawable.snooze_24dp),
                            contentDescription = stringResource(Res.string.chats_snooze_reminder),
                            modifier = Modifier.size(14.dp),
                            tint = accentOn(containerColor, MaterialTheme.colorScheme.primary, secondaryColor),
                        )
                    } else if (chat.isPinned) {
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
                        val badgeAccent = accentOn(
                            background = containerColor,
                            accent = MaterialTheme.colorScheme.primary,
                            fallback = LocalContentColor.current,
                        )
                        if (chat.isSecret) SecretChatLock(tint = badgeAccent)
                        Text(
                            text = chatTitle(chat.id, chat.title),
                            // `bodyLarge` místo `titleMedium`: stejná velikost, ale běžný řez.
                            // Polotučné jméno u KAŽDÉHO chatu znamená, že nevyčnívá žádný —
                            // tučností se proto odlišuje jen nepřečtený, jako v Google Messages.
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (showUnread) FontWeight.SemiBold else null,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false).sharedNavText(chatTitleKey(chat.id)),
                        )
                        chat.verification?.let { VerificationBadge(verification = it, accent = badgeAccent) }
                        chat.emojiStatus?.let { EmojiStatusBadge(it, accent = badgeAccent) }
                    }

                    if (chat.isMuted) {
                        Icon(
                            painterResource(Res.drawable.notifications_off_24dp),
                            contentDescription = stringResource(Res.string.chats_muted),
                            modifier = Modifier.size(14.dp),
                            tint = secondaryColor,
                        )
                    }
                    if (sleeping) {
                        Icon(
                            painterResource(Res.drawable.snooze_24dp),
                            contentDescription = stringResource(Res.string.chats_snoozed),
                            modifier = Modifier.size(14.dp),
                            tint = secondaryColor,
                        )
                    }

                    // Čas je zarovnaný doprava na konec řádku s názvem, ne pod něj — jinak by
                    // soupeřil o místo s náhledem zprávy, který bývá dlouhý.
                    // Koncept posouvá chat v seznamu nahoru, takže čas musí jít s ním — jinak by
                    // u chatu nahoře stálo datum starší než u těch pod ním.
                    // Sponzorovaný chat má místo času důvod, proč v seznamu je: uživatel do něj
                    // nevstoupil a bez štítku by vypadal jako kanál, který si přidal a zapomněl.
                    chat.sponsor?.let { sponsor ->
                        Text(
                            text = stringResource(
                                when (sponsor) {
                                    ChatSponsor.Proxy -> Res.string.chats_sponsor_proxy
                                    is ChatSponsor.Announcement -> Res.string.chats_sponsor_announcement
                                },
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = secondaryColor,
                            maxLines = 1,
                        )
                    } ?: listOfNotNull(chat.lastMessageTimestamp, chat.draft?.timestamp).maxOrNull()?.let { timestamp ->
                        Text(
                            text = timestamp.asRelativeTime(),
                            // Časy stojí pod sebou u pravého okraje — stejně široké číslice
                            // je drží v zákrytu.
                            style = MaterialTheme.typography.labelMedium.tabularNumbers(),
                            // U nepřečteného chatu je čas akcentem, stejně jako odznak pod ním —
                            // oči jdou po pravém sloupci a hledají, kde se něco stalo.
                            color = if (showUnread && !selected) {
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
                // Vlastní emoji v náhledu (a konceptu) jako obrázek, ne náhradní znak.
                val customEmoji = mutableSetOf<Long>()
                val sleepingUntil = (snooze as? RowSnooze.Sleeping)?.let { (it.until / 1000).asUpcomingTime() }
                val subtitle: AnnotatedString? = sleepingUntil?.let { AnnotatedString(stringResource(Res.string.chats_snoozed_until, it)) }
                    ?: chat.activity?.label()?.let { AnnotatedString(stringResource(it)) }
                    ?: chat.draft?.let { draft ->
                        buildAnnotatedString {
                            // Štítek jinou barvou než „píše…" (primary) — jde o náš text, ne o dění.
                            withStyle(SpanStyle(color = draftLabelColor)) { append(stringResource(Res.string.chats_draft_prefix)) }
                            // Zalomení by v jednořádkovém náhledu useklo text hned na začátku;
                            // mezera je stejně dlouhá, takže offsety emoji sedí dál.
                            customEmoji += appendWithCustomEmoji(draft.text.replace('\n', ' '), draft.entities)
                        }
                    }
                    ?: chat.lastMessagePreview?.resolve()?.let { preview ->
                        buildAnnotatedString {
                            customEmoji += appendWithCustomEmoji(preview, chat.lastMessagePreviewEntities)
                        }
                    }

                subtitle?.let {
                    Text(
                        text = it,
                        inlineContent = customEmojiInlineContent(customEmoji),
                        style = MaterialTheme.typography.bodyMedium,
                        // Nepřečtený náhled se odliší barvou, ne řezem: s polotučným názvem nad
                        // ním by byly na jednom řádku dvě úrovně tučnosti a blok by ztěžkl.
                        color = when {
                            selected -> MaterialTheme.colorScheme.onSecondaryContainer
                            sleeping -> secondaryColor
                            // Činnost se odliší barvou, aby bylo poznat, že to není text zprávy.
                            chat.activity != null -> accentOn(containerColor, MaterialTheme.colorScheme.primary, contentColor)
                            chat.hasUnread -> MaterialTheme.colorScheme.onSurface
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            UnreadBadges(chat, quiet = sleeping, accent = accent)
        }
    }
}

/** Odložení chatu z pohledu řádku: ještě spí (do [Sleeping.until], ms od epochy), nebo se vynořil. */
private sealed interface RowSnooze {
    data class Sleeping(val until: Long) : RowSnooze
    data object Due : RowSnooze
}

/**
 * Odznaky vpravo v řádku: reakce, zmínka, počet nepřečtených — v tomhle pořadí, počet u kraje.
 *
 * Zmínka a reakce mají vlastní odznak, protože se v počtu ztratí: „@" ve skupině se stovkou
 * nepřečtených je to jediné, kvůli čemu ji uživatel otevře. Číslo nenesou (kolik jich je, řekne
 * tlačítko v konverzaci) — jde o to, ŽE nějaké jsou.
 */
@Composable
private fun UnreadBadges(
    chat: ChatSummary,
    /** Odznaky v neutrální barvě — u odloženého chatu, který se má do té doby neozývat. */
    quiet: Boolean = false,
    /** Paleta z barvy konverzace; odznak je pak jediné místo v řádku, které ji nese. */
    accent: ColorScheme? = null,
) {
    val scheme = accent ?: MaterialTheme.colorScheme
    val container = if (quiet) MaterialTheme.colorScheme.surfaceContainerHighest else scheme.primary
    val content = if (quiet) MaterialTheme.colorScheme.onSurfaceVariant else scheme.onPrimary
    if (chat.unreadPollVoteCount == 0 && chat.unreadReactionCount == 0 && chat.unreadMentionCount == 0 && chat.unreadCount == 0) return
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (chat.unreadPollVoteCount > 0) {
            IconBadge(Res.drawable.ballot_24dp, stringResource(Res.string.chats_unread_poll_vote), container, content)
        }
        if (chat.unreadReactionCount > 0) {
            IconBadge(Res.drawable.favorite_filled_24dp, stringResource(Res.string.chats_unread_reaction), container, content)
        }
        if (chat.unreadMentionCount > 0) {
            IconBadge(Res.drawable.alternate_email_24dp, stringResource(Res.string.chats_unread_mention), container, content)
        }
        if (chat.unreadCount > 0) {
            // `Badge` je ve výchozím stavu chybově červený. Nepřečtená zpráva ale není
            // chyba — v odlehčeném seznamu byla ta červená jediná sytá barva na obrazovce
            // a přebíjela všechno ostatní.
            Badge(containerColor = container, contentColor = content) {
                RollingNumber(chat.unreadCount, style = LocalTextStyle.current.tabularNumbers())
            }
        }
    }
}

/** Kulatý odznak s ikonou, velký jako odznak s jednou číslicí. */
@Composable
private fun IconBadge(icon: DrawableResource, description: String, container: Color, content: Color) {
    Badge(
        containerColor = container,
        contentColor = content,
        modifier = Modifier.semantics { contentDescription = description },
    ) {
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(12.dp))
    }
}

/**
 * Palety barev, které mají některé chaty — jedna na barvu, ne na řádek: vygenerovat paletu stojí
 * desítky výpočtů v HCT a řádky se při scrollu skládají pořád znovu.
 *
 * Barva chatu je v seznamu JEN v odznaku nepřečtených. Celý řádek v barvě by ze seznamu udělal
 * pestrou mozaiku a barva by přestala cokoli znamenat; odznak je sytá tečka, kterou oko hledá
 * stejně, a nepřečtený chat je právě ten, u kterého na rozlišení záleží.
 */
@Composable
private fun rememberChatAccents(used: Set<ThemeColor>): Map<ThemeColor, ColorScheme> {
    val isDark = LocalIsDarkTheme.current
    val accents = HashMap<ThemeColor, ColorScheme>()
    ThemeColor.entries.forEach { color ->
        key(color) {
            if (color in used) accents[color] = chatColorScheme(isDark, color.seed)
        }
    }
    return accents
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
private const val REORDER_HINT_KEY = "reorder-hint"
private val REORDER_AVATAR_SIZE = 40.dp

/** Klíč karty s aktualizací. */
private const val UPDATE_CARD_KEY = "update-card"
private const val FROZEN_ACCOUNT_KEY = "frozen-account"
private const val SUGGESTION_KEY = "suggested-action"
private const val BIRTHDAYS_KEY = "close-birthdays"
private const val NEW_FOLDER_CHATS_KEY = "new-folder-chats"
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

/** Odsazení FAB od okraje obrazovky — výchozí M3 (FAB menu si ho dává samo). */
private val FAB_MARGIN = 16.dp

/** Ztlumení seznamu pod otevřeným FAB menu. */
private const val FAB_MENU_SCRIM_ALPHA = 0.8f

/**
 * O kolik se prodlužuje spodek seznamu kvůli FAB: jeho výška (56 dp), odsazení od okraje
 * a trochu vzduchu, aby poslední chat nekončil těsně pod ním.
 */
private val FAB_CLEARANCE = 56.dp + FAB_MARGIN + 8.dp

/** FAB v rozbalené liště složek (standardní) a ve sbalené (střední) — hodnoty `FloatingToolbar`. */
private val TOOLBAR_FAB_EXPANDED_SIZE = 56.dp
private val TOOLBAR_FAB_COLLAPSED_SIZE = 80.dp

/**
 * Totéž s lištou složek: lišta s FAB je vysoká jako sbalený FAB (80 dp), aby při jeho
 * nafouknutí neposkočila.
 */
private val FAB_CLEARANCE_WITH_FOLDERS = TOOLBAR_FAB_COLLAPSED_SIZE + FAB_MARGIN + 8.dp

/**
 * Alt+↓ / Alt+↑ (a Ctrl+Tab) z klávesnice: další a předchozí chat v pořadí, jak je vidět —
 * i s blokem nepřečtených ([neighbourChat] nad skupinami). Jen když je seznam složený: na
 * telefonu s otevřenou konverzací ho konverzace překrývá a zkratka nedělá nic; na rozloženém
 * Foldu se tak prochází chat za chatem a vpravo se mění konverzace. Ve výběru a v uspořádání
 * připnutých ne — tam by otevření chatu z režimu vytrhlo.
 */
@Composable
private fun ChatSwitchShortcuts(
    enabled: Boolean,
    chats: List<List<ChatSummary>>,
    selectedChatId: Long?,
    onOpen: (ChatSummary) -> Unit,
) {
    val shortcuts = koinInject<KeyboardShortcuts>()
    val currentEnabled by rememberUpdatedState(enabled)
    val currentChats by rememberUpdatedState(chats)
    val currentSelected by rememberUpdatedState(selectedChatId)
    val currentOnOpen by rememberUpdatedState(onOpen)
    LaunchedEffect(shortcuts) {
        shortcuts.shortcuts.collect { shortcut ->
            val next = when (shortcut) {
                KeyboardShortcut.NextChat -> true
                KeyboardShortcut.PreviousChat -> false
                else -> return@collect
            }
            if (!currentEnabled) return@collect
            val flat = currentChats.flatten().filter { it.sponsor == null }
            val id = neighbourChat(flat.map { it.id }, currentSelected, next) ?: return@collect
            flat.firstOrNull { it.id == id }?.let(currentOnOpen)
        }
    }
}
