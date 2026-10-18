package eu.tintera.plume.features.forum

import androidx.compose.foundation.layout.width
import androidx.compose.material3.ShapeDefaults
import eu.tintera.plume.resources.search_off_24dp
import eu.tintera.plume.core.ui.GroupEmptyRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.IconButtonDefaults
import kotlinx.coroutines.launch
import eu.tintera.plume.resources.visibility_24dp
import eu.tintera.plume.resources.chats_search_clear
import eu.tintera.plume.resources.forum_topics_search
import eu.tintera.plume.resources.forum_topics_nothing_found
import eu.tintera.plume.resources.forum_topic_show_general
import eu.tintera.plume.resources.forum_topic_new
import eu.tintera.plume.resources.add_24dp
import eu.tintera.plume.core.ui.SearchPill
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.foundation.layout.navigationBarsPadding
import eu.tintera.plume.core.ui.contextClickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.background
import eu.tintera.plume.features.conversation.JoinRequestsBar
import eu.tintera.plume.core.telegram.ChatKind
import eu.tintera.plume.core.telegram.ForumTopic
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.TextButton
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import eu.tintera.plume.core.telegram.canReorderPinned
import eu.tintera.plume.core.ui.ReorderRow
import eu.tintera.plume.core.ui.ReorderTopBar
import eu.tintera.plume.core.ui.draggedItem
import eu.tintera.plume.core.ui.rememberDragReorderState
import eu.tintera.plume.resources.common_cancel
import eu.tintera.plume.resources.forum_topics_reorder_title
import eu.tintera.plume.resources.forum_topics_reorder_hint
import eu.tintera.plume.features.profile.ChatNotificationSheet
import eu.tintera.plume.features.profile.rememberChatSounds
import eu.tintera.plume.features.profile.chatSoundLabel
import eu.tintera.plume.features.profile.ChatSoundHost
import eu.tintera.plume.core.ui.Avatar
import eu.tintera.plume.core.ui.ExpressiveMenu
import eu.tintera.plume.core.ui.ForumTopicIcon
import eu.tintera.plume.core.ui.IntroEmptyState
import eu.tintera.plume.core.ui.MenuAction
import eu.tintera.plume.core.ui.accentOn
import eu.tintera.plume.core.ui.appendWithCustomEmoji
import eu.tintera.plume.core.ui.asRelativeTime
import eu.tintera.plume.core.ui.avatarShape
import eu.tintera.plume.core.ui.chatAvatarKey
import eu.tintera.plume.core.ui.chatTitleKey
import eu.tintera.plume.core.ui.customEmojiInlineContent
import eu.tintera.plume.core.ui.groupItemShape
import eu.tintera.plume.core.ui.GROUP_ITEM_SPACING
import eu.tintera.plume.core.ui.resolve
import eu.tintera.plume.core.ui.sharedNavElement
import eu.tintera.plume.core.ui.sharedNavText
import eu.tintera.plume.core.ui.tabularNumbers
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.alternate_email_24dp
import eu.tintera.plume.resources.arrow_back_24dp
import eu.tintera.plume.resources.chats_draft_prefix
import eu.tintera.plume.resources.chats_muted
import eu.tintera.plume.resources.chats_section_pinned
import eu.tintera.plume.resources.chats_unread_mention
import eu.tintera.plume.resources.chats_unread_reaction
import eu.tintera.plume.resources.common_back
import eu.tintera.plume.resources.favorite_filled_24dp
import eu.tintera.plume.resources.forum_24dp
import eu.tintera.plume.resources.forum_topic_closed
import eu.tintera.plume.resources.forum_topics_empty
import eu.tintera.plume.resources.forum_topics_empty_title
import eu.tintera.plume.resources.forum_topics_subtitle
import eu.tintera.plume.resources.forum_view_as_messages
import eu.tintera.plume.resources.format_list_bulleted_24dp
import eu.tintera.plume.resources.lock_24dp
import eu.tintera.plume.resources.more_vert_24dp
import eu.tintera.plume.resources.notifications_off_24dp
import eu.tintera.plume.resources.profile_more
import eu.tintera.plume.resources.profile_open
import eu.tintera.plume.resources.push_pin_filled_24dp
import eu.tintera.plume.resources.sender_you
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Seznam témat skupiny s tématy. Řádky mluví jazykem seznamu chatů: blok zaoblených řádků,
 * tučný název jen u nepřečteného, pod ním kdo naposledy psal a co, vpravo čas a odznaky.
 *
 * Ikona tématu (`ForumTopicIcon`) stojí na místě avataru. Hlavička nese fotku a název skupiny
 * — letí sem ze seznamu chatů stejně jako do konverzace, a ťuknutí na ni otevře profil skupiny.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ForumTopicsScreen(
    viewModel: ForumTopicsViewModel,
    onBack: () -> Unit,
    onOpenTopic: (topicId: Int) -> Unit,
    onOpenProfile: () -> Unit,
    /** Celá skupina jako jedna konverzace, jak ji znají klienti bez témat. */
    onViewAsMessages: () -> Unit,
    /** Pruh s čekajícími žádostmi o vstup: otevře jejich seznam. */
    onOpenJoinRequests: () -> Unit = {},
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
    val currentOnOpenTopic by rememberUpdatedState(onOpenTopic)
    LaunchedEffect(state.createdTopicId) {
        val created = state.createdTopicId ?: return@LaunchedEffect
        currentOnOpenTopic(created)
        viewModel.createdTopicOpened()
    }
    val joinRequests by viewModel.joinRequests.collectAsStateWithLifecycle()
    // ✕ schová pruh do další žádosti, stejně jako nad konverzací (pamatuje se počet).
    var joinRequestsHiddenAt by rememberSaveable(viewModel.chatId) { mutableIntStateOf(0) }
    var actionsFor by remember { mutableStateOf<ForumTopic?>(null) }
    var deleteTarget by remember { mutableStateOf<ForumTopic?>(null) }

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
        onMove = { from, to -> viewModel.moveInReorder(from as Int, to as Int) },
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (state.rights.canCreate && reorder == null) {
                // Obsahová varianta, ne `text`/`icon`: ta popisek skrývá před čtečkou obrazovky
                // (`clearAndSetSemantics`) a tlačítko by nemělo žádný název.
                ExtendedFloatingActionButton(
                    onClick = viewModel::openCreate,
                    // Nad navigační lištou: Scaffold tu insety nemá (`contentWindowInsets = 0`).
                    modifier = Modifier.navigationBarsPadding(),
                ) {
                    Icon(painterResource(Res.drawable.add_24dp), contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(Res.string.forum_topic_new))
                }
            }
        },
        topBar = {
            if (reorder != null) {
                ReorderTopBar(
                    title = stringResource(Res.string.forum_topics_reorder_title),
                    busy = state.busy,
                    onCancel = viewModel::cancelReorder,
                    onDone = viewModel::saveReorder,
                )
            } else TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(ShapeDefaults.Medium)
                            .clickable(onClickLabel = stringResource(Res.string.profile_open), onClick = onOpenProfile)
                            .padding(4.dp),
                    ) {
                        state.info?.let { info ->
                            Avatar(
                                chatId = info.id,
                                title = info.title,
                                avatarFileId = info.avatarFileId,
                                size = HEADER_AVATAR,
                                shape = ChatKind.Group.avatarShape(),
                                modifier = Modifier.padding(end = 12.dp).sharedNavElement(chatAvatarKey(info.id)),
                            )
                            Column {
                                Text(
                                    text = info.title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.sharedNavText(chatTitleKey(info.id)),
                                )
                                Text(
                                    text = stringResource(Res.string.forum_topics_subtitle),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(painterResource(Res.drawable.arrow_back_24dp), contentDescription = stringResource(Res.string.common_back))
                    }
                },
                actions = {
                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { expanded = true }, shapes = IconButtonDefaults.shapes()) {
                            Icon(painterResource(Res.drawable.more_vert_24dp), contentDescription = stringResource(Res.string.profile_more))
                        }
                        ExpressiveMenu(
                            expanded = expanded,
                            onDismiss = { expanded = false },
                            groups = listOf(
                                listOfNotNull(
                                    MenuAction(
                                        stringResource(Res.string.forum_view_as_messages),
                                        Res.drawable.format_list_bulleted_24dp,
                                        onClick = {
                                            expanded = false
                                            viewModel.viewAsMessages(onViewAsMessages)
                                        },
                                    ),
                                    // Skryté obecné téma v seznamu není, takže se vrací odsud.
                                    MenuAction(
                                        stringResource(Res.string.forum_topic_show_general),
                                        Res.drawable.visibility_24dp,
                                        onClick = {
                                            expanded = false
                                            viewModel.setGeneralHidden(false)
                                        },
                                    ).takeIf { state.general?.isHidden == true && state.rights.canManage },
                                ),
                            ),
                        )
                    }
                },
                // Sbalený bar barvu nemění — tónovaný pruh přes celou šířku je to, co z hlavičky
                // seznamu chatů vyhnalo velký bar.
                colors = TopAppBarDefaults.topAppBarColors(scrolledContainerColor = MaterialTheme.colorScheme.surface),
            )
        },
        // Spodek si bere seznam (`contentPadding`): témata jedou pod navigační lištu.
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        val bottom = WindowInsets.navigationBars.only(WindowInsetsSides.Bottom).asPaddingValues()
        when {
            state.loading && state.topics.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                LoadingIndicator()
            }
            state.topics.isEmpty() -> IntroEmptyState(
                icon = Res.drawable.forum_24dp,
                title = stringResource(Res.string.forum_topics_empty_title),
                text = state.error?.resolve() ?: stringResource(Res.string.forum_topics_empty),
                modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(
                state = listState,
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    // Místo na tlačítko nového tématu, jinak by zakrylo poslední téma.
                    bottom = bottom.calculateBottomPadding() + if (state.rights.canCreate) FAB_CLEARANCE else 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(GROUP_ITEM_SPACING),
                modifier = Modifier.fillMaxSize(),
            ) {
                /*
                 * Hledání podle názvu jako první položka seznamu, ne v liště: odjede s obsahem
                 * a u pár témat nepřekáží. Hledá server (`getForumTopics` s dotazem), takže
                 * najde i téma, které ještě není načtené.
                 */
                if (reorder != null) {
                    item(key = "reorder-hint") {
                        Text(
                            stringResource(Res.string.forum_topics_reorder_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                        )
                    }
                    itemsIndexed(reorder, key = { _, topic -> topic.id }) { index, topic ->
                        val dragged = dragState.draggedKey == topic.id
                        ReorderRow(
                            key = topic.id,
                            title = topic.name,
                            shape = groupItemShape(index, reorder.size),
                            dragState = dragState,
                            onMoveUp = reorder.getOrNull(index - 1)?.let { above -> { viewModel.moveInReorder(topic.id, above.id) } },
                            onMoveDown = reorder.getOrNull(index + 1)?.let { below -> { viewModel.moveInReorder(topic.id, below.id) } },
                            // Tažená položka jede pod prstem; animace umístění by se s tím prala.
                            modifier = Modifier
                                .then(if (dragged) Modifier else Modifier.animateItem())
                                .draggedItem(dragState, topic.id),
                        ) {
                            ForumTopicIcon(icon = topic.icon, name = topic.name, isGeneral = topic.isGeneral, size = ICON_SIZE)
                        }
                    }
                    return@LazyColumn
                }
                // Žádosti o vstup nad hledáním: čekají na rozhodnutí správce, jako nad konverzací.
                // Blok seznamu, ne plovoucí pruh — seznam témat nad sebou nic nescrolluje.
                joinRequests?.takeIf { it.count > joinRequestsHiddenAt }?.let { requests ->
                    item(key = "join-requests") {
                        JoinRequestsBar(
                            requests = requests,
                            onClick = onOpenJoinRequests,
                            onHide = { joinRequestsHiddenAt = requests.count },
                            glass = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh),
                            floating = false,
                            modifier = Modifier.animateItem().padding(bottom = 12.dp),
                        )
                    }
                }
                item(key = "search") {
                    SearchPill(
                        initialQuery = "",
                        onQueryChange = viewModel::search,
                        placeholder = stringResource(Res.string.forum_topics_search),
                        clearDescription = stringResource(Res.string.chats_search_clear),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    )
                }
                val shown = state.searchResults ?: state.topics
                if (state.searchResults?.isEmpty() == true) {
                    item(key = "nothing") {
                        GroupEmptyRow(stringResource(Res.string.forum_topics_nothing_found), icon = Res.drawable.search_off_24dp)
                    }
                }
                itemsIndexed(shown, key = { _, topic -> topic.id }) { index, topic ->
                    TopicRow(
                        topic = topic,
                        shape = groupItemShape(index, shown.size),
                        onClick = { onOpenTopic(topic.id) },
                        onLongClick = { actionsFor = topic },
                        modifier = Modifier.animateItem(),
                    )
                    // Dotažení další stránky, když se seznam blíží ke konci. Skládání položky je
                    // spolehlivější než `layoutInfo` (viz „Sdílená média v profilu").
                    if (state.searchResults == null && index >= state.topics.size - LOAD_MORE_THRESHOLD && state.next != null) {
                        LaunchedEffect(state.topics.size) { viewModel.loadMore() }
                    }
                }
            }
        }
    }
    actionsFor?.let { topic ->
        TopicActionsSheet(
            topic = topic,
            rights = state.rights,
            onDismiss = { actionsFor = null },
            onPin = { viewModel.setPinned(topic, it) },
            onMute = { viewModel.setMuted(topic, it) },
            onNotifications = { viewModel.openNotifications(topic) },
            onReorder = viewModel::startReorder.takeIf { canReorderPinned(state.topics, state.rights) },
            onRename = { viewModel.openRename(topic) },
            onClose = { viewModel.setClosed(topic, it) },
            onHideGeneral = { viewModel.setGeneralHidden(true) },
            onDelete = { deleteTarget = topic },
        )
    }
    // Zvuk tématu: tentýž výběr jako u chatu v profilu. Téma, kterému se vybírá, si drží
    // obrazovka — sheet upozornění se při výběru zvuku zavře (dva sheety přes sebe ne).
    val sounds = rememberChatSounds(viewModel.chatId)
    val tones by sounds.tones.collectAsStateWithLifecycle()
    var soundFor by remember { mutableStateOf<ForumTopic?>(null) }
    state.notificationsFor?.let { topic ->
        state.topicNotifications?.let { prefs ->
            ChatNotificationSheet(
                prefs = prefs,
                title = topic.name,
                // Kanál konverzace je jeden na CHAT: notifikace všech témat skupiny jdou jím.
                supportsConversationSettings = false,
                onMute = viewModel::muteTopic,
                onUnmute = viewModel::unmuteTopic,
                onOption = viewModel::setTopicOption,
                onOpenConversationSettings = {},
                onReset = viewModel::resetTopicNotifications,
                onDismiss = viewModel::closeNotifications,
                soundLabel = chatSoundLabel(prefs, tones),
                onPickSound = {
                    soundFor = topic
                    viewModel.closeNotifications()
                },
            )
        }
    }
    soundFor?.let { topic ->
        val prefs by remember(topic.id) { viewModel.topicSettings(topic.id) }.collectAsStateWithLifecycle(null)
        ChatSoundHost(
            viewModel = sounds,
            chatId = viewModel.chatId,
            prefs = prefs,
            topicId = topic.id,
            open = true,
            onClose = { soundFor = null },
            snackbarHostState = snackbarHostState,
        )
    }
    deleteTarget?.let { topic ->
        DeleteTopicDialog(
            topic = topic,
            onConfirm = {
                deleteTarget = null
                viewModel.delete(topic)
            },
            onDismiss = { deleteTarget = null },
        )
    }
    state.editor?.let { editor ->
        TopicEditorSheet(
            editor = editor,
            busy = state.busy,
            onChange = viewModel::updateEditor,
            onSave = viewModel::saveEditor,
            onDismiss = viewModel::closeEditor,
        )
    }
}

@Composable
private fun TopicRow(
    topic: ForumTopic,
    shape: androidx.compose.ui.graphics.Shape,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
    val secondaryColor = MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        shape = shape,
        color = containerColor,
        // Podržení otevře nabídku nad tématem, jako podržení chatu v seznamu chatů.
        modifier = modifier.fillMaxWidth().clip(shape).contextClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ForumTopicIcon(icon = topic.icon, name = topic.name, isGeneral = topic.isGeneral, size = ICON_SIZE)
            Column(Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (topic.isPinned) {
                        SmallIcon(Res.drawable.push_pin_filled_24dp, stringResource(Res.string.chats_section_pinned))
                    }
                    // Název a ikony za ním vyplní zbytek řádku, takže čas sedí na konci. Název
                    // uvnitř má váhu BEZ vyplnění — se dvěma vahami vedle sebe (název a mezera)
                    // si řádek rozdělily napůl a čas visel uprostřed.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = topic.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (topic.hasUnread) FontWeight.SemiBold else null,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        // Uzavřené téma: psát do něj smí jen správci — zámek to řekne dřív, než se otevře.
                        if (topic.isClosed) {
                            SmallIcon(Res.drawable.lock_24dp, stringResource(Res.string.forum_topic_closed))
                        }
                        if (topic.isMuted) {
                            SmallIcon(Res.drawable.notifications_off_24dp, stringResource(Res.string.chats_muted))
                        }
                    }
                    listOfNotNull(topic.lastMessage?.timestamp, topic.draft?.timestamp).maxOrNull()?.let { timestamp ->
                        Text(
                            text = timestamp.asRelativeTime(),
                            style = MaterialTheme.typography.labelMedium.tabularNumbers(),
                            color = if (topic.hasUnread) MaterialTheme.colorScheme.primary else secondaryColor,
                            maxLines = 1,
                        )
                    }
                }
                TopicPreview(topic, containerColor)
            }
            TopicBadges(topic)
        }
    }
}

/**
 * Koncept > poslední zpráva. U zprávy kdo psal: téma má víc pisatelů, a bez jména by náhled
 * nepoznal, jestli odpověděl ten, na koho se čeká.
 */
@Composable
private fun TopicPreview(topic: ForumTopic, containerColor: androidx.compose.ui.graphics.Color) {
    val contentColor = MaterialTheme.colorScheme.onSurface
    val draftLabelColor = accentOn(containerColor, MaterialTheme.colorScheme.tertiary, contentColor)
    val you = stringResource(Res.string.sender_you)
    val draftPrefix = stringResource(Res.string.chats_draft_prefix)
    val customEmoji = mutableSetOf<Long>()
    val text: AnnotatedString = topic.draft?.let { draft ->
        buildAnnotatedString {
            withStyle(SpanStyle(color = draftLabelColor)) { append(draftPrefix) }
            customEmoji += appendWithCustomEmoji(draft.text.replace('\n', ' '), draft.entities)
        }
    } ?: topic.lastMessage?.let { message ->
        val messageText = message.text.resolve()
        buildAnnotatedString {
            val sender = when {
                !message.showsSender -> null
                message.isOutgoing -> you
                else -> message.senderName
            }
            sender?.let { withStyle(SpanStyle(color = contentColor)) { append("$it: ") } }
            customEmoji += appendWithCustomEmoji(messageText.replace('\n', ' '), message.entities)
        }
    } ?: return
    Text(
        text = text,
        inlineContent = customEmojiInlineContent(customEmoji),
        style = MaterialTheme.typography.bodyMedium,
        color = if (topic.hasUnread) contentColor else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Odznaky jako v seznamu chatů: reakce, zmínka, počet — počet u kraje. */
@Composable
private fun TopicBadges(topic: ForumTopic) {
    if (topic.unreadReactionCount == 0 && topic.unreadMentionCount == 0 && topic.unreadCount == 0) return
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (topic.unreadReactionCount > 0) {
            IconBadge(Res.drawable.favorite_filled_24dp, stringResource(Res.string.chats_unread_reaction))
        }
        if (topic.unreadMentionCount > 0) {
            IconBadge(Res.drawable.alternate_email_24dp, stringResource(Res.string.chats_unread_mention))
        }
        if (topic.unreadCount > 0) {
            // Ztlumené téma má odznak neutrální — sytý by volal kvůli něčemu, co uživatel ztišil.
            Badge(
                containerColor = if (topic.isMuted) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary,
                contentColor = if (topic.isMuted) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onPrimary,
            ) {
                Text(topic.unreadCount.toString(), style = LocalTextStyle.current.tabularNumbers())
            }
        }
    }
}

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

@Composable
private fun SmallIcon(icon: DrawableResource, description: String) {
    Icon(
        painterResource(icon),
        contentDescription = description,
        modifier = Modifier.size(14.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Stejně jako avatar v hlavičce konverzace — hlavička témat je tatáž plocha. */
private val HEADER_AVATAR = 36.dp

/** Ikona tématu; menší než avatar chatu, v řádku nese váhu název. */
private val ICON_SIZE = 44.dp

private const val LOAD_MORE_THRESHOLD = 5

/** Místo pod posledním tématem na rozšířené tlačítko nového tématu (56 dp + odstup). */
private val FAB_CLEARANCE = 88.dp
