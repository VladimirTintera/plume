package eu.tintera.plume.tv

import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import eu.tintera.plume.core.telegram.ChatInfo
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.CommentThread
import eu.tintera.plume.core.telegram.ConversationRepository
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.telegram.customEmojiIdOf
import eu.tintera.plume.core.ui.asDate
import eu.tintera.plume.core.ui.asTimeOfDay
import eu.tintera.plume.core.ui.compactCountText
import eu.tintera.plume.core.ui.isSameDayAs
import eu.tintera.plume.core.ui.resolve
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.chat_bubble_24dp
import eu.tintera.plume.resources.cloud_off_24dp
import eu.tintera.plume.resources.comments_unavailable
import eu.tintera.plume.resources.connection_connecting
import eu.tintera.plume.resources.mode_comment_24dp
import eu.tintera.plume.resources.play_arrow_24dp
import eu.tintera.plume.resources.post_comments_count
import eu.tintera.plume.resources.post_views
import eu.tintera.plume.resources.tv_comments_empty
import eu.tintera.plume.resources.tv_comments_off
import eu.tintera.plume.resources.tv_comments_title
import eu.tintera.plume.resources.tv_compose_comment
import eu.tintera.plume.resources.tv_conversation_empty
import eu.tintera.plume.resources.tv_conversation_failed
import eu.tintera.plume.resources.visibility_24dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Příspěvek kanálu: jedna zpráva, nebo album (víc zpráv se stejným `mediaAlbumId`). Popisek
 * alba nese jen jedna z jeho zpráv, a počty (zhlédnutí, komentáře) jen ta, ke které server
 * diskusi přivázal — proto se hledají napříč albem.
 */
class ChannelPost(val messages: List<ChatMessage>) {
    val id: Long get() = messages.first().id
    val timestamp: Int get() = messages.first().timestamp
    val text: String get() = messages.firstOrNull { it.text.isNotBlank() }?.text.orEmpty()

    /** Formátování [text] (entity té zprávy alba, která text nese). */
    val entities get() = messages.firstOrNull { it.text.isNotBlank() }?.entities.orEmpty()
    val media: List<MessageMedia> get() = messages.mapNotNull { message -> message.media.takeIf { it is MessageMedia.Photo || it is MessageMedia.Video } }
    val statsMessage: ChatMessage get() = messages.firstOrNull { it.postStats?.comments != null } ?: messages.last()

    /** Popis obsahu pro příspěvek bez textu (anketa, soubor…), jak ho kreslí konverzace. */
    val description get() = messages.firstNotNullOfOrNull { it.description }
}

/** Zprávy (od nejstarší) složené do příspěvků od NEJNOVĚJŠÍHO; servisní zprávy feed vynechá. */
fun channelPosts(messages: List<ChatMessage>): List<ChannelPost> {
    val posts = mutableListOf<MutableList<ChatMessage>>()
    for (message in messages.asReversed()) {
        if (message.isService) continue
        val last = posts.lastOrNull()
        if (last != null && message.mediaAlbumId != 0L && last.first().mediaAlbumId == message.mediaAlbumId) {
            last.add(0, message)
        } else {
            posts.add(mutableListOf(message))
        }
    }
    return posts.map { ChannelPost(it) }
}

/**
 * Kanál jako feed: příspěvky od nejnovějšího shora jako karty (médium vlevo, nadpis a text
 * vpravo, dole zhlédnutí, komentáře a reakce). Podle návodů Google jsou karty na obsah k vizuálnímu
 * procházení — u kanálu, kde se čtou příspěvky, sedí líp než bubliny konverzace. OK otevře
 * detail ([PostDetail]) s celým textem a komentáři.
 *
 * Detail kartu NAHRADÍ, neleží nad ní: fokus by jinak šipkou utekl na karty pod detailem. Pozice
 * feedu ([listState]) a karta, na kterou se má fokus vrátit, proto žijí tady, nad oběma.
 */
@Composable
fun ChannelFeedScreen(chatId: Long, info: ChatInfo, playerOpen: Boolean, actions: MediaActions, startAt: Long? = null) {
    val state = rememberConversationState(chatId, startAt = startAt)
    val listState = rememberLazyListState()
    var openPost by remember { mutableStateOf<Long?>(null) }
    var focusTarget by remember { mutableStateOf<Long?>(null) }
    val messages = state.messages
    val posts = remember(messages) { channelPosts(messages.orEmpty()) }
    // Skok na zprávu kanálu (hledání, Rozkoukaná videa): rovnou detail jejího příspěvku; po
    // návratu z něj je fokus ve feedu na tomtéž příspěvku.
    val jump = state.jump
    LaunchedEffect(jump, posts) {
        val target = jump ?: return@LaunchedEffect
        val post = posts.firstOrNull { post -> post.messages.any { it.id == target.messageId } } ?: return@LaunchedEffect
        focusTarget = post.id
        openPost = post.id
        state.consumeJump()
    }

    val opened = openPost?.let { id -> posts.firstOrNull { it.id == id } }
    if (opened != null) {
        BackHandler { openPost = null }
        // Kanál jako pořad: po videu příspěvku hrají videa STARŠÍCH příspěvků, jak jde feed dolů.
        val olderVideos = remember(posts, opened.id) {
            videoQueue(chatId, posts.dropWhile { it.id != opened.id }.drop(1).flatMap { it.messages })
        }
        PostDetail(chatId, info, opened, playerOpen, actions, olderVideos)
        return
    }
    Column(Modifier.fillMaxSize()) {
        ChatHeader(chatId, info)
        when {
            state.failed -> MessageScreen(stringResource(Res.string.tv_conversation_failed), Res.drawable.cloud_off_24dp, error = true)
            messages == null -> LoadingScreen(stringResource(Res.string.connection_connecting))
            posts.isEmpty() -> MessageScreen(stringResource(Res.string.tv_conversation_empty), Res.drawable.chat_bubble_24dp)
            else -> PostList(
                state = state,
                posts = posts,
                listState = listState,
                focusTarget = focusTarget,
                onOpen = { post ->
                    focusTarget = post.id
                    openPost = post.id
                },
            )
        }
    }
}

@Composable
private fun PostList(
    state: ConversationState,
    posts: List<ChannelPost>,
    listState: LazyListState,
    focusTarget: Long?,
    onOpen: (ChannelPost) -> Unit,
) {
    val focus = remember { FocusRequester() }
    val targetId = focusTarget?.takeIf { id -> posts.any { it.id == id } } ?: posts.first().id
    LaunchedEffect(Unit) {
        val index = posts.indexOfFirst { it.id == targetId }
        if (index >= 0 && listState.layoutInfo.visibleItemsInfo.none { it.index == index }) listState.scrollToItem(index)
        runCatching { focus.requestFocus() }
    }
    // Starší příspěvky se dočítají, když se fokus blíží konci feedu; novější, když je načtené
    // okno uprostřed historie (skok na zprávu) a fokus se blíží začátku.
    val loaded by rememberUpdatedState(posts.size)
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { last -> if (last != null && last >= loaded - LOAD_AHEAD) state.loadOlder() }
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.firstOrNull()?.index }
            .collect { first -> if (first != null && first <= LOAD_AHEAD) state.loadNewer() }
    }
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = SAFE_HORIZONTAL, end = SAFE_HORIZONTAL, top = 8.dp, bottom = SAFE_VERTICAL + audioDockInset()),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(posts, key = { it.id }) { post ->
            PostCard(
                post = post,
                onClick = { onOpen(post) },
                modifier = if (post.id == targetId) Modifier.focusRequester(focus) else Modifier,
            )
        }
    }
}

/**
 * Karta příspěvku. Zaostřená se zvětší a zesvětlí jako každá plocha tv-material; zvětšení je
 * menší než výchozí 1,1 — karta jde přes celou šířku a víc by přetekla přes bezpečný okraj.
 */
@Composable
private fun PostCard(post: ChannelPost, onClick: () -> Unit, modifier: Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(CARD_HEIGHT),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(20.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedContainerColor = MaterialTheme.colorScheme.inverseSurface,
        ),
    ) {
        Row(Modifier.fillMaxSize()) {
            val media = post.media
            if (media.isNotEmpty()) {
                PostMedia(media, Modifier.fillMaxHeight().aspectRatio(16f / 9f))
            }
            Column(Modifier.weight(1f).fillMaxHeight().padding(20.dp)) {
                // Karta kreslí holý text (nadpis a výňatek): spoiler se zamaskuje, jinak by tu byl čitelný.
                val text = maskSpoilers(post.text, post.entities).ifBlank { post.description?.resolve().orEmpty() }
                val title = text.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
                // Prázdné řádky mezi odstavci by na kartě snědly místo pro text.
                val body = text.substringAfter(title, "").trim().replace(BLANK_LINES, "\n")
                if (title.isNotEmpty()) {
                    Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (body.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = LocalContentColor.current.copy(alpha = 0.75f),
                        maxLines = if (title.isEmpty()) 5 else 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.weight(1f))
                PostFooter(post)
            }
        }
    }
}

/** Médium příspěvku: první fotka nebo náhled videa, u videa tlačítko, u alba počet dalších. */
@Composable
private fun PostMedia(media: List<MessageMedia>, modifier: Modifier) {
    val first = media.first()
    val fileId = when (first) {
        is MessageMedia.Photo -> first.fileId
        is MessageMedia.Video -> first.thumbnailFileId
        else -> null
    }
    val targetPx = with(LocalDensity.current) { CARD_HEIGHT.roundToPx() }
    val image = rememberImage(fileId, targetPx)
    Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        image?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        if (first is MessageMedia.Video) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                Icon(painterResource(Res.drawable.play_arrow_24dp), contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
            }
            val watched = rememberWatched(first)
            MediaBadge(watched?.progressLabel() ?: first.durationSeconds.asDuration(), Modifier.align(Alignment.BottomEnd))
            watched?.let { WatchedBar(it, Modifier.align(Alignment.BottomCenter)) }
        }
        if (media.size > 1) MediaBadge("+${media.size - 1}", Modifier.align(Alignment.TopEnd))
    }
}

@Composable
private fun MediaBadge(text: String, modifier: Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium.tabular(),
        color = Color.White,
        modifier = modifier
            .padding(8.dp)
            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/** Datum, zhlédnutí, komentáře a reakce (jen obyčejná emoji — vlastní televize neumí kreslit). */
@Composable
private fun PostFooter(post: ChannelPost) {
    val stats = post.statsMessage.postStats
    val muted = LocalContentColor.current.copy(alpha = 0.7f)
    val style = MaterialTheme.typography.labelLarge.tabular()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        val time = post.timestamp.let { if (it.isSameDayAs(nowSeconds())) it.asTimeOfDay() else it.asDate() }
        Text(time, style = style, color = muted)
        if (stats != null && stats.views > 0) {
            val views = compactCountText(stats.views)
            FooterStat(Res.drawable.visibility_24dp, views, stringResource(Res.string.post_views, views), muted)
        }
        stats?.comments?.let { count ->
            val text = compactCountText(count)
            FooterStat(Res.drawable.mode_comment_24dp, text, pluralStringResource(Res.plurals.post_comments_count, count, text), muted)
        }
        post.messages.flatMap { it.reactions }
            .filter { customEmojiIdOf(it.emoji) == null }
            .sortedByDescending { it.count }
            .take(MAX_REACTIONS)
            .forEach { reaction -> Text("${reaction.emoji} ${compactCountText(reaction.count)}", style = style, color = muted) }
    }
}

@Composable
private fun FooterStat(icon: org.jetbrains.compose.resources.DrawableResource, text: String, description: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(icon), contentDescription = description, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelLarge.tabular(), color = color)
    }
}

/**
 * Detail příspěvku: vlevo médium a celý text, vpravo komentáře (vlákno v diskusní skupině,
 * `ThreadConversationRepository` z `core`). Text se roluje šipkami nahoru a dolů, dokud má kam
 * — teprve na konci by fokus odešel jinam; doprava se jde do komentářů. U videa OK přehraje.
 */
@Composable
private fun PostDetail(
    chatId: Long,
    info: ChatInfo,
    post: ChannelPost,
    playerOpen: Boolean,
    actions: MediaActions,
    olderVideos: List<VideoRequest>,
) {
    Column(Modifier.fillMaxSize()) {
        ChatHeader(chatId, info)
        Row(
            Modifier.fillMaxSize().padding(start = SAFE_HORIZONTAL, end = SAFE_HORIZONTAL, bottom = SAFE_VERTICAL),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            PostBody(chatId, post, playerOpen, actions, olderVideos, Modifier.weight(0.56f).fillMaxHeight())
            PostComments(chatId, info.title, post, actions, Modifier.weight(0.44f).fillMaxHeight())
        }
    }
}

@Composable
private fun PostBody(
    chatId: Long,
    post: ChannelPost,
    playerOpen: Boolean,
    actions: MediaActions,
    olderVideos: List<VideoRequest>,
    modifier: Modifier,
) {
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val stepPx = with(LocalDensity.current) { SCROLL_STEP.toPx() }
    val focus = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(playerOpen) { if (!playerOpen) runCatching { focus.requestFocus() } }
    // Samotné video se pustí, fotka nebo album otevře prohlížeč na první položce.
    val open = mediaOpenAction(chatId, post.messages, post.messages, actions, videosAfter = olderVideos)
    // Spoiler v textu příspěvku: první OK ho odkryje, teprve další pustí médium.
    var spoilersRevealed by remember(post.id) { mutableStateOf(false) }
    val hiddenSpoiler = !spoilersRevealed && post.entities.hasSpoiler()
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .border(if (focused) 3.dp else 0.dp, if (focused) MaterialTheme.colorScheme.border else Color.Transparent, RoundedCornerShape(20.dp))
            .focusRequester(focus)
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.nativeKeyEvent.keyCode) {
                    // Rolovat, dokud je kam; na kraji šipku pustit dál (fokus nikam jinam nahoře nejde).
                    AndroidKeyEvent.KEYCODE_DPAD_DOWN -> if (scroll.canScrollForward) {
                        scope.launch { scroll.animateScrollBy(stepPx) }
                        true
                    } else {
                        false
                    }
                    AndroidKeyEvent.KEYCODE_DPAD_UP -> if (scroll.canScrollBackward) {
                        scope.launch { scroll.animateScrollBy(-stepPx) }
                        true
                    } else {
                        false
                    }
                    AndroidKeyEvent.KEYCODE_DPAD_CENTER, AndroidKeyEvent.KEYCODE_ENTER -> when {
                        hiddenSpoiler -> {
                            spoilersRevealed = true
                            true
                        }
                        else -> {
                            open?.invoke()
                            open != null
                        }
                    }
                    else -> false
                }
            }
            .focusable()
            .verticalScroll(scroll)
            .padding(20.dp),
    ) {
        post.media.firstOrNull()?.let { media ->
            when (media) {
                is MessageMedia.Video -> MessageVideo(media)
                is MessageMedia.Photo -> {
                    val targetPx = with(LocalDensity.current) { DETAIL_MEDIA_HEIGHT.roundToPx() }
                    rememberImage(media.fileId, targetPx)?.let {
                        Image(
                            it,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth().height(DETAIL_MEDIA_HEIGHT).clip(RoundedCornerShape(16.dp)),
                        )
                    }
                }
                else -> {}
            }
            if (post.media.size > 1) {
                Spacer(Modifier.height(12.dp))
                AlbumStrip(post.media)
            }
            Spacer(Modifier.height(20.dp))
        }
        val content = LocalContentColor.current
        if (post.text.isNotBlank()) {
            Text(rememberRichText(post.text, post.entities, content, MaterialTheme.colorScheme.primary, spoilersRevealed), style = MaterialTheme.typography.bodyLarge)
        } else {
            Text(post.description?.resolve().orEmpty(), style = MaterialTheme.typography.bodyLarge)
        }
        Spacer(Modifier.height(16.dp))
        PostFooter(post)
    }
}

/** Zbytek alba pod hlavním médiem: řada malých náhledů; celé album se prochází v prohlížeči (OK). */
@Composable
private fun AlbumStrip(media: List<MessageMedia>) {
    val targetPx = with(LocalDensity.current) { STRIP_HEIGHT.roundToPx() }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        media.drop(1).take(STRIP_TILES).forEachIndexed { index, item ->
            val fileId = when (item) {
                is MessageMedia.Photo -> item.fileId
                is MessageMedia.Video -> item.thumbnailFileId
                else -> null
            }
            Box(
                Modifier.height(STRIP_HEIGHT).aspectRatio(1f).clip(RoundedCornerShape(10.dp)).background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                rememberImage(fileId, targetPx)?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                if (item is MessageMedia.Video) {
                    Icon(painterResource(Res.drawable.play_arrow_24dp), contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                }
                val more = media.size - 1 - STRIP_TILES
                if (index == STRIP_TILES - 1 && more > 0) {
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                        Text("+$more", style = MaterialTheme.typography.titleLarge, color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun PostComments(
    chatId: Long,
    channelTitle: String,
    post: ChannelPost,
    actions: MediaActions,
    modifier: Modifier,
) {
    val conversations = koinInject<ConversationRepository>()
    val stats = post.statsMessage.postStats
    Column(modifier) {
        Text(
            stringResource(Res.string.tv_comments_title) + (stats?.comments?.let { " · ${compactCountText(it)}" }.orEmpty()),
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(12.dp))
        if (stats?.comments == null) {
            MessageScreen(stringResource(Res.string.tv_comments_off), Res.drawable.mode_comment_24dp)
            return@Column
        }
        // Vlákno komentářů leží v diskusní skupině; kam, řekne až `getMessageThread`.
        val thread by produceState<Result<CommentThread>?>(null, chatId, post.statsMessage.id) {
            value = conversations.commentThread(chatId, post.statsMessage.id)
        }
        val current = thread
        when {
            current == null -> LoadingScreen(stringResource(Res.string.connection_connecting))
            current.isFailure -> MessageScreen(stringResource(Res.string.comments_unavailable), Res.drawable.cloud_off_24dp, error = true)
            else -> CommentList(current.getOrThrow(), channelTitle, actions)
        }
    }
}

/**
 * Komentáře vlákna. [channelTitle] je jméno pro komentáře bez odesílatele — napsané za kanál
 * nebo anonymním správcem; citace by jinak měla prázdný řádek se jménem.
 */
@Composable
private fun CommentList(thread: CommentThread, channelTitle: String, actions: MediaActions) {
    val state = rememberConversationState(thread.chatId, threadRootId = thread.rootMessageId)
    // Kořen vlákna (kopie příspěvku v diskusní skupině) je vlevo, mezi komentáři by byl podruhé.
    val comments = state.messages?.filter { it.id != thread.rootMessageId && !it.isService }
    val listState = rememberLazyListState()
    val interactions = remember { MessageInteractions() }
    MessageInteractionDialogs(state, interactions, chatTitle = channelTitle)
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) { CommentListContent(thread, channelTitle, state, comments, listState, interactions, actions) }
        if (state.canWrite && comments != null) {
            ComposeButton(
                label = stringResource(Res.string.tv_compose_comment),
                onClick = { interactions.composing = true },
                modifier = Modifier.padding(top = 12.dp, bottom = audioDockInset()),
            )
        }
    }
}

@Composable
private fun CommentListContent(
    thread: CommentThread,
    channelTitle: String,
    state: ConversationState,
    comments: List<ChatMessage>?,
    listState: LazyListState,
    interactions: MessageInteractions,
    actions: MediaActions,
) {
    when {
        state.failed -> MessageScreen(stringResource(Res.string.comments_unavailable), Res.drawable.cloud_off_24dp, error = true)
        comments == null -> LoadingScreen(stringResource(Res.string.connection_connecting))
        comments.isEmpty() -> MessageScreen(stringResource(Res.string.tv_comments_empty), Res.drawable.mode_comment_24dp)
        else -> {
            // Alba v komentářích jako jedna bublina, jako v konverzaci.
            val newestFirst = remember(comments) { albums(comments).asReversed() }
            LaunchedEffect(listState) {
                snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
                    .collect { last -> if (last != null && last >= newestFirst.size - LOAD_AHEAD) state.loadOlder() }
            }
            // Jako konverzace: nejnovější dole, šipkou nahoru ke starším. Šipka zleva z textu
            // příspěvku by podle polohy padla na kterýkoli komentář; `focusRestorer` pošle fokus
            // na naposledy zaostřený, napoprvé na nejnovější.
            val newest = remember { FocusRequester() }
            // Komentář, který se do sloupce nevejde, se šipkou napřed odroluje (TallItemScroll).
            val tall = rememberTallItemScroll(listState, reverse = true, paddingTop = 0.dp, paddingBottom = 0.dp)
            LazyColumn(
                state = listState,
                reverseLayout = true,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize().focusRestorer(newest).then(tall.listModifier),
            ) {
                items(newestFirst, key = { it.first().id }) { album ->
                    val pick = if (album.size > 1) remember(album.first().id) { AlbumPick() } else null
                    MessageRow(
                        album = album,
                        chatTitle = channelTitle,
                        albumPick = pick,
                        onOpen = mediaOpenAction(thread.chatId, album, comments, actions, pick = pick),
                        onLongPress = { interactions.actionsFor = album.first() },
                        // Odpověď kořeni (příspěvku) se necituje — komentáře mu odpovídají všechny.
                        quote = album.first().replyToMessageId?.takeIf { it != thread.rootMessageId }?.let(state::quote),
                        modifier = tall.item(album.first().id)
                            .then(if (album.first().id == newestFirst.first().first().id) Modifier.focusRequester(newest) else Modifier),
                    )
                }
            }
        }
    }
}

private val BLANK_LINES = Regex("\\n\\s*\\n+")
private val CARD_HEIGHT = 200.dp
private val DETAIL_MEDIA_HEIGHT = 320.dp
private val SCROLL_STEP = 160.dp
private const val MAX_REACTIONS = 3
private val STRIP_HEIGHT = 96.dp
private const val STRIP_TILES = 5

/** Kolik příspěvků před koncem načtené části se dočítají starší. */
private const val LOAD_AHEAD = 4
