package eu.tintera.plume.tv

import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.items as gridItems
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
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
import eu.tintera.plume.resources.tv_post_play_hint
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
import kotlinx.coroutines.flow.first
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

    /** Obrázek náhledu odkazu (ne profilovka cíle odkazu na Telegram) pro kartu příspěvku bez média. */
    val previewPhoto: MessageMedia.Photo? get() = messages.firstNotNullOfOrNull { it.linkPreview }?.takeUnless { it.roundPhoto }?.photo

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
 * Kanál jako feed: příspěvky od nejnovějšího jako mřížka karet ([PostCard]), po řádcích zleva
 * doprava a shora dolů. Podle návodů Google jsou karty na obsah k vizuálnímu procházení — u kanálu,
 * kde se čtou příspěvky, sedí líp než bubliny konverzace. OK otevře detail ([PostDetail]) s celým
 * textem a komentáři.
 *
 * Detail kartu NAHRADÍ, neleží nad ní: fokus by jinak šipkou utekl na karty pod detailem. Pozice
 * feedu (`gridState`) a karta, na kterou se má fokus vrátit, proto žijí tady, nad oběma.
 */
@Composable
fun ChannelFeedScreen(chatId: Long, info: ChatInfo, playerOpen: Boolean, actions: MediaActions, startAt: Long? = null) {
    val state = rememberConversationState(chatId, startAt = startAt)
    val gridState = rememberLazyGridState()
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
    // Karta s videem: OK ho pustí rovnou (a po něm videa starších příspěvků, jako z detailu),
    // podržení OK otevře detail s textem a komentáři.
    val playableVideo: (ChannelPost) -> (() -> Unit)? = { post ->
        videoQueue(chatId, post.messages).singleOrNull()?.takeIf { post.messages.size == 1 }?.let { request ->
            {
                focusTarget = post.id
                val older = posts.dropWhile { it.id != post.id }.drop(1).flatMap { it.messages }
                actions.playVideo(request.copy(queue = videoQueue(chatId, older)))
            }
        }
    }
    var focusedPost by remember { mutableStateOf<Long?>(null) }
    Column(Modifier.fillMaxSize()) {
        ChatHeader(chatId, info) {
            // Že OK video pustí a k textu a komentářům vede podržení, by se jinak nikdo nedozvěděl.
            // V hlavičce, ne na kartě: do třetiny šířky by se nevešla a měnila by výšku řady.
            val focusedVideo = posts.firstOrNull { it.id == focusedPost }?.let(playableVideo) != null
            if (focusedVideo) {
                Text(
                    stringResource(Res.string.tv_post_play_hint),
                    style = MaterialTheme.typography.labelLarge,
                    color = LocalContentColor.current.copy(alpha = 0.7f),
                    textAlign = TextAlign.End,
                    maxLines = 2,
                    modifier = Modifier.padding(start = 24.dp).widthIn(max = HINT_WIDTH),
                )
            }
        }
        when {
            state.failed -> MessageScreen(stringResource(Res.string.tv_conversation_failed), Res.drawable.cloud_off_24dp, error = true)
            messages == null -> LoadingScreen(stringResource(Res.string.connection_connecting))
            posts.isEmpty() -> MessageScreen(stringResource(Res.string.tv_conversation_empty), Res.drawable.chat_bubble_24dp)
            else -> PostList(
                state = state,
                posts = posts,
                gridState = gridState,
                focusTarget = focusTarget,
                playerOpen = playerOpen,
                onOpen = { post ->
                    focusTarget = post.id
                    openPost = post.id
                },
                playableVideo = playableVideo,
                onFocused = { focusedPost = it.id },
            )
        }
    }
}

@Composable
private fun PostList(
    state: ConversationState,
    posts: List<ChannelPost>,
    gridState: LazyGridState,
    focusTarget: Long?,
    playerOpen: Boolean,
    onOpen: (ChannelPost) -> Unit,
    /** Přehrání videa příspěvku rovnou z karty; `null` = příspěvek není jedno video. */
    playableVideo: (ChannelPost) -> (() -> Unit)?,
    onFocused: (ChannelPost) -> Unit,
) {
    val focus = remember { FocusRequester() }
    val targetId = focusTarget?.takeIf { id -> posts.any { it.id == id } } ?: posts.first().id
    val density = LocalDensity.current
    // I po zavření přehrávače: fokus zpátky na kartu, ze které se video pustilo.
    LaunchedEffect(playerOpen) {
        if (playerOpen) return@LaunchedEffect
        val index = posts.indexOfFirst { it.id == targetId }
        if (index >= 0 && gridState.layoutInfo.visibleItemsInfo.none { it.index == index }) gridState.scrollToItem(index)
        runCatching { focus.requestFocus() }
        // Po zavření přehrávače padne fokus napřed na první kartu a mřížka se k ní rozjede;
        // vrácený fokus kartu zaostří, ale buňku už neodroluje (ověřeno na emulátoru: karta
        // s videem ve druhé řadě zůstala napůl pod okrajem). Až se posun usadí, dorovnat.
        if (index < 0) return@LaunchedEffect
        withFrameNanos { }
        snapshotFlow { gridState.isScrollInProgress }.first { !it }
        with(density) { gridState.revealItem(index, above = CARD_GAP.toPx(), below = SAFE_VERTICAL.toPx()) }
    }
    // Starší příspěvky se dočítají, když se fokus blíží konci feedu; novější, když je načtené
    // okno uprostřed historie (skok na zprávu) a fokus se blíží začátku.
    val loaded by rememberUpdatedState(posts.size)
    LaunchedEffect(gridState) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { last -> if (last != null && last >= loaded - LOAD_AHEAD) state.loadOlder() }
    }
    LaunchedEffect(gridState) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.firstOrNull()?.index }
            .collect { first -> if (first != null && first <= LOAD_AHEAD) state.loadNewer() }
    }
    // Mřížka tří karet 16 : 9 přes šířku v bezpečných okrajích (3 × 268 dp + 2 × 20 dp = 844 dp),
    // jako Rozkoukaná videa. Nahoře místo na zvětšení zaostřené karty (1,1 = 7,5 dp na každou stranu).
    LazyVerticalGrid(
        columns = GridCells.Fixed(FEED_COLUMNS),
        state = gridState,
        contentPadding = PaddingValues(start = SAFE_HORIZONTAL, end = SAFE_HORIZONTAL, top = 16.dp, bottom = SAFE_VERTICAL + audioDockInset()),
        horizontalArrangement = Arrangement.spacedBy(CARD_GAP),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        gridItems(posts, key = { it.id }) { post ->
            val play = playableVideo(post)
            PostCard(
                post = post,
                onClick = play ?: { onOpen(post) },
                onLongClick = play?.let { { onOpen(post) } },
                onFocused = { onFocused(post) },
                modifier = if (post.id == targetId) Modifier.focusRequester(focus) else Modifier,
            )
        }
    }
}

/** Posune mřížku, aby byla položka [index] vidět celá i s místem [above] nad a [below] pod ní. */
private suspend fun LazyGridState.revealItem(index: Int, above: Float, below: Float) {
    val item = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return
    val top = item.offset.y - above
    val bottom = item.offset.y + item.size.height + below
    val delta = when {
        top < layoutInfo.viewportStartOffset -> top - layoutInfo.viewportStartOffset
        bottom > layoutInfo.viewportEndOffset -> bottom - layoutInfo.viewportEndOffset
        else -> return
    }
    animateScrollBy(delta)
}

/**
 * Karta příspěvku podle návodu Google pro karty na TV: obraz 16 : 9 (fotka, náhled videa, obrázek
 * náhledu odkazu), pod ním pár slov — první řádek textu — a datum se zhlédnutími a komentáři.
 * Příspěvek bez obrázku má místo něj plošku s tím prvním řádkem. Celý text, reakce a komentáře
 * jsou v detailu ([PostDetail]).
 *
 * Karta nemá pevnou výšku: obraz je dán šířkou a text pod ním roste s velikostí písma systému.
 * Dřív pevných 200 dp s textem v sp při větším písmu obsah ořízlo (návod k přístupnosti TV:
 * sp ne v nepružných komponentách). Zaostřená karta se zvětší o výchozích 10 % a dostane rámeček
 * (`Card` z tv-material); popisek pod ní se nezvětšuje, jako u `StandardCardContainer`.
 */
@Composable
private fun PostCard(
    post: ChannelPost,
    onClick: () -> Unit,
    /** Podržení OK: u karty s videem detail příspěvku (OK video rovnou pustí). */
    onLongClick: (() -> Unit)?,
    onFocused: () -> Unit,
    modifier: Modifier,
) {
    // Karta kreslí holý text: spoiler se zamaskuje, jinak by tu byl čitelný.
    val text = maskSpoilers(post.text, post.entities).ifBlank { post.description?.resolve().orEmpty() }
    val headline = text.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
    val media = post.media.ifEmpty { listOfNotNull(post.previewPhoto) }
    // Zaostřená karta si sama řekne jen o SEBE: popisek pod ní zůstal pod spodním okrajem
    // (ověřeno na emulátoru, druhá řada). Do obrazu se proto posune celá buňka, i s místem
    // na zvětšení nahoře a bezpečným okrajem dole.
    val cell = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    var cellSize by remember { mutableStateOf(IntSize.Zero) }
    val above = with(LocalDensity.current) { CARD_GAP.toPx() }
    val below = with(LocalDensity.current) { SAFE_VERTICAL.toPx() }
    Column(Modifier.fillMaxWidth().bringIntoViewRequester(cell).onSizeChanged { cellSize = it }) {
        Card(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = modifier.fillMaxWidth().aspectRatio(16f / 9f).onFocusChanged {
                if (it.isFocused) {
                    onFocused()
                    scope.launch { cell.bringIntoView(Rect(0f, -above, cellSize.width.toFloat(), cellSize.height + below)) }
                }
            },
            // Zaostřená ploška s textem zesvětlí jako řádky a tlačítka v celé aplikaci; obraz ji
            // zakryje, tam fokus nese zvětšení a rámeček.
            colors = CardDefaults.colors(focusedContainerColor = MaterialTheme.colorScheme.inverseSurface),
        ) {
            if (media.isNotEmpty()) {
                PostMedia(media, Modifier.fillMaxSize())
            } else {
                // Text se zalomí, kolik řádků se do plošky vejde, a poslední dostane výpustku
                // (Compose ji u textu omezeného výškou udělá sám).
                Text(
                    headline,
                    style = MaterialTheme.typography.titleLarge,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                )
            }
        }
        if (media.isNotEmpty() && headline.isNotEmpty()) {
            Text(
                headline,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = BELOW_CARD),
            )
        }
        PostMeta(post, Modifier.padding(top = if (media.isNotEmpty() && headline.isNotEmpty()) 4.dp else BELOW_CARD))
    }
}

/** Datum, zhlédnutí a komentáře pod kartou; reakce až v detailu, do třetiny šířky se nevejdou. */
@Composable
private fun PostMeta(post: ChannelPost, modifier: Modifier) {
    val stats = post.statsMessage.postStats
    val muted = LocalContentColor.current.copy(alpha = 0.7f)
    val style = MaterialTheme.typography.labelMedium.tabular()
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val time = post.timestamp.let { if (it.isSameDayAs(nowSeconds())) it.asTimeOfDay() else it.asDate() }
        Text(time, style = style, color = muted, maxLines = 1)
        if (stats != null && stats.views > 0) {
            val views = compactCountText(stats.views)
            FooterStat(Res.drawable.visibility_24dp, views, stringResource(Res.string.post_views, views), muted, style, META_ICON)
        }
        stats?.comments?.let { count ->
            val text = compactCountText(count)
            FooterStat(Res.drawable.mode_comment_24dp, text, pluralStringResource(Res.plurals.post_comments_count, count, text), muted, style, META_ICON)
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
    val targetPx = with(LocalDensity.current) { CARD_WIDTH.roundToPx() }
    val image = rememberImage(fileId, targetPx)
    Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        image?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        if (first is MessageMedia.Video) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                Icon(painterResource(Res.drawable.play_arrow_24dp), contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
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
private fun FooterStat(
    icon: org.jetbrains.compose.resources.DrawableResource,
    text: String,
    description: String,
    color: Color,
    style: TextStyle = MaterialTheme.typography.labelLarge.tabular(),
    iconSize: Dp = 18.dp,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(icon), contentDescription = description, tint = color, modifier = Modifier.size(iconSize))
        Spacer(Modifier.width(iconSize / 3))
        Text(text, style = style, color = color, maxLines = 1)
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
    // Médium se pustí, příspěvek bez média s odkazem na Telegram (jiný příspěvek, kanál) ho otevře.
    val articlePreview = post.messages.firstNotNullOfOrNull { it.linkPreview?.takeIf { preview -> preview.instantView } }
    val open = mediaOpenAction(chatId, post.messages, post.messages, actions, videosAfter = olderVideos)
        ?: articlePreview?.let { preview -> { actions.openArticle(preview.url) } }
        ?: telegramLinkOf(post.entities)?.let { url -> { actions.openLink(url, post.id) } }
    // Spoiler v textu příspěvku: první OK ho odkryje, teprve další pustí médium.
    var spoilersRevealed by remember(post.id) { mutableStateOf(false) }
    val hiddenSpoiler = !spoilersRevealed && post.entities.hasSpoiler()
    Column(
        modifier
            .focusRing(focused, focusRingShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
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
        post.messages.firstNotNullOfOrNull { it.linkPreview }?.let { preview ->
            Spacer(Modifier.height(12.dp))
            LinkPreviewCard(preview, content, MaterialTheme.colorScheme.primary)
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
            // Okraj nahoře a dole pro fokusový prstenec, který sahá za hranice zprávy.
            val tall = rememberTallItemScroll(listState, reverse = true, paddingTop = FOCUS_RING_OUTSET, paddingBottom = FOCUS_RING_OUTSET)
            LazyColumn(
                state = listState,
                reverseLayout = true,
                contentPadding = PaddingValues(vertical = FOCUS_RING_OUTSET),
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

/** Karty vedle sebe a jejich šířka (mřížka 12 sloupců, docs/poznatky/android-tv.md). */
private const val FEED_COLUMNS = 3
private val CARD_WIDTH = 268.dp
private val CARD_GAP = 20.dp
private val META_ICON = 14.dp

/** Popisek pod kartou: zvětšená zaostřená karta (1,1) přesahuje o 7,5 dp, text pod ní nesmí krýt. */
private val BELOW_CARD = 14.dp
private val HINT_WIDTH = 360.dp
private val DETAIL_MEDIA_HEIGHT = 320.dp
private val SCROLL_STEP = 160.dp
private const val MAX_REACTIONS = 3
private val STRIP_HEIGHT = 96.dp
private const val STRIP_TILES = 5

/** Kolik příspěvků (dvě řady karet) před koncem načtené části se dočítají starší. */
private const val LOAD_AHEAD = 6
