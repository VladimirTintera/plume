package eu.tintera.plume.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Tab
import androidx.tv.material3.TabRow
import androidx.tv.material3.Text
import eu.tintera.plume.core.telegram.ChatFolder
import eu.tintera.plume.core.telegram.ChatListState
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.core.telegram.ChatSummary
import eu.tintera.plume.core.ui.asDate
import eu.tintera.plume.core.ui.asTimeOfDay
import eu.tintera.plume.core.ui.isSameDayAs
import eu.tintera.plume.core.ui.label
import eu.tintera.plume.core.ui.resolve
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.chat_bubble_24dp
import eu.tintera.plume.resources.chat_folders_all
import eu.tintera.plume.resources.chats_archive
import eu.tintera.plume.resources.chats_load_failed
import eu.tintera.plume.resources.chats_section_pinned
import eu.tintera.plume.resources.cloud_off_24dp
import eu.tintera.plume.resources.connection_connecting
import eu.tintera.plume.resources.continue_watching_empty
import eu.tintera.plume.resources.continue_watching_title
import eu.tintera.plume.resources.play_arrow_24dp
import eu.tintera.plume.resources.push_pin_filled_24dp
import eu.tintera.plume.resources.search_24dp
import eu.tintera.plume.resources.tv_chats_empty
import eu.tintera.plume.resources.tv_search_tab
import kotlin.time.Clock
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Hlavní obrazovka podle návodů Google pro TV (docs/poznatky/android-tv.md, „Skladba obrazovky"):
 *
 * - **Složky jsou záložky nahoře** (`TabRow` s pilulkou): rovnocenné sekce jedné obrazovky.
 *   Lišta (`NavigationDrawer`) je podle návodu jen na hlavní cíle aplikace (pět až šest) a složek
 *   může být víc. Záložka se vybere už zaostřením, jako všude na TV.
 * - **Chaty jsou svislý dvouřádkový seznam s kulatými avatary** — to Google doporučuje na
 *   seznam, ze kterého se vybírá jedna položka. Immersive list je podle návodu pro pár
 *   vybraných položek (novinky), ne pro dlouhý seznam.
 * - **Vpravo náhled zaostřeného chatu** ([ChatPreview]): jeho poslední fotka nebo video přes
 *   pozadí celé obrazovky (prolíná se i pod záložky) a poslední zprávy — z immersive listu si
 *   bere to, že obraz na pozadí sleduje fokus.
 *
 * [listStateFor] a [lastOpenedChat] drží navigace, ne obrazovka: po návratu z konverzace musí
 * seznam stát na stejném místě a fokus na chatu, ze kterého uživatel odešel.
 *
 * **Hledání** ([ListSection.Search]) je první záložka, jen s lupou — jako horní navigace Google
 * TV. **Rozkoukaná videa** ([ListSection.ContinueWatching]) jsou záložka hned za Všemi chaty, jen když nějaká jsou:
 * mřížka karet ([ContinueWatchingPane]) místo seznamu a náhledu. Na plochu Google TV (řada
 * „Pokračovat ve sledování") je dát nejde — viz docs/poznatky/android-tv.md, „Rozkoukaná videa".
 */
@Composable
fun ChatListScreen(
    folder: ChatFolder,
    section: ListSection,
    onFolder: (ChatFolder) -> Unit,
    onSection: (ListSection) -> Unit,
    listStateFor: (ChatFolder) -> LazyListState,
    lastOpenedChat: Long?,
    /** Leží nad seznamem přehrávač nebo prohlížeč příběhů: seznam si pak fokus nebere. */
    overlayOpen: Boolean,
    onPlay: (VideoRequest) -> Unit,
    onOpen: (Long) -> Unit,
    /** Chat otevřený rovnou na zprávě (výsledek hledání, video z Rozkoukaných). */
    onOpenMessage: (chatId: Long, messageId: Long) -> Unit,
    /** Příběhy autora (řada nad Všemi chaty); [lastStoriesChat] dostane po zavření fokus. */
    onOpenStories: (Long) -> Unit,
    lastStoriesChat: Long?,
    search: TvSearchState,
) {
    val watching = section == ListSection.ContinueWatching
    val chats = koinInject<ChatRepository>()
    // Náhled patří zaostřenému chatu VYBRANÉ složky; po přepnutí složky by jinak zůstal chat
    // z předchozí, dokud fokus nesjede do seznamu.
    var focusedChat by remember(folder, section) { mutableStateOf<ChatSummary?>(null) }
    var tabsFocused by remember { mutableStateOf(false) }
    val preview = rememberChatPreview(focusedChat?.id)
    val watchingPositions = rememberWatchingPositions()
    val storyPosters = rememberStoryPosters()
    Box(Modifier.fillMaxSize()) {
        ChatPreviewBackdrop(preview)
        Column(Modifier.fillMaxSize()) {
            FolderTabs(
                selected = folder,
                section = section,
                // Záložka zůstane i s prázdným seznamem, dokud je vybraná (poslední video odebrané).
                showWatching = watching || watchingPositions.isNotEmpty(),
                onFolder = onFolder,
                onSection = onSection,
                modifier = Modifier
                    .padding(start = SAFE_HORIZONTAL, end = SAFE_HORIZONTAL, top = SAFE_VERTICAL, bottom = 8.dp)
                    .onFocusChanged { tabsFocused = it.hasFocus },
            )
            if (section == ListSection.Search) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    SearchPane(
                        state = search,
                        takeFocus = !tabsFocused && !overlayOpen,
                        focusedChat = focusedChat,
                        preview = preview,
                        onFocusChat = { focusedChat = it },
                        onOpen = onOpen,
                        onOpenMessage = onOpenMessage,
                    )
                }
                return@Column
            }
            if (watching) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    if (watchingPositions.isEmpty()) {
                        MessageScreen(stringResource(Res.string.continue_watching_empty), Res.drawable.play_arrow_24dp)
                    } else {
                        ContinueWatchingPane(
                            positions = watchingPositions,
                            playerOpen = overlayOpen,
                            takeFocus = !tabsFocused,
                            onPlay = onPlay,
                            onShowInChat = onOpenMessage,
                        )
                    }
                }
                return@Column
            }
            Row(Modifier.weight(1f).fillMaxWidth()) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(LIST_FRACTION)) {
                    key(folder) {
                        ChatListPane(
                            chats = chats,
                            folder = folder,
                            listState = listStateFor(folder),
                            lastOpenedChat = lastOpenedChat,
                            // Dokud uživatel prochází záložky, seznam si fokus nebere.
                            takeFocus = !tabsFocused && !overlayOpen,
                            onFocusChat = { focusedChat = it },
                            onOpen = onOpen,
                            // Řada příběhů jen nad Všemi chaty, jako hromádka na telefonu.
                            stories = storyPosters.takeIf { folder == ChatFolder.Main && it.isNotEmpty() }
                                ?.let { StoryRow(it, lastStoriesChat, overlayOpen, onOpenStories) },
                        )
                    }
                }
                ChatPreview(
                    chat = focusedChat,
                    preview = preview,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(start = 32.dp, end = SAFE_HORIZONTAL, bottom = SAFE_VERTICAL),
                )
            }
        }
    }
}

/** Co ukazuje hlavní obrazovka pod záložkami. */
enum class ListSection { Chats, ContinueWatching, Search }

/** Záložka: složka chatů ([section] Chats), Rozkoukaná videa, nebo Hledání (jen ikona). */
private data class TabEntry(val section: ListSection, val folder: ChatFolder?, val title: String, val count: Int)

/** Záložky: Hledání, Všechny chaty, Rozkoukaná videa, složky Telegramu v pořadí uživatele, Archiv. */
@Composable
private fun FolderTabs(
    selected: ChatFolder,
    section: ListSection,
    showWatching: Boolean,
    onFolder: (ChatFolder) -> Unit,
    onSection: (ListSection) -> Unit,
    modifier: Modifier,
) {
    val chats = koinInject<ChatRepository>()
    val folders by chats.chatFolders.collectAsState()
    val unread by chats.folderUnread.collectAsState()
    val archiveUnread by chats.archiveUnread.collectAsState()
    val all = stringResource(Res.string.chat_folders_all)
    val archive = stringResource(Res.string.chats_archive)
    val continueWatching = stringResource(Res.string.continue_watching_title)
    val searchLabel = stringResource(Res.string.tv_search_tab)
    val tabs = buildList {
        add(TabEntry(ListSection.Search, null, searchLabel, 0))
        add(TabEntry(ListSection.Chats, ChatFolder.Main, all, 0))
        if (showWatching) add(TabEntry(ListSection.ContinueWatching, null, continueWatching, 0))
        folders.folders.forEach { add(TabEntry(ListSection.Chats, ChatFolder.Custom(it.id), it.title, unread[it.id]?.chats ?: 0)) }
        add(TabEntry(ListSection.Chats, ChatFolder.Archive, archive, archiveUnread.chats))
    }
    val selectedIndex = tabs.indexOfFirst { it.section == section && (section != ListSection.Chats || it.folder == selected) }.coerceAtLeast(0)
    // Šipka nahoru ze seznamu jde na VYBRANOU záložku, ne na tu, která je nad zaostřeným řádkem
    // nejblíž: záložka se vybírá už zaostřením, takže by šipka nahoru přepnula složku.
    val selectedTab = remember { FocusRequester() }
    TabRow(selectedTabIndex = selectedIndex, modifier = modifier.focusRestorer(selectedTab).focusGroup()) {
        tabs.forEachIndexed { index, (tabSection, folder, title, count) ->
            val choose = {
                when {
                    folder == null -> if (section != tabSection) onSection(tabSection)
                    section != ListSection.Chats || folder != selected -> onFolder(folder)
                }
            }
            Tab(
                selected = index == selectedIndex,
                onFocus = choose,
                onClick = choose,
                modifier = if (index == selectedIndex) Modifier.focusRequester(selectedTab) else Modifier,
            ) {
                if (tabSection == ListSection.Search) {
                    Icon(
                        painterResource(Res.drawable.search_24dp),
                        contentDescription = title,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp).size(20.dp),
                    )
                } else {
                    Text(
                        title,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
                if (count > 0) {
                    Text(
                        count.toString(),
                        style = MaterialTheme.typography.labelMedium.tabular(),
                        modifier = Modifier.padding(end = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatListPane(
    chats: ChatRepository,
    folder: ChatFolder,
    listState: LazyListState,
    lastOpenedChat: Long?,
    takeFocus: Boolean,
    onFocusChat: (ChatSummary) -> Unit,
    onOpen: (Long) -> Unit,
    stories: StoryRow?,
) {
    val state by remember(folder) { chats.chats(folder) }.collectAsState()
    // Hlavní seznam načítá repository sama po přihlášení; archiv a složky až otevřením.
    LaunchedEffect(folder) { if (folder != ChatFolder.Main) chats.loadMore(folder) }
    when (val current = state) {
        ChatListState.Loading -> LoadingScreen(stringResource(Res.string.connection_connecting))
        is ChatListState.Failed -> MessageScreen(stringResource(Res.string.chats_load_failed), Res.drawable.cloud_off_24dp, error = true)
        is ChatListState.Ready ->
            if (current.chats.isEmpty()) {
                MessageScreen(stringResource(Res.string.tv_chats_empty), Res.drawable.chat_bubble_24dp)
            } else {
                ChatList(chats, folder, current.chats, listState, lastOpenedChat, takeFocus, onFocusChat, onOpen, stories)
            }
    }
}

@Composable
private fun ChatList(
    repository: ChatRepository,
    folder: ChatFolder,
    chats: List<ChatSummary>,
    listState: LazyListState,
    lastOpenedChat: Long?,
    takeFocus: Boolean,
    onFocusChat: (ChatSummary) -> Unit,
    onOpen: (Long) -> Unit,
    stories: StoryRow?,
) {
    val savedMessagesId = savedMessagesChatId()
    // Řada příběhů je první položka seznamu; indexy chatů jsou o ni posunuté.
    val header = if (stories != null) 1 else 0
    val focus = remember { FocusRequester() }
    // Fokus na chat, ze kterého se uživatel vrátil, jinak na první. Bez toho by po návratu
    // z konverzace fokus nebyl nikde.
    val focusedId = lastOpenedChat?.takeIf { id -> chats.any { it.id == id } } ?: chats.first().id
    // Složka se dočítá po dávkách a chaty s vyšším pořadím přibývají NAD zaostřeným řádkem —
    // seznam drží první viditelný řádek podle klíče, takže zaostřený odjel nad horní okraj
    // a fokus nebyl vidět (ověřeno na emulátoru). Dokud uživatel sám nepohne šipkou, fokus se
    // proto přenastavuje s každou změnou a řádek se posune do obrazu. Ne ale, když prochází
    // záložky ([takeFocus]): seznam by mu fokus ukradl při každém přepnutí složky.
    var navigated by remember(folder, lastOpenedChat) { mutableStateOf(false) }
    LaunchedEffect(folder, focusedId, navigated, chats, takeFocus) {
        if (navigated || !takeFocus) return@LaunchedEffect
        val index = chats.indexOfFirst { it.id == focusedId }
        if (index >= 0 && listState.layoutInfo.visibleItemsInfo.none { it.index == index + header }) listState.scrollToItem(index + header)
        runCatching { focus.requestFocus() }
    }
    // Řada příběhů přibude nad seznamem až s updaty od TDLib. Seznam drží první viditelnou
    // položku podle klíče, takže by řada zůstala nad horním okrajem a šipka nahoru by ukázala jen
    // avatar bez nadpisu. Dokud se uživatel nepohnul, seznam se vrátí na začátek.
    LaunchedEffect(header) {
        if (header == 1 && !navigated && listState.firstVisibleItemIndex <= 1) listState.scrollToItem(0)
    }
    // Dočítání: když se fokus blíží konci načtené části, požádá se o další stránku.
    val loaded by rememberUpdatedState(chats.size + header)
    LaunchedEffect(folder, listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { last -> if (last != null && last >= loaded - LOAD_AHEAD) repository.loadMore(folder) }
    }
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = SAFE_HORIZONTAL - 12.dp, end = 12.dp, top = 8.dp, bottom = SAFE_VERTICAL + audioDockInset()),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (stories != null) {
            item(key = "stories") {
                // Fokus v řadě příběhů = uživatel se pohnul: seznam mu ho už nebere zpátky na chat.
                StoryRowContent(stories, Modifier.onFocusChanged { if (it.hasFocus) navigated = true }.padding(bottom = 8.dp))
            }
        }
        items(chats, key = { it.id }) { chat ->
            ChatRow(
                chat = chat,
                savedMessagesId = savedMessagesId,
                repository = repository,
                onClick = { onOpen(chat.id) },
                modifier = (if (chat.id == focusedId) Modifier.focusRequester(focus) else Modifier)
                    .onFocusChanged {
                        if (it.isFocused) {
                            onFocusChat(chat)
                            if (chat.id != focusedId) navigated = true
                        }
                    },
            )
        }
    }
}

@Composable
private fun ChatRow(
    chat: ChatSummary,
    savedMessagesId: Long?,
    repository: ChatRepository,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val title = chatTitle(chat.id, chat.title, savedMessagesId)
    // „Píše…" odebírá každý viditelný řádek sám; odběr skončí s řádkem, který odjede z obrazovky.
    val typing by remember(chat.id) { repository.typing(chat.id) }.collectAsState(null)
    val time = chat.lastMessageTimestamp?.let { if (it.isSameDayAs(nowSeconds())) it.asTimeOfDay() else it.asDate() }
    val unread = chat.unreadCount > 0 || chat.isMarkedAsUnread
    ListItem(
        selected = false,
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        leadingContent = {
            ChatAvatar(fileId = chat.avatarFileId, title = title, size = AVATAR_SIZE, savedMessages = chat.id == savedMessagesId)
        },
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (chat.isPinned) {
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        painterResource(Res.drawable.push_pin_filled_24dp),
                        contentDescription = stringResource(Res.string.chats_section_pinned),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        },
        supportingContent = {
            val text = typing?.activity?.label() ?: chat.lastMessagePreview?.resolve()
            if (text != null) Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                time?.let { Text(it, style = MaterialTheme.typography.labelMedium.tabular()) }
                if (unread) UnreadBadge(chat.unreadCount, muted = chat.isMuted)
            }
        },
    )
}

/**
 * Odznak nepřečtených: vyplněná pilulka s počtem, jako na telefonu. Ztlumený chat má odznak
 * tlumený, ručně označený jako nepřečtený bez počtu je jen tečka.
 */
@Composable
private fun UnreadBadge(count: Int, muted: Boolean) {
    val container = if (muted) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary
    val content = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimary
    Box(
        Modifier
            .heightIn(min = BADGE_SIZE)
            .widthIn(min = BADGE_SIZE)
            .background(container, CircleShape)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (count > 0) {
            Text(
                if (count > MAX_BADGE) "$MAX_BADGE+" else count.toString(),
                style = MaterialTheme.typography.labelMedium.tabular(),
                color = content,
                maxLines = 1,
            )
        }
    }
}

/** Tabulkové číslice: časy a počty stojí ve sloupci a mění se na místě. */
internal fun androidx.compose.ui.text.TextStyle.tabular() = copy(fontFeatureSettings = "tnum")

/** Podíl šířky pro seznam; zbytek je náhled zaostřeného chatu. */
private const val LIST_FRACTION = 0.44f
private val AVATAR_SIZE = 40.dp
private val BADGE_SIZE = 22.dp
private const val MAX_BADGE = 999

/** Kolik řádků před koncem načtené části se dočítá další stránka. */
private const val LOAD_AHEAD = 10

internal fun nowSeconds(): Int = Clock.System.now().epochSeconds.toInt()
