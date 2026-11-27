package eu.tintera.plume.features.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.accounts.AccountTopAppBar
import eu.tintera.plume.core.adaptive.ContentWidth
import eu.tintera.plume.core.telegram.GalleryItem
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.ui.ExpressiveMenu
import eu.tintera.plume.core.ui.GroupTitle
import eu.tintera.plume.core.ui.IntroEmptyState
import eu.tintera.plume.core.ui.MenuAction
import eu.tintera.plume.core.ui.RemoteImage
import eu.tintera.plume.core.ui.contextClickable
import eu.tintera.plume.core.ui.resolve
import eu.tintera.plume.core.ui.standaloneName
import eu.tintera.plume.core.ui.tabularNumbers
import eu.tintera.plume.features.conversation.formatDuration
import eu.tintera.plume.features.conversation.items.rememberSpoilerBlurStyle
import eu.tintera.plume.features.conversation.items.spoilerBlur
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.arrow_back_24dp
import eu.tintera.plume.resources.calendar_month_year
import eu.tintera.plume.resources.chat_24dp
import eu.tintera.plume.resources.common_back
import eu.tintera.plume.resources.common_try_again
import eu.tintera.plume.resources.empty_state_error_title
import eu.tintera.plume.resources.gallery_empty
import eu.tintera.plume.resources.gallery_empty_title
import eu.tintera.plume.resources.gallery_more_failed
import eu.tintera.plume.resources.gallery_title
import eu.tintera.plume.resources.image_24dp
import eu.tintera.plume.resources.media_sensitive
import eu.tintera.plume.resources.media_show_in_chat
import eu.tintera.plume.resources.media_spoiler
import eu.tintera.plume.resources.perm_media_24dp
import eu.tintera.plume.resources.play_arrow_24dp
import eu.tintera.plume.resources.profile_shared_media_video
import eu.tintera.plume.resources.profile_shared_photo
import eu.tintera.plume.resources.visibility_off_24dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.yearMonth
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

/**
 * **Fotky a videa ze všech chatů** v jedné mřížce, od nejnovějších, rozdělené po měsících
 * (jako galerie v telefonu). Ťuknutí otevře prohlížeč v chatu, odkud fotka je (listuje se pak
 * médii toho chatu); podržení nabídne „Zobrazit v chatu". Spoiler a citlivý obsah zůstávají
 * rozostřené, první ťuknutí je odhalí — jako ve sdílených médiích profilu.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun GalleryScreen(
    viewModel: GalleryViewModel,
    onBack: () -> Unit,
    onOpen: (chatId: Long, messageId: Long) -> Unit,
    onShowInChat: (chatId: Long, messageId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        modifier = modifier,
        topBar = {
            AccountTopAppBar(
                title = { Text(stringResource(Res.string.gallery_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(painterResource(Res.drawable.arrow_back_24dp), contentDescription = stringResource(Res.string.common_back))
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            val content = Modifier.widthIn(max = ContentWidth.Reading)
            val failure = state.failure
            when {
                failure != null -> IntroEmptyState(
                    icon = Res.drawable.perm_media_24dp,
                    title = stringResource(Res.string.empty_state_error_title),
                    text = failure.resolve(),
                    modifier = content.padding(top = 48.dp),
                    action = {
                        FilledTonalButton(onClick = viewModel::retry, shapes = ButtonDefaults.shapes()) {
                            Text(stringResource(Res.string.common_try_again))
                        }
                    },
                )
                state.items.isEmpty() && state.loading -> Box(content.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                    LoadingIndicator()
                }
                state.items.isEmpty() -> IntroEmptyState(
                    icon = Res.drawable.perm_media_24dp,
                    title = stringResource(Res.string.gallery_empty_title),
                    text = stringResource(Res.string.gallery_empty),
                    modifier = content.padding(top = 48.dp),
                )
                else -> GalleryGrid(state, viewModel::loadMore, onOpen, onShowInChat, content.fillMaxSize())
            }
        }
    }
}

@Composable
private fun GalleryGrid(
    state: GalleryLoad,
    onLoadMore: () -> Unit,
    onOpen: (chatId: Long, messageId: Long) -> Unit,
    onShowInChat: (chatId: Long, messageId: Long) -> Unit,
    modifier: Modifier,
) {
    val gridState = rememberLazyGridState()
    val zone = remember { TimeZone.currentSystemDefault() }
    val months = remember(state.items) { groupByMonth(state.items, zone) }
    // Dotáhnout další stránku, když mřížka dojíždí ke konci. Po chybě ne: mřížka u konce už stojí
    // a bez sítě by se zkoušelo dokola — znovu jen tlačítkem v patičce.
    val loadMore by rememberUpdatedState(onLoadMore)
    val canLoad = !state.reachedEnd && !state.loading && !state.moreFailed
    LaunchedEffect(gridState, canLoad) {
        if (!canLoad) return@LaunchedEffect
        snapshotFlow { gridState.layoutInfo.let { (it.visibleItemsInfo.lastOrNull()?.index ?: 0) >= it.totalItemsCount - LOAD_AHEAD } }
            .distinctUntilChanged()
            .collect { nearEnd -> if (nearEnd) loadMore() }
    }
    var revealed by rememberSaveable { mutableStateOf(emptySet<String>()) }
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(TILE_MIN),
        contentPadding = PaddingValues(start = 2.dp, end = 2.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier,
    ) {
        months.forEach { (month, items) ->
            monthHeader(month)
            items(items, key = { it.key }) { item ->
                GalleryTile(
                    item = item,
                    hidden = item.hasSpoiler && item.key !in revealed,
                    onReveal = { revealed = revealed + item.key },
                    onOpen = { onOpen(item.chatId, item.message.id) },
                    onShowInChat = { onShowInChat(item.chatId, item.message.id) },
                )
            }
        }
        if (state.loading || state.moreFailed) {
            item(key = "footer", span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    if (state.loading) {
                        LoadingIndicator()
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                stringResource(Res.string.gallery_more_failed),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TextButton(onClick = onLoadMore, shapes = ButtonDefaults.shapes()) {
                                Text(stringResource(Res.string.common_try_again))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Nadpis měsíce přes celou šířku mřížky: „listopad 2026". */
private fun LazyGridScope.monthHeader(month: YearMonth) {
    item(key = "month-$month", span = { GridItemSpan(maxLineSpan) }) {
        GroupTitle(
            stringResource(Res.string.calendar_month_year, stringResource(month.month.standaloneName()), month.year),
            Modifier.padding(start = 14.dp, end = 14.dp, top = 16.dp, bottom = 8.dp),
        )
    }
}

@Composable
private fun GalleryTile(
    item: GalleryItem,
    hidden: Boolean,
    onReveal: () -> Unit,
    onOpen: () -> Unit,
    onShowInChat: () -> Unit,
) {
    val photo = item.message.media as? MessageMedia.Photo
    val video = item.message.media as? MessageMedia.Video
    val fileId = photo?.fileId ?: video?.thumbnailFileId
    val spoilerStyle = rememberSpoilerBlurStyle()
    var menu by remember { mutableStateOf(false) }
    Box(
        Modifier
            .aspectRatio(1f)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .contextClickable(onClick = { if (hidden) onReveal() else onOpen() }, onLongClick = { menu = true }),
    ) {
        if (fileId != null) {
            RemoteImage(
                fileId = fileId,
                thumbnailFileId = photo?.thumbnailFileId ?: video?.thumbnailFileId,
                placeholder = photo?.minithumbnail ?: video?.minithumbnail,
                contentDescription = stringResource(if (video != null) Res.string.profile_shared_media_video else Res.string.profile_shared_photo),
                modifier = Modifier.fillMaxSize().spoilerBlur(hidden, revealed = false, spoilerStyle),
            )
        } else {
            Icon(painterResource(Res.drawable.image_24dp), contentDescription = null, modifier = Modifier.align(Alignment.Center))
        }
        if (hidden) {
            Icon(
                painterResource(Res.drawable.visibility_off_24dp),
                contentDescription = stringResource(
                    if (photo?.isSensitive == true || video?.isSensitive == true) Res.string.media_sensitive else Res.string.media_spoiler,
                ),
                tint = Color.White,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        if (video != null) VideoBadge(video.durationSeconds, Modifier.align(Alignment.BottomStart))
        ExpressiveMenu(
            expanded = menu,
            onDismiss = { menu = false },
            groups = listOf(
                listOf(
                    MenuAction(stringResource(Res.string.media_show_in_chat), Res.drawable.chat_24dp) {
                        menu = false
                        onShowInChat()
                    },
                ),
            ),
        )
    }
}

/**
 * Značka videa s délkou („1:24") vlevo dole. Bílá na tmavém podkladu: leží na libovolném
 * obrázku a barva z motivu by na něm nebyla čitelná (jako ve sdílených médiích).
 */
@Composable
private fun VideoBadge(durationSeconds: Int, modifier: Modifier) {
    Surface(
        shape = RoundedCornerShape(percent = 50),
        color = Color.Black.copy(alpha = VIDEO_BADGE_ALPHA),
        contentColor = Color.White,
        modifier = modifier.padding(6.dp),
    ) {
        Row(Modifier.padding(start = 2.dp, end = 6.dp, top = 1.dp, bottom = 1.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(Res.drawable.play_arrow_24dp), contentDescription = null, modifier = Modifier.size(16.dp))
            if (durationSeconds > 0) {
                Text(formatDuration(durationSeconds), style = MaterialTheme.typography.labelSmall.tabularNumbers())
            }
        }
    }
}

/** Položky po měsících, od nejnovějšího; pořadí uvnitř měsíce zůstává (od nejnovější). */
internal fun groupByMonth(items: List<GalleryItem>, zone: TimeZone): List<Pair<YearMonth, List<GalleryItem>>> {
    val months = LinkedHashMap<YearMonth, MutableList<GalleryItem>>()
    for (item in items) {
        val month = Instant.fromEpochSeconds(item.message.timestamp.toLong()).toLocalDateTime(zone).date.yearMonth
        months.getOrPut(month) { mutableListOf() } += item
    }
    return months.map { (month, list) -> month to list }
}

/** Klíč dlaždice: id zpráv se mezi chaty opakují, chat do něj patří. */
private val GalleryItem.key: String get() = "$chatId:${message.id}"

private val GalleryItem.hasSpoiler: Boolean
    get() = when (val media = message.media) {
        is MessageMedia.Photo -> media.hasSpoiler
        is MessageMedia.Video -> media.hasSpoiler
        else -> false
    }

/** Nejmenší šířka dlaždice — na telefonu tři sloupce, v širokém panelu víc. */
private val TILE_MIN = 110.dp

/** Kolik dlaždic před koncem se dotáhne další stránka. */
private const val LOAD_AHEAD = 18

private const val VIDEO_BADGE_ALPHA = 0.55f
