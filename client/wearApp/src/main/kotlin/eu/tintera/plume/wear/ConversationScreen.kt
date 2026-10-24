package eu.tintera.plume.wear

import androidx.lifecycle.viewmodel.compose.viewModel
import eu.tintera.plume.core.telegram.SpeechTranscript
import eu.tintera.plume.resources.description_24dp
import eu.tintera.plume.resources.conversation_forwarded_from
import eu.tintera.plume.resources.conversation_forwarded
import eu.tintera.plume.resources.conversation_edited
import eu.tintera.plume.core.ui.fileSizeText
import androidx.compose.ui.text.font.FontStyle
import eu.tintera.plume.core.telegram.MessageEntityType
import eu.tintera.plume.core.telegram.MessageEntity
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.graphics.Color
import eu.tintera.plume.resources.wear_phone_unavailable
import eu.tintera.plume.resources.wear_opened_on_phone
import eu.tintera.plume.resources.wear_bot_button_phone_only
import eu.tintera.plume.core.telegram.BotButtonKind
import eu.tintera.plume.core.telegram.BotButton
import androidx.wear.compose.material3.openOnPhoneDialogCurvedText
import androidx.wear.compose.material3.OpenOnPhoneDialogDefaults
import androidx.wear.compose.material3.OpenOnPhoneDialog
import androidx.wear.compose.material3.AlertDialogDefaults
import androidx.wear.compose.material3.AlertDialog
import androidx.compose.ui.platform.LocalContext
import eu.tintera.plume.resources.wear_confirm_vote_failed
import eu.tintera.plume.resources.wear_message_you
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.drawBehind
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.FilledTonalIconButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButtonDefaults
import eu.tintera.plume.core.telegram.FileRepository
import eu.tintera.plume.core.telegram.FileState
import eu.tintera.plume.resources.media_pause
import eu.tintera.plume.resources.media_play
import eu.tintera.plume.resources.mic_24dp
import eu.tintera.plume.resources.play_arrow_24dp
import eu.tintera.plume.resources.stop_24dp
import eu.tintera.plume.resources.wear_confirm_play_failed
import eu.tintera.plume.resources.wear_voice_record
import kotlinx.coroutines.flow.first
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import android.app.RemoteInput
import android.content.Intent
import android.view.inputmethod.EditorInfo
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.input.RemoteInputIntentHelper
import androidx.wear.input.wearableExtender
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.di.APP_SCOPE
import eu.tintera.plume.core.telegram.ChatListState
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.core.telegram.ConversationRepository
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.telegram.MessageReaction
import eu.tintera.plume.core.telegram.SendState
import eu.tintera.plume.core.telegram.SentMessageUpdate
import eu.tintera.plume.core.telegram.customEmojiIdOf
import eu.tintera.plume.core.ui.asTimeOfDay
import eu.tintera.plume.core.ui.resolve
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.connection_connecting
import eu.tintera.plume.resources.conversation_history_failed
import eu.tintera.plume.resources.wear_conversation_empty
import eu.tintera.plume.resources.wear_message_failed
import eu.tintera.plume.resources.wear_message_sending
import eu.tintera.plume.resources.wear_quick_reply_1
import eu.tintera.plume.resources.wear_quick_reply_2
import eu.tintera.plume.resources.wear_quick_reply_3
import eu.tintera.plume.resources.wear_quick_reply_4
import eu.tintera.plume.resources.wear_quick_reply_5
import eu.tintera.plume.resources.wear_reply
import eu.tintera.plume.resources.wear_reply_label
import eu.tintera.plume.resources.wear_confirm_not_sent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.core.qualifier.named

private val logger = Logger.withTag("WearConversation")

/** Kolik položek od začátku seznamu (nadpis, indikátor) spustí dočtení starších zpráv. */
private const val NEAR_TOP_ITEMS = 2

private const val REPLY_KEY = "reply"

/**
 * Konverzace: posledních 30 zpráv, u začátku seznamu dočítá starší, nové doplňuje živě,
 * nejnovější je dole a na ni se seznam postaví. Odpověď má nad textem citaci. Otevřený chat se TDLib ohlásí (`openChat`) a zobrazené zprávy se hlásí jako přečtené —
 * jako na telefonu, kdo ji na hodinkách viděl, ten ji četl.
 *
 * - **Odpověď** tlačítkem dole (`EdgeButton`): systémové zadávání hodinek — diktování, klávesnice,
 *   emoji a nabídnuté rychlé odpovědi (`RemoteInput` s volbami). Odeslaná zpráva se ukáže hned
 *   s dočasným id a po potvrzení se vymění za tu se skutečným ([SentMessageUpdate]).
 * - **Hlasovky**: v bublině přehrát/zastavit a délka ([VoicePlayback]); nahrát novou otevře
 *   [onRecord] (tlačítko na konci seznamu).
 * - **Reakce**: ťuknutí na zprávu otevře výběr ([onReact]), pod bublinou jsou živé
 *   (`interactionUpdates`). Vlastní emoji hodinky nevykreslí, vynechávají se.
 */
@Composable
fun ConversationScreen(chatId: Long, onReact: (messageId: Long) -> Unit, onRecord: () -> Unit, onOpenPhoto: (fileId: Int) -> Unit) {
    val conversations = koinInject<ConversationRepository>()
    val chats = koinInject<ChatRepository>()
    val files = koinInject<FileRepository>()
    val appScope = koinInject<CoroutineScope>(named(APP_SCOPE))
    val scope = rememberCoroutineScope()
    val listState by chats.chats().collectAsState()
    val title = chatTitle(chatId, (listState as? ChatListState.Ready)?.chats?.firstOrNull { it.id == chatId }?.title.orEmpty(), savedMessagesChatId())

    // Zprávy a odběry patří ViewModelu položky navigace: přežije výběr reakce, fotku i nahrávání
    // (jinak se po návratu konverzace načetla znovu a sjela dolů), viz ConversationViewModel.
    val model = viewModel(key = "conversation-$chatId") { ConversationViewModel(chatId, conversations, appScope) }
    // Tlačítko bota, u kterého se čeká na odpověď (zpráva, řádek, sloupec) — naráz jen jedno.
    var pressing by remember(chatId) { mutableStateOf<Triple<Long, Int, Int>?>(null) }
    // Odpověď bota jako poznámka pod konverzací; upozornění (alert) jako dialog k potvrzení.
    var botNote by remember(chatId) { mutableStateOf<String?>(null) }
    var botAlert by remember(chatId) { mutableStateOf<String?>(null) }
    var openedOnPhone by remember { mutableStateOf(false) }
    val context = LocalContext.current
    // Chyba odeslání, hlasování nebo přehrání jako potvrzovací dialog (FailureConfirmation).
    // Dřív to byla poznámka na konci seznamu — při hlasování ve starší anketě nebo přehrávání
    // starší hlasovky odrolovaná mimo displej, takže ji nikdo neviděl. Text zůstává i po skrytí,
    // ať dialog nezmizí prázdný.
    var problem by remember(chatId) { mutableStateOf<StringResource?>(null) }
    var problemShown by remember(chatId) { mutableStateOf(false) }
    val fail: (StringResource) -> Unit = {
        problem = it
        problemShown = true
    }
    val playback = remember { VoicePlayback() }
    // Přehrávání patří obrazovce: s odchodem z ní (i na výběr reakce) skončí.
    DisposableEffect(chatId) { onDispose { playback.stop() } }

    val phoneOnly = stringResource(Res.string.wear_bot_button_phone_only)
    val openedOnPhoneText = stringResource(Res.string.wear_opened_on_phone)
    val phoneUnavailable = stringResource(Res.string.wear_phone_unavailable)
    val openLink: (String) -> Unit = { url ->
        openOnPhone(context, url) { opened -> if (opened) openedOnPhone = true else botNote = phoneUnavailable }
    }
    val onBotButton: (ChatMessage, Int, Int, BotButton) -> Unit = { message, row, column, button ->
        when (val kind = button.kind) {
            BotButtonKind.Callback -> {
                pressing = Triple(message.id, row, column)
                scope.launch {
                    conversations.pressBotButton(chatId, message.id, row, column, button.text)
                        .onSuccess { answer ->
                            when {
                                answer.alert && answer.text.isNotBlank() -> botAlert = answer.text
                                answer.text.isNotBlank() -> botNote = answer.text
                            }
                            if (answer.url.isNotBlank()) openLink(answer.url)
                        }
                        .onFailure { logger.w(it) { "Tlačítko bota v chatu $chatId neprošlo." } }
                    pressing = null
                }
            }
            is BotButtonKind.Url -> openLink(kind.url)
            // Mini aplikace, přihlášení na web, inline dotaz, kontakt, platba… patří na telefon.
            else -> botNote = phoneOnly
        }
    }

    val replyLabel = stringResource(Res.string.wear_reply_label)
    val quickReplies = listOf(
        stringResource(Res.string.wear_quick_reply_1),
        stringResource(Res.string.wear_quick_reply_2),
        stringResource(Res.string.wear_quick_reply_3),
        stringResource(Res.string.wear_quick_reply_4),
        stringResource(Res.string.wear_quick_reply_5),
    )
    val replyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val text = result.data
            ?.let(RemoteInput::getResultsFromIntent)
            ?.getCharSequence(REPLY_KEY)
            ?.toString()
            ?.trim()
        if (!text.isNullOrEmpty()) {
            scope.launch {
                conversations.send(chatId, text, replyToMessageId = null)
                    .onSuccess { model.upsert(it) }
                    .onFailure {
                        logger.w(it) { "Odpověď z hodinek do chatu $chatId neodešla." }
                        fail(Res.string.wear_confirm_not_sent)
                    }
            }
        }
    }
    val onReply = { replyLauncher.launch(replyInput(title, replyLabel, quickReplies)) }

    // Hlasovka: stažení spustí odběr souboru, poslech se ohlásí TDLib (`openContent`) jako na telefonu.
    val onPlay: (ChatMessage) -> Unit = { message ->
        val voice = message.media as? MessageMedia.VoiceNote
        if (playback.playingId == message.id) {
            playback.stop()
        } else if (voice != null) {
            scope.launch {
                val file = files.file(voice.fileId).first { it is FileState.Ready || it is FileState.Failed }
                if (file is FileState.Ready) {
                    playback.play(message.id, file.path) {
                        logger.w(it) { "Hlasovka ${message.id} se na hodinkách nepřehrála." }
                        fail(Res.string.wear_confirm_play_failed)
                    }
                    conversations.openContent(chatId, message.id)
                } else {
                    fail(Res.string.wear_confirm_play_failed)
                }
            }
        }
    }

    val current = model.messages
    when {
        model.failed && current.isNullOrEmpty() -> MessageScreen(stringResource(Res.string.conversation_history_failed))
        current == null -> LoadingScreen(stringResource(Res.string.connection_connecting))
        else -> MessageList(
            title = title,
            messages = current,
            quoted = model.quoted,
            loadingOlder = model.loadingOlder,
            playingId = playback.playingId,
            onReply = onReply,
            onReact = onReact,
            onRecord = onRecord,
            onPlay = onPlay,
            onOpenPhoto = onOpenPhoto,
            onNearTop = model::loadOlder,
            scrolledToId = model.scrolledToId,
            onScrolledTo = { model.scrolledToId = it },
            pressing = pressing,
            botNote = botNote,
            onBotButton = onBotButton,
            onOpenLink = openLink,
            onVote = { messageId, answers ->
                scope.launch {
                    conversations.vote(chatId, messageId, answers)
                        .onFailure {
                            logger.w(it) { "Hlas v anketě z hodinek neodešel (chat $chatId)." }
                            fail(Res.string.wear_confirm_vote_failed)
                        }
                }
            },
        )
    }
    botAlert?.let { text ->
        AlertDialog(
            visible = true,
            onDismissRequest = { botAlert = null },
            // Nadpis dialogu má málo řádků a delší upozornění by uťal: nadpis je bot (název
            // chatu), odpověď jde do těla.
            title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = { Text(text) },
            confirmButton = { AlertDialogDefaults.ConfirmButton(onClick = { botAlert = null }) },
            dismissButton = {},
        )
    }
    FailureConfirmation(
        visible = problemShown,
        text = problem?.let { stringResource(it) }.orEmpty(),
        onDismiss = { problemShown = false },
    )
    // Styl se čte v kompozici, slot zakřiveného textu už composable není.
    val curvedStyle = OpenOnPhoneDialogDefaults.curvedTextStyle
    OpenOnPhoneDialog(
        visible = openedOnPhone,
        onDismissRequest = { openedOnPhone = false },
        curvedText = { openOnPhoneDialogCurvedText(text = openedOnPhoneText, style = curvedStyle) },
    )
}

@Composable
private fun MessageList(
    title: String,
    messages: List<ChatMessage>,
    quoted: Map<Long, ChatMessage?>,
    loadingOlder: Boolean,
    playingId: Long?,
    onReply: () -> Unit,
    onReact: (Long) -> Unit,
    onRecord: () -> Unit,
    onPlay: (ChatMessage) -> Unit,
    onOpenPhoto: (Int) -> Unit,
    onNearTop: () -> Unit,
    pressing: Triple<Long, Int, Int>?,
    botNote: String?,
    onBotButton: (ChatMessage, Int, Int, BotButton) -> Unit,
    onOpenLink: (String) -> Unit,
    onVote: (messageId: Long, answers: List<Int>) -> Unit,
    scrolledToId: Long?,
    onScrolledTo: (Long?) -> Unit,
) {
    val state = rememberTransformingLazyColumnState()
    // Zprávy se u okraje displeje zmenšují jako karty v seznamu chatů (M3 Expressive).
    val spec = rememberTransformationSpec()
    // Na nejnovější zprávu (dole) při otevření a s každou novou — ale ne po návratu z výběru
    // reakce, fotky nebo nahrávání: tam pozici drží uložený stav seznamu a sjet dolů by uživatele
    // odvezlo od zprávy, na kterou reagoval.
    val lastId = messages.lastOrNull()?.id
    LaunchedEffect(lastId) {
        if (lastId != scrolledToId) {
            state.scrollToItem(messages.size + 2)
            onScrolledTo(lastId)
        }
    }
    // U začátku seznamu dočíst starší zprávy. Zprávy mají klíče, takže vložené nahoře seznam
    // neposunou — uživatel zůstane na zprávě, kterou četl.
    LaunchedEffect(state) {
        snapshotFlow { state.layoutInfo.visibleItems.firstOrNull()?.index }
            .collect { first -> if (first != null && first <= NEAR_TOP_ITEMS) onNearTop() }
    }
    ScreenScaffold(
        scrollState = state,
        edgeButton = { EdgeButton(onClick = onReply) { Text(stringResource(Res.string.wear_reply)) } },
    ) { contentPadding ->
        TransformingLazyColumn(state = state, contentPadding = contentPadding) {
            item(key = "header") {
                ListHeader(
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                ) { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            if (loadingOlder) {
                item(key = "loading") {
                    Box(Modifier.fillMaxWidth().transformedItem(this, spec), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
            }
            if (messages.isEmpty()) {
                item { CenteredNote(stringResource(Res.string.wear_conversation_empty), Modifier.transformedItem(this, spec)) }
            }
            items(messages, key = { it.id }) { message ->
                val replyTo = message.replyToMessageId
                MessageBubble(
                    message,
                    chatTitle = title,
                    quote = replyTo?.let { id -> messages.firstOrNull { it.id == id } ?: quoted[id] },
                    playing = message.id == playingId,
                    onPlay = { onPlay(message) },
                    onOpenPhoto = onOpenPhoto,
                    onVote = { answers -> onVote(message.id, answers) },
                    pressing = pressing?.takeIf { it.first == message.id }?.let { it.second to it.third },
                    onBotButton = { row, column, button -> onBotButton(message, row, column, button) },
                    onOpenLink = onOpenLink,
                    // Reagovat jde jen na zprávu, kterou server zná — dočasná ještě skutečné id nemá.
                    onClick = { onReact(message.id) }
                        .takeIf { !message.isService && (message.sendState == null || message.sendState == SendState.Sent) },
                    modifier = Modifier.transformedItem(this, spec),
                )
            }
            botNote?.let { item { CenteredNote(it, Modifier.transformedItem(this, spec)) } }
            item {
                Button(
                    onClick = onRecord,
                    icon = { Icon(painterResource(Res.drawable.mic_24dp), contentDescription = null) },
                    label = { Text(stringResource(Res.string.wear_voice_record)) },
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                )
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    chatTitle: String,
    quote: ChatMessage?,
    playing: Boolean,
    onPlay: () -> Unit,
    onOpenPhoto: (Int) -> Unit,
    onVote: (List<Int>) -> Unit,
    pressing: Pair<Int, Int>?,
    onBotButton: (Int, Int, BotButton) -> Unit,
    onOpenLink: (String) -> Unit,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    // Slovy kreslí `text`, a když je prázdný (fotka bez popisku, anketa…), popis obsahu.
    // Samotné emoji a hod kostkou nemají ani jedno: znak nese příloha, kterou telefon animuje
    // (bez toho byla odpověď „👍" z hodinek prázdná bublina).
    val emoji = (message.media as? MessageMedia.AnimatedEmoji)?.emoji ?: (message.media as? MessageMedia.Dice)?.emoji
    val text = message.text.ifEmpty { emoji ?: message.description?.resolve().orEmpty() }
    if (message.isService) {
        CenteredNote(text, modifier)
        return
    }
    val outgoing = message.isOutgoing
    // Samolepka a kolečko bez bubliny, jen s časem pod nimi (jako na telefonu).
    val sticker = message.media?.shownSticker()
    val videoNote = message.media as? MessageMedia.VideoNote
    if (sticker != null || videoNote != null) {
        Column(
            modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
            horizontalAlignment = if (outgoing) Alignment.End else Alignment.Start,
        ) {
            if (sticker != null) MessageSticker(sticker)
            if (videoNote != null) {
                MessageVideoNote(videoNote)
                // Přepis řeči, když ho server udělal (o přepis se na hodinkách nežádá).
                (videoNote.transcript as? SpeechTranscript.Done)?.text?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                }
            }
            ReactionsAndStatus(
                reactions = message.reactions,
                status = message.timestamp.asTimeOfDay(),
                statusColor = MaterialTheme.colorScheme.onSurfaceVariant,
                fill = false,
            )
        }
        return
    }
    // Jméno odesílatele jen tam, kde z chatu není jasné (skupiny) — v soukromém chatu je to název.
    val sender = message.senderName?.takeIf { !outgoing && it != chatTitle }
    val status = when (message.sendState) {
        SendState.Sending -> stringResource(Res.string.wear_message_sending)
        SendState.Failed -> stringResource(Res.string.wear_message_failed)
        // „upraveno" k času, jako na telefonu — u zprávy, kterou někdo přepsal, to mění význam.
        SendState.Sent, null -> message.timestamp.asTimeOfDay().let { time ->
            if (message.isEdited) "${stringResource(Res.string.conversation_edited)} $time" else time
        }
    }
    Box(modifier.fillMaxWidth(), contentAlignment = if (outgoing) Alignment.CenterEnd else Alignment.CenterStart) {
        Column(
            Modifier
                .fillMaxWidth(BUBBLE_WIDTH)
                .background(
                    if (outgoing) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                    RoundedCornerShape(16.dp),
                )
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            sender?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1)
            }
            // Přeposláno od …: bez toho by přeposlaná zpráva vypadala jako vlastní slova odesílatele.
            message.forwardOrigin?.let { origin ->
                Text(
                    origin.name?.let { stringResource(Res.string.conversation_forwarded_from, it) }
                        ?: stringResource(Res.string.conversation_forwarded),
                    style = MaterialTheme.typography.labelSmall,
                    fontStyle = FontStyle.Italic,
                    color = if (outgoing) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            quote?.let { ReplyQuote(it, chatTitle, outgoing) }
            val photo = message.media?.shownPhoto()
            if (photo != null) {
                MessagePhoto(photo, onClick = { onOpenPhoto(photo.fileId) }, modifier = Modifier.padding(vertical = 4.dp))
            }
            val video = message.media?.shownVideo()
            if (video != null) {
                MessageVideo(video, modifier = Modifier.padding(vertical = 4.dp))
            }
            val document = message.media as? MessageMedia.Document
            if (document != null) {
                MessageDocument(document, outgoing)
            }
            val poll = message.poll
            if (poll != null) {
                MessagePoll(poll, outgoing, onVote)
            }
            val voice = message.media as? MessageMedia.VoiceNote
            if (voice != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Tónová výplň má barvu příchozí bubliny a tlačítko by v ní zmizelo — tam plná.
                    FilledTonalIconButton(
                        onClick = onPlay,
                        shapes = IconButtonDefaults.animatedShapes(),
                        colors = if (outgoing) IconButtonDefaults.filledTonalIconButtonColors() else IconButtonDefaults.filledIconButtonColors(),
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            painterResource(if (playing) Res.drawable.stop_24dp else Res.drawable.play_arrow_24dp),
                            contentDescription = stringResource(if (playing) Res.string.media_pause else Res.string.media_play),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(formatDuration(voice.durationSeconds), style = MaterialTheme.typography.bodyMedium)
                }
            }
            // U hlasovky a ukázané fotky je popis („Hlasová zpráva", „Fotka") zbytečný — kreslí se
            // jen případný popisek.
            // Anketa se kreslí celá — `text` u ní nese jen „Anketa: otázka".
            if (poll == null && ((voice == null && photo == null && video == null && document == null) || message.text.isNotEmpty())) {
                val textColor = if (outgoing) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                // Odkaz na tónové bublině: akcent jen v příchozí, v odchozí barva popředí bubliny —
                // `primary` na `primaryContainer` kontrast nezaručuje (CLAUDE.md). Odlišení nese
                // podtržení v obou.
                val linkColor = if (outgoing) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
                Text(
                    if (message.text.isNotEmpty()) linkedText(message.text, message.entities, linkColor, onOpenLink) else AnnotatedString(text),
                    style = if (emoji != null && message.text.isEmpty()) MaterialTheme.typography.displaySmall else MaterialTheme.typography.bodyMedium,
                    color = textColor,
                )
            }
            message.keyboard?.let { MessageBotKeyboard(it, pressing, onBotButton) }
            ReactionsAndStatus(
                reactions = message.reactions,
                status = status,
                statusColor = if (message.sendState == SendState.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Reakce a čas v JEDNOM řádku: reakce vlevo, čas vpravo, jako na telefonu. Dřív měl každý svůj
 * řádek a na malém displeji to bublinu zbytečně natahovalo (připomínka uživatele z Pixel Watch).
 * Delší řada reakcí se zalomí ve své části řádku, čas zůstane vpravo dole. Bez reakcí je čas
 * sám vpravo. [fill] = přes celou šířku bubliny; u samolepky a kolečka (bez bubliny) jen podle
 * obsahu, ať čas nestojí na opačném kraji displeje.
 */
@Composable
private fun ReactionsAndStatus(reactions: List<MessageReaction>, status: String, statusColor: Color, fill: Boolean = true) {
    val line = reactionsLine(reactions)
    Row(if (fill) Modifier.fillMaxWidth() else Modifier, verticalAlignment = Alignment.Bottom) {
        if (line != null) {
            Text(
                line,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = if (fill) Modifier.weight(1f) else Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(6.dp))
        } else if (fill) {
            Spacer(Modifier.weight(1f))
        }
        Text(status, style = MaterialTheme.typography.labelSmall, color = statusColor, maxLines = 1)
    }
}

/**
 * Soubor: ikona, jméno a velikost místo slova „Soubor". Otevřít ho na hodinkách nejde (prohlížeče
 * dokumentů tam nejsou) — ťuknutí patří bublině; jméno aspoň řekne, co přišlo.
 */
@Composable
private fun MessageDocument(document: MessageMedia.Document, outgoing: Boolean) {
    val color = if (outgoing) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(Res.drawable.description_24dp), contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(6.dp))
        Column {
            Text(document.fileName, style = MaterialTheme.typography.bodySmall, color = color, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(fileSizeText(document.sizeBytes), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Citace nad odpovědí: kdo a první řádek toho, na co se odpovídá, s proužkem vlevo jako na
 * telefonu. Proužek se kreslí za obsahem (`drawBehind`), ne jako prvek s `fillMaxHeight` —
 * výška by se musela měřit intrinsicky.
 */
@Composable
private fun ReplyQuote(quote: ChatMessage, chatTitle: String, inOutgoing: Boolean) {
    val accent = if (inOutgoing) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
    // V soukromém chatu zpráva odesílatele nenese — je to protějšek, tedy název chatu.
    val name = if (quote.isOutgoing) stringResource(Res.string.wear_message_you) else quote.senderName ?: chatTitle
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .drawBehind { drawRect(accent, size = Size(3.dp.toPx(), size.height)) }
            .padding(start = 8.dp),
    ) {
        if (name.isNotBlank()) {
            Text(name, style = MaterialTheme.typography.labelSmall, color = accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(
            previewText(quote),
            style = MaterialTheme.typography.labelSmall,
            color = if (inOutgoing) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Text zprávy s klikatelnými odkazy (`MessageEntityType.Link`, i e-mail a telefon). Ťuknutí
 * odkaz otevře v telefonu ([onOpenLink]) — web se na hodinkách neotevírá. Ostatní entity
 * (zmínky, tagy, formátování) zůstávají obyčejným textem: zmínka ani tag by na hodinkách
 * neměly kam vést a modrý text bez akce je horší než černý.
 */
private fun linkedText(text: String, entities: List<MessageEntity>, linkColor: Color, onOpenLink: (String) -> Unit): AnnotatedString =
    buildAnnotatedString {
        append(text)
        entities.forEach { entity ->
            val link = entity.type as? MessageEntityType.Link ?: return@forEach
            val start = entity.offset.coerceIn(0, text.length)
            val end = (entity.offset + entity.length).coerceIn(start, text.length)
            if (start == end) return@forEach
            addLink(
                LinkAnnotation.Clickable(
                    tag = link.url,
                    styles = TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)),
                ) { onOpenLink(link.url) },
                start,
                end,
            )
        }
    }

/** Zpráva jedním řádkem: text, a když není, emoji z přílohy nebo popis obsahu („Fotka"). */
@Composable
private fun previewText(message: ChatMessage): String {
    val emoji = (message.media as? MessageMedia.AnimatedEmoji)?.emoji ?: (message.media as? MessageMedia.Dice)?.emoji
    return message.text.ifEmpty { emoji ?: message.description?.resolve().orEmpty() }
}

@Composable
private fun CenteredNote(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp),
    )
}

private const val BUBBLE_WIDTH = 0.85f

/** Reakce pod zprávou jako „👍 2  ❤ 1"; vlastní emoji hodinky nevykreslí, vynechají se. */
private fun reactionsLine(reactions: List<MessageReaction>): String? =
    reactions.filter { customEmojiIdOf(it.emoji) == null }
        .takeIf { it.isNotEmpty() }
        ?.joinToString("  ") { if (it.count > 1) "${it.emoji} ${it.count}" else it.emoji }

/**
 * Systémové zadávání hodinek: diktování, klávesnice a emoji, k tomu rychlé odpovědi jako volby.
 * Odeslání se potvrzuje akcí „odeslat" na klávesnici.
 */
private fun replyInput(title: String, label: String, quickReplies: List<String>): Intent =
    RemoteInputIntentHelper.createActionRemoteInputIntent().also { intent ->
        val input = RemoteInput.Builder(REPLY_KEY)
            .setLabel(label)
            .setChoices(quickReplies.toTypedArray())
            .wearableExtender {
                setEmojisAllowed(true)
                setInputActionType(EditorInfo.IME_ACTION_SEND)
            }
            .build()
        RemoteInputIntentHelper.putRemoteInputsExtra(intent, listOf(input))
        RemoteInputIntentHelper.putTitleExtra(intent, title)
    }

