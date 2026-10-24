package eu.tintera.plume.wear

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
import eu.tintera.plume.resources.voice_playback_failed
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
import eu.tintera.plume.resources.wear_send_failed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.core.qualifier.named

private val logger = Logger.withTag("WearConversation")

/** Kolik zpráv se načte při otevření — na hodinkách se dlouhá historie nečte. */
private const val PAGE = 30

private const val REPLY_KEY = "reply"

/**
 * Konverzace: posledních [PAGE] zpráv, nové doplňuje živě, nejnovější je dole a na ni se seznam
 * postaví. Otevřený chat se TDLib ohlásí (`openChat`) a zobrazené zprávy se hlásí jako přečtené —
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
fun ConversationScreen(chatId: Long, onReact: (messageId: Long) -> Unit, onRecord: () -> Unit) {
    val conversations = koinInject<ConversationRepository>()
    val chats = koinInject<ChatRepository>()
    val files = koinInject<FileRepository>()
    val appScope = koinInject<CoroutineScope>(named(APP_SCOPE))
    val scope = rememberCoroutineScope()
    val listState by chats.chats().collectAsState()
    val title = chatTitle(chatId, (listState as? ChatListState.Ready)?.chats?.firstOrNull { it.id == chatId }?.title.orEmpty(), savedMessagesChatId())

    var messages by remember(chatId) { mutableStateOf<List<ChatMessage>?>(null) }
    var failed by remember(chatId) { mutableStateOf(false) }
    // Poslední chyba odeslání nebo přehrání, jako poznámka pod konverzací.
    var problem by remember(chatId) { mutableStateOf<StringResource?>(null) }
    val playback = remember { VoicePlayback() }

    fun upsert(message: ChatMessage) {
        messages = (messages.orEmpty().filterNot { it.id == message.id } + message).sortedBy { it.id }
    }

    LaunchedEffect(chatId) {
        conversations.openChat(chatId)
        // Nové, upravené, smazané, potvrzené a reakce se odebírají DŘÍV než historie, ať se nic
        // mezi tím neztratí.
        launch {
            conversations.newMessages(chatId).collect { message ->
                upsert(message)
                if (!message.isOutgoing) conversations.markRead(chatId, listOf(message.id))
            }
        }
        launch {
            conversations.editedMessages(chatId).collect { edited ->
                messages = messages?.map { if (it.id == edited.id) edited else it }
            }
        }
        launch {
            conversations.deletedMessages(chatId).collect { ids -> messages = messages?.filterNot { it.id in ids } }
        }
        launch {
            // TDLib po potvrzení zprávě změní id: dočasnou je potřeba nahradit, ne aktualizovat.
            conversations.sentMessageUpdates(chatId).collect { update ->
                val (oldId, message) = when (update) {
                    is SentMessageUpdate.Succeeded -> update.oldMessageId to update.message
                    is SentMessageUpdate.Failed -> update.oldMessageId to update.message
                }
                messages = (messages.orEmpty().filterNot { it.id == oldId || it.id == message.id } + message).sortedBy { it.id }
            }
        }
        launch {
            conversations.interactionUpdates(chatId).collect { change ->
                messages = messages?.map { if (it.id == change.messageId) it.copy(reactions = change.reactions) else it }
            }
        }
        loadHistory(conversations, chatId)
            .onSuccess { loaded ->
                messages = (loaded + messages.orEmpty()).distinctBy { it.id }.sortedBy { it.id }
                if (loaded.isNotEmpty()) conversations.markRead(chatId, loaded.map { it.id })
            }
            .onFailure {
                logger.w(it) { "Historie chatu $chatId se na hodinkách nenačetla." }
                failed = true
            }
    }
    // Zavření běží v aplikačním scope: scope obrazovky v onDispose už je zrušený.
    DisposableEffect(chatId) {
        onDispose {
            playback.stop()
            appScope.launch { conversations.closeChat(chatId) }
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
                    .onSuccess {
                        problem = null
                        upsert(it)
                    }
                    .onFailure {
                        logger.w(it) { "Odpověď z hodinek do chatu $chatId neodešla." }
                        problem = Res.string.wear_send_failed
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
                    problem = null
                    playback.play(message.id, file.path) {
                        logger.w(it) { "Hlasovka ${message.id} se na hodinkách nepřehrála." }
                        problem = Res.string.voice_playback_failed
                    }
                    conversations.openContent(chatId, message.id)
                } else {
                    problem = Res.string.voice_playback_failed
                }
            }
        }
    }

    val current = messages
    when {
        failed && current.isNullOrEmpty() -> MessageScreen(stringResource(Res.string.conversation_history_failed))
        current == null -> LoadingScreen(stringResource(Res.string.connection_connecting))
        else -> MessageList(title, current, problem, playback.playingId, onReply, onReact, onRecord, onPlay)
    }
}

@Composable
private fun MessageList(
    title: String,
    messages: List<ChatMessage>,
    problem: StringResource?,
    playingId: Long?,
    onReply: () -> Unit,
    onReact: (Long) -> Unit,
    onRecord: () -> Unit,
    onPlay: (ChatMessage) -> Unit,
) {
    val state = rememberTransformingLazyColumnState()
    // Na nejnovější zprávu (dole) při otevření i s každou další.
    LaunchedEffect(messages.lastOrNull()?.id) { state.scrollToItem(messages.size + 2) }
    ScreenScaffold(
        scrollState = state,
        edgeButton = { EdgeButton(onClick = onReply) { Text(stringResource(Res.string.wear_reply)) } },
    ) { contentPadding ->
        TransformingLazyColumn(state = state, contentPadding = contentPadding) {
            item { ListHeader { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) } }
            if (messages.isEmpty()) {
                item { CenteredNote(stringResource(Res.string.wear_conversation_empty)) }
            }
            items(messages) { message ->
                MessageBubble(
                    message,
                    chatTitle = title,
                    playing = message.id == playingId,
                    onPlay = { onPlay(message) },
                    // Reagovat jde jen na zprávu, kterou server zná — dočasná ještě skutečné id nemá.
                    onClick = { onReact(message.id) }
                        .takeIf { !message.isService && (message.sendState == null || message.sendState == SendState.Sent) },
                )
            }
            problem?.let { item { CenteredNote(stringResource(it), error = true) } }
            item {
                Button(
                    onClick = onRecord,
                    icon = { Icon(painterResource(Res.drawable.mic_24dp), contentDescription = null) },
                    label = { Text(stringResource(Res.string.wear_voice_record)) },
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    chatTitle: String,
    playing: Boolean,
    onPlay: () -> Unit,
    onClick: (() -> Unit)?,
) {
    // Slovy kreslí `text`, a když je prázdný (fotka bez popisku, anketa…), popis obsahu.
    // Samotné emoji a hod kostkou nemají ani jedno: znak nese příloha, kterou telefon animuje
    // (bez toho byla odpověď „👍" z hodinek prázdná bublina).
    val emoji = (message.media as? MessageMedia.AnimatedEmoji)?.emoji ?: (message.media as? MessageMedia.Dice)?.emoji
    val text = message.text.ifEmpty { emoji ?: message.description?.resolve().orEmpty() }
    if (message.isService) {
        CenteredNote(text)
        return
    }
    val outgoing = message.isOutgoing
    // Jméno odesílatele jen tam, kde z chatu není jasné (skupiny) — v soukromém chatu je to název.
    val sender = message.senderName?.takeIf { !outgoing && it != chatTitle }
    val status = when (message.sendState) {
        SendState.Sending -> stringResource(Res.string.wear_message_sending)
        SendState.Failed -> stringResource(Res.string.wear_message_failed)
        SendState.Sent, null -> message.timestamp.asTimeOfDay()
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = if (outgoing) Alignment.CenterEnd else Alignment.CenterStart) {
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
            val voice = message.media as? MessageMedia.VoiceNote
            if (voice != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Tónová výplň má barvu příchozí bubliny a tlačítko by v ní zmizelo — tam plná.
                    FilledTonalIconButton(
                        onClick = onPlay,
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
            // U hlasovky je popis „Hlasová zpráva" zbytečný — kreslí se jen případný popisek.
            if (voice == null || message.text.isNotEmpty()) {
                Text(
                    text,
                    style = if (emoji != null && message.text.isEmpty()) MaterialTheme.typography.displaySmall else MaterialTheme.typography.bodyMedium,
                    color = if (outgoing) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                )
            }
            reactionsLine(message.reactions)?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
            }
            Text(
                status,
                style = MaterialTheme.typography.labelSmall,
                color = if (message.sendState == SendState.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.End),
            )
        }
    }
}

@Composable
private fun CenteredNote(text: String, error: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
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

/**
 * Posledních [PAGE] zpráv. První `getChatHistory` vrací TDLib často jen to, co má v lokální
 * databázi (klidně jednu zprávu), takže se dočítá od nejstarší, dokud stránka není plná nebo
 * historie neskončí.
 */
private suspend fun loadHistory(conversations: ConversationRepository, chatId: Long): Result<List<ChatMessage>> = runCatching {
    val loaded = mutableListOf<ChatMessage>()
    var from = 0L
    repeat(3) {
        if (loaded.size >= PAGE) return@runCatching loaded
        val page = conversations.history(chatId, fromMessageId = from, limit = PAGE - loaded.size).getOrThrow()
            .filterNot { message -> loaded.any { it.id == message.id } }
        if (page.isEmpty()) return@runCatching loaded
        loaded += page
        from = loaded.minOf { it.id }
    }
    loaded
}
