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
class ChatPreviewData(
    val chatId: Long,
    val messages: List<ChatMessage>,
    val backdropFileId: Int?,
    /** Nejnovější zpráva v načtené historii (i servisní, kterou náhled nekreslí). */
    val newestMessageId: Long?,
)

/** Kolik posledních zpráv se v náhledu ukáže. */
private const val PREVIEW_MESSAGES = 4

/** Kolik zpráv se projde při hledání média na pozadí. */
private const val PREVIEW_HISTORY = 20

/** Nejvýš kolikrát se historie pro náhled dočítá, než je [PREVIEW_HISTORY] zpráv pohromadě. */
private const val PREVIEW_PAGES = 3

/**
 * Prodleva před načtením náhledu: při rychlém projíždění seznamu šipkou by se jinak historie
 * četla pro každý řádek, přes který fokus jen přejel.
 */
private const val PREVIEW_DELAY_MS = 300L

/**
 * Náhled chatu [chatId]: posledních [PREVIEW_HISTORY] zpráv z historie TDLib. `getChatHistory`
 * chat neotevírá ani nehlásí přečtení, takže projíždění seznamu nikomu neodbaví „přečteno".
 * Načtené náhledy zůstávají v paměti obrazovky, návrat na chat ukáže hned to, co už bylo.
 *
 * **Nová poslední zpráva ([lastMessageId]) náhled načte znovu, a to bez prodlevy.** Dřív byl
 * klíčem jen chat: náhled zaostřeného chatu zůstal u staré zprávy, i když řádek vedle už ukazoval
 * novou (ověřeno na emulátoru se zprávou od bota). Prodleva je proti čtení historie každého řádku,
 * přes který šipka jen přejede; u téhož chatu by jen držela starý náhled z mezipaměti.
 */
@Composable
fun rememberChatPreview(chatId: Long?, lastMessageId: Long?): ChatPreviewData? {
    val conversations = koinInject<ConversationRepository>()
    val cache = remember { HashMap<Long, ChatPreviewData>() }
    val preview by produceState<ChatPreviewData?>(initialValue = null, chatId, lastMessageId) {
        if (chatId == null) {
            value = null
            return@produceState
        }
        val cached = cache[chatId]
        value = cached
        val stale = cached != null && lastMessageId != null && cached.newestMessageId != lastMessageId
        if (!stale) delay(PREVIEW_DELAY_MS)
        // `getChatHistory` vrací často jen to, co má TDLib v lokální databázi — klidně jedinou
        // zprávu, a náhled pak ukázal jen ji. Dočítá se proto po stránkách (jako historie
        // v konverzaci, `ConversationState.loadHistory`), a co je načtené, ukáže se hned: první
        // stránka je z databáze, další můžou čekat na server.
        val history = mutableListOf<ChatMessage>()
        var from = 0L
        repeat(PREVIEW_PAGES) {
            if (history.size >= PREVIEW_HISTORY) return@produceState
            val page = conversations.history(chatId, fromMessageId = from, limit = PREVIEW_HISTORY - history.size).getOrNull()
                ?.filterNot { message -> history.any { it.id == message.id } }
            if (page.isNullOrEmpty()) return@produceState
            history += page
            from = history.minOf { it.id }
            val next = previewOf(chatId, history)
            // Mezivýsledek nesmí přebít víc zpráv z mezipaměti (návrat na chat by bliknul z pěti
            // zpráv na jednu a zpátky); novější zpráva ano.
            val shown = value
            if (shown == null || shown.newestMessageId != next.newestMessageId || shown.messages.size <= next.messages.size) {
                cache[chatId] = next
                value = next
            }
        }
    }
    return preview
}

/** Náhled z načtené historie: poslední zprávy s textem a médium z nich na pozadí. */
private fun previewOf(chatId: Long, history: List<ChatMessage>): ChatPreviewData {
    val newestFirst = history.sortedByDescending { it.id }
    // Spoiler, citlivé a mizející médium na pozadí nepatří: odkrývá se vědomě u zprávy, ne tím,
    // že fokus přejede přes chat. Pozadí pak dá starší médium, které skryté není.
    val backdrop = newestFirst.firstNotNullOfOrNull { message ->
        when (val media = message.media?.takeIf { it.showsUncovered && !message.isSelfDestruct }) {
            is MessageMedia.Photo -> media.fileId
            is MessageMedia.Video -> media.thumbnailFileId
            else -> null
        }
    }
    val shown = newestFirst.filter { !it.isService && (it.text.isNotBlank() || it.description != null) }.take(PREVIEW_MESSAGES).reversed()
    return ChatPreviewData(chatId, shown, backdrop, newestMessageId = newestFirst.firstOrNull()?.id)
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
            ChatAvatar(fileId = chat.avatarFileId, title = title, size = 64.dp, savedMessages = chat.id == savedMessagesId, shape = chat.kind.avatarShape())
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
    // Spoiler zamaskovaný: náhled kreslí holý text a odkrýt ho tu nejde.
    maskSpoilers(text, entities).ifEmpty { description?.resolve().orEmpty() }.replace(WHITESPACE, " ").trim()

private val WHITESPACE = Regex("\\s+")

/** Pozadí stačí v polovičním rozlišení 1080p: je pod přechodem a dekódovaná fotka nezabere 8 MB. */
private const val BACKDROP_PX = 540
