package eu.tintera.plume.wear

import androidx.compose.foundation.background
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.di.APP_SCOPE
import eu.tintera.plume.core.telegram.ChatListState
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.core.telegram.ConversationRepository
import eu.tintera.plume.core.ui.asTimeOfDay
import eu.tintera.plume.core.ui.resolve
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.connection_connecting
import eu.tintera.plume.resources.conversation_history_failed
import eu.tintera.plume.resources.wear_conversation_empty
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.core.qualifier.named

private val logger = Logger.withTag("WearConversation")

/** Kolik zpráv se načte při otevření — na hodinkách se dlouhá historie nečte. */
private const val PAGE = 30

/**
 * Konverzace jen pro čtení: posledních [PAGE] zpráv, nové doplňuje živě, nejnovější je dole a na
 * ni se seznam postaví. Otevřený chat se TDLib ohlásí (`openChat`) a zobrazené zprávy se hlásí
 * jako přečtené — jako na telefonu, kdo ji na hodinkách viděl, ten ji četl.
 */
@Composable
fun ConversationScreen(chatId: Long) {
    val conversations = koinInject<ConversationRepository>()
    val chats = koinInject<ChatRepository>()
    val appScope = koinInject<CoroutineScope>(named(APP_SCOPE))
    val listState by chats.chats().collectAsState()
    val title = (listState as? ChatListState.Ready)?.chats?.firstOrNull { it.id == chatId }?.title.orEmpty()

    var messages by remember(chatId) { mutableStateOf<List<ChatMessage>?>(null) }
    var failed by remember(chatId) { mutableStateOf(false) }

    LaunchedEffect(chatId) {
        conversations.openChat(chatId)
        // Nové, upravené a smazané se odebírají DŘÍV než historie, ať se nic mezi tím neztratí.
        launch {
            conversations.newMessages(chatId).collect { message ->
                messages = (messages.orEmpty().filterNot { it.id == message.id } + message).sortedBy { it.id }
                conversations.markRead(chatId, listOf(message.id))
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
    DisposableEffect(chatId) { onDispose { appScope.launch { conversations.closeChat(chatId) } } }

    val current = messages
    when {
        failed && current.isNullOrEmpty() -> MessageScreen(stringResource(Res.string.conversation_history_failed))
        current == null -> LoadingScreen(stringResource(Res.string.connection_connecting))
        current.isEmpty() -> MessageScreen(stringResource(Res.string.wear_conversation_empty))
        else -> MessageList(title, current)
    }
}

@Composable
private fun MessageList(title: String, messages: List<ChatMessage>) {
    val state = rememberTransformingLazyColumnState()
    // Na nejnovější zprávu (dole) při otevření i s každou další.
    LaunchedEffect(messages.lastOrNull()?.id) { state.scrollToItem(messages.size) }
    ScreenScaffold(scrollState = state) { contentPadding ->
        TransformingLazyColumn(state = state, contentPadding = contentPadding) {
            item { ListHeader { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) } }
            items(messages) { message -> MessageBubble(message, chatTitle = title) }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage, chatTitle: String) {
    // Slovy kreslí `text`, a když je prázdný (fotka bez popisku, anketa…), popis obsahu.
    val text = message.text.ifEmpty { message.description?.resolve().orEmpty() }
    if (message.isService) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        )
        return
    }
    val outgoing = message.isOutgoing
    // Jméno odesílatele jen tam, kde z chatu není jasné (skupiny) — v soukromém chatu je to název.
    val sender = message.senderName?.takeIf { !outgoing && it != chatTitle }
    Box(Modifier.fillMaxWidth(), contentAlignment = if (outgoing) Alignment.CenterEnd else Alignment.CenterStart) {
        Column(
            Modifier
                .fillMaxWidth(BUBBLE_WIDTH)
                .background(
                    if (outgoing) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                    RoundedCornerShape(16.dp),
                )
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            sender?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1)
            }
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (outgoing) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                message.timestamp.asTimeOfDay(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.End),
            )
        }
    }
}

private const val BUBBLE_WIDTH = 0.85f

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
