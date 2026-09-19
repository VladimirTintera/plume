package eu.tintera.plume.features.chats

import eu.tintera.plume.resources.saved_messages
import eu.tintera.plume.resources.more_vert_24dp
import eu.tintera.plume.resources.chats_menu
import eu.tintera.plume.resources.bookmark_filled_24dp
import eu.tintera.plume.core.ui.isSavedMessages
import eu.tintera.plume.core.ui.chatTitle
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import eu.tintera.plume.core.adaptive.isCompact
import eu.tintera.plume.core.adaptive.isShortWindow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.update.rememberStartUpdate
import eu.tintera.plume.core.telegram.ChatActivity
import eu.tintera.plume.core.telegram.ChatListState
import eu.tintera.plume.core.telegram.ChatSummary
import eu.tintera.plume.core.telegram.Presence
import eu.tintera.plume.core.ui.Avatar
import eu.tintera.plume.core.ui.asRelativeTime
import eu.tintera.plume.core.ui.chatAvatarKey
import eu.tintera.plume.core.ui.chatTitleKey
import eu.tintera.plume.core.ui.label
import eu.tintera.plume.core.ui.resolve
import eu.tintera.plume.core.ui.sharedNavElement
import eu.tintera.plume.core.ui.sharedNavText
import eu.tintera.plume.core.ui.tabularNumbers
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.archive_24dp
import eu.tintera.plume.resources.chats_archive
import eu.tintera.plume.resources.chats_archived
import eu.tintera.plume.resources.chats_undo
import eu.tintera.plume.resources.chats_unarchived
import eu.tintera.plume.resources.chats_draft_prefix
import eu.tintera.plume.resources.chats_empty
import eu.tintera.plume.resources.chats_muted
import eu.tintera.plume.resources.chats_section_pinned
import eu.tintera.plume.resources.common_try_again
import eu.tintera.plume.resources.notifications_off_24dp
import eu.tintera.plume.resources.push_pin_filled_24dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun ChatListScreen(
    viewModel: ChatsViewModel,
    selectedChatId: Long?,
    onChatClick: (Long) -> Unit,
    onOpenArchive: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val search by viewModel.search.state.collectAsStateWithLifecycle()
    val connection by viewModel.connection.collectAsStateWithLifecycle()
    val savedMessagesChatId by viewModel.savedMessagesChatId.collectAsStateWithLifecycle()
    val updateCard by viewModel.updateCard.collectAsStateWithLifecycle()
    val startUpdate = rememberStartUpdate(viewModel.appUpdates)
    // Hledání při scrollu dolů odjede a při scrollu nahoru se vrátí.
    val scrollBehavior = SearchBarDefaults.enterAlwaysSearchBarScrollBehavior()
    val snackbarHostState = remember { SnackbarHostState() }

    ChatActionsHost(viewModel, snackbarHostState)

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

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            ChatSearchBar(
                connection = connection.label(),
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
                onClearRecent = viewModel.search::clearRecent,
                actions = {
                    /*
                     * Menu, ne samostatné ikony vedle hledání. Uložené zprávy v seznamu nejsou
                     * (jen připnuté) a archiv je vlastní seznam — tohle je vstup do obou, jako
                     * v oficiálních klientech.
                     */
                    var menuOpen by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(painterResource(Res.drawable.more_vert_24dp), contentDescription = stringResource(Res.string.chats_menu))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.saved_messages)) },
                                leadingIcon = { Icon(painterResource(Res.drawable.bookmark_filled_24dp), contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    viewModel.openSavedMessages(onChatClick)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.chats_archive)) },
                                leadingIcon = { Icon(painterResource(Res.drawable.archive_24dp), contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    onOpenArchive()
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            ChatListContent(
                viewModel = viewModel,
                selectedChatId = selectedChatId,
                onChatClick = onChatClick,
                padding = padding,
                scrollToChat = scrollToChat,
                onScrolledToChat = { scrollToChat = null },
                // Karta s aktualizací je jediná položka hlavičky.
                headerItemCount = if (updateCard != null) 1 else 0,
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
                // Nad chaty, jako vlastní blok se stejnou mezerou jako mezi skupinami.
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
        }
    }
}

/**
 * Nabídka akcí po dlouhém stisku, potvrzení smazání a snackbary (chyba, „Vrátit" po archivaci).
 * Společné pro hlavní seznam i archiv — liší se jen tím, co archivace znamená, a to ví chat sám
 * ([ChatSummary.folder]).
 */
@Composable
internal fun ChatActionsHost(viewModel: ChatsViewModel, snackbarHostState: SnackbarHostState) {
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
        snackbarScope.launch {
            val result = snackbarHostState.showSnackbar(
                message = getString(if (change.archived) Res.string.chats_archived else Res.string.chats_unarchived),
                actionLabel = getString(Res.string.chats_undo),
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.undoArchive(change)
        }
    }

    actions.target?.let { chat ->
        ChatActionsSheet(
            chat = chat,
            onTogglePinned = { viewModel.togglePinned(chat) },
            onToggleMuted = { viewModel.toggleMuted(chat) },
            onToggleUnread = { viewModel.toggleUnread(chat) },
            // Uložené zprávy archivovat nejdou — TDLib by to odmítl.
            onToggleArchived = if (chat.id == savedMessagesChatId) null else {
                { viewModel.toggleArchived(chat) }
            },
            onDelete = { viewModel.askDelete(chat) },
            onDismiss = viewModel::hideActions,
        )
    }

    actions.pendingDelete?.let { chat ->
        ChatDeleteDialog(
            chat = chat,
            onConfirm = { viewModel.confirmDelete(chat) },
            onDismiss = viewModel::cancelDelete,
        )
    }
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
    header: LazyListScope.() -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val separateUnread by viewModel.separateUnreadChats.collectAsStateWithLifecycle()
    val savedMessagesChatId by viewModel.savedMessagesChatId.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

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

    /*
     * Vedle railu na telefonu naležato je okraj vlevo užší. Rail má kolem ikon vlastní vzduch,
     * takže plných 16 dp jen ukusovalo šířku, které je naležato málo. 8 dp je vnitřní okraj
     * vyhledávací pilulky nad seznamem — karty s ní lícují.
     */
    val startPadding = if (!isCompact() && isShortWindow()) 8.dp else 16.dp

    val groups = (state as? ChatListState.Ready)?.chats?.let { chats ->
        remember(chats, separateUnread, savedMessagesChatId) { chatGroups(chats, separateUnread, savedMessagesChatId) }
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
                    start = startPadding,
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
                        ChatRow(
                            chat = chat,
                            selected = chat.id == selectedChatId,
                            // Krajní položky skupiny nesou její zaoblení, prostřední
                            // se drží u sebe.
                            firstInGroup = index == 0,
                            lastInGroup = index == group.lastIndex,
                            onClick = { onChatClick(chat.id) },
                            onLongClick = { viewModel.showActions(chat) },
                            // Animace přeskládání při změně pořadí — bez toho by chaty
                            // při nové zprávě skákaly.
                            modifier = Modifier
                                .animateItem()
                                .padding(
                                    top = if (index == 0 && groupIndex > 0) GROUP_SPACING
                                    else 0.dp,
                                ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun ChatRow(
    chat: ChatSummary,
    selected: Boolean,
    firstInGroup: Boolean,
    lastInGroup: Boolean,
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

    /*
     * Zaoblení dělá ze sousedních položek jeden blok: velké nahoře u první, velké dole
     * u poslední, malé mezi nimi. Animuje se, protože skupiny se přeskládávají za běhu —
     * přečtený chat přeskočí z nepřečtených mezi ostatní a tvary sousedů se tím mění.
     */
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

    val shape = RoundedCornerShape(
        topStart = topCorner,
        topEnd = topCorner,
        bottomStart = bottomCorner,
        bottomEnd = bottomCorner,
    )

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
                    modifier = Modifier.sharedNavElement(chatAvatarKey(chat.id)),
                )

                // Tečka sedí NA avataru, ne vedle jména: v seznamu se očima jede po levém
                // sloupci, takže se dostupnost přečte spolu s tím, kdo to je.
                if (chat.presence is Presence.Online) {
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .size(ONLINE_DOT)
                            // Obroučka barvou pozadí položky — bez ní tečka splyne s tmavým
                            // avatarem a je z ní jen skvrna.
                            .background(containerColor, CircleShape)
                            .padding(2.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                    )
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
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = chatTitle(chat.id, chat.title),
                        // `bodyLarge` místo `titleMedium`: stejná velikost, ale běžný řez.
                        // Polotučné jméno u KAŽDÉHO chatu znamená, že nevyčnívá žádný —
                        // tučností se proto odlišuje jen nepřečtený, jako v Google Messages.
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (chat.hasUnread) FontWeight.SemiBold else null,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        // Plná váha: název roztlačí zbytek řádku doprava, takže čas sedí
                        // na konci a nelepí se na jméno.
                        modifier = Modifier.weight(1f).sharedNavText(chatTitleKey(chat.id)),
                    )

                    if (chat.isMuted) {
                        Icon(
                            painterResource(Res.drawable.notifications_off_24dp),
                            contentDescription = stringResource(Res.string.chats_muted),
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                val draftLabelColor = MaterialTheme.colorScheme.tertiary
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
                            chat.activity != null -> MaterialTheme.colorScheme.primary
                            selected -> MaterialTheme.colorScheme.onSecondaryContainer
                            chat.hasUnread -> MaterialTheme.colorScheme.onSurface
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
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

/** Výška kotvy — nejmenší, se kterou ji seznam ještě počítá za viditelnou. */
private val TOP_ANCHOR_HEIGHT = 1.dp

/** Avatar. Větší než výchozích 48 dp — v odlehčeném řádku nese hlavní váhu on, ne písmo. */
private val AVATAR_SIZE = 52.dp

/** Průměr zelené tečky i s obroučkou. */
private val ONLINE_DOT = 14.dp

