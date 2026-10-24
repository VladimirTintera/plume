package eu.tintera.plume.features.webapp

import org.jetbrains.compose.resources.painterResource
import eu.tintera.plume.features.conversation.icon
import eu.tintera.plume.core.ui.RemoteImage
import eu.tintera.plume.core.telegram.InlineResult
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.material3.Icon
import androidx.compose.material3.ShapeDefaults
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.core.telegram.ChatSummary
import eu.tintera.plume.core.telegram.ConversationRepository
import eu.tintera.plume.core.telegram.PreparedMessage
import eu.tintera.plume.core.ui.ChatPicker
import eu.tintera.plume.core.ui.GROUP_CORNER
import eu.tintera.plume.core.ui.GROUP_ITEM_SPACING
import eu.tintera.plume.core.ui.GROUP_SPACING
import eu.tintera.plume.core.ui.PickerSection
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.core.ui.foldDiacritics
import eu.tintera.plume.features.conversation.InlineResultItem
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.bot_switch_inline_empty_text
import eu.tintera.plume.resources.bot_switch_inline_empty_title
import eu.tintera.plume.resources.forum_24dp
import eu.tintera.plume.resources.forward_search
import eu.tintera.plume.resources.send_24dp
import eu.tintera.plume.resources.webapp_share_confirm_count
import eu.tintera.plume.resources.webapp_share_failed
import eu.tintera.plume.resources.webapp_share_info
import eu.tintera.plume.resources.webapp_share_title
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

data class PreparedShareState(
    val query: String = "",
    val loading: Boolean = true,
    val chats: List<ChatSummary> = emptyList(),
    val selected: Set<Long> = emptySet(),
    val sending: Boolean = false,
    val error: UiText? = null,
) {
    /** Hledá se lokálně, bez ohledu na velikost písmen a diakritiku — jako výběr pro inline dotaz. */
    val shown: List<ChatSummary>
        get() {
            val needle = foldDiacritics(query.trim()).lowercase()
            if (needle.isEmpty()) return chats
            return chats.filter { needle in foldDiacritics(it.title).lowercase() }
        }
}

/**
 * Sdílení zprávy, kterou bot připravil mini aplikaci (`shareMessage`). Zpráva odejde jako výsledek
 * inline dotazu („přes @bot") do vybraných chatů.
 *
 * - **Nabízí se jen druh chatu, který bot dovolil, a jen chat, kam se smí psát**
 *   ([ChatRepository.inlineQueryTargets]) — týž výběr jako u tlačítka bota s inline dotazem.
 * - **Cílů smí být víc** (jako přeposílání a oficiální klienti). Povedlo se, když odešla aspoň
 *   do jednoho; když nikam, výběr zůstane otevřený s hláškou a uživatel to zkusí znovu nebo
 *   odejde.
 */
class PreparedShareViewModel(
    private val message: PreparedMessage,
    private val conversations: ConversationRepository,
    chatRepository: ChatRepository,
) : ViewModel() {

    private val logger = Logger.withTag("WebApp")

    private val _state = MutableStateFlow(PreparedShareState())
    val state: StateFlow<PreparedShareState> = _state.asStateFlow()

    val savedMessagesChatId: StateFlow<Long?> = chatRepository.savedMessagesChatId

    init {
        viewModelScope.launch {
            val chats = chatRepository.inlineQueryTargets(message.targets)
            _state.update { it.copy(chats = chats, loading = false) }
        }
    }

    fun onQueryChange(query: String) = _state.update { it.copy(query = query) }

    fun toggle(chatId: Long) = _state.update {
        if (it.sending) it else it.copy(selected = if (chatId in it.selected) it.selected - chatId else it.selected + chatId)
    }

    /** Pošle do vybraných; [onSent] = aspoň jedna odešla, výběr odchází. */
    fun send(onSent: () -> Unit) {
        val targets = _state.value.selected.takeIf { it.isNotEmpty() && !_state.value.sending } ?: return
        _state.update { it.copy(sending = true) }
        viewModelScope.launch {
            val sent = targets.count { chatId ->
                conversations.sendInlineResult(chatId, message.queryId, message.result.id, replyToMessageId = null)
                    .onFailure { logger.w(it) { "Připravenou zprávu nejde poslat do $chatId." } }
                    .isSuccess
            }
            if (sent > 0) {
                onSent()
            } else {
                _state.update { it.copy(sending = false, error = UiText(Res.string.webapp_share_failed)) }
            }
        }
    }

    fun consumeError() = _state.update { it.copy(error = null) }
}

/** Výběr chatů pro připravenou zprávu; nahoře karta s tím, co odejde. */
@Composable
fun PreparedShareScreen(
    viewModel: PreparedShareViewModel,
    message: PreparedMessage,
    bot: String,
    onBack: () -> Unit,
    onSent: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val savedMessagesChatId by viewModel.savedMessagesChatId.collectAsStateWithLifecycle()

    ChatPicker(
        title = stringResource(Res.string.webapp_share_title),
        query = state.query,
        onQueryChange = viewModel::onQueryChange,
        searchPlaceholder = stringResource(Res.string.forward_search),
        loading = state.loading,
        sections = listOf(PickerSection(key = "chats", chats = state.shown)),
        enabled = !state.sending,
        onPick = viewModel::toggle,
        onBack = onBack,
        savedMessagesChatId = savedMessagesChatId.takeIf { message.targets.users },
        selected = state.selected,
        onConfirm = { viewModel.send(onSent) },
        confirmLabel = Res.string.webapp_share_confirm_count,
        confirmIcon = Res.drawable.send_24dp,
        emptyIcon = Res.drawable.forum_24dp,
        emptyTitle = stringResource(Res.string.bot_switch_inline_empty_title).takeIf { state.query.isBlank() },
        emptyText = stringResource(Res.string.bot_switch_inline_empty_text).takeIf { state.query.isBlank() },
        error = state.error,
        onErrorShown = viewModel::consumeError,
        header = { PreparedMessageCard(message, bot) },
        modifier = modifier,
    )
}

/**
 * Co odejde: věta, od koho zpráva je, a náhled výsledku, jak ho kreslí panel inline bota.
 * V roli cizího vloženého obsahu (`tertiaryContainer`), jako karta u sdílení s botem.
 */
@Composable
private fun PreparedMessageCard(message: PreparedMessage, bot: String) {
    Surface(
        shape = RoundedCornerShape(GROUP_CORNER),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier.fillMaxWidth().padding(bottom = GROUP_SPACING - GROUP_ITEM_SPACING),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(Res.string.webapp_share_info, bot), style = MaterialTheme.typography.bodyMedium)
            when (val result = message.result) {
                // Řádek panelu inline bota má barvy plovoucího panelu, na kartě by zmizel.
                is InlineResult.Row -> PreparedRow(result)
                // Obrázkové výsledky jako v panelu; náhled se nevybírá, ťuknutí nic nedělá.
                else -> InlineResultItem(result = result, onPick = {}, onPreview = {})
            }
        }
    }
}

/** Titulek a popis výsledku s náhledem nebo ikonou druhu, v barvách karty. */
@Composable
private fun PreparedRow(result: InlineResult.Row) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier.size(PREVIEW_THUMBNAIL).clip(ShapeDefaults.Medium),
            contentAlignment = Alignment.Center,
        ) {
            val thumbnailFileId = result.thumbnailFileId
            if (thumbnailFileId != null) {
                RemoteImage(fileId = thumbnailFileId, modifier = Modifier.fillMaxSize())
            } else {
                Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f), modifier = Modifier.fillMaxSize()) {
                    Box(contentAlignment = Alignment.Center) { Icon(painterResource(result.kind.icon), contentDescription = null) }
                }
            }
        }
        Column(Modifier.weight(1f)) {
            Text(result.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (result.description.isNotBlank()) {
                Text(result.description, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private val PREVIEW_THUMBNAIL = 40.dp
