package eu.tintera.plume.features.share

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import eu.tintera.plume.core.share.SharedContent
import eu.tintera.plume.core.share.ShareRequests
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.core.telegram.ChatSummary
import eu.tintera.plume.features.chats.ChatSearch
import eu.tintera.plume.features.chats.pickerChats
import eu.tintera.plume.features.forward.ChatPicker
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.share_files
import eu.tintera.plume.resources.share_preparing
import eu.tintera.plume.resources.share_title
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Kam poslat obsah sdílený do aplikace ze systému.
 *
 * Tentýž výběr jako u přeposílání ([ChatPicker]), jen se po výběru nic neposílá: otevře se chat
 * a obsah čeká nad vstupním polem (soubory) a v něm (text). Uživatel k tomu může napsat popisek
 * a vidí, co odejde — odeslat rovnou z výběru by bylo odeslání naslepo.
 */
class ShareViewModel(
    private val requests: ShareRequests,
    chatRepository: ChatRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")

    /** Hledání ve všech chatech, i v archivu — stejně jako u přeposílání. */
    private val search = ChatSearch(chatRepository, viewModelScope)

    val state: StateFlow<ShareState> = combine(
        combine(chatRepository.chats(), query, search.state) { list, text, found -> pickerChats(text, list, found) },
        query,
        requests.incoming,
        chatRepository.savedMessagesChatId,
    ) { picker, text, content, saved ->
        ShareState(
            query = text,
            loading = picker.loading,
            chats = picker.chats,
            content = content,
            savedMessagesChatId = saved,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShareState(content = requests.incoming.value))

    fun onQueryChange(text: String) {
        query.value = text
        search.setQuery(text)
    }

    fun choose(chatId: Long) = requests.choose(chatId)

    fun abandon() = requests.abandon()
}

data class ShareState(
    val query: String = "",
    val loading: Boolean = true,
    val chats: List<ChatSummary> = emptyList(),
    val content: SharedContent? = null,
    val savedMessagesChatId: Long? = null,
)

@Composable
fun ShareScreen(
    viewModel: ShareViewModel,
    /**
     * ✕: sdílení se zahodí (až výběr zmizí, viz `DisposableEffect` níž) a uživatel se vrátí do
     * aplikace, ze které sdílel. Šipka zpět by slibovala návrat do Plume, kde by obsah zmizel.
     */
    onClose: () -> Unit,
    onChosen: (chatId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val content = state.content

    // Obsah zmizel (po zabití procesu se routa obnoví, obsah ne) — výběr nemá co nabízet.
    LaunchedEffect(content == null) { if (content == null) onClose() }

    /*
     * Sdílení se zahodí, když výběr odejde BEZ volby chatu — ať už šipkou, systémovým gestem zpět
     * (to jde přes `NavDisplay`, ne přes tuhle obrazovku), nebo přepnutím sekce. Kdyby se rušilo
     * jen na šipce, zůstalo by po gestu viset a výběr by se po obnově aktivity otevřel znovu.
     */
    DisposableEffect(Unit) { onDispose { viewModel.abandon() } }

    val attachments = content?.attachments
    val subtitle = when {
        content == null -> null
        attachments == null -> stringResource(Res.string.share_preparing)
        attachments.isNotEmpty() -> pluralStringResource(Res.plurals.share_files, attachments.size, attachments.size)
        else -> content.text
    }

    ChatPicker(
        title = stringResource(Res.string.share_title),
        query = state.query,
        onQueryChange = viewModel::onQueryChange,
        loading = state.loading,
        chats = state.chats,
        enabled = content != null,
        onPick = { chatId ->
            viewModel.choose(chatId)
            onChosen(chatId)
        },
        onBack = onClose,
        closeIcon = true,
        subtitle = subtitle,
        savedMessagesChatId = state.savedMessagesChatId,
        modifier = modifier,
    )
}
