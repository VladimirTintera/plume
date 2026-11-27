package eu.tintera.plume.tv

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.Icon
import eu.tintera.plume.resources.media_play
import eu.tintera.plume.resources.play_arrow_24dp
import org.jetbrains.compose.resources.painterResource
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import eu.tintera.plume.core.telegram.ChatInfo
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.ConversationRepository
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.ui.asDayLabel
import eu.tintera.plume.core.ui.asTimeOfDay
import eu.tintera.plume.core.ui.resolve
import eu.tintera.plume.resources.Res
import java.time.Instant
import java.time.ZoneId
import eu.tintera.plume.resources.chat_bubble_24dp
import eu.tintera.plume.resources.cloud_off_24dp
import eu.tintera.plume.resources.connection_connecting
import eu.tintera.plume.resources.tv_conversation_empty
import eu.tintera.plume.resources.tv_conversation_failed
import eu.tintera.plume.resources.tv_message_unsupported
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Konverzace jen ke čtení: hlavička s chatem, pod ní zprávy od nejstarší po nejnovější (dole),
 * text a fotky. Šipkami nahoru a dolů se jde po zprávách — každá je zaostřitelná a rámeček
 * ukazuje, kde uživatel je; seznam sám roluje za fokusem. U horního okraje se dočte starší
 * historie.
 *
 * Psaní a komentáře v kanálech přijdou v dalších krocích (docs/roadmap.md, „Android TV").
 */
@Composable
fun ConversationScreen(chatId: Long, playerOpen: Boolean, onPlayVideo: (MessageMedia.Video, String) -> Unit) {
    val state = rememberConversationState(chatId)
    val conversations = koinInject<ConversationRepository>()
    val info by produceState<ChatInfo?>(null, chatId) { value = conversations.chatInfo(chatId).getOrNull() }
    val savedMessagesId = savedMessagesChatId()
    val title = info?.let { chatTitle(chatId, it.title, savedMessagesId) }.orEmpty()

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = SAFE_HORIZONTAL, end = SAFE_HORIZONTAL, top = SAFE_VERTICAL, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChatAvatar(fileId = info?.avatarFileId, title = title, size = 56.dp, savedMessages = chatId == savedMessagesId)
            Spacer(Modifier.width(16.dp))
            Text(title, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        val messages = state.messages
        when {
            state.failed -> MessageScreen(stringResource(Res.string.tv_conversation_failed), Res.drawable.cloud_off_24dp, error = true)
            messages == null -> LoadingScreen(stringResource(Res.string.connection_connecting))
            messages.isEmpty() -> MessageScreen(stringResource(Res.string.tv_conversation_empty), Res.drawable.chat_bubble_24dp)
            else -> MessageList(state, messages, title, playerOpen, onPlayVideo)
        }
    }
}

@Composable
private fun MessageList(
    state: ConversationState,
    messages: List<ChatMessage>,
    chatTitle: String,
    playerOpen: Boolean,
    onPlayVideo: (MessageMedia.Video, String) -> Unit,
) {
    // Obrácený seznam: index 0 je nejnovější zpráva dole a nová zpráva se přidá pod ni, aniž by
    // seznam odjel. Předěl dne stojí nad první zprávou dne, v obráceném seznamu tedy ZA ní.
    val entries = remember(messages) { conversationEntries(messages) }
    val newestId = messages.last().id
    val listState = rememberLazyListState()
    val focus = remember { FocusRequester() }
    // Fokus na nejnovější zprávu, po zavření videa zpátky na zprávu s ním: přehrávač fokus
    // odnesl a bez vrácení skončil na jiné zprávě (ověřeno na emulátoru).
    var focusTarget by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(playerOpen) { if (!playerOpen) runCatching { focus.requestFocus() } }
    // Dočítání starší historie, když se fokus blíží nejstarší načtené zprávě (horní okraj).
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { last -> if (last != null && last >= entries.size - LOAD_AHEAD) state.loadOlder() }
    }
    LazyColumn(
        state = listState,
        reverseLayout = true,
        contentPadding = PaddingValues(horizontal = SAFE_HORIZONTAL, vertical = SAFE_VERTICAL),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(entries, key = { it.key }) { entry ->
            when (entry) {
                is Entry.Day -> DayDivider(entry.timestamp)
                is Entry.Message -> MessageRow(
                    message = entry.message,
                    chatTitle = chatTitle,
                    onPlayVideo = { video, caption ->
                        focusTarget = entry.message.id
                        onPlayVideo(video, caption)
                    },
                    modifier = if (entry.message.id == (focusTarget ?: newestId)) Modifier.focusRequester(focus) else Modifier,
                )
            }
        }
    }
}

/** Položka konverzace: zpráva, nebo předěl dne (klíč je den, ať dočtení historie nezmění kotvu). */
private sealed interface Entry {
    val key: Any

    data class Day(val timestamp: Int, val epochDay: Long) : Entry {
        override val key: Any get() = "day-$epochDay"
    }

    data class Message(val message: ChatMessage) : Entry {
        override val key: Any get() = message.id
    }
}

/** Zprávy od nejnovější, s předělem dne za poslední (v čase první) zprávou každého dne. */
private fun conversationEntries(messages: List<ChatMessage>): List<Entry> {
    val zone = ZoneId.systemDefault()
    val days = messages.map { Instant.ofEpochSecond(it.timestamp.toLong()).atZone(zone).toLocalDate().toEpochDay() }
    return buildList {
        for (index in messages.indices.reversed()) {
            add(Entry.Message(messages[index]))
            if (index == 0 || days[index] != days[index - 1]) add(Entry.Day(messages[index].timestamp, days[index]))
        }
    }
}

/** Předěl dne: nezaostřitelný — šipka ho přeskočí, seznam ho ukáže spolu se zprávou pod ním. */
@Composable
private fun DayDivider(timestamp: Int) {
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Text(
            timestamp.asDayLabel(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MessageRow(
    message: ChatMessage,
    chatTitle: String,
    onPlayVideo: (MessageMedia.Video, String) -> Unit,
    modifier: Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val video = message.media as? MessageMedia.Video
    // Zpráva s videem se OK otevře (`clickable` na televizi bere i DPAD_CENTER), ostatní jen
    // drží fokus, aby šlo po zprávách chodit šipkami.
    val focusModifier = modifier
        .onFocusChanged { focused = it.isFocused }
        .then(
            if (video != null) {
                // Bez vlnky přes celou šířku řádku: fokus nese rámeček bubliny.
                Modifier.clickable(
                    interactionSource = null,
                    indication = null,
                    onClickLabel = stringResource(Res.string.media_play),
                ) { onPlayVideo(video, message.text) }
            } else {
                Modifier.focusable()
            },
        )
    if (message.isService) {
        Box(focusModifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                message.text.ifEmpty { message.description?.resolve().orEmpty() },
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .background(colors.surfaceVariant, RoundedCornerShape(50))
                    .border(if (focused) 2.dp else 0.dp, if (focused) colors.border else colors.surfaceVariant, RoundedCornerShape(50))
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
        return
    }
    val outgoing = message.isOutgoing
    val container = if (outgoing) colors.primaryContainer else colors.surfaceVariant
    val content = if (outgoing) colors.onPrimaryContainer else colors.onSurfaceVariant
    Box(
        focusModifier.fillMaxWidth(),
        contentAlignment = if (outgoing) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Column(
            Modifier
                .widthIn(max = BUBBLE_MAX_WIDTH)
                .clip(BUBBLE_SHAPE)
                .background(container)
                .border(if (focused) 3.dp else 0.dp, if (focused) colors.border else container, BUBBLE_SHAPE)
                .padding(16.dp),
        ) {
            val sender = message.senderName?.takeIf { !outgoing && it != chatTitle }
            if (sender != null) {
                Text(sender, style = MaterialTheme.typography.labelLarge, color = colors.primary)
                Spacer(Modifier.height(4.dp))
            }
            (message.media as? MessageMedia.Photo)?.let { photo ->
                MessagePhoto(photo.fileId)
                Spacer(Modifier.height(8.dp))
            }
            video?.let {
                MessageVideo(it)
                Spacer(Modifier.height(8.dp))
            }
            val text = message.text.ifEmpty {
                if (message.media is MessageMedia.Photo || video != null) "" else message.description?.resolve() ?: stringResource(Res.string.tv_message_unsupported)
            }
            if (text.isNotEmpty()) {
                Text(text, style = MaterialTheme.typography.bodyLarge, color = content)
            }
            Text(
                message.timestamp.asTimeOfDay(),
                style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                color = content.copy(alpha = 0.7f),
                modifier = Modifier.align(Alignment.End),
            )
        }
    }
}

/** Fotka v bublině: varianta pro bublinu (ne největší), zmenšená na šířku bubliny. */
@Composable
private fun MessagePhoto(fileId: Int) {
    val targetPx = with(LocalDensity.current) { PHOTO_HEIGHT.roundToPx() }
    val image = rememberImage(fileId, targetPx)
    Box(
        Modifier
            .height(PHOTO_HEIGHT)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface),
    ) {
        image?.let {
            val ratio = it.width.toFloat() / it.height.coerceAtLeast(1)
            Image(
                it,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.height(PHOTO_HEIGHT).width(PHOTO_HEIGHT * ratio.coerceIn(0.5f, 2f)),
            )
        } ?: Spacer(Modifier.size(PHOTO_HEIGHT))
    }
}

/** Náhled videa v bublině: první snímek, uprostřed tlačítko přehrát, v rohu délka. */
@Composable
private fun MessageVideo(video: MessageMedia.Video) {
    val targetPx = with(LocalDensity.current) { PHOTO_HEIGHT.roundToPx() }
    val thumbnail = rememberImage(video.thumbnailFileId, targetPx)
    val ratio = if (video.width > 0 && video.height > 0) video.width.toFloat() / video.height else 16f / 9f
    Box(
        Modifier
            .height(PHOTO_HEIGHT)
            .width(PHOTO_HEIGHT * ratio.coerceIn(0.5f, 2f))
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        thumbnail?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        Box(
            Modifier.size(56.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(Res.drawable.play_arrow_24dp), contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
        }
        Text(
            video.durationSeconds.asDuration(),
            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
            color = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp)
                .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(50))
                .padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

private fun Int.asDuration(): String = if (this >= 3600) {
    "%d:%02d:%02d".format(this / 3600, this % 3600 / 60, this % 60)
} else {
    "%d:%02d".format(this / 60, this % 60)
}

private val BUBBLE_SHAPE = RoundedCornerShape(20.dp)
private val BUBBLE_MAX_WIDTH = 720.dp
private val PHOTO_HEIGHT = 240.dp

/** Kolik zpráv před nejstarší načtenou se dočítá další stránka. */
private const val LOAD_AHEAD = 8
