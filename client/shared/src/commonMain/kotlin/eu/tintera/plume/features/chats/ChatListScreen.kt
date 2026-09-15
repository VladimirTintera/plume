package eu.tintera.plume.features.chats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Surface
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.push_pin_filled_24dp
import org.jetbrains.compose.resources.painterResource
import eu.tintera.plume.core.telegram.ChatListState
import eu.tintera.plume.core.telegram.ChatSummary
import eu.tintera.plume.core.ui.Avatar
import eu.tintera.plume.core.ui.chatAvatarKey
import eu.tintera.plume.core.ui.chatTitleKey
import eu.tintera.plume.core.ui.sharedNavElement
import eu.tintera.plume.core.ui.sharedNavText

@Composable
fun ChatListScreen(
    viewModel: ChatsViewModel,
    selectedChatId: Long?,
    onChatClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val actions by viewModel.actions.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val appBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(appBarState)
    val snackbarHostState = remember { SnackbarHostState() }

    // Bez tohohle by neúspěšná akce (typicky překročený limit připnutých chatů) zmizela beze stopy
    // a vypadalo by to, že se nic nestalo.
    LaunchedEffect(actions.error) {
        val message = actions.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.dismissError()
    }

    actions.target?.let { chat ->
        ChatActionsSheet(
            chat = chat,
            onTogglePinned = { viewModel.togglePinned(chat) },
            onToggleMuted = { viewModel.toggleMuted(chat) },
            onToggleUnread = { viewModel.toggleUnread(chat) },
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

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            // Expressive „flexible" top bar — kolabuje při scrollu a mění výšku podle motion schématu.
            LargeFlexibleTopAppBar(
                title = { Text("Chaty") },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
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
                    Text(current.message, style = MaterialTheme.typography.bodyLarge)
                    TextButton(onClick = viewModel::loadMore) { Text("Zkusit znovu") }
                }
            }

            is ChatListState.Ready -> if (current.chats.isEmpty()) {
                Box(
                    Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Zatím tu nic není.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = padding.calculateTopPadding() + 8.dp,
                        bottom = padding.calculateBottomPadding() + 8.dp,
                        start = 16.dp,
                        end = 16.dp,
                    ),
                    // Rozestup UVNITŘ skupiny. Mezera mezi skupinami se přidává u její první
                    // položky — seznam o sousedech položky nic neví.
                    verticalArrangement = Arrangement.spacedBy(GROUP_ITEM_SPACING),
                ) {
                    val groups = current.chats.grouped()

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
}

/**
 * Rozdělení seznamu na skupiny: připnuté, nepřečtené, ostatní.
 *
 * Pořadí uvnitř skupiny zůstává takové, jaké dává TDLib — to je pořadí chatů, na které je
 * uživatel zvyklý z ostatních klientů, a přeskládávat ho by znamenalo rozhodovat za něj.
 *
 * Prázdné skupiny vypadnou, aby po nich nezbyla mezera. Když jsou všechny chaty přečtené,
 * je z toho zase jeden souvislý blok.
 */
private fun List<ChatSummary>.grouped(): List<List<ChatSummary>> = listOf(
    filter { it.isPinned },
    filter { !it.isPinned && it.unreadCount > 0 },
    filter { !it.isPinned && it.unreadCount == 0 },
).filter { it.isNotEmpty() }

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
    // Výběr v list-detail je navigační stav, ne zvýraznění obsahu → `secondaryContainer`,
    // stejná role, jakou používá vybraná položka v nav railu.
    val colors = if (selected) {
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    } else {
        CardDefaults.cardColors()
    }

    /*
     * Zaoblení dělá ze sousedních položek jeden blok: velké nahoře u první, velké dole
     * u poslední, malé mezi nimi. Animuje se, protože skupiny se přeskládávají za běhu —
     * přečtený chat přeskočí z nepřečtených mezi ostatní a tvary sousedů se tím mění.
     */
    val topCorner by animateDpAsState(
        targetValue = if (firstInGroup) GROUP_CORNER else INNER_CORNER,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "chat-corner-top",
    )
    val bottomCorner by animateDpAsState(
        targetValue = if (lastInGroup) GROUP_CORNER else INNER_CORNER,
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
        color = colors.containerColor,
        contentColor = colors.contentColor,
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Row(
            Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Avatar(
                chatId = chat.id,
                title = chat.title,
                avatarFileId = chat.avatarFileId,
                modifier = Modifier.sharedNavElement(chatAvatarKey(chat.id)),
            )

            Column(Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (chat.isPinned) {
                        Icon(
                            painterResource(Res.drawable.push_pin_filled_24dp),
                            contentDescription = "Připnuto",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = chat.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.sharedNavText(chatTitleKey(chat.id)),
                    )
                }
                chat.lastMessagePreview?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = if (chat.unreadCount > 0) FontWeight.Medium else null,
                    )
                }
            }

            if (chat.unreadCount > 0) {
                Badge { Text(chat.unreadCount.toString()) }
            }
        }
    }
}

/** Kolik položek před koncem už spustí dotažení další dávky. */
private const val LOAD_MORE_THRESHOLD = 5

/** Zaoblení na krajích skupiny. */
private val GROUP_CORNER = 20.dp

/** Zaoblení mezi sousedy uvnitř skupiny — tak akorát, aby byly rozeznatelné, ne oddělené. */
private val INNER_CORNER = 4.dp

/** Rozestup mezi položkami jedné skupiny. */
private val GROUP_ITEM_SPACING = 2.dp

/** Mezera mezi skupinami. */
private val GROUP_SPACING = 14.dp
