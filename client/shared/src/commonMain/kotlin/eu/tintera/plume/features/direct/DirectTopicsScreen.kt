package eu.tintera.plume.features.direct

import eu.tintera.plume.core.ui.rememberExpandedSheetState
import eu.tintera.plume.core.ui.ConfirmDialog
import eu.tintera.plume.core.ui.SheetTitle
import eu.tintera.plume.resources.profile_open
import androidx.compose.material3.ShapeDefaults
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalTextStyle
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.telegram.ChatKind
import eu.tintera.plume.core.telegram.DirectTopic
import eu.tintera.plume.core.ui.Avatar
import eu.tintera.plume.core.ui.GROUP_ITEM_SPACING
import eu.tintera.plume.core.ui.IntroEmptyState
import eu.tintera.plume.core.ui.appendWithCustomEmoji
import eu.tintera.plume.core.ui.asRelativeTime
import eu.tintera.plume.core.ui.avatarShape
import eu.tintera.plume.core.ui.chatAvatarKey
import eu.tintera.plume.core.ui.chatTitleKey
import eu.tintera.plume.core.ui.contextClickable
import eu.tintera.plume.core.ui.customEmojiInlineContent
import eu.tintera.plume.core.ui.groupItemShape
import eu.tintera.plume.core.ui.resolve
import eu.tintera.plume.core.ui.sharedNavElement
import eu.tintera.plume.core.ui.sharedNavText
import eu.tintera.plume.core.ui.tabularNumbers
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.arrow_back_24dp
import eu.tintera.plume.resources.chats_action_mark_read
import eu.tintera.plume.resources.chats_action_mark_unread
import eu.tintera.plume.resources.common_back
import eu.tintera.plume.resources.delete_24dp
import eu.tintera.plume.resources.direct_topic_delete
import eu.tintera.plume.resources.direct_topic_delete_body
import eu.tintera.plume.resources.direct_topic_delete_title
import eu.tintera.plume.resources.direct_topic_unknown
import eu.tintera.plume.resources.direct_topics_empty
import eu.tintera.plume.resources.direct_topics_empty_title
import eu.tintera.plume.resources.direct_topics_subtitle
import eu.tintera.plume.resources.done_all_24dp
import eu.tintera.plume.resources.mail_24dp
import eu.tintera.plume.resources.mark_chat_unread_24dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Přímé zprávy kanálu, jak je vidí správce: vlákno na každého, kdo kanálu napsal
 * (`DirectMessagesChatTopic`). Řádky mluví jazykem témat Uložených zpráv — blok zaoblených
 * řádků, tvář odesílatele, poslední zpráva, čas — a navíc nesou nepřečtené. Podržení nabídne
 * označení přečtení a smazání vlákna.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DirectTopicsScreen(
    viewModel: DirectTopicsViewModel,
    onBack: () -> Unit,
    onOpenTopic: (topicId: Long) -> Unit,
    onOpenProfile: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val noticeText = state.notice?.resolve()
    // Snackbar mimo tělo efektu: vynulování hlášky mění klíč a zrušilo by ho.
    LaunchedEffect(state.notice) {
        if (noticeText != null) {
            viewModel.noticeShown()
            scope.launch { snackbarHostState.showSnackbar(noticeText) }
        }
    }
    var actionsFor by remember { mutableStateOf<DirectTopic?>(null) }
    var deleteTarget by remember { mutableStateOf<DirectTopic?>(null) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    val title = state.info?.title.orEmpty()
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        // Vlnka po ťuknutí v zaobleném obdélníku, ne přes celou výšku lišty.
                        modifier = Modifier
                            .clip(ShapeDefaults.Medium)
                            .clickable(onClickLabel = stringResource(Res.string.profile_open), onClick = onOpenProfile)
                            .padding(4.dp),
                    ) {
                        Avatar(
                            chatId = viewModel.chatId,
                            title = title,
                            avatarFileId = state.info?.avatarFileId,
                            size = HEADER_AVATAR,
                            shape = ChatKind.Channel.avatarShape(),
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
                                text = stringResource(Res.string.direct_topics_subtitle),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(painterResource(Res.drawable.arrow_back_24dp), contentDescription = stringResource(Res.string.common_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(scrolledContainerColor = MaterialTheme.colorScheme.surface),
            )
        },
        // Spodek si bere seznam (`contentPadding`): vlákna jedou pod navigační lištu.
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        val bottom = WindowInsets.navigationBars.only(WindowInsetsSides.Bottom).asPaddingValues()
        val topics = state.topics
        when {
            topics == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                LoadingIndicator()
            }
            topics.isEmpty() -> IntroEmptyState(
                icon = Res.drawable.mail_24dp,
                title = stringResource(Res.string.direct_topics_empty_title),
                text = state.error?.resolve() ?: stringResource(Res.string.direct_topics_empty),
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
                    DirectTopicRow(
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
        DirectTopicActionsSheet(
            topic = topic,
            onDismiss = { actionsFor = null },
            onMarkUnread = { viewModel.setMarkedAsUnread(topic, it) },
            onDelete = { deleteTarget = topic },
        )
    }
    deleteTarget?.let { topic ->
        ConfirmDialog(
            title = stringResource(Res.string.direct_topic_delete_title, topicTitle(topic)),
            text = stringResource(Res.string.direct_topic_delete_body),
            confirmLabel = stringResource(Res.string.direct_topic_delete),
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

/** Jméno odesílatele; prázdné, když ho TDLib ještě nezná. */
@Composable
private fun topicTitle(topic: DirectTopic): String =
    topic.title.ifBlank { stringResource(Res.string.direct_topic_unknown) }

private val DirectTopic.hasUnread: Boolean get() = unreadCount > 0 || isMarkedAsUnread

@Composable
private fun DirectTopicRow(
    topic: DirectTopic,
    shape: Shape,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val secondaryColor = MaterialTheme.colorScheme.onSurfaceVariant
    val title = topicTitle(topic)
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = modifier.fillMaxWidth().clip(shape).contextClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Avatar(chatId = topic.senderId, title = title, avatarFileId = topic.avatarFileId, size = AVATAR_SIZE)
            Column(Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = title,
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val messageText = topic.lastMessage?.text?.resolve().orEmpty()
                    val customEmoji = mutableSetOf<Long>()
                    val text = buildAnnotatedString {
                        topic.lastMessage?.let { customEmoji += appendWithCustomEmoji(messageText.replace('\n', ' '), it.entities) }
                    }
                    Text(
                        text = text,
                        inlineContent = customEmojiInlineContent(customEmoji),
                        style = MaterialTheme.typography.bodyMedium,
                        // Nepřečtené se odlišuje barvou, ne řezem — jako v seznamu chatů.
                        color = if (topic.hasUnread) MaterialTheme.colorScheme.onSurface else secondaryColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (topic.hasUnread) {
                        Badge(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ) {
                            // Ručně označené vlákno bez nepřečtených zpráv má jen tečku, jako chat.
                            if (topic.unreadCount > 0) {
                                Text(topic.unreadCount.toString(), style = LocalTextStyle.current.tabularNumbers())
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Nabídka nad vláknem: označit přečtení a smazat. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DirectTopicActionsSheet(
    topic: DirectTopic,
    onDismiss: () -> Unit,
    onMarkUnread: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    val unread = topic.hasUnread
    val items = listOf(
        Triple(
            stringResource(if (unread) Res.string.chats_action_mark_read else Res.string.chats_action_mark_unread),
            if (unread) Res.drawable.done_all_24dp else Res.drawable.mark_chat_unread_24dp,
            false,
        ) to { onMarkUnread(!unread) },
        Triple(stringResource(Res.string.direct_topic_delete), Res.drawable.delete_24dp, true) to onDelete,
    )
    val title = topicTitle(topic)
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
                Avatar(chatId = topic.senderId, title = title, avatarFileId = topic.avatarFileId, size = 32.dp)
                SheetTitle(title, maxLines = 1)
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

/** Stejně jako avatar v hlavičce konverzace — hlavička vláken je tatáž plocha. */
private val HEADER_AVATAR = 36.dp

private val AVATAR_SIZE = 44.dp
