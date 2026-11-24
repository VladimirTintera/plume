package eu.tintera.plume.features.chats

import androidx.compose.foundation.lazy.LazyItemScope
import eu.tintera.plume.core.settings.AppSettings
import eu.tintera.plume.core.accounts.LocalAccount
import eu.tintera.plume.core.accounts.AccountSlot
import eu.tintera.plume.core.accounts.AccountScope
import eu.tintera.plume.resources.accounts_already_signed_in
import eu.tintera.plume.core.accounts.Accounts
import eu.tintera.plume.features.conversation.ClearHistorySheet
import eu.tintera.plume.core.ui.CountdownSnackbarHost
import eu.tintera.plume.core.ui.UNDO_COUNTDOWN_MS
import eu.tintera.plume.core.ui.groupRow
import eu.tintera.plume.core.ui.showCountdownSnackbar
import eu.tintera.plume.resources.chats_deleted
import eu.tintera.plume.resources.chats_left
import eu.tintera.plume.resources.chats_deleted_many
import eu.tintera.plume.resources.chats_left_many
import androidx.compose.material3.Icon
import org.jetbrains.compose.resources.painterResource
import androidx.compose.foundation.layout.width
import eu.tintera.plume.resources.add_24dp
import eu.tintera.plume.resources.chat_folder_add_chats
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import eu.tintera.plume.core.ui.animatedGroupItemShape
import eu.tintera.plume.core.ui.GROUP_CORNER
import eu.tintera.plume.core.ui.GROUP_ITEM_SPACING
import eu.tintera.plume.core.ui.INNER_CORNER
import eu.tintera.plume.core.ui.LocalChatRowGaps
import eu.tintera.plume.core.telegram.canBeArchived
import androidx.compose.material3.FilledTonalButton
import eu.tintera.plume.resources.error_24dp
import eu.tintera.plume.resources.chat_24dp
import eu.tintera.plume.resources.empty_state_error_title
import eu.tintera.plume.resources.chats_empty_title
import eu.tintera.plume.features.notices.rememberNoticeLinkOpener
import androidx.compose.material3.ButtonDefaults
import org.koin.compose.koinInject
import eu.tintera.plume.core.ui.neighbourChat
import eu.tintera.plume.core.ui.KeyboardShortcuts
import eu.tintera.plume.core.ui.KeyboardShortcut
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.derivedStateOf
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import eu.tintera.plume.core.adaptive.COLLAPSED_LIST_MAX_WIDTH
import eu.tintera.plume.core.adaptive.LIST_CONTENT_INSET
import eu.tintera.plume.core.adaptive.LocalExpandListPane
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import eu.tintera.plume.core.ui.ReportDrawnWhen
import eu.tintera.plume.core.ui.resolveNow
import eu.tintera.plume.core.ui.isSavedMessages
import eu.tintera.plume.core.ui.chatTitle
import eu.tintera.plume.core.telegram.isPrivate
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.animation.togetherWith
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.material3.LoadingIndicator
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import eu.tintera.plume.core.ui.rememberShownConnection
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import eu.tintera.plume.core.update.rememberStartUpdate
import eu.tintera.plume.core.telegram.ChatListState
import eu.tintera.plume.features.report.ReportHost
import eu.tintera.plume.core.telegram.ChatSummary
import eu.tintera.plume.core.ui.Avatar
import eu.tintera.plume.core.ui.StoryRingState
import eu.tintera.plume.features.stories.StoryStack
import eu.tintera.plume.core.telegram.OutgoingAttachment
import eu.tintera.plume.core.telegram.ChatFolder
import eu.tintera.plume.core.ui.rememberAttachmentPicker
import eu.tintera.plume.core.ui.StoryVideo
import eu.tintera.plume.core.ui.ReorderRow
import eu.tintera.plume.core.ui.ReorderTopBar
import eu.tintera.plume.resources.chats_reorder_title
import eu.tintera.plume.core.ui.draggedItem
import eu.tintera.plume.core.ui.groupItemShape
import eu.tintera.plume.core.ui.rememberDragReorderState
import eu.tintera.plume.resources.chats_reorder_hint
import eu.tintera.plume.core.ui.label
import eu.tintera.plume.core.ui.resolve
import eu.tintera.plume.core.telegram.opensAsTopics
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.chats_archived
import eu.tintera.plume.resources.chats_archived_many
import eu.tintera.plume.resources.chats_unarchived_many
import eu.tintera.plume.resources.chats_undo
import eu.tintera.plume.resources.chats_blocked
import eu.tintera.plume.resources.chats_unblocked
import eu.tintera.plume.resources.chats_unarchived
import eu.tintera.plume.resources.chats_empty
import eu.tintera.plume.resources.chats_folder_empty
import eu.tintera.plume.resources.chats_folder_empty_title
import eu.tintera.plume.resources.common_try_again
import androidx.compose.ui.semantics.selected
import eu.tintera.plume.core.ui.IntroEmptyState
import eu.tintera.plume.core.ui.folderIcon
import eu.tintera.plume.core.ui.avatarShape
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.unit.max

@Composable
fun ChatListScreen(
    viewModel: ChatsViewModel,
    selectedChatId: Long?,
    navigation: ChatListNavigation,
    /** Účet chatu otevřeného vedle seznamu; totéž id může mít chat v jiném účtu. */
    selectedChatSlot: AccountSlot? = null,
    /**
     * Chat, na který má seznam dojet (vybraný v hledání, `AppNavigation.chatListScroll`); po
     * dojetí [onScrolledToChat].
     */
    scrollToChat: Long? = null,
    onScrolledToChat: () -> Unit = {},
    /** Vedle seznamu je otevřená konverzace (dvoupanel) — hudbu pak ukazuje její dock. */
    conversationOpen: Boolean = false,
    nowPlayingViewModel: NowPlayingViewModel = koinViewModel(),
    modifier: Modifier = Modifier,
) {

    /*
     * Víc účtů: společný seznam (režim Vše, `docs/navrh-vice-uctu.md`, „Společný seznam
     * chatů"); zapíná se v menu účtu. Obrazovka zůstává obrazovkou výchozího účtu — hledání, složky,
     * menu a karty nahoře jsou jeho —, společné jsou jen řádky. Výběr víc chatů a uspořádání
     * připnutých patří jednomu účtu: po dobu, co běží, ukazuje seznam jen chaty toho účtu.
     */
    val screenAccount = LocalAccount.current
    val accounts = koinInject<Accounts>()
    val mergedChats = rememberMergedChats(ChatFolder.Main)
    val sources = mergedChats.sources
    val busySource = mergedChats.busy
    val merged = mergedChats.merged
    // Čí řádky ukazuje seznam jednoho účtu: obrazovky, nebo účtu, ve kterém běží výběr.
    val listViewModel = busySource?.viewModel ?: viewModel
    val listAccount = busySource?.account ?: screenAccount
    // Otevřené sheety nad chatem, každý ViewModel svoje (viz [ChatSheets]).
    val sheets = remember(viewModel) { ChatSheets() }
    val listSheets = busySource?.sheets ?: sheets
    val storyStack by viewModel.storyStack.collectAsStateWithLifecycle()
    val storyNotice by viewModel.storyNotice.collectAsStateWithLifecycle()
    val storyVideoSupported = koinInject<StoryVideo>().supported
    // Výběr fotky nebo videa pro nový příběh; otevírá se až po kontrole limitu (`startStory`).
    val storyPicker = rememberAttachmentPicker(
        onPicked = { picked ->
            when (val first = picked.firstOrNull()) {
                is OutgoingAttachment.Photo -> navigation.onComposeStory?.invoke(first.path, false)
                // Video převede jen platforma s kodérem (desktop ho nemá).
                is OutgoingAttachment.Video ->
                    if (storyVideoSupported) navigation.onComposeStory?.invoke(first.path, true) else viewModel.storyNeedsPhoto()
                null -> Unit
                else -> viewModel.storyNeedsPhoto()
            }
        },
        onError = viewModel::storyError,
    )
    val onNewStory: (() -> Unit)? = navigation.onComposeStory
        ?.takeIf { viewModel.canComposeStory }
        ?.let { { viewModel.startStory(storyPicker::pickMedia) } }
    val connection by viewModel.connection.collectAsStateWithLifecycle()
    val shownConnection = rememberShownConnection(connection)
    val savedMessagesChatId by viewModel.savedMessagesChatId.collectAsStateWithLifecycle()
    val hiddenSavedMessagesChatId by viewModel.hiddenSavedMessagesChatId.collectAsStateWithLifecycle()
    val updateCard by viewModel.updateCard.collectAsStateWithLifecycle()
    val freeze by viewModel.freeze.collectAsStateWithLifecycle()
    val suggestion by viewModel.suggestion.collectAsStateWithLifecycle()
    val birthdays by viewModel.birthdays.collectAsStateWithLifecycle()
    val awaitingCard by viewModel.awaitingReplyCard.collectAsStateWithLifecycle()
    val accountPhone by viewModel.accountPhone.collectAsStateWithLifecycle()
    val openNoticeLink = rememberNoticeLinkOpener()
    val unconfirmedLogin by viewModel.unconfirmedLogin.collectAsStateWithLifecycle()
    val newFolderChats by viewModel.newFolderChats.collectAsStateWithLifecycle()
    val cards = ChatListCards(
        freeze = freeze,
        unconfirmedLogin = unconfirmedLogin,
        birthdays = birthdays,
        awaitingReply = awaitingCard,
        suggestion = suggestion,
        accountPhone = accountPhone,
        newFolderChats = newFolderChats,
        update = updateCard,
    )
    val account by viewModel.account.collectAsStateWithLifecycle()
    val archiveUnread by viewModel.archiveUnread.collectAsStateWithLifecycle()
    val drafts by viewModel.drafts.collectAsStateWithLifecycle()
    /*
     * Otevřené sheety (a FAB menu níž) jsou `remember`, ne ve ViewModelu ani `rememberSaveable`:
     * obojí přežije odchod seznamu z kompozice a sheet by se vrátil s prvním složením seznamu —
     * i jen v náhledu gesta zpět z konverzace otevřené zvenčí. Proč to vadí, viz [ChatSheets].
     * Koncepty, záložky a Můj QR kód jsou navigační destinace (`SheetRoute`), otevírá je shell.
     */
    val bookmarks by viewModel.allBookmarks.collectAsStateWithLifecycle()
    var showFolderSuggestions by remember { mutableStateOf(false) }
    var showNewFolderChats by remember { mutableStateOf(false) }
    // Složky jsou po účtech na serveru; společný seznam je jen hlavní seznam, pilulky nemá.
    val folderTabs = viewModel.folderTabs.collectAsStateWithLifecycle().value.takeIf { sources.isEmpty() }.orEmpty()
    val folderSuggestions by viewModel.folderSuggestions.collectAsStateWithLifecycle()
    val reorder by listViewModel.reorder.collectAsStateWithLifecycle()
    val selection by listViewModel.selection.collectAsStateWithLifecycle()
    val selectionActions by listViewModel.selectionActions.collectAsStateWithLifecycle()
    val allSelected by listViewModel.allSelected.collectAsStateWithLifecycle()
    val folderActions = remember(viewModel, navigation) {
        FolderTabActions(
            onReadAll = viewModel::readFolder,
            onEdit = navigation.onEditFolder,
            onManage = navigation.onManageFolders,
            onDelete = viewModel::deleteFolder,
        )
    }
    // Vybraná složka Telegramu (ne celý seznam) — kvůli prázdnému stavu.
    val selectedFolder = folderTabs.firstOrNull { it.selected && it.id != null }
    val startUpdate = rememberStartUpdate(viewModel.appUpdates)
    // Hledání při scrollu dolů odjede a při scrollu nahoru se vrátí.
    val scrollBehavior = SearchBarDefaults.enterAlwaysSearchBarScrollBehavior()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(storyNotice) {
        val message = storyNotice ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message.resolveNow())
        viewModel.storyNoticeShown()
    }
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
    var fabMenuExpanded by remember { mutableStateOf(false) }
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = fabMenuExpanded,
        onBackCompleted = { fabMenuExpanded = false },
    )

    ChatActionsHost(
        viewModel,
        sheets,
        snackbarHostState,
        onArchived = navigation.onChatArchived?.let { archived -> { change -> archived(screenAccount.slot, change) } },
        onOpenProxySettings = navigation.onOpenProxySettings,
    )
    // Nabídka nad chatem a hlášky z řádků společného seznamu: každý účet má svůj ViewModel.
    MergedChatActionsHosts(sources, snackbarHostState, navigation.onChatArchived, navigation.onOpenProxySettings)
    // Nové chaty patří vybrané složce: jiná složka (i vyřízené nové chaty) sheet zavře, jinak by
    // se sám otevřel nad kartou další složky.
    LaunchedEffect(newFolderChats?.folderId) { showNewFolderChats = false }
    newFolderChats?.takeIf { showNewFolderChats }?.let { folderChats ->
        NewFolderChatsSheet(
            state = folderChats,
            onToggle = viewModel::toggleNewFolderChat,
            onJoin = { viewModel.processNewFolderChats(join = true) },
            onDismiss = { showNewFolderChats = false },
        )
    }
    folderSuggestions?.takeIf { showFolderSuggestions }?.let { suggestions ->
        FolderSuggestionsSheet(
            state = suggestions,
            onAdd = { folder ->
                viewModel.addRecommendedFolder(folder, onAdded = { showFolderSuggestions = false })
                // Přidaná složka se rovnou vybere — začít od vršku jejího seznamu.
                scope.launch { listState.scrollToItem(0) }
            },
            onCreate = {
                showFolderSuggestions = false
                navigation.onCreateFolder()
            },
            onManage = {
                showFolderSuggestions = false
                navigation.onManageFolders()
            },
            onDismiss = { showFolderSuggestions = false },
        )
    }

    /*
     * Chat, na který má plný seznam dojet po rozbalení ze sbaleného sloupce (kotva přechodu).
     * Chat vybraný v hledání přichází zvenku ([scrollToChat], drží ho navigace).
     */
    var anchorScroll by rememberSaveable { mutableStateOf<Long?>(null) }

    // Obchod se ptá při otevření seznamu i při návratu do popředí — tak se po restartu připomene
    // i stažená, ale nenainstalovaná aktualizace.
    LifecycleResumeEffect(Unit) {
        viewModel.checkForUpdate()
        onPauseOrDispose { }
    }

    val accountSwitcher = rememberAccountSwitcher()
    // Přidávaný účet už v aplikaci je: přihlášení se zahodilo, tak to říct, ať uživatel neváhá.
    LaunchedEffect(accounts) {
        accounts.alreadySignedIn.collect { snackbarHostState.showSnackbar(getString(Res.string.accounts_already_signed_in)) }
    }
    // Avatar účtu přeletí mezi plným seznamem a sbaleným sloupcem (`collapseFlight`).
    val accountMenu: @Composable () -> Unit = {
        Box(Modifier.collapseFlight("account")) {
        AccountMenu(
            account = account,
            archiveUnread = archiveUnread,
            onSavedMessages = { viewModel.openSavedMessages(navigation.onChatClick) },
            onArchive = navigation.onOpenArchive,
            onCalls = navigation.onOpenCalls,
            onDownloads = navigation.onOpenDownloads,
            onMyStories = navigation.onOpenMyStories,
            draftCount = drafts?.size ?: 0,
            onDrafts = navigation.onOpenDrafts,
            bookmarkCount = bookmarks.size,
            onBookmarks = navigation.onOpenBookmarks,
            onMyQrCode = navigation.onOpenMyQrCode,
            onSettings = navigation.onOpenSettings,
            switcher = accountSwitcher,
        )
        }
    }

    /*
     * Seznam sbalený táhlem na úzký sloupec. Rozhoduje šířka, ne zvolená kotva: během tahu se
     * prochází všemi šířkami a řádek s názvem by byl nečitelný dávno před sbalenou kotvou.
     * Sheety a hlášky výš zůstávají společné, takže se při přepnutí nezavřou.
     */
    val expandListPane = LocalExpandListPane.current
    BoxWithConstraints(modifier) {
        /*
         * Plný seznam a sbalený sloupec v jednom `AnimatedContent`: prvky, které mají obě podoby
         * (účet, lupa, Vytvořit, avatary chatů), přeletí na nové místo, zbytek se prolne
         * (`ListCollapseTransition.kt`). Přechod se spustí přejetím prahu a dojede sám, i když
         * táhlo zůstává v ruce; přejetí zpět ho obrátí.
         */
        val enterSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
        val exitSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
        val collapsedNow = maxWidth < COLLAPSED_LIST_MAX_WIDTH
        // Sloupec si drží pozici sám; při přepnutí dojede na chat, který byl nahoře v odcházející
        // podobě (spočítá se v okamžiku přepnutí, dokud je odcházející seznam ještě rozložený).
        val collapsedListState = rememberLazyListState()
        // Kde stály létající prvky v obou podobách — odtud při přepnutí vyletí (`collapseFlight`).
        val collapseFlights = remember { CollapseFlights() }
        val collapseAnchor = remember(collapsedNow) { firstVisibleChatKey(if (collapsedNow) listState else collapsedListState) }
        LaunchedEffect(collapsedNow) {
            // Plný seznam po rozbalení dojede na chat ze sloupce (existující dojetí z hledání; jen
            // jeden účet, společný seznam víc účtů si pozici nechá).
            if (!collapsedNow && !merged) (collapseAnchor as? Long)?.let { anchorScroll = it }
        }
        AnimatedContent(
            targetState = collapsedNow,
            transitionSpec = { fadeIn(enterSpec) togetherWith fadeOut(exitSpec) using null },
            label = "list-collapse",
        ) { collapsed ->
            CompositionLocalProvider(
                LocalListCollapseScope provides this,
                LocalCollapseFlights provides collapseFlights,
                LocalCollapseSide provides if (collapsed) CollapseSide.Column else CollapseSide.Full,
            ) {
                if (collapsed) {
                    CollapsedChatList(
                        viewModel = listViewModel,
                        sheets = listSheets,
                        listState = collapsedListState,
                        anchorKey = collapseAnchor,
                        accounts = CollapsedAccounts(
                            sources = if (merged) sources else emptyList(),
                            listAccount = listAccount,
                            selected = selectedChatId?.let { id -> (selectedChatSlot ?: screenAccount.slot) to id },
                            onOpen = { slot, chat ->
                                if (chat.opensAsTopics) navigation.onAccountForumClick(slot, chat.id) else navigation.onAccountChatClick(slot, chat.id)
                            },
                        ),
                        onSearch = {
                            expandListPane?.invoke()
                            navigation.onOpenSearch()
                        },
                        onNewChat = navigation.onNewChat,
                        onNewGroup = navigation.onNewGroup,
                        onNewChannel = navigation.onNewChannel,
                        onNewStory = onNewStory,
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
                        // Při rozbalování se sloupec ještě chvíli prolíná v širším panelu: zůstane
                        // u levého kraje ve své šířce, jinak by se vystředil a avatary, lupa
                        // a Vytvořit by letěly ze středu panelu.
                        modifier = Modifier.widthIn(max = COLLAPSED_LIST_MAX_WIDTH),
                    )
                    return@CompositionLocalProvider
                }

                /*
                 * FAB a snackbar nejsou ve slotech Scaffoldu: FAB je součástí plovoucího toolbaru dole
                 * a snackbar musí stát NAD ním — Scaffold by FAB dal do rohu a snackbar pod toolbar.
                 */
                Scaffold(
                    // Při sbalování se plný seznam ještě chvíli prolíná v úzkém panelu: drží šířku
                    // aspoň na prahu (u levého kraje, zbytek ořízne panel), jinak by se pilulka
                    // a řádky zmáčkly a prvky letěly ze zmačkaných míst.
                    modifier = Modifier
                        .fillMaxHeight()
                        .wrapContentWidth(Alignment.Start, unbounded = true)
                        .requiredWidth(max(maxWidth, COLLAPSED_LIST_MAX_WIDTH))
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                    topBar = {
                        reorder?.let { current ->
                            ReorderTopBar(
                                title = stringResource(Res.string.chats_reorder_title),
                                busy = current.busy,
                                onCancel = listViewModel::cancelReorder,
                                onDone = listViewModel::saveReorder,
                            )
                            return@Scaffold
                        }
                        selection?.let { current ->
                            ChatSelectionTopBar(
                                count = current.count,
                                allSelected = allSelected,
                                onSelectAll = listViewModel::selectAll,
                                onClose = listViewModel::closeSelection,
                            )
                            return@Scaffold
                        }
                        // Hlavička pod otevřeným FAB menu ztlumená a neťukací, stejně jako seznam.
                        Box {
                            ChatSearchBar(
                                connection = shownConnection,
                                scrollBehavior = scrollBehavior,
                                // Hledání je vlastní obrazovka v panelu seznamu (`ChatSearchScreen`).
                                onOpenSearch = navigation.onOpenSearch,
                                trailing = {
                                    StoryStack(
                                        posters = storyStack,
                                        rim = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        onOpen = navigation.onOpenStories,
                                    )
                                },
                                actions = { accountMenu() },
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
                            FAB_CLEARANCE +
                            with(LocalDensity.current) { playerHeight.toDp() },
                    )
                    Box(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize().hazeSource(listHaze)) {
                            val emptyContent: @Composable BoxScope.() -> Unit = {
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
                                                // Cesta dál, ne slepá ulička: editor složky začíná výběrem chatů.
                                                action = selectedFolder.id?.let { id ->
                                                    {
                                                        FilledTonalButton(onClick = { navigation.onEditFolder(id) }, shapes = ButtonDefaults.shapes()) {
                                                            Icon(painterResource(Res.drawable.add_24dp), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                                                            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                                                            Text(stringResource(Res.string.chat_folder_add_chats))
                                                        }
                                                    }
                                                },
                                            )
                                        }
                                    } else {
                                        // Prázdný účet (čerstvě založený): tentýž stav, s cestou dál — tlačítkem
                                        // Nová zpráva v rohu.
                                        IntroEmptyState(
                                            icon = Res.drawable.chat_24dp,
                                            title = stringResource(Res.string.chats_empty_title),
                                            text = stringResource(Res.string.chats_empty),
                                        )
                                    }
                                }
                            if (merged) {
                                MergedChatListContent(
                                    sources = sources,
                                    selectedChat = selectedChatId?.let { id -> selectedChatSlot?.let { it to id } },
                                    onOpen = { slot, chat ->
                                        if (chat.opensAsTopics) navigation.onAccountForumClick(slot, chat.id) else navigation.onAccountChatClick(slot, chat.id)
                                    },
                                    onOpenStories = navigation.onOpenStories,
                                    storiesSlot = screenAccount.slot,
                                    padding = padding,
                                    listState = listState,
                                    headerItemCount = cards.count,
                                    empty = emptyContent,
                                ) {
                                    chatListHeaderCards(cards, viewModel, navigation, openNoticeLink, startUpdate, onShowNewFolderChats = { showNewFolderChats = true })
                                }
                            } else AccountScope(listAccount) {
                                // Výběr nebo uspořádání v jiném účtu: chat se otevírá v jeho účtu.
                                val otherAccount = listAccount.slot != screenAccount.slot
                                ChatListContent(
                                    viewModel = listViewModel,
                                    sheets = listSheets,
                                    selectedChatId = selectedChatId,
                                    selectedChatSlot = selectedChatSlot,
                                    onChatClick = if (otherAccount) { id -> navigation.onAccountChatClick(listAccount.slot, id) } else navigation.onChatClick,
                                    onForumClick = if (otherAccount) { id -> navigation.onAccountForumClick(listAccount.slot, id) } else navigation.onForumClick,
                                    onOpenStories = navigation.onOpenStories.takeUnless { otherAccount },
                                    padding = padding,
                                    scrollToChat = anchorScroll ?: scrollToChat,
                                    onScrolledToChat = { if (anchorScroll != null) anchorScroll = null else onScrolledToChat() },
                                    listState = listState,
                                    // Položky hlavičky: nové přihlášení a karta s aktualizací.
                                    headerItemCount = cards.count,
                                    empty = emptyContent,
                                ) {
                                    chatListHeaderCards(cards, viewModel, navigation, openNoticeLink, startUpdate, onShowNewFolderChats = { showNewFolderChats = true })
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
                         *
                         * **Shora končí pod hlavičkou.** Obsah Scaffoldu sahá až pod hledání, takže FAB
                         * menu si myslelo, že má celou výšku, a na telefonu naležato horní položka (Nový
                         * příběh) vyjela pod lištu hledání, kde na ni nešlo ťuknout. S výškou jen pod
                         * hlavičkou menu pozná, že se nevejde, a rozroluje se samo (material3 to umí).
                         * Obyčejný `padding`, ne modifikátor insetů — past s rozložením Foldu výš se ho
                         * netýká.
                         */
                        Column(
                            horizontalAlignment = Alignment.End,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(top = scaffoldPadding.calculateTopPadding())
                                .windowInsetsPadding(
                                    ScaffoldDefaults.contentWindowInsets
                                        .only(WindowInsetsSides.Bottom)
                                        .union(WindowInsets.ime),
                                ),
                        ) {
                            SnackbarHost(snackbarHostState, Modifier.fillMaxWidth()) { CountdownSnackbarHost(it) }
                            // Lišta výběru stojí MÍSTO FAB menu, uprostřed: zakládat nový chat uprostřed
                            // vybírání nedává smysl a obojí by se o spodek obrazovky přetahovalo.
                            ChatSelectionToolbar(
                                actions = selectionActions,
                                callbacks = listViewModel.selectionCallbacks,
                                count = selection?.count ?: 0,
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
                                onNewChat = navigation.onNewChat,
                                onNewGroup = navigation.onNewGroup,
                                onNewChannel = navigation.onNewChannel,
                                onNewStory = onNewStory,
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
                                        onAdd = {
                                            viewModel.loadFolderSuggestions()
                                            showFolderSuggestions = true
                                        },
                                        actions = folderActions,
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
                                enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec(), expandFrom = Alignment.Top) + fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                                exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec(), shrinkTowards = Alignment.Top) + fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
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
                                        // Chat skladby v jejím účtu (fronta je přes všechny účty jedna).
                                        onShow = {
                                            val slot = nowPlaying?.slot ?: screenAccount.slot
                                            navigation.onAccountMessageClick(slot, player.key.chatId, player.key.messageId, null)
                                        },
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
    sheets: ChatSheets,
    snackbarHostState: SnackbarHostState,
    onArchived: ((ArchiveChange) -> Unit)? = null,
    /** Nabídka nad chatem od provozovatele proxy vede do nastavení proxy (jen hlavní seznam). */
    onOpenProxySettings: () -> Unit = {},
) {
    val actions by viewModel.actions.collectAsStateWithLifecycle()
    val savedMessagesChatId by viewModel.savedMessagesChatId.collectAsStateWithLifecycle()
    val chatFolders by viewModel.chatFolders.collectAsStateWithLifecycle()
    // Průvodce nahlášením žije ve ViewModelu, takže se s odchodem seznamu ruší ručně; ostatní
    // sheety jsou v [sheets] a zaniknou samy (viz [ChatSheets]). Host má hlavní seznam, archiv
    // i každý účet společného seznamu, takže tady to platí pro všechny.
    DisposableEffect(viewModel) { onDispose { viewModel.cancelReportOnLeave() } }
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

    LaunchedEffect(actions.notice) {
        val message = actions.notice ?: return@LaunchedEffect
        viewModel.dismissNotice()
        snackbarScope.launch { snackbarHostState.showSnackbar(message.resolveNow()) }
    }

    LaunchedEffect(actions.archived) {
        val change = actions.archived ?: return@LaunchedEffect
        viewModel.consumeArchived()
        onArchived?.invoke(change)
        snackbarScope.launch {
            val result = snackbarHostState.showSnackbar(
                message = when {
                    change.chatIds.size > 1 -> getPluralString(
                        if (change.archived) Res.plurals.chats_archived_many else Res.plurals.chats_unarchived_many,
                        change.chatIds.size,
                        change.chatIds.size,
                    )
                    change.archived -> getString(Res.string.chats_archived)
                    else -> getString(Res.string.chats_unarchived)
                },
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

    /*
     * Smazání a opuštění s „Vrátit": chat už ze seznamu zmizel, smazání čeká na konec lhůty
     * (`PendingChatDeletes`). Běží mimo efekt ze stejného důvodu jako u archivace.
     */
    LaunchedEffect(actions.deleted) {
        val change = actions.deleted ?: return@LaunchedEffect
        viewModel.consumeDeleted()
        snackbarScope.launch {
            val count = change.chatIds.size
            // Předchozí snackbar pryč: odpočet se rozběhne, až je tenhle vidět, a ve frontě za
            // jiným by smazání doběhlo dřív, než by se „Vrátit" vůbec ukázalo.
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showCountdownSnackbar(
                message = when {
                    count > 1 -> getPluralString(
                        if (change.left) Res.plurals.chats_left_many else Res.plurals.chats_deleted_many,
                        count,
                        count,
                    )
                    change.left -> getString(Res.string.chats_left)
                    else -> getString(Res.string.chats_deleted)
                },
                actionLabel = getString(Res.string.chats_undo),
                durationMs = UNDO_COUNTDOWN_MS,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete(change)
        }
    }

    val listState by viewModel.state.collectAsStateWithLifecycle()
    val chats = (listState as? ChatListState.Ready)?.chats.orEmpty()

    val snoozeState by viewModel.snoozeState.collectAsStateWithLifecycle()
    // Doby odložení po tahu chatem.
    sheets.snooze?.let { chat ->
        SnoozeSheet(
            chat = chat,
            onSnooze = { option ->
                sheets.snooze = null
                viewModel.snooze(chat, option)
            },
            onDismiss = { sheets.snooze = null },
        )
    }
    sheets.actions?.let { chat ->
        // Co nabídnout, se dočítá až teď; položka se ukáže, až odpověď dorazí.
        LaunchedEffect(chat.id) { viewModel.loadActionDetails(chat) }
        val details = viewModel.actionDetails.collectAsStateWithLifecycle().value?.takeIf { it.chatId == chat.id }
        val folderChoices = remember(details?.folders, chatFolders) {
            folderChoices(chatFolders, details?.folders)
        }
        // Akce nabídku zavře hned po ťuknutí; výsledek (chyba, „Vrátit") dorazí snackbarem.
        val close = { sheets.actions = null }
        ChatActionsSheet(
            chat = chat,
            spam = SpamControls(
                offer = details?.spam,
                onSetBlocked = { blocked -> close(); viewModel.setBlocked(chat, blocked) },
                onReport = { close(); viewModel.report(chat) },
            ),
            onOpenProxySettings = onOpenProxySettings,
            onTogglePinned = { close(); viewModel.togglePinned(chat) },
            onReorderPinned = if (canReorderPinned(chats)) {
                { close(); viewModel.startReorder() }
            } else null,
            onSelect = { close(); viewModel.startSelection(chat) },
            quiet = QuietActions(
                onMute = { duration -> close(); viewModel.mute(chat, duration) },
                onUnmute = { close(); viewModel.unmute(chat) },
                snoozedUntil = snoozeState.sleeping[chat.id],
                onSnooze = if (viewModel.canSnooze) {
                    { option -> close(); viewModel.snooze(chat, option) }
                } else null,
                onUnsnooze = { close(); viewModel.unsnooze(chat) },
            ),
            onToggleUnread = { close(); viewModel.toggleUnread(chat) },
            // Uložené zprávy a chat Telegramu archivovat nejdou — TDLib by to odmítl (`canBeArchived`).
            onToggleArchived = if (!chat.isArchived && !chat.canBeArchived(savedMessagesChatId)) null else {
                { close(); viewModel.toggleArchived(chat) }
            },
            folders = folderChoices,
            // Do složek se přidává často do víc po sobě, takže nabídka zůstává otevřená.
            onToggleFolder = { id -> viewModel.toggleFolder(chat, id) },
            onAddToHomeScreen = if (viewModel.canAddToHomeScreen) {
                { close(); viewModel.addToHomeScreen(chat) }
            } else null,
            onClearHistory = details?.clearHistory?.let { rights ->
                {
                    close()
                    sheets.clearHistory = ClearHistoryTarget(chat, rights)
                }
            },
            onDelete = { cleanup -> close(); viewModel.delete(chat, cleanup) },
            onDismiss = close,
        )
    }
    // Tentýž sheet jako ⋮ v konverzaci — dvě podoby téhož kroku by se rozešly.
    sheets.clearHistory?.let { target ->
        ClearHistorySheet(
            rights = target.rights,
            peerName = chatTitle(target.chat.id, target.chat.title).takeIf { target.chat.isPrivate },
            onConfirm = { from, to, forAll ->
                sheets.clearHistory = null
                viewModel.clearHistory(target.chat, from, to, forAll)
            },
            onDismiss = { sheets.clearHistory = null },
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
    /** Sheety nad chaty [viewModel] — podržení řádku a tah otevírají jeho nabídky. */
    sheets: ChatSheets,
    selectedChatId: Long?,
    /** Účet vybraného chatu: totéž id může mít chat v jiném účtu. `null` = jen podle id. */
    selectedChatSlot: AccountSlot? = null,
    onChatClick: (Long) -> Unit,
    onForumClick: (Long) -> Unit,
    padding: PaddingValues,
    empty: @Composable BoxScope.() -> Unit,
    scrollToChat: Long? = null,
    onScrolledToChat: () -> Unit = {},
    headerItemCount: Int = 0,
    listState: LazyListState = rememberLazyListState(),
    /** Ťuknutí na avatar s kroužkem příběhů; `null` = avatar se ťuká jako zbytek řádku. */
    onOpenStories: ((Long) -> Unit)? = null,
    header: LazyListScope.() -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val storyRings by viewModel.storyRings.collectAsStateWithLifecycle()
    // Start je hotový, až seznam ukazuje chaty (nebo že žádné nejsou), ne s prvním snímkem.
    ReportDrawnWhen { state !is ChatListState.Loading }
    val rowGaps = LocalChatRowGaps.current
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
    val awaitingReply by viewModel.awaitingReply.collectAsStateWithLifecycle()
    val chatAccents = rememberChatAccents(chatColors.values.toSet())
    val rows = ChatRowsContext(
        viewModel = viewModel,
        sheets = sheets,
        selection = selection,
        savedMessagesChatId = savedMessagesChatId,
        swipeActions = swipeActions,
        snoozes = snoozes,
        awaitingReply = awaitingReply,
        storyRings = storyRings,
        chatColors = chatColors,
        chatAccents = chatAccents,
        rowGaps = rowGaps,
    )
    val groups = (state as? ChatListState.Ready)?.chats?.let { chats ->
        remember(chats, hiddenSavedMessagesChatId, snoozes.due) {
            chatGroups(chats, hiddenSavedMessagesChatId, snoozes.due)
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
        key = { it.id },
        selected = selectedChatId,
        sponsored = { it.sponsor != null },
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

        is ChatListState.Failed -> IntroEmptyState(
            icon = Res.drawable.error_24dp,
            title = stringResource(Res.string.empty_state_error_title),
            text = current.message.resolve(),
            modifier = Modifier.padding(padding),
            action = {
                FilledTonalButton(onClick = viewModel::loadMore, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(Res.string.common_try_again))
                }
            },
        )

        is ChatListState.Ready -> if (current.chats.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), content = empty)
        } else {
            // Uspořádání mezery nechává vždycky: tažený řádek je samostatný kus, ne část panelu.
            val itemSpacing = if (rowGaps || reorder != null) GROUP_ITEM_SPACING else 0.dp
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    // Kotva i rozestup za ní jdou z horního odsazení, první chat tak
                    // zůstane tam, kde byl bez ní.
                    top = padding.calculateTopPadding() + 8.dp - TOP_ANCHOR_HEIGHT - itemSpacing,
                    bottom = padding.calculateBottomPadding() + 8.dp,
                    start = LIST_CONTENT_INSET,
                    end = LIST_CONTENT_INSET,
                ),
                // Rozestup UVNITŘ skupiny. Mezera mezi skupinami se přidává u její první
                // položky — seznam o sousedech položky nic neví.
                verticalArrangement = Arrangement.spacedBy(itemSpacing),
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
                                backdrop = MaterialTheme.colorScheme.groupRow,
                            )
                        }
                    }
                    return@LazyColumn
                }

                header()

                groups.forEachIndexed { groupIndex, group ->
                    itemsIndexed(group, key = { _, chat -> chat.id }) { index, chat ->
                        ChatListItem(
                            rows = rows,
                            chat = chat,
                            position = GroupPosition(index, group.size, groupIndex),
                            selected = chat.id == selectedChatId && (selectedChatSlot == null || selectedChatSlot == LocalAccount.current.slot),
                            onOpen = { if (it.opensAsTopics) onForumClick(it.id) else onChatClick(it.id) },
                            onOpenStories = onOpenStories,
                        )
                    }
                }
            }
        }
    }
}


/**
 * Řádek seznamu chatů: tah do stran, tvar v bloku, značky a akce. Sdílí ho seznam jednoho účtu
 * ([ChatListContent]) i společný seznam víc účtů ([MergedChatListContent]); co patří účtu řádku
 * (ViewModel, odložení, barvy, příběhy), nese [rows].
 */
@Composable
internal fun LazyItemScope.ChatListItem(
    rows: ChatRowsContext,
    chat: ChatSummary,
    position: GroupPosition,
    /** Chat je otevřený v detailu vedle seznamu. */
    selected: Boolean,
    onOpen: (ChatSummary) -> Unit,
    onOpenStories: ((Long) -> Unit)?,
) {
    // Ve výběru zvýrazňuje řádek výběr, ne chat otevřený v detailu vedle —
    // dvě různé barvy pro „vybraný" by se v jednom seznamu pletly.
    val selecting = rows.selection != null
    val picked = rows.selection?.ids?.contains(chat.id) == true
    val highlighted = if (selecting) picked else selected
    // Krajní položky skupiny nesou její zaoblení, prostřední se drží u sebe;
    // stisknutý řádek se z bloku vyčlení (M3 Expressive). Stisk hlásí klikání
    // řádku, v posouvaném seznamu se zpožděním, takže scroll tvar nemění.
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = animatedGroupItemShape(
        first = position.index == 0,
        last = position.index == position.size - 1,
        selected = highlighted,
        pressed = pressed,
        inner = if (rows.rowGaps) INNER_CORNER else 0.dp,
    )
    SwipeableChat(
        // Ve výběru tah nejde: akce jdou lištou a tah by mířil jen na jeden chat.
        // Co tah s chatem udělá (a jestli vůbec), řeší `chatSwipeEffect`.
        right = chatSwipeEffect(rows.swipeActions.right, chat, rows.savedMessagesChatId, chat.id in rows.snoozes.sleeping).takeUnless { selecting },
        left = chatSwipeEffect(rows.swipeActions.left, chat, rows.savedMessagesChatId, chat.id in rows.snoozes.sleeping).takeUnless { selecting },
        shape = shape,
        // Odložení si dobu vybírá v sheetu, ostatní tahy jsou rovnou akce.
        onSwipe = { effect -> if (effect == ChatSwipeEffect.Snooze) rows.sheets.snooze = chat else rows.viewModel.swipe(chat, effect) },
        // Animace přeskládání při změně pořadí — bez toho by chaty
        // při nové zprávě skákaly. `animateItem` musí být na kořeni
        // položky, takže sedí na tahu, ne na řádku uvnitř.
        modifier = Modifier
            .animateItem()
            .padding(
                top = if (position.index == 0 && position.groupIndex > 0) GROUP_SPACING
                else 0.dp,
            )
            // Bez mezer drží panel podklad: v rozích stisknutého a vybraného
            // řádku má prosvítat on, ne stránka.
            .then(
                if (rows.rowGaps) {
                    Modifier
                } else {
                    Modifier.background(
                        MaterialTheme.colorScheme.groupRow,
                        RoundedCornerShape(
                            topStart = if (position.index == 0) GROUP_CORNER else 0.dp,
                            topEnd = if (position.index == 0) GROUP_CORNER else 0.dp,
                            bottomStart = if (position.index == position.size - 1) GROUP_CORNER else 0.dp,
                            bottomEnd = if (position.index == position.size - 1) GROUP_CORNER else 0.dp,
                        ),
                    )
                },
            ),
    ) { swipeSemantics ->
        ChatRow(
            chat = chat,
            selected = highlighted,
            marks = ChatRowMarks(
                selectionMark = picked.takeIf { selecting && chat.isSelectable() },
                snooze = when {
                    chat.id in rows.snoozes.due -> RowSnooze.Due
                    else -> rows.snoozes.sleeping[chat.id]?.let(RowSnooze::Sleeping)
                },
                // Odznak patří poslední zprávě, nad kterou to model řekl; nová
                // zpráva ho schová, dokud se neposoudí znovu.
                awaitingReply = chat.unreadCount > 0 &&
                    chat.lastMessageId != null && rows.awaitingReply[chat.id] == chat.lastMessageId,
                account = rows.account,
                stories = rows.storyRings[chat.id]?.let {
                    // Skrytý autor se mimo archiv nehlásí: lem jen tenký a šedý,
                    // jako by bylo zhlédnuté — příběhy jdou otevřít, ale nevolají.
                    val quiet = it.isHidden && rows.viewModel.folder != ChatFolder.Archive
                    StoryRingState(
                        total = it.stories.size,
                        unread = if (quiet) 0 else it.unreadCount,
                        closeFriends = !quiet && it.hasUnreadForCloseFriends,
                    )
                },
            ),
            shape = shape,
            interactionSource = interaction,
            accent = rows.chatColors[chat.id]?.let(rows.chatAccents::get),
            onClick = {
                if (!selecting) rows.viewModel.opened(chat.id)
                if (selecting) rows.viewModel.toggleSelected(chat) else onOpen(chat)
            },
            // Ve výběru i dlouhý stisk jen přepíná — nabídka nad jedním chatem
            // by z výběru vytrhla.
            onLongClick = {
                if (selecting) rows.viewModel.toggleSelected(chat) else rows.sheets.actions = chat
            },
            // Ve výběru avatar přepíná výběr jako zbytek řádku.
            onAvatarClick = onOpenStories
                ?.takeIf { !selecting && chat.id in rows.storyRings }
                ?.let { open -> { open(chat.id) } },
            // Akce tahu pro čtečku obrazovky — na uzel řádku, ne na obal.
            modifier = swipeSemantics,
        )
    }
}


/** Kolik položek před koncem už spustí dotažení další dávky. */
private const val LOAD_MORE_THRESHOLD = 5


/** Mezera mezi skupinami. */
internal val GROUP_SPACING = 14.dp

/** Klíč neviditelné kotvy nad prvním chatem. */
private const val TOP_ANCHOR_KEY = "top-anchor"
private const val REORDER_HINT_KEY = "reorder-hint"
private val REORDER_AVATAR_SIZE = 40.dp


/** Výška kotvy — nejmenší, se kterou ji seznam ještě počítá za viditelnou. */
private val TOP_ANCHOR_HEIGHT = 1.dp


/** Odsazení FAB od okraje obrazovky — výchozí M3 (FAB menu si ho dává samo). */
private val FAB_MARGIN = 16.dp


/**
 * O kolik se prodlužuje spodek seznamu kvůli FAB: jeho pás (80 dp, se složkami i bez nich, viz
 * `NewChatFabMenu`), odsazení od okraje a trochu vzduchu, aby poslední chat nekončil těsně pod ním.
 */
private val FAB_CLEARANCE = TOOLBAR_FAB_COLLAPSED_SIZE + FAB_MARGIN + 8.dp

/**
 * Alt+↓ / Alt+↑ (a Ctrl+Tab) z klávesnice: další a předchozí chat v pořadí, jak je vidět —
 * i s blokem nepřečtených ([neighbourChat] nad skupinami). Jen když je seznam složený: na
 * telefonu s otevřenou konverzací ho konverzace překrývá a zkratka nedělá nic; na rozloženém
 * Foldu se tak prochází chat za chatem a vpravo se mění konverzace. Ve výběru a v uspořádání
 * připnutých ne — tam by otevření chatu z režimu vytrhlo.
 *
 * Položkou je chat jednoho účtu i chat společného seznamu víc účtů; [key] ho porovná
 * s [selected] (ve společném seznamu i s účtem), [sponsored] se přeskakují.
 */
@Composable
internal fun <T, K> ChatSwitchShortcuts(
    enabled: Boolean,
    chats: List<List<T>>,
    key: (T) -> K,
    selected: K?,
    sponsored: (T) -> Boolean,
    onOpen: (T) -> Unit,
) {
    val shortcuts = koinInject<KeyboardShortcuts>()
    val currentEnabled by rememberUpdatedState(enabled)
    val currentChats by rememberUpdatedState(chats)
    val currentKey by rememberUpdatedState(key)
    val currentSelected by rememberUpdatedState(selected)
    val currentSponsored by rememberUpdatedState(sponsored)
    val currentOnOpen by rememberUpdatedState(onOpen)
    LaunchedEffect(shortcuts) {
        shortcuts.shortcuts.collect { shortcut ->
            val next = when (shortcut) {
                KeyboardShortcut.NextChat -> true
                KeyboardShortcut.PreviousChat -> false
                else -> return@collect
            }
            if (!currentEnabled) return@collect
            val flat = currentChats.flatten().filterNot(currentSponsored)
            val target = neighbourChat(flat.map(currentKey), currentSelected, next) ?: return@collect
            flat.firstOrNull { currentKey(it) == target }?.let(currentOnOpen)
        }
    }
}
