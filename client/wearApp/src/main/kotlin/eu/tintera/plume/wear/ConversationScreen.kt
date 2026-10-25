package eu.tintera.plume.wear

import androidx.lifecycle.viewmodel.compose.viewModel
import eu.tintera.plume.core.telegram.SpeechTranscript
import eu.tintera.plume.core.speech.SystemSpeechToText
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import eu.tintera.plume.core.telegram.FileRepository
import eu.tintera.plume.core.telegram.FileState
import eu.tintera.plume.resources.mic_24dp
import eu.tintera.plume.resources.sticker_24dp
import eu.tintera.plume.resources.location_on_24dp
import eu.tintera.plume.resources.location_on_filled_24dp
import eu.tintera.plume.resources.content_location
import eu.tintera.plume.resources.content_live_location
import eu.tintera.plume.resources.wear_location_button
import eu.tintera.plume.resources.wear_sticker_button
import eu.tintera.plume.resources.cloud_off_24dp
import eu.tintera.plume.resources.check_24dp
import eu.tintera.plume.resources.done_all_24dp
import eu.tintera.plume.resources.conversation_delivery_read
import eu.tintera.plume.resources.conversation_delivery_sent
import eu.tintera.plume.resources.wear_confirm_play_failed
import eu.tintera.plume.resources.wear_voice_record
import kotlinx.coroutines.flow.first
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.animation.core.FiniteAnimationSpec
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
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.di.APP_SCOPE
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.core.telegram.ConversationRepository
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.telegram.MessageReaction
import eu.tintera.plume.core.telegram.SendState
import eu.tintera.plume.core.telegram.SentMessageUpdate
import eu.tintera.plume.core.telegram.customEmojiIdOf
import eu.tintera.plume.core.ui.asDayLabel
import eu.tintera.plume.core.ui.asTimeOfDay
import eu.tintera.plume.core.ui.resolve
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.connection_connecting
import eu.tintera.plume.resources.conversation_history_failed
import eu.tintera.plume.resources.wear_conversation_empty
import eu.tintera.plume.resources.wear_message_failed
import eu.tintera.plume.resources.wear_message_sending
import eu.tintera.plume.resources.wear_reply
import eu.tintera.plume.resources.wear_confirm_not_sent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.core.qualifier.named

private val logger = Logger.withTag("WearConversation")

/** Kolik položek od začátku seznamu (nadpis, indikátor) spustí dočtení starších zpráv. */
private const val NEAR_TOP_ITEMS = 2

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
 * - **Nabídka nad zprávou**: ťuknutí na zprávu otevře reakce, odpověď s citací a smazání
 *   ([onReact], `MessageMenuScreen`); reakce pod bublinou jsou živé
 *   (`interactionUpdates`). Vlastní emoji hodinky nevykreslí, vynechávají se.
 */
@Composable
fun ConversationScreen(
    chatId: Long,
    onReact: (messageId: Long) -> Unit,
    onRecord: () -> Unit,
    onSticker: () -> Unit,
    onLocation: () -> Unit,
) {
    val conversations = koinInject<ConversationRepository>()
    val chats = koinInject<ChatRepository>()
    val files = koinInject<FileRepository>()
    val appScope = koinInject<CoroutineScope>(named(APP_SCOPE))
    val scope = rememberCoroutineScope()
    // Název a fotka podle id, ne z hlavního seznamu: chat může být otevřený z archivu nebo
    // ze složky Telegramu a v hlavním seznamu by chyběl.
    val appearance by remember(chatId) { chats.appearance(chatId) }.collectAsState(null)
    val savedMessagesId = savedMessagesChatId()
    val title = chatTitle(chatId, appearance?.title.orEmpty(), savedMessagesId)

    // Zprávy a odběry patří ViewModelu položky navigace: přežije nabídku nad zprávou i nahrávání
    // (jinak se po návratu konverzace načetla znovu a sjela dolů), viz ConversationViewModel.
    val appContext = LocalContext.current.applicationContext
    val model = viewModel(key = "conversation-$chatId") {
        ConversationViewModel(chatId, conversations, appScope, chats, files, PhoneTranscription(appContext), SystemSpeechToText(appContext))
    }
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
    // Přehrávání patří obrazovce: s odchodem z ní (i do nabídky nad zprávou) skončí.
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

    val openReply = rememberReplyInput { text ->
        scope.launch {
            conversations.send(chatId, text, replyToMessageId = null)
                .onSuccess { model.upsert(it) }
                .onFailure {
                    logger.w(it) { "Odpověď z hodinek do chatu $chatId neodešla." }
                    fail(Res.string.wear_confirm_not_sent)
                }
        }
    }
    val onReply = { openReply(title) }

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
        model.failed && current.isNullOrEmpty() -> MessageScreen(stringResource(Res.string.conversation_history_failed), Res.drawable.cloud_off_24dp, error = true)
        current == null -> LoadingScreen(stringResource(Res.string.connection_connecting))
        else -> MessageList(
            title = title,
            avatarFileId = appearance?.avatarFileId,
            savedMessages = chatId == savedMessagesId,
            messages = current,
            quoted = model.quoted,
            transcripts = model.transcripts,
            onTranscribe = model::transcribe,
            lastReadOutboxId = model.lastReadOutboxId,
            loadingOlder = model.loadingOlder,
            playback = playback,
            onReply = onReply,
            onReact = onReact,
            onRecord = onRecord,
            onSticker = onSticker,
            onLocation = onLocation,
            onPlay = onPlay,
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
    avatarFileId: Int?,
    savedMessages: Boolean,
    messages: List<ChatMessage>,
    quoted: Map<Long, ChatMessage?>,
    transcripts: Map<Long, SpeechTranscript>,
    onTranscribe: (Long) -> Unit,
    lastReadOutboxId: Long,
    loadingOlder: Boolean,
    playback: VoicePlayback,
    onReply: () -> Unit,
    onReact: (Long) -> Unit,
    onRecord: () -> Unit,
    onSticker: () -> Unit,
    onLocation: () -> Unit,
    onPlay: (ChatMessage) -> Unit,
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
    val motion = rememberItemMotion()
    // Na nejnovější zprávu (dole) při otevření a s každou novou — ale ne po návratu z výběru
    // reakce, fotky nebo nahrávání: tam pozici drží uložený stav seznamu a sjet dolů by uživatele
    // odvezlo od zprávy, na kterou reagoval.
    // Předěly dní a bloky zpráv téhož odesílatele (ConversationEntries).
    val entries = remember(messages) { conversationEntries(messages) }
    val lastId = messages.lastOrNull()?.id
    LaunchedEffect(lastId) {
        if (lastId != scrolledToId) {
            // Při otevření skokem; s novou zprávou v otevřené konverzaci plynule — `scrollToItem`
            // ruší animace položek, takže by nová zpráva naskočila bez prolnutí (ItemMotion).
            if (scrolledToId == null) state.scrollToItem(entries.size + 2) else state.animateScrollToItem(entries.size + 2)
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
        // Mezi bublinami jednoho bloku jen 2 dp; začátek bloku a předěl si přidají vlastní mezeru
        // (GROUP_GAP), takže mezi bloky zůstane výchozí odstup seznamu.
        TransformingLazyColumn(
            state = state,
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.Top),
        ) {
            item(key = "header") {
                ListHeader(
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                ) {
                    // Fotka chatu vedle názvu, jako v seznamu chatů: je hned jasné, kde jsi.
                    ChatAvatar(fileId = avatarFileId, title = title, size = HEADER_AVATAR, savedMessages = savedMessages)
                    Spacer(Modifier.width(6.dp))
                    Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
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
            items(entries, key = { it.key }) { entry ->
                if (entry is ConversationEntry.Day) {
                    DayDivider(entry.timestamp, Modifier.transformedItem(this, spec).animateItem(motion.fadeIn, motion.placement, motion.fadeOut))
                    return@items
                }
                val (message, joinsPrevious, joinsNext) = entry as ConversationEntry.Message
                val replyTo = message.replyToMessageId
                MessageBubble(
                    message,
                    joinsPrevious = joinsPrevious,
                    joinsNext = joinsNext,
                    delivery = message.delivery(lastReadOutboxId),
                    chatTitle = title,
                    quote = replyTo?.let { id -> messages.firstOrNull { it.id == id } ?: quoted[id] },
                    playing = message.id == playback.playingId,
                    playback = playback,
                    // Přepis z hodinek (telefon, hodinky) přebíjí ten ze serveru ve zprávě.
                    transcript = transcripts[message.id] ?: (message.media as? MessageMedia.VoiceNote)?.transcript,
                    onTranscribe = { onTranscribe(message.id) },
                    onPlay = { onPlay(message) },
                    onVote = { answers -> onVote(message.id, answers) },
                    pressing = pressing?.takeIf { it.first == message.id }?.let { it.second to it.third },
                    onBotButton = { row, column, button -> onBotButton(message, row, column, button) },
                    onOpenLink = onOpenLink,
                    // Reagovat jde jen na zprávu, kterou server zná — dočasná ještě skutečné id nemá.
                    onClick = { onReact(message.id) }
                        .takeIf { !message.isService && (message.sendState == null || message.sendState == SendState.Sent) },
                    modifier = Modifier
                        .transformedItem(this, spec)
                        .animateItem(motion.fadeIn, motion.placement, motion.fadeOut)
                        .then(if (joinsPrevious) Modifier else Modifier.padding(top = GROUP_GAP)),
                )
            }
            botNote?.let { item { CenteredNote(it, Modifier.transformedItem(this, spec)) } }
            item {
                Button(
                    onClick = onRecord,
                    icon = { Icon(painterResource(Res.drawable.mic_24dp), contentDescription = null) },
                    label = { Text(stringResource(Res.string.wear_voice_record)) },
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec).padding(top = GROUP_GAP),
                    transformation = SurfaceTransformation(spec),
                )
            }
            // Samolepka z oblíbených a nedávných (StickerPickerScreen) — rychlá odpověď jedním ťuknutím.
            item {
                Button(
                    onClick = onSticker,
                    icon = { Icon(painterResource(Res.drawable.sticker_24dp), contentDescription = null) },
                    label = { Text(stringResource(Res.string.wear_sticker_button)) },
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                )
            }
            // Jednorázová poloha z hodinek (SendLocationScreen), po potvrzení.
            item {
                Button(
                    onClick = onLocation,
                    icon = { Icon(painterResource(Res.drawable.location_on_24dp), contentDescription = null) },
                    label = { Text(stringResource(Res.string.wear_location_button)) },
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                )
            }
        }
    }
}

/**
 * Animace položek konverzace: nová zpráva se objeví prolnutím a ostatní k ní plynule popojedou,
 * místo aby seznam skočil. Specifikace z `MotionScheme` Wear M3; posun bez překmitu (effects, ne
 * spatial) — pružina by při každé nové zprávě rozhoupala celou konverzaci.
 */
private class ItemMotion(
    val fadeIn: FiniteAnimationSpec<Float>,
    val placement: FiniteAnimationSpec<IntOffset>,
    val fadeOut: FiniteAnimationSpec<Float>,
)

@Composable
private fun rememberItemMotion(): ItemMotion {
    val scheme = MaterialTheme.motionScheme
    return remember(scheme) {
        ItemMotion(scheme.defaultEffectsSpec(), scheme.defaultEffectsSpec(), scheme.fastEffectsSpec())
    }
}

/**
 * Předěl dne nad první zprávou dne: „Dnes", „Včera", jinak datum. Bez něj ukazovaly bubliny jen
 * hodinu a u starší historie nebylo poznat, jestli je zpráva z dneška, nebo z minulého týdne.
 */
@Composable
private fun DayDivider(timestamp: Int, modifier: Modifier) {
    Text(
        timestamp.asDayLabel(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth().padding(top = GROUP_GAP + 4.dp, bottom = 2.dp),
    )
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    joinsPrevious: Boolean,
    joinsNext: Boolean,
    delivery: Delivery?,
    chatTitle: String,
    quote: ChatMessage?,
    playing: Boolean,
    playback: VoicePlayback,
    transcript: SpeechTranscript?,
    onTranscribe: () -> Unit,
    onPlay: () -> Unit,
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
                delivery = delivery,
                fill = false,
            )
        }
        return
    }
    // Jméno odesílatele jen tam, kde z chatu není jasné (skupiny) — v soukromém chatu je to název —
    // a jen u první zprávy bloku, další bubliny téhož člověka se pod ní řadí bez něj.
    val sender = message.senderName?.takeIf { !outgoing && it != chatTitle && !joinsPrevious }
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
                    bubbleShape(outgoing, joinsPrevious, joinsNext),
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
                MessagePhoto(photo, modifier = Modifier.padding(vertical = 4.dp))
            }
            val video = message.media?.shownVideo()
            if (video != null) {
                MessageVideo(video, modifier = Modifier.padding(vertical = 4.dp))
            }
            val document = message.media as? MessageMedia.Document
            if (document != null) {
                MessageDocument(document, outgoing)
            }
            val location = message.media as? MessageMedia.Location
            if (location != null) {
                MessageLocation(location, outgoing, onOpenLink)
            }
            val poll = message.poll
            if (poll != null) {
                MessagePoll(poll, outgoing, onVote)
            }
            val voice = message.media as? MessageMedia.VoiceNote
            if (voice != null) {
                VoiceNoteRow(
                    voice,
                    outgoing = outgoing,
                    playing = playing,
                    onPlay = onPlay,
                    progress = playback::progress,
                    elapsedSeconds = playback::elapsedSeconds,
                    transcript = transcript,
                    onTranscribe = onTranscribe,
                )
            }
            // U hlasovky a ukázané fotky je popis („Hlasová zpráva", „Fotka") zbytečný — kreslí se
            // jen případný popisek.
            // Anketa se kreslí celá — `text` u ní nese jen „Anketa: otázka".
            if (poll == null && ((voice == null && photo == null && video == null && document == null && location == null) || message.text.isNotEmpty())) {
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
                delivery = delivery,
            )
        }
    }
}

/**
 * Reakce a čas v JEDNOM řádku: reakce vlevo, čas vpravo, jako na telefonu. Dřív měl každý svůj
 * řádek a na malém displeji to bublinu zbytečně natahovalo (připomínka uživatele z Pixel Watch).
 * Bez reakcí je čas sám vpravo. [fill] = přes celou šířku bubliny; u samolepky a kolečka (bez
 * bubliny) jen podle obsahu, ať čas nestojí na opačném kraji displeje.
 *
 * Každá reakce je nedělitelná dvojice emoji + počet ([ReactionCount]) a řada se zalamuje po
 * dvojicích (`FlowRow`): jako jeden text s mezerami se u kanálu s mnoha reakcemi zalomila mezi
 * emoji a jeho počet, takže číslo viselo na začátku dalšího řádku u cizí reakce.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReactionsAndStatus(
    reactions: List<MessageReaction>,
    status: String,
    statusColor: Color,
    delivery: Delivery?,
    fill: Boolean = true,
) {
    val shown = reactions.filter { customEmojiIdOf(it.emoji) == null }
    // S reakcemi kousek od obsahu: emoji jsou vyšší než čas a bez mezery se lepily na text.
    val gap = if (shown.isNotEmpty()) Modifier.padding(top = 4.dp) else Modifier
    Row((if (fill) Modifier.fillMaxWidth() else Modifier).then(gap), verticalAlignment = Alignment.Bottom) {
        if (shown.isNotEmpty()) {
            FlowRow(
                modifier = if (fill) Modifier.weight(1f) else Modifier.weight(1f, fill = false),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                shown.forEach { ReactionCount(it) }
            }
            Spacer(Modifier.width(6.dp))
        } else if (fill) {
            Spacer(Modifier.weight(1f))
        }
        Text(status, style = MaterialTheme.typography.labelSmall, color = statusColor, maxLines = 1)
        delivery?.let { DeliveryIcon(it, statusColor) }
    }
}

/** Stav odeslané zprávy, který už server zná; odesílání a nezdar nesou slova místo času. */
private enum class Delivery { Sent, Read }

/**
 * Doručeno, nebo přečteno — jako na telefonu z posuvného ukazatele na chatu
 * ([ConversationViewModel.lastReadOutboxId]), ne ze zprávy. Příchozí a dosud neodeslaná nic.
 */
private fun ChatMessage.delivery(lastReadOutboxId: Long): Delivery? = when {
    !isOutgoing || isService -> null
    sendState != null && sendState != SendState.Sent -> null
    id <= lastReadOutboxId -> Delivery.Read
    else -> Delivery.Sent
}

/**
 * Fajfka za časem: jedna = odesláno, dvě = přečteno. Rozlišuje je tvar, ne barva — akcent na
 * odchozí (tónové) bublině kontrast nezaručuje (CLAUDE.md), proto barva času.
 */
@Composable
private fun DeliveryIcon(delivery: Delivery, color: Color) {
    Spacer(Modifier.width(2.dp))
    Icon(
        painterResource(if (delivery == Delivery.Read) Res.drawable.done_all_24dp else Res.drawable.check_24dp),
        contentDescription = stringResource(
            if (delivery == Delivery.Read) Res.string.conversation_delivery_read else Res.string.conversation_delivery_sent,
        ),
        tint = color,
        modifier = Modifier.size(14.dp),
    )
}

/**
 * Jedna reakce: emoji a hned za ním menší počet v tlumené barvě, jako čas. Počet stejně velký
 * a stejně barevný jako emoji splýval s ním i se sousední reakcí. Vlastní reakce má počet tučně
 * — barvou to nejde, akcent na tónované bublině kontrast nezaručuje (CLAUDE.md). Počet 1 se
 * nepíše.
 */
@Composable
private fun ReactionCount(reaction: MessageReaction) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(reaction.emoji, style = MaterialTheme.typography.labelMedium)
        if (reaction.count > 1) {
            Spacer(Modifier.width(2.dp))
            Text(
                reaction.count.toString(),
                style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                fontWeight = if (reaction.isChosen) FontWeight.Bold else null,
                color = if (reaction.isChosen) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
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
 * Poloha: ikona, název (místo, živá poloha, nebo „Poloha") a adresa, jinak souřadnice. Mapu
 * hodinky nekreslí — `core` dává poloze prázdné tělo, protože telefon ji kreslí mapou, a bez
 * tohoto řádku byla bublina prázdná. Ťuknutí otevře mapu v telefonu ([onOpenLink], jako odkazy).
 */
@Composable
private fun MessageLocation(location: MessageMedia.Location, outgoing: Boolean, onOpenLink: (String) -> Unit) {
    val color = if (outgoing) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    val title = location.title?.takeIf { it.isNotBlank() }
        ?: stringResource(if (location.isLive) Res.string.content_live_location else Res.string.content_location)
    // Souřadnice s tečkou v každém jazyce: s českou desetinnou čárkou by „50,08750, 14,42080"
    // nešlo přečíst, kde končí šířka.
    val detail = location.address?.takeIf { it.isNotBlank() }
        ?: "%.5f, %.5f".format(java.util.Locale.ROOT, location.latitude, location.longitude)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onOpenLink("https://maps.google.com/?q=${location.latitude},${location.longitude}") }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(Res.drawable.location_on_filled_24dp), contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(6.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodySmall, color = color, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
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

private val HEADER_AVATAR = 20.dp

/** Mezera nad začátkem bloku zpráv (a nad předělem); uvnitř bloku jen odstup seznamu 2 dp. */
private val GROUP_GAP = 2.dp

private val BUBBLE_CORNER = 16.dp
private val JOINED_CORNER = 4.dp

/**
 * Bublina v bloku má na straně odesílatele stažený roh tam, kde na ni navazuje sousední zpráva
 * — blok se čte jako jeden celek, jako na telefonu.
 */
private fun bubbleShape(outgoing: Boolean, joinsPrevious: Boolean, joinsNext: Boolean): RoundedCornerShape {
    val top = if (joinsPrevious) JOINED_CORNER else BUBBLE_CORNER
    val bottom = if (joinsNext) JOINED_CORNER else BUBBLE_CORNER
    return if (outgoing) {
        RoundedCornerShape(topStart = BUBBLE_CORNER, topEnd = top, bottomEnd = bottom, bottomStart = BUBBLE_CORNER)
    } else {
        RoundedCornerShape(topStart = top, topEnd = BUBBLE_CORNER, bottomEnd = BUBBLE_CORNER, bottomStart = bottom)
    }
}

