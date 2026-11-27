package eu.tintera.plume.tv

import androidx.compose.foundation.background
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
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.NavigationDrawer
import androidx.tv.material3.NavigationDrawerItem
import androidx.tv.material3.NavigationDrawerScope
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
import eu.tintera.plume.resources.archive_24dp
import eu.tintera.plume.resources.archive_filled_24dp
import eu.tintera.plume.resources.chat_bubble_24dp
import eu.tintera.plume.resources.chat_folders_all
import eu.tintera.plume.resources.chats_archive
import eu.tintera.plume.resources.chats_load_failed
import eu.tintera.plume.resources.chats_section_pinned
import eu.tintera.plume.resources.cloud_off_24dp
import eu.tintera.plume.resources.connection_connecting
import eu.tintera.plume.resources.folder_24dp
import eu.tintera.plume.resources.forum_24dp
import eu.tintera.plume.resources.forum_filled_24dp
import eu.tintera.plume.resources.push_pin_filled_24dp
import eu.tintera.plume.resources.tv_chats_empty
import kotlin.time.Clock
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Hlavní obrazovka: vlevo lišta složek (Všechny chaty, složky Telegramu, Archiv), vpravo chaty
 * vybrané složky. Lišta je `NavigationDrawer` z tv-material — sbalená ukazuje ikony a rozbalí se
 * s popisky, jakmile do ní uživatel šipkou vlevo vjede.
 *
 * [listState] a [lastOpenedChat] drží navigace, ne obrazovka: po návratu z konverzace musí
 * seznam stát na stejném místě a fokus na chatu, ze kterého uživatel odešel — jinak by na
 * televizi začínal znovu od prvního řádku.
 */
@Composable
fun ChatListScreen(
    folder: ChatFolder,
    onFolder: (ChatFolder) -> Unit,
    listState: LazyListState,
    lastOpenedChat: Long?,
    onOpen: (Long) -> Unit,
) {
    val chats = koinInject<ChatRepository>()
    NavigationDrawer(
        drawerContent = { FolderDrawer(folder, onFolder) },
    ) {
        Box(Modifier.fillMaxSize()) {
            ChatListPane(chats, folder, listState, lastOpenedChat, onOpen)
        }
    }
}

@Composable
private fun NavigationDrawerScope.FolderDrawer(selected: ChatFolder, onFolder: (ChatFolder) -> Unit) {
    val chats = koinInject<ChatRepository>()
    val folders by chats.chatFolders.collectAsState()
    val unread by chats.folderUnread.collectAsState()
    val archiveUnread by chats.archiveUnread.collectAsState()
    Column(
        Modifier
            .fillMaxHeight()
            .padding(12.dp)
            .selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        FolderItem(
            title = stringResource(Res.string.chat_folders_all),
            icon = Res.drawable.forum_24dp,
            selectedIcon = Res.drawable.forum_filled_24dp,
            unread = 0,
            selected = selected == ChatFolder.Main,
            onClick = { onFolder(ChatFolder.Main) },
        )
        folders.folders.forEach { tab ->
            FolderItem(
                title = tab.title,
                icon = Res.drawable.folder_24dp,
                selectedIcon = Res.drawable.folder_24dp,
                unread = unread[tab.id]?.chats ?: 0,
                selected = selected == ChatFolder.Custom(tab.id),
                onClick = { onFolder(ChatFolder.Custom(tab.id)) },
            )
        }
        FolderItem(
            title = stringResource(Res.string.chats_archive),
            icon = Res.drawable.archive_24dp,
            selectedIcon = Res.drawable.archive_filled_24dp,
            unread = archiveUnread.chats,
            selected = selected == ChatFolder.Archive,
            onClick = { onFolder(ChatFolder.Archive) },
        )
    }
}

/** Položka lišty; vybraná má vyplněnou ikonu, počet nepřečtených chatů je vidět jen rozbalený. */
@Composable
private fun NavigationDrawerScope.FolderItem(
    title: String,
    icon: DrawableResource,
    selectedIcon: DrawableResource,
    unread: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    NavigationDrawerItem(
        selected = selected,
        onClick = onClick,
        leadingContent = {
            Icon(painterResource(if (selected) selectedIcon else icon), contentDescription = null)
        },
        trailingContent = if (unread > 0) {
            { Text(unread.toString(), style = MaterialTheme.typography.labelLarge.tabular()) }
        } else {
            null
        },
    ) {
        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ChatListPane(
    chats: ChatRepository,
    folder: ChatFolder,
    listState: LazyListState,
    lastOpenedChat: Long?,
    onOpen: (Long) -> Unit,
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
                ChatList(chats, folder, current.chats, listState, lastOpenedChat, onOpen)
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
    onOpen: (Long) -> Unit,
) {
    val savedMessagesId = savedMessagesChatId()
    val focus = remember { FocusRequester() }
    // Fokus na chat, ze kterého se uživatel vrátil, jinak na první. Bez toho by po návratu
    // z konverzace fokus nebyl nikde a první stisk šipky by skočil do lišty složek.
    val focusedId = lastOpenedChat?.takeIf { id -> chats.any { it.id == id } } ?: chats.first().id
    // Složka se dočítá po dávkách a chaty s vyšším pořadím přibývají NAD zaostřeným řádkem —
    // seznam drží první viditelný řádek podle klíče, takže zaostřený odjel nad horní okraj
    // a fokus nebyl vidět (ověřeno na emulátoru). Dokud uživatel sám nepohne šipkou, fokus se
    // proto přenastavuje s každou změnou a řádek se posune do obrazu.
    var navigated by remember(folder, lastOpenedChat) { mutableStateOf(false) }
    LaunchedEffect(folder, focusedId, navigated, chats) {
        if (navigated) return@LaunchedEffect
        val index = chats.indexOfFirst { it.id == focusedId }
        if (index >= 0 && listState.layoutInfo.visibleItemsInfo.none { it.index == index }) listState.scrollToItem(index)
        runCatching { focus.requestFocus() }
    }
    // Dočítání: když se fokus blíží konci načtené části, požádá se o další stránku.
    val loaded by rememberUpdatedState(chats.size)
    LaunchedEffect(folder, listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { last -> if (last != null && last >= loaded - LOAD_AHEAD) repository.loadMore(folder) }
    }
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = 32.dp, end = 48.dp, top = 32.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(chats, key = { it.id }) { chat ->
            ChatRow(
                chat = chat,
                savedMessagesId = savedMessagesId,
                repository = repository,
                onClick = { onOpen(chat.id) },
                modifier = (if (chat.id == focusedId) Modifier.focusRequester(focus) else Modifier)
                    .onFocusChanged { if (it.isFocused && chat.id != focusedId) navigated = true },
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
 * tlumený, ručně označený jako nepřečtený bez počtu je jen tečka. Barvy si bere z obsahu
 * řádku, takže na zaostřeném (světlém) řádku zůstane čitelný.
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
private fun androidx.compose.ui.text.TextStyle.tabular() = copy(fontFeatureSettings = "tnum")

private val AVATAR_SIZE = 48.dp
private val BADGE_SIZE = 22.dp
private const val MAX_BADGE = 999

/** Kolik řádků před koncem načtené části se dočítá další stránka. */
private const val LOAD_AHEAD = 10

private fun nowSeconds(): Int = Clock.System.now().epochSeconds.toInt()
