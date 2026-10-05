package eu.tintera.plume.features.saved

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.material3.TextButton
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
import eu.tintera.plume.resources.common_cancel
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

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
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
                    IconButton(onClick = onBack) {
                        Icon(painterResource(Res.drawable.arrow_back_24dp), contentDescription = stringResource(Res.string.common_back))
                    }
                },
                actions = {
                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { expanded = true }) {
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
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = bottom.calculateBottomPadding() + 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(GROUP_ITEM_SPACING),
                modifier = Modifier.fillMaxSize(),
            ) {
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
            onDelete = { deleteTarget = topic },
        )
    }
    deleteTarget?.let { topic ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(Res.string.saved_topic_delete_title, savedTopicTitle(topic.header))) },
            text = { Text(stringResource(Res.string.saved_topic_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    deleteTarget = null
                    viewModel.delete(topic)
                }) {
                    Text(stringResource(Res.string.saved_topic_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(stringResource(Res.string.common_cancel)) } },
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
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = modifier.fillMaxWidth().clip(shape).combinedClickable(onClick = onClick, onLongClick = onLongClick),
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
    onDelete: () -> Unit,
) {
    val items = listOf(
        Triple(
            stringResource(if (topic.isPinned) Res.string.forum_topic_unpin else Res.string.forum_topic_pin),
            Res.drawable.push_pin_24dp,
            false,
        ) to { onPin(!topic.isPinned) },
        // Moje poznámky jsou vlastní zprávy; smazat je jedním ťuknutím by bylo moc snadné.
        Triple(stringResource(Res.string.saved_topic_delete), Res.drawable.delete_24dp, true) to onDelete,
    ).filter { (item, _) -> !item.third || topic.kind != SavedTopicKind.MyNotes }
    ModalBottomSheet(onDismissRequest = onDismiss) {
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
                Text(savedTopicTitle(topic.header), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            items.forEachIndexed { index, (item, onClick) ->
                val (label, icon, destructive) = item
                val color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                ListItem(
                    headlineContent = { Text(label) },
                    leadingContent = { Icon(painterResource(icon), contentDescription = null) },
                    colors = ListItemDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                        headlineColor = color,
                        leadingIconColor = color,
                    ),
                    modifier = Modifier
                        .clip(groupItemShape(index, items.size))
                        .clickable {
                            onDismiss()
                            onClick()
                        },
                )
            }
        }
    }
}

/** Stejně jako avatar v hlavičce konverzace — hlavička témat je tatáž plocha. */
private val HEADER_AVATAR = 36.dp

private val AVATAR_SIZE = 44.dp
