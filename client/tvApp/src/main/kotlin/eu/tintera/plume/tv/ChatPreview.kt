package eu.tintera.plume.tv

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.ChatSummary
import eu.tintera.plume.core.telegram.ConversationRepository
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.ui.asTimeOfDay
import eu.tintera.plume.core.ui.resolve
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.tv_message_you
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/** Poslední zprávy zaostřeného chatu pro náhled a médium z nich na pozadí. */
class ChatPreviewData(val chatId: Long, val messages: List<ChatMessage>, val backdropFileId: Int?)

/** Kolik posledních zpráv se v náhledu ukáže. */
private const val PREVIEW_MESSAGES = 4

/** Kolik zpráv se projde při hledání média na pozadí. */
private const val PREVIEW_HISTORY = 20

/**
 * Prodleva před načtením náhledu: při rychlém projíždění seznamu šipkou by se jinak historie
 * četla pro každý řádek, přes který fokus jen přejel.
 */
private const val PREVIEW_DELAY_MS = 300L

/**
 * Náhled chatu [chatId]: posledních [PREVIEW_HISTORY] zpráv z historie TDLib. `getChatHistory`
 * chat neotevírá ani nehlásí přečtení, takže projíždění seznamu nikomu neodbaví „přečteno".
 * Načtené náhledy zůstávají v paměti obrazovky, návrat na chat ukáže hned to, co už bylo.
 */
@Composable
fun rememberChatPreview(chatId: Long?): ChatPreviewData? {
    val conversations = koinInject<ConversationRepository>()
    val cache = remember { HashMap<Long, ChatPreviewData>() }
    val preview by produceState<ChatPreviewData?>(initialValue = null, chatId) {
        if (chatId == null) {
            value = null
            return@produceState
        }
        value = cache[chatId]
        delay(PREVIEW_DELAY_MS)
        val history = conversations.history(chatId, fromMessageId = 0L, limit = PREVIEW_HISTORY).getOrNull() ?: return@produceState
        val newestFirst = history.sortedByDescending { it.id }
        val backdrop = newestFirst.firstNotNullOfOrNull { message ->
            when (val media = message.media) {
                is MessageMedia.Photo -> media.fileId
                is MessageMedia.Video -> media.thumbnailFileId
                else -> null
            }
        }
        val shown = newestFirst.filter { !it.isService && (it.text.isNotBlank() || it.description != null) }.take(PREVIEW_MESSAGES).reversed()
        ChatPreviewData(chatId, shown, backdrop).also {
            cache[chatId] = it
            value = it
        }
    }
    return preview
}

/**
 * Pozadí celé obrazovky: poslední fotka nebo video zaostřeného chatu, zarovnané doprava nahoru
 * (Google: hlavní motiv obrazu vpravo nahoře, text ho nemá zakrývat). Zleva a zespodu ho kryje
 * přechod do plochy, takže seznam a záložky zůstávají čitelné a obraz se do nich jen prolíná.
 * Při změně chatu se pozadí prolne.
 */
@Composable
fun ChatPreviewBackdrop(preview: ChatPreviewData?) {
    val image = rememberImage(preview?.backdropFileId, BACKDROP_PX)
    val surface = MaterialTheme.colorScheme.surface
    Crossfade(targetState = image, animationSpec = tween(400), label = "chat-backdrop") { shown ->
        if (shown != null) {
            Image(
                shown,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopEnd,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
    Box(
        Modifier.fillMaxSize().background(
            Brush.horizontalGradient(
                0f to surface,
                0.38f to surface,
                0.7f to surface.copy(alpha = 0.6f),
                1f to surface.copy(alpha = 0.2f),
            ),
        ),
    )
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to surface)))
}

/**
 * Text vpravo: kdo to je a co si naposledy napsali. Stojí dole, nad přechodem do plochy, aby
 * obraz nahoře zůstal volný.
 */
@Composable
fun ChatPreview(chat: ChatSummary?, preview: ChatPreviewData?, modifier: Modifier) {
    if (chat == null) return
    val savedMessagesId = savedMessagesChatId()
    val title = chatTitle(chat.id, chat.title, savedMessagesId)
    Column(modifier, verticalArrangement = Arrangement.Bottom) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ChatAvatar(fileId = chat.avatarFileId, title = title, size = 64.dp, savedMessages = chat.id == savedMessagesId)
            Spacer(Modifier.width(20.dp))
            Text(title, style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        val messages = preview?.takeIf { it.chatId == chat.id }?.messages.orEmpty()
        if (messages.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                messages.forEach { PreviewMessage(it, title) }
            }
        }
    }
}

@Composable
private fun PreviewMessage(message: ChatMessage, chatTitle: String) {
    val sender = when {
        message.isOutgoing -> stringResource(Res.string.tv_message_you)
        else -> message.senderName?.takeIf { it != chatTitle }
    }
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (sender != null) {
                Text(sender, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                Spacer(Modifier.width(12.dp))
            }
            Text(
                message.timestamp.asTimeOfDay(),
                style = MaterialTheme.typography.labelMedium.tabular(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            message.previewText(),
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Text zprávy, a když žádný nemá, náš popis obsahu („Fotka", „Video"). */
@Composable
private fun ChatMessage.previewText(): String =
    // Odstavce do jednoho řádku: náhled má na zprávu dva řádky a prázdný řádek by je snědl.
    text.ifEmpty { description?.resolve().orEmpty() }.replace(WHITESPACE, " ").trim()

private val WHITESPACE = Regex("\\s+")

/** Pozadí stačí v polovičním rozlišení 1080p: je pod přechodem a dekódovaná fotka nezabere 8 MB. */
private const val BACKDROP_PX = 540
