package eu.tintera.plume.features.saved

import eu.tintera.plume.features.conversation.rememberChatAccounts
import eu.tintera.plume.features.conversation.AccountSwitchButton
import eu.tintera.plume.core.accounts.LocalAccount
import eu.tintera.plume.core.accounts.AccountSlot
import eu.tintera.plume.core.ui.groupRow
import eu.tintera.plume.core.ui.rememberExpandedSheetState
import eu.tintera.plume.core.ui.ConfirmDialog
import eu.tintera.plume.core.ui.SheetTitle
import androidx.compose.foundation.clickable
import eu.tintera.plume.core.ui.contextClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import eu.tintera.plume.core.telegram.pinnedSavedTopics
import eu.tintera.plume.core.ui.ReorderRow
import eu.tintera.plume.core.ui.ReorderTopBar
import eu.tintera.plume.core.ui.draggedItem
import eu.tintera.plume.core.ui.rememberDragReorderState
import eu.tintera.plume.resources.drag_indicator_24dp
import eu.tintera.plume.resources.forum_topic_reorder
import eu.tintera.plume.resources.saved_topics_reorder_hint
import eu.tintera.plume.resources.saved_topics_reorder_title
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.telegram.SavedTopic
import eu.tintera.plume.core.telegram.SavedTopicKind
import eu.tintera.plume.core.telegram.header
import eu.tintera.plume.core.ui.Avatar
import eu.tintera.plume.core.ui.ExpressiveMenu
import eu.tintera.plume.core.ui.GROUP_ITEM_SPACING
import eu.tintera.plume.core.ui.IntroEmptyState
import eu.tintera.plume.core.ui.MenuAction
import eu.tintera.plume.core.ui.SavedTopicAvatar
import eu.tintera.plume.core.ui.appendWithCustomEmoji
import eu.tintera.plume.core.ui.asRelativeTime
import eu.tintera.plume.core.ui.chatAvatarKey
import eu.tintera.plume.core.ui.chatTitleKey
import eu.tintera.plume.core.ui.customEmojiInlineContent
import eu.tintera.plume.core.ui.groupItemShape
import eu.tintera.plume.core.ui.resolve
import eu.tintera.plume.core.ui.savedTopicTitle
import eu.tintera.plume.core.ui.sharedNavElement
import eu.tintera.plume.core.ui.sharedNavText
import eu.tintera.plume.core.ui.tabularNumbers
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.arrow_back_24dp
import eu.tintera.plume.resources.bookmark_24dp
import eu.tintera.plume.resources.chats_section_pinned
import eu.tintera.plume.resources.common_back
import eu.tintera.plume.resources.delete_24dp
import eu.tintera.plume.resources.format_list_bulleted_24dp
import eu.tintera.plume.resources.forum_topic_pin
import eu.tintera.plume.resources.forum_topic_unpin
import eu.tintera.plume.resources.more_vert_24dp
import eu.tintera.plume.resources.profile_more
import eu.tintera.plume.resources.push_pin_24dp
import eu.tintera.plume.resources.push_pin_filled_24dp
import eu.tintera.plume.resources.saved_messages
import eu.tintera.plume.resources.saved_topic_delete
import eu.tintera.plume.resources.saved_topic_delete_body
import eu.tintera.plume.resources.saved_topic_delete_title
import eu.tintera.plume.resources.saved_topics_empty
import eu.tintera.plume.resources.saved_topics_empty_title
import eu.tintera.plume.resources.saved_topics_subtitle
import eu.tintera.plume.resources.saved_view_as_messages
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import eu.tintera.plume.core.adaptive.WindowControlsEndSpace
import eu.tintera.plume.core.adaptive.WindowControlsSpace

/**
 * Uložené zprávy rozdělené podle chatu, ze kterého se zpráva uložila (`SavedMessagesTopic`).
 * Řádky mluví jazykem seznamu témat skupiny: blok zaoblených řádků, tvář zdrojového chatu,
 * poslední uložená zpráva a čas. Podržení nabídne připnutí a smazání.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SavedTopicsScreen(
    viewModel: SavedTopicsViewModel,
    onBack: () -> Unit,
    onOpenTopic: (topicId: Long) -> Unit,
    /** Uložené zprávy jako jedna konverzace; volba se pamatuje. */
    onViewAsMessages: () -> Unit,
    /**
     * Výběr účtu v liště (režim Všechny účty, víc účtů): Uložené zprávy jiného účtu, s jejich
     * id. Seznam témat nemá pole zprávy, kde výběr stojí v konverzaci. `null` = nenabízet.
     */
    onSwitchAccount: ((slot: AccountSlot, chatId: Long) -> Unit)? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val noticeText = state.notice?.resolve()
    // Snackbar mimo tělo efektu: vynulování hlášky mění klíč a zrušilo by ho (viz „Vrátit").
    LaunchedEffect(state.notice) {
        if (noticeText != null) {
            viewModel.noticeShown()
            scope.launch { snackbarHostState.showSnackbar(noticeText) }
        }
    }
    var actionsFor by remember { mutableStateOf<SavedTopic?>(null) }
    var deleteTarget by remember { mutableStateOf<SavedTopic?>(null) }

    // Režim uspořádání připnutých: zpět ho zruší bez uložení, teprve další zpět odejde.
    val reorder = state.reorder
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = reorder != null,
        onBackCompleted = viewModel::cancelReorder,
    )
    val listState = rememberLazyListState()
    val reorderIds = reorder?.map { it.id }.orEmpty()
    val dragState = rememberDragReorderState(
        listState = listState,
        canMove = { key -> key in reorderIds },
        onMove = { from, to -> viewModel.moveInReorder(from as Long, to as Long) },
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (reorder != null) {
                ReorderTopBar(
                    title = stringResource(Res.string.saved_topics_reorder_title),
                    busy = state.busy,
                    onCancel = viewModel::cancelReorder,
                    onDone = viewModel::saveReorder,
                )
            } else TopAppBar(
                title = {
                    val title = stringResource(Res.string.saved_messages)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(
                            chatId = viewModel.chatId,
                            title = title,
                            avatarFileId = null,
                            size = HEADER_AVATAR,
                            isSavedMessages = true,
                            modifier = Modifier.padding(end = 12.dp).sharedNavElement(chatAvatarKey(viewModel.chatId)),
                        )
                        Column {
                            Text(
                                text = title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.sharedNavText(chatTitleKey(viewModel.chatId)),
                            )
                            Text(
                                text = stringResource(Res.string.saved_topics_subtitle),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                },
                navigationIcon = {
                    WindowControlsSpace {
                        IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                            Icon(painterResource(Res.drawable.arrow_back_24dp), contentDescription = stringResource(Res.string.common_back))
                        }
                    }
                },
                actions = {
                    // Tentýž výběr jako u pole zprávy v konverzaci (`AccountSwitchButton`).
                    val accounts = rememberChatAccounts(viewModel.chatId, enabled = onSwitchAccount != null)
                    if (onSwitchAccount != null && accounts.size > 1) {
                        AccountSwitchButton(accounts, LocalAccount.current.slot, { target -> onSwitchAccount(target.slot, target.chatId) })
                    }
                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { expanded = true }, shapes = IconButtonDefaults.shapes()) {
                            Icon(painterResource(Res.drawable.more_vert_24dp), contentDescription = stringResource(Res.string.profile_more))
                        }
                        ExpressiveMenu(
                            expanded = expanded,
                            onDismiss = { expanded = false },
                            groups = listOf(
                                listOf(
                                    MenuAction(
                                        stringResource(Res.string.saved_view_as_messages),
                                        Res.drawable.format_list_bulleted_24dp,
                                        onClick = {
                                            expanded = false
                                            viewModel.viewAsMessages()
                                            onViewAsMessages()
                                        },
                                    ),
                                ),
                            ),
                        )
                    }
                    WindowControlsEndSpace()
                },
                colors = TopAppBarDefaults.topAppBarColors(scrolledContainerColor = MaterialTheme.colorScheme.surface),
            )
        },
        // Spodek si bere seznam (`contentPadding`): témata jedou pod navigační lištu.
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        val bottom = WindowInsets.navigationBars.only(WindowInsetsSides.Bottom).asPaddingValues()
        val topics = state.topics
        when {
            topics == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                LoadingIndicator()
            }
            topics.isEmpty() -> IntroEmptyState(
                icon = Res.drawable.bookmark_24dp,
                title = stringResource(Res.string.saved_topics_empty_title),
                text = state.error?.resolve() ?: stringResource(Res.string.saved_topics_empty),
                modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(
                state = listState,
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = bottom.calculateBottomPadding() + 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(GROUP_ITEM_SPACING),
                modifier = Modifier.fillMaxSize(),
            ) {
                // Uspořádání ukazuje jen připnutá — ostatní se přeskládat nedají. Jako u témat skupiny.
                if (reorder != null) {
                    item(key = "reorder-hint") {
                        Text(
                            stringResource(Res.string.saved_topics_reorder_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                        )
                    }
                    itemsIndexed(reorder, key = { _, topic -> topic.id }) { index, topic ->
                        val dragged = dragState.draggedKey == topic.id
                        ReorderRow(
                            key = topic.id,
                            title = savedTopicTitle(topic.header),
                            shape = groupItemShape(index, reorder.size),
                            dragState = dragState,
                            onMoveUp = reorder.getOrNull(index - 1)?.let { above -> { viewModel.moveInReorder(topic.id, above.id) } },
                            onMoveDown = reorder.getOrNull(index + 1)?.let { below -> { viewModel.moveInReorder(topic.id, below.id) } },
                            // Tažená položka jede pod prstem; animace umístění by se s tím prala.
                            modifier = Modifier
                                .then(if (dragged) Modifier else Modifier.animateItem())
                                .draggedItem(dragState, topic.id),
                        ) {
                            SavedTopicAvatar(topic.header, REORDER_AVATAR_SIZE)
                        }
                    }
                    return@LazyColumn
                }
                itemsIndexed(topics, key = { _, topic -> topic.id }) { index, topic ->
                    SavedTopicRow(
                        topic = topic,
                        shape = groupItemShape(index, topics.size),
                        onClick = { onOpenTopic(topic.id) },
                        onLongClick = { actionsFor = topic },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
    actionsFor?.let { topic ->
        SavedTopicActionsSheet(
            topic = topic,
            onDismiss = { actionsFor = null },
            onPin = { viewModel.setPinned(topic, it) },
            onReorder = viewModel::startReorder.takeIf { topic.isPinned && state.topics.orEmpty().pinnedSavedTopics().size >= 2 },
            onDelete = { deleteTarget = topic },
        )
    }
    deleteTarget?.let { topic ->
        ConfirmDialog(
            title = stringResource(Res.string.saved_topic_delete_title, savedTopicTitle(topic.header)),
            text = stringResource(Res.string.saved_topic_delete_body),
            confirmLabel = stringResource(Res.string.saved_topic_delete),
            onConfirm = {
                deleteTarget = null
                viewModel.delete(topic)
            },
            onDismiss = { deleteTarget = null },
            icon = Res.drawable.delete_24dp,
            destructive = true,
        )
    }
}

@Composable
private fun SavedTopicRow(
    topic: SavedTopic,
    shape: androidx.compose.ui.graphics.Shape,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val secondaryColor = MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.groupRow,
        modifier = modifier.fillMaxWidth().clip(shape).contextClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SavedTopicAvatar(topic.header, AVATAR_SIZE)
            Column(Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (topic.isPinned) {
                        Icon(
                            painterResource(Res.drawable.push_pin_filled_24dp),
                            contentDescription = stringResource(Res.string.chats_section_pinned),
                            modifier = Modifier.size(14.dp),
                            tint = secondaryColor,
                        )
                    }
                    Text(
                        text = savedTopicTitle(topic.header),
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    topic.lastMessage?.let { message ->
                        Text(
                            text = message.timestamp.asRelativeTime(),
                            style = MaterialTheme.typography.labelMedium.tabularNumbers(),
                            color = secondaryColor,
                            maxLines = 1,
                        )
                    }
                }
                topic.lastMessage?.let { message ->
                    val messageText = message.text.resolve()
                    val customEmoji = mutableSetOf<Long>()
                    val text = buildAnnotatedString {
                        customEmoji += appendWithCustomEmoji(messageText.replace('\n', ' '), message.entities)
                    }
                    Text(
                        text = text,
                        inlineContent = customEmojiInlineContent(customEmoji),
                        style = MaterialTheme.typography.bodyMedium,
                        color = secondaryColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Nabídka nad tématem: připnout a smazat. Ztlumit ani přejmenovat Uložené zprávy nejdou. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SavedTopicActionsSheet(
    topic: SavedTopic,
    onDismiss: () -> Unit,
    onPin: (Boolean) -> Unit,
    /** Uspořádat připnutá — jen u připnutého tématu a když je co přeskládat; jinak `null`. */
    onReorder: (() -> Unit)?,
    onDelete: () -> Unit,
) {
    val items = listOfNotNull(
        Triple(
            stringResource(if (topic.isPinned) Res.string.forum_topic_unpin else Res.string.forum_topic_pin),
            Res.drawable.push_pin_24dp,
            false,
        ) to { onPin(!topic.isPinned) },
        onReorder?.let { reorder ->
            Triple(stringResource(Res.string.forum_topic_reorder), Res.drawable.drag_indicator_24dp, false) to reorder
        },
        // Moje poznámky jsou vlastní zprávy; smazat je jedním ťuknutím by bylo moc snadné.
        Triple(stringResource(Res.string.saved_topic_delete), Res.drawable.delete_24dp, true) to onDelete,
    ).filter { (item, _) -> !item.third || topic.kind != SavedTopicKind.MyNotes }
    ModalBottomSheet(sheetState = rememberExpandedSheetState(), onDismissRequest = onDismiss) {
        Column(
            Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(GROUP_ITEM_SPACING),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(start = 16.dp, bottom = 12.dp),
            ) {
                SavedTopicAvatar(topic.header, 32.dp)
                SheetTitle(savedTopicTitle(topic.header), maxLines = 1)
            }
            items.forEachIndexed { index, (item, onClick) ->
                val (label, icon, destructive) = item
                val color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                ListItem(
                    leadingContent = { Icon(painterResource(icon), contentDescription = null) },
                    colors = ListItemDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.groupRow,
                        headlineColor = color,
                        leadingIconColor = color,
                    ),
                    modifier = Modifier
                        .clip(groupItemShape(index, items.size))
                        .clickable {
                            onDismiss()
                            onClick()
                        },
                ) { Text(label) }
            }
        }
    }
}

/** Stejně jako avatar v hlavičce konverzace — hlavička témat je tatáž plocha. */
private val HEADER_AVATAR = 36.dp

private val AVATAR_SIZE = 44.dp

/** Ikona v řádku uspořádání — menší, řádek nese úchyt a šipky. */
private val REORDER_AVATAR_SIZE = 32.dp
