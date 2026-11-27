package eu.tintera.plume.tv

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.core.telegram.ConversationRepository
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.video.VideoPosition
import eu.tintera.plume.core.video.VideoSource
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.continue_watching_progress
import eu.tintera.plume.resources.continue_watching_remove
import eu.tintera.plume.resources.play_arrow_24dp
import eu.tintera.plume.resources.saved_topic_open_chat
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Rozkoukaná videa účtu, nejnověji sledovaná první: pozice ze zprávy tohoto účtu (i z telefonu,
 * [TvPositionSync]). Jen ty se zdrojem — z id souboru zprávu dohledat nejde (TDLib na to dotaz
 * nemá), takže starší záznamy a zvuk v seznamu nejsou.
 */
@Composable
fun rememberWatchingPositions(): List<VideoPosition> {
    val all by koinInject<TvVideoPositions>().all.collectAsState()
    val account by koinInject<ChatRepository>().savedMessagesChatId.collectAsState()
    return remember(all, account) { account?.let { id -> all.filter { it.source?.accountId == id } }.orEmpty() }
}

/** Rozkoukané video, jak ho ukáže karta: pozice, zpráva s videem a chat. */
@Immutable
data class WatchingItem(val position: VideoPosition, val source: VideoSource, val chatTitle: String, val video: MessageMedia.Video, val caption: String) {
    val request: VideoRequest get() = VideoRequest(source.chatId, source.messageId, video, caption)
}

/**
 * Záložka Rozkoukaná videa: mřížka karet po třech (Google: karty pro vizuální katalog, šířka
 * pro tři vedle sebe 268 dp). OK pustí video z pamatované pozice, podržení OK nabídne otevřít
 * chat nebo video ze seznamu odebrat. Zpráva se načte jednou a pamatuje se — pozice se za
 * přehrávání přepisuje každých 10 s. Smazaná zpráva (nebo bez videa) se tiše nevykreslí.
 *
 * [playerOpen]: po zavření přehrávače si mřížka vezme fokus zpátky na kartu, ze které hrálo.
 */
@Composable
fun ContinueWatchingPane(
    positions: List<VideoPosition>,
    playerOpen: Boolean,
    takeFocus: Boolean,
    onPlay: (VideoRequest) -> Unit,
    onOpenChat: (Long) -> Unit,
) {
    val chats = koinInject<ChatRepository>()
    val conversations = koinInject<ConversationRepository>()
    val store = koinInject<TvVideoPositions>()
    val savedMessagesId = savedMessagesChatId()
    // Načtené zprávy; `null` = zpráva není nebo v ní video není.
    val loaded = remember { mutableStateMapOf<VideoSource, WatchingItem?>() }
    LaunchedEffect(positions) {
        val missing = positions.mapNotNull { it.source }.filterNot { it in loaded }
        if (missing.isEmpty()) return@LaunchedEffect
        // Napřed chaty: video rozkoukané na telefonu může být z chatu, který televize ještě
        // nenačetla, a `getMessage` by bez něj selhal.
        val titles = chats.summaries(missing.map { it.chatId }.distinct()).associate { it.id to it.title }
        missing.forEach { source ->
            val message = conversations.message(source.chatId, source.messageId).getOrNull()
            val video = message?.media as? MessageMedia.Video
            loaded[source] = if (message != null && video != null) {
                WatchingItem(VideoPosition("", 0, 0), source, titles[source.chatId].orEmpty(), video, message.text)
            } else {
                null
            }
        }
    }
    val items = positions.mapNotNull { position -> position.source?.let { loaded[it] }?.copy(position = position) }
    var lastPlayed by remember { mutableStateOf<String?>(null) }
    var menuFor by remember { mutableStateOf<WatchingItem?>(null) }
    val focus = remember { FocusRequester() }
    val focusedKey = lastPlayed?.takeIf { key -> items.any { it.position.key == key } } ?: items.firstOrNull()?.position?.key
    LaunchedEffect(focusedKey, playerOpen, takeFocus, menuFor) {
        if (focusedKey != null && !playerOpen && takeFocus && menuFor == null) runCatching { focus.requestFocus() }
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(start = SAFE_HORIZONTAL, end = SAFE_HORIZONTAL, top = 16.dp, bottom = SAFE_VERTICAL + audioDockInset()),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(items, key = { it.position.key }) { item ->
            WatchingCard(
                item = item,
                title = chatTitle(item.source.chatId, item.chatTitle, savedMessagesId),
                onClick = {
                    lastPlayed = item.position.key
                    onPlay(item.request)
                },
                onLongClick = { menuFor = item },
                modifier = if (item.position.key == focusedKey) Modifier.focusRequester(focus) else Modifier,
            )
        }
    }
    menuFor?.let { item ->
        Dialog(onDismissRequest = { menuFor = null }, properties = WideDialog) {
            DialogPanel {
                Text(
                    chatTitle(item.source.chatId, item.chatTitle, savedMessagesId),
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(20.dp))
                val open = remember { FocusRequester() }
                LaunchedEffect(Unit) { runCatching { open.requestFocus() } }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = {
                            menuFor = null
                            onOpenChat(item.source.chatId)
                        },
                        modifier = Modifier.focusRequester(open),
                    ) { Text(stringResource(Res.string.saved_topic_open_chat)) }
                    Button(onClick = {
                        menuFor = null
                        store.remove(item.position.key)
                    }) { Text(stringResource(Res.string.continue_watching_remove)) }
                }
            }
        }
    }
}

@Composable
private fun WatchingCard(item: WatchingItem, title: String, onClick: () -> Unit, onLongClick: () -> Unit, modifier: Modifier) {
    // Popisek pod kartou zarovnaný vlevo (`StandardCardContainer` ho staví na střed).
    Column(Modifier.fillMaxWidth()) {
        Card(onClick = onClick, onLongClick = onLongClick, modifier = modifier.fillMaxWidth()) {
            VideoThumbnail(item.video, Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                WatchedBar(item.position, Modifier.align(Alignment.BottomCenter))
            }
        }
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 10.dp),
        )
        Text(item.position.progressLabel(), style = MaterialTheme.typography.labelMedium.tabular())
    }
}

/** Náhled videa (první snímek) s tlačítkem přehrát; [overlay] kreslí proužek a odznaky. */
@Composable
private fun VideoThumbnail(video: MessageMedia.Video, modifier: Modifier, overlay: @Composable BoxScope.() -> Unit) {
    val targetPx = with(LocalDensity.current) { CARD_WIDTH.roundToPx() }
    val image = rememberImage(video.thumbnailFileId, targetPx)
    Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        image?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        Box(Modifier.size(44.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
            Icon(painterResource(Res.drawable.play_arrow_24dp), contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
        }
        overlay()
    }
}

/**
 * Pamatovaná pozice videa [video] (i z telefonu), nebo `null`. Klíč je soubor na serveru, takže
 * sedí i na přeposlané video v jiném chatu.
 */
@Composable
fun rememberWatched(video: MessageMedia.Video): VideoPosition? {
    val key = video.fileKey ?: return null
    val all by koinInject<TvVideoPositions>().all.collectAsState()
    return all.firstOrNull { it.key == key }
}

/** Proužek zhlédnutého přes spodek náhledu: bílý na tmavé dráze, jako posuvník přehrávače. */
@Composable
fun WatchedBar(position: VideoPosition, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(4.dp).background(Color.Black.copy(alpha = 0.5f))) {
        Box(Modifier.fillMaxWidth(position.fraction).height(4.dp).background(Color.White))
    }
}

/** „0:42 / 1:02:08" — kde se skončilo a celá délka. */
@Composable
fun VideoPosition.progressLabel(): String =
    stringResource(Res.string.continue_watching_progress, (positionMs / 1000).toInt().asDuration(), (durationMs / 1000).toInt().asDuration())

/** Šířka karty pro tři vedle sebe (mřížka 12 sloupců, docs/poznatky/android-tv.md). */
private val CARD_WIDTH = 268.dp
