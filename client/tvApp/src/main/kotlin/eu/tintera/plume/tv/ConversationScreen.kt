package eu.tintera.plume.tv

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.RectangleShape
import androidx.tv.material3.Icon
import eu.tintera.plume.resources.conversation_edited
import eu.tintera.plume.resources.conversation_forwarded
import eu.tintera.plume.resources.conversation_forwarded_from
import eu.tintera.plume.resources.forward_24dp
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
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
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
import eu.tintera.plume.resources.tv_compose_button
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Konverzace jen ke čtení: hlavička s chatem, pod ní zprávy od nejstarší po nejnovější (dole),
 * text a fotky. Šipkami nahoru a dolů se jde po zprávách — každá je zaostřitelná a rámeček
 * ukazuje, kde uživatel je; seznam sám roluje za fokusem. U horního okraje se dočte starší
 * historie.
 *
 * Pod seznamem je „Napsat zprávu", kde se smí psát; odpověď a reakce jsou v nabídce podržením OK.
 */
@Composable
fun ConversationScreen(chatId: Long, playerOpen: Boolean, actions: MediaActions, startAt: Long? = null) {
    val state = rememberConversationState(chatId, startAt = startAt)
    val conversations = koinInject<ConversationRepository>()
    val info by produceState<ChatInfo?>(null, chatId) { value = conversations.chatInfo(chatId).getOrNull() }
    val title = chatTitle(chatId, info?.title.orEmpty(), savedMessagesChatId())

    val interactions = remember { MessageInteractions() }
    MessageInteractionDialogs(state, interactions, title)
    Column(Modifier.fillMaxSize()) {
        ChatHeader(chatId, info)
        val messages = state.messages
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.failed -> MessageScreen(stringResource(Res.string.tv_conversation_failed), Res.drawable.cloud_off_24dp, error = true)
                messages == null -> LoadingScreen(stringResource(Res.string.connection_connecting))
                messages.isEmpty() -> MessageScreen(stringResource(Res.string.tv_conversation_empty), Res.drawable.chat_bubble_24dp)
                else -> MessageList(chatId, state, messages, title, playerOpen, actions, interactions)
            }
        }
        if (state.canWrite && messages != null) {
            ComposeButton(
                label = stringResource(Res.string.tv_compose_button),
                onClick = { interactions.composing = true },
                modifier = Modifier.padding(start = SAFE_HORIZONTAL, top = 8.dp, bottom = SAFE_VERTICAL + audioDockInset()),
            )
        }
    }
}

/** Hlavička konverzace a feedu kanálu: avatar a název. */
@Composable
fun ChatHeader(chatId: Long, info: ChatInfo?) {
    val savedMessagesId = savedMessagesChatId()
    val title = info?.let { chatTitle(chatId, it.title, savedMessagesId) }.orEmpty()
    Row(
        Modifier.fillMaxWidth().padding(start = SAFE_HORIZONTAL, end = SAFE_HORIZONTAL, top = SAFE_VERTICAL, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChatAvatar(fileId = info?.avatarFileId, title = title, size = 56.dp, savedMessages = chatId == savedMessagesId)
        Spacer(Modifier.width(16.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun MessageList(
    chatId: Long,
    state: ConversationState,
    messages: List<ChatMessage>,
    chatTitle: String,
    playerOpen: Boolean,
    actions: MediaActions,
    interactions: MessageInteractions,
) {
    // Obrácený seznam: index 0 je nejnovější zpráva dole a nová zpráva se přidá pod ni, aniž by
    // seznam odjel. Předěl dne stojí nad první zprávou dne, v obráceném seznamu tedy ZA ní.
    val entries = remember(messages) { conversationEntries(messages) }
    val newestId = (entries.first { it is Entry.Message } as Entry.Message).album.first().id
    val listState = rememberLazyListState()
    val focus = remember { FocusRequester() }
    // Fokus na nejnovější zprávu, po zavření videa nebo prohlížeče zpátky na zprávu, ze které
    // se otevřel: přehrávač fokus odnesl a bez vrácení skončil na jiné zprávě (ověřeno na emulátoru).
    var focusTarget by remember { mutableStateOf<Long?>(null) }
    // Po odeslání z televize fokus na novou zprávu, jinak by zůstal na té, na kterou se
    // odpovídalo, a nová by visela pod spodním okrajem (ověřeno na emulátoru). Jen po odeslání
    // odsud (`sentCount`), ne na každou novou vlastní zprávu — ta z telefonu by odrolovala čtení.
    LaunchedEffect(state.sentCount) {
        if (state.sentCount == 0) return@LaunchedEffect
        focusTarget = null
        listState.animateScrollToItem(0)
        withFrameNanos { }
        runCatching { focus.requestFocus() }
    }
    LaunchedEffect(playerOpen) { if (!playerOpen) runCatching { focus.requestFocus() } }
    // Skok na zprávu (hledání, citace, Rozkoukaná videa): posun na její bublinu (album, ve kterém
    // je) a fokus na ni. Po posunu se čeká snímek, ať je položka složená a fokus má kam jít.
    val jump = state.jump
    LaunchedEffect(jump) {
        val target = jump ?: return@LaunchedEffect
        val index = entries.indexOfFirst { it is Entry.Message && it.album.any { message -> message.id == target.messageId } }
        if (index >= 0) {
            focusTarget = (entries[index] as Entry.Message).album.first().id
            listState.scrollToItem(index)
            withFrameNanos { }
            runCatching { focus.requestFocus() }
        }
        state.consumeJump()
    }
    // Dočítání starší historie, když se fokus blíží nejstarší načtené zprávě (horní okraj),
    // a novější, když je načtené okno uprostřed historie a fokus se blíží spodku.
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { last -> if (last != null && last >= entries.size - LOAD_AHEAD) state.loadOlder() }
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.firstOrNull()?.index }
            .collect { first -> if (first != null && first <= LOAD_AHEAD) state.loadNewer() }
    }
    val scope = rememberCoroutineScope()
    // Dole je tlačítko psaní (pak stačí malá mezera) nebo nic (bezpečný okraj a místo pro dock).
    val bottomPadding = if (state.canWrite) 8.dp else SAFE_VERTICAL + audioDockInset()
    // Zpráva vyšší než seznam se šipkou napřed odroluje (TallItemScroll).
    val tall = rememberTallItemScroll(listState, reverse = true, paddingTop = SAFE_VERTICAL, paddingBottom = bottomPadding)
    LazyColumn(
        state = listState,
        reverseLayout = true,
        contentPadding = PaddingValues(
            start = SAFE_HORIZONTAL,
            end = SAFE_HORIZONTAL,
            top = SAFE_VERTICAL,
            bottom = bottomPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize().then(tall.listModifier),
    ) {
        items(entries, key = { it.key }) { entry ->
            when (entry) {
                is Entry.Day -> DayDivider(entry.timestamp)
                is Entry.Message -> {
                    val id = entry.album.first().id
                    val quotedId = entry.album.first().replyToMessageId
                    // OK na zprávě bez média, která na něco odpovídá, skočí na citovanou zprávu.
                    val open = mediaOpenAction(chatId, entry.album, messages, actions)
                        ?: quotedId?.let { quoted -> { scope.launch { state.jumpTo(quoted) } } }
                    MessageRow(
                        album = entry.album,
                        chatTitle = chatTitle,
                        onOpen = open?.let {
                            {
                                focusTarget = id
                                it()
                            }
                        },
                        onLongPress = { interactions.actionsFor = entry.album.first() }.takeIf { !entry.album.first().isService },
                        quote = quotedId?.let(state::quote),
                        modifier = tall.item(entry.key).then(if (id == (focusTarget ?: newestId)) Modifier.focusRequester(focus) else Modifier),
                    )
                }
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

    /** Zpráva, nebo album ([album] má víc zpráv se stejným `mediaAlbumId`) jako jedna bublina. */
    data class Message(val album: List<ChatMessage>) : Entry {
        override val key: Any get() = album.first().id
    }
}

/** Po sobě jdoucí zprávy (od nejstarší) složené do alb; zpráva mimo album je album o jedné zprávě. */
internal fun albums(messages: List<ChatMessage>): List<List<ChatMessage>> {
    val groups = mutableListOf<MutableList<ChatMessage>>()
    for (message in messages) {
        val last = groups.lastOrNull()
        if (last != null && message.mediaAlbumId != 0L && last.first().mediaAlbumId == message.mediaAlbumId) {
            last += message
        } else {
            groups += mutableListOf(message)
        }
    }
    return groups
}

/** Zprávy od nejnovější, s předělem dne za poslední (v čase první) zprávou každého dne. */
private fun conversationEntries(messages: List<ChatMessage>): List<Entry> {
    val zone = ZoneId.systemDefault()
    val groups = albums(messages)
    val days = groups.map { Instant.ofEpochSecond(it.first().timestamp.toLong()).atZone(zone).toLocalDate().toEpochDay() }
    return buildList {
        for (index in groups.indices.reversed()) {
            add(Entry.Message(groups[index]))
            if (index == 0 || days[index] != days[index - 1]) add(Entry.Day(groups[index].first().timestamp, days[index]))
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
internal fun MessageRow(
    album: List<ChatMessage>,
    chatTitle: String,
    onOpen: (() -> Unit)?,
    onLongPress: (() -> Unit)?,
    quote: ChatMessage?,
    modifier: Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    // Popisek alba nese jen jedna z jeho zpráv; odesílatel a čas jsou z první.
    val message = album.first()
    val captioned = album.firstOrNull { it.text.isNotBlank() } ?: message
    val video = message.media as? MessageMedia.Video
    val isAlbum = album.size > 1
    // Zpráva s médiem se OK otevře (`combinedClickable` na televizi bere i DPAD_CENTER): video se
    // pustí, fotka a album otevřou prohlížeč. PODRŽENÍ OK otevře nabídku (reakce, odpověď) —
    // `combinedClickable` dlouhý stisk klávesy pozná sám. Zpráva bez akce jen drží fokus.
    val focusModifier = modifier
        .onFocusChanged { focused = it.isFocused }
        .then(
            if (onOpen != null || onLongPress != null) {
                // Bez vlnky přes celou šířku řádku: fokus nese rámeček bubliny.
                Modifier.combinedClickable(
                    interactionSource = null,
                    indication = null,
                    onClickLabel = if (video != null && !isAlbum) stringResource(Res.string.media_play) else null,
                    onLongClick = onLongPress,
                    onClick = onOpen ?: {},
                )
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
    val sender = message.senderName?.takeIf { !outgoing && it != chatTitle }
    val forwarded = forwardLabel(message)
    // Fotka, video a album jako na telefonu („Médium bez bubliny" v konverzace-zobrazeni.md):
    // bez popisku a citace holé na ploše, s nimi jedna karta, ve které jde snímek od kraje ke kraji.
    val visual = isAlbum || message.media is MessageMedia.Photo || video != null
    if (visual) {
        Box(
            focusModifier.fillMaxWidth(),
            contentAlignment = if (outgoing) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            // Přeposlané médium jde do karty: „Přeposláno od …" potřebuje plochu, na holé fotce
            // by se nedalo přečíst.
            if (captioned.text.isBlank() && quote == null && forwarded == null) {
                BareMedia(album, sender, focused)
            } else {
                MediaCard(album, captioned.text, sender, forwarded, quote, chatTitle, container, content, focused)
            }
        }
        return
    }
    Box(
        focusModifier.fillMaxWidth(),
        contentAlignment = if (outgoing) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        // Odpověď je karta ze dvou částí, jako na telefonu: nahoře citace v tónované ploše, pod
        // ní odpověď v barvě bubliny, bez čáry a bez vnořené kartičky. Obě části mají šířku
        // širší z nich (`IntrinsicSize.Max`) — uvnitř bubliny není nic se `SubcomposeLayout`.
        Column(
            Modifier
                .widthIn(max = BUBBLE_MAX_WIDTH)
                .width(IntrinsicSize.Max)
                .clip(BUBBLE_SHAPE)
                .background(container)
                .then(if (focused) Modifier.border(3.dp, colors.border, BUBBLE_SHAPE) else Modifier),
        ) {
            if (quote != null) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(colors.tertiaryContainer)
                        .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 12.dp),
                ) {
                    // Má-li karta hlavičku, je jméno odesílatele v ní (jako na telefonu).
                    if (sender != null) {
                        Text(sender, style = MaterialTheme.typography.labelLarge, color = colors.onTertiaryContainer)
                        Spacer(Modifier.height(6.dp))
                    }
                    QuoteContent(quote, chatTitle)
                }
            }
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                if (sender != null && quote == null) {
                    Text(sender, style = MaterialTheme.typography.labelLarge, color = colors.primary)
                    Spacer(Modifier.height(4.dp))
                }
                if (forwarded != null) {
                    ForwardedLabel(forwarded, content)
                    Spacer(Modifier.height(6.dp))
                }
                if (message.media is MessageMedia.VoiceNote || message.media is MessageMedia.Audio) {
                    AudioBubbleContent(message, content)
                    Spacer(Modifier.height(8.dp))
                }
                val hasVisualMedia = message.media is MessageMedia.VoiceNote || message.media is MessageMedia.Audio
                val text = captioned.text.ifEmpty {
                    if (hasVisualMedia) "" else message.description?.resolve() ?: stringResource(Res.string.tv_message_unsupported)
                }
                if (text.isNotEmpty()) {
                    Text(text, style = MaterialTheme.typography.bodyLarge, color = content)
                }
                // Reakce alba nese jedna z jeho zpráv (ta, na kterou se reagovalo).
                album.firstOrNull { it.reactions.isNotEmpty() }?.let {
                    Spacer(Modifier.height(8.dp))
                    ReactionChips(it.reactions, content)
                }
                Text(
                    timeLabel(message),
                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                    color = content.copy(alpha = 0.7f),
                    modifier = Modifier.align(Alignment.End),
                )
            }
        }
    }
}

/**
 * Snímek bez popisku a citace: holý na ploše konverzace, bez bubliny — bublina kolem obrázku
 * nemá co ohraničovat a jen přidává rám (jako na telefonu a v Google Zprávách). Čas leží v rohu
 * snímku na tmavé pilulce, reakce pod ním na ploše. Jméno odesílatele nad snímkem je odsazené
 * jako text v bublině. Fokus nese rámeček po tvaru snímku.
 */
@Composable
private fun BareMedia(album: List<ChatMessage>, sender: String?, focused: Boolean) {
    val colors = MaterialTheme.colorScheme
    val message = album.first()
    Column(horizontalAlignment = if (message.isOutgoing) Alignment.End else Alignment.Start) {
        if (sender != null) {
            Text(
                sender,
                style = MaterialTheme.typography.labelLarge,
                color = colors.primary,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 6.dp),
            )
        }
        Box(
            Modifier
                .clip(BUBBLE_SHAPE)
                .then(if (focused) Modifier.border(3.dp, colors.border, BUBBLE_SHAPE) else Modifier),
        ) {
            VisualMedia(album, timeOnMedia = true)
            Text(
                timeLabel(message),
                style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(50))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        album.firstOrNull { it.reactions.isNotEmpty() }?.let {
            Spacer(Modifier.height(6.dp))
            ReactionChips(it.reactions, colors.onSurface)
        }
    }
}

/**
 * Snímek s popiskem nebo citací: JEDNA karta v barvě bubliny — nahoře citace, pod ní snímek od
 * kraje ke kraji (bez vlastního zaoblení, ořezává karta) a pod ním popisek, reakce a čas.
 * Popisek má přesně šířku snímku (`IntrinsicSize.Min`: snímek má pevnou šířku, text jen
 * nejdelší slovo), jinak by se dlouhý roztáhl vedle úzké fotky.
 */
@Composable
private fun MediaCard(
    album: List<ChatMessage>,
    caption: String,
    sender: String?,
    forwarded: String?,
    quote: ChatMessage?,
    chatTitle: String,
    container: Color,
    content: Color,
    focused: Boolean,
) {
    val colors = MaterialTheme.colorScheme
    val message = album.first()
    Column(
        Modifier
            .width(IntrinsicSize.Min)
            .clip(BUBBLE_SHAPE)
            .background(container)
            .then(if (focused) Modifier.border(3.dp, colors.border, BUBBLE_SHAPE) else Modifier),
    ) {
        if (quote != null) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(colors.tertiaryContainer)
                    .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 12.dp),
            ) {
                if (sender != null) {
                    Text(sender, style = MaterialTheme.typography.labelLarge, color = colors.onTertiaryContainer)
                    Spacer(Modifier.height(6.dp))
                }
                QuoteContent(quote, chatTitle)
            }
        } else if (sender != null) {
            Text(
                sender,
                style = MaterialTheme.typography.labelLarge,
                color = colors.primary,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = if (forwarded != null) 0.dp else 8.dp),
            )
        }
        if (forwarded != null) {
            ForwardedLabel(forwarded, content, Modifier.padding(start = 16.dp, end = 16.dp, top = if (sender != null && quote == null) 4.dp else 12.dp, bottom = 8.dp))
        }
        VisualMedia(album, timeOnMedia = false)
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)) {
            if (caption.isNotBlank()) Text(caption, style = MaterialTheme.typography.bodyLarge, color = content)
            album.firstOrNull { it.reactions.isNotEmpty() }?.let {
                Spacer(Modifier.height(8.dp))
                ReactionChips(it.reactions, content)
            }
            Text(
                timeLabel(message),
                style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                color = content.copy(alpha = 0.7f),
                modifier = Modifier.align(Alignment.End),
            )
        }
    }
}

/**
 * Snímek zprávy bez vlastního rámu (tvar dává holý snímek nebo karta): album jako mřížka,
 * fotka, nebo video. [timeOnMedia]: v pravém dolním rohu leží čas, délka videa jde nahoru.
 */
@Composable
private fun VisualMedia(album: List<ChatMessage>, timeOnMedia: Boolean) {
    val message = album.first()
    when {
        album.size > 1 -> AlbumGrid(album.mapNotNull { it.media })
        message.media is MessageMedia.Photo -> MessagePhoto((message.media as MessageMedia.Photo).fileId, RectangleShape)
        message.media is MessageMedia.Video -> MessageVideo(message.media as MessageMedia.Video, RectangleShape, durationOnTop = timeOnMedia)
    }
}

/**
 * Album v bublině: až čtyři náhledy ve dvou řadách po dvou, u posledního „+N" za ostatní.
 * Celé album se prochází v prohlížeči (OK na bublině), mřížka jen ukazuje, co v něm je.
 */
@Composable
private fun AlbumGrid(media: List<MessageMedia>) {
    val shown = media.take(ALBUM_TILES)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        shown.chunked(2).forEachIndexed { row, pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                pair.forEachIndexed { column, item ->
                    val position = row * 2 + column
                    AlbumTile(item, more = if (position == shown.lastIndex) media.size - shown.size else 0)
                }
            }
        }
    }
}

@Composable
private fun AlbumTile(media: MessageMedia, more: Int) {
    val fileId = when (media) {
        is MessageMedia.Photo -> media.fileId
        is MessageMedia.Video -> media.thumbnailFileId
        else -> null
    }
    val targetPx = with(LocalDensity.current) { ALBUM_TILE_HEIGHT.roundToPx() }
    val image = rememberImage(fileId, targetPx)
    Box(
        Modifier
            .width(ALBUM_TILE_WIDTH)
            .height(ALBUM_TILE_HEIGHT)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        image?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        if (media is MessageMedia.Video) {
            Icon(painterResource(Res.drawable.play_arrow_24dp), contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
        }
        if (more > 0) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                Text("+$more", style = MaterialTheme.typography.headlineSmall, color = Color.White)
            }
        }
    }
}

/** Fotka v bublině: varianta pro bublinu (ne největší), zmenšená na šířku bubliny. */
@Composable
private fun MessagePhoto(fileId: Int, shape: Shape = RoundedCornerShape(12.dp)) {
    val targetPx = with(LocalDensity.current) { PHOTO_HEIGHT.roundToPx() }
    val image = rememberImage(fileId, targetPx)
    Box(
        Modifier
            .height(PHOTO_HEIGHT)
            .clip(shape)
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
internal fun MessageVideo(
    video: MessageMedia.Video,
    shape: Shape = RoundedCornerShape(12.dp),
    /** Délka nahoře vlevo — dole vpravo leží čas holé zprávy. */
    durationOnTop: Boolean = false,
) {
    val targetPx = with(LocalDensity.current) { PHOTO_HEIGHT.roundToPx() }
    val thumbnail = rememberImage(video.thumbnailFileId, targetPx)
    val watched = rememberWatched(video)
    val ratio = if (video.width > 0 && video.height > 0) video.width.toFloat() / video.height else 16f / 9f
    Box(
        Modifier
            .height(PHOTO_HEIGHT)
            .width(PHOTO_HEIGHT * ratio.coerceIn(0.5f, 2f))
            .clip(shape)
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
        // Rozkoukané: kde se skončilo i číslem — u hodinového videa je proužek po minutě
        // sledování pár bodů (jako na telefonu).
        Text(
            watched?.progressLabel() ?: video.durationSeconds.asDuration(),
            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
            color = Color.White,
            modifier = Modifier
                .align(if (durationOnTop) Alignment.TopStart else Alignment.BottomEnd)
                .padding(if (durationOnTop) 10.dp else 8.dp)
                .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(50))
                .padding(horizontal = 8.dp, vertical = 2.dp),
        )
        watched?.let { WatchedBar(it, Modifier.align(Alignment.BottomCenter)) }
    }
}

internal fun Int.asDuration(): String = if (this >= 3600) {
    "%d:%02d:%02d".format(this / 3600, this % 3600 / 60, this % 60)
} else {
    "%d:%02d".format(this / 60, this % 60)
}

private val BUBBLE_SHAPE = RoundedCornerShape(20.dp)
private val BUBBLE_MAX_WIDTH = 720.dp
private val PHOTO_HEIGHT = 240.dp
private val ALBUM_TILE_WIDTH = 220.dp
private val ALBUM_TILE_HEIGHT = 150.dp
private const val ALBUM_TILES = 4

/** Kolik zpráv před nejstarší načtenou se dočítá další stránka. */
private const val LOAD_AHEAD = 8

/** Čas pod zprávou; upravená má před ním „upraveno", jako na telefonu (`MessageFrame`). */
@Composable
private fun timeLabel(message: ChatMessage): String {
    val time = message.timestamp.asTimeOfDay()
    return if (message.isEdited) "${stringResource(Res.string.conversation_edited)} $time" else time
}

/**
 * „Přeposláno od …" nad obsahem přeposlané zprávy; bez známého jména jen „Přeposlaná zpráva"
 * (smazaný účet, chat, který TDLib nezná). `null` u zprávy, která přeposlaná není.
 */
@Composable
private fun forwardLabel(message: ChatMessage): String? {
    val origin = message.forwardOrigin ?: return null
    return origin.name?.let { stringResource(Res.string.conversation_forwarded_from, it) } ?: stringResource(Res.string.conversation_forwarded)
}

@Composable
private fun ForwardedLabel(text: String, content: Color, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(Res.drawable.forward_24dp), contentDescription = null, tint = content.copy(alpha = 0.8f), modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = content.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
