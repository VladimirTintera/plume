package eu.tintera.plume.features.chats

import eu.tintera.plume.resources.smart_toy_24dp
import eu.tintera.plume.core.ui.ConfirmDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.core.telegram.ChatSummary
import eu.tintera.plume.core.telegram.ConversationRepository
import eu.tintera.plume.core.telegram.userMessage
import eu.tintera.plume.core.ui.ChatPicker
import eu.tintera.plume.core.ui.PickerSection
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.core.ui.foldDiacritics
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.bot_add_group_confirm
import eu.tintera.plume.resources.bot_add_group_confirm_text
import eu.tintera.plume.resources.bot_add_group_confirm_text_admin
import eu.tintera.plume.resources.bot_add_group_confirm_title
import eu.tintera.plume.resources.bot_add_group_empty_text
import eu.tintera.plume.resources.bot_add_group_empty_text_admin
import eu.tintera.plume.resources.bot_add_group_empty_title
import eu.tintera.plume.resources.bot_add_group_failed
import eu.tintera.plume.resources.bot_add_group_search
import eu.tintera.plume.resources.bot_add_group_title
import eu.tintera.plume.resources.group_24dp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

data class AddBotToGroupState(
    val query: String = "",
    val loading: Boolean = true,
    /** Skupiny, kam smí uživatel bota přidat ([ChatRepository.groupsForBot]). */
    val groups: List<ChatSummary> = emptyList(),
    /** Jméno bota do potvrzení; `null`, dokud se nenačte. */
    val botName: String? = null,
    /** Vybraná skupina čekající na potvrzení. */
    val confirming: ChatSummary? = null,
    val adding: Boolean = false,
    val error: UiText? = null,
) {
    /** Hledá se lokálně, bez ohledu na velikost písmen a diakritiku — jako TDLib. */
    val shown: List<ChatSummary>
        get() {
            val needle = foldDiacritics(query.trim()).lowercase()
            if (needle.isEmpty()) return groups
            return groups.filter { needle in foldDiacritics(it.title).lowercase() }
        }
}

/**
 * Přidání bota do skupiny ze startovacího odkazu (`t.me/bot?startgroup=…`).
 *
 * **Nabízí se jen skupina, kam bota přidat JDE** ([eu.tintera.plume.core.telegram.canAddBot]):
 * výběr, ve kterém by většina skupin skončila chybou od serveru, by byl horší než kratší seznam.
 * Přidání se potvrzuje — bot tím dostane přístup ke zprávám skupiny, a když o to v odkazu
 * řekl, i práva správce.
 */
class AddBotToGroupViewModel(
    private val url: String,
    botChatId: Long,
    val asAdmin: Boolean,
    private val chatRepository: ChatRepository,
    conversationRepository: ConversationRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AddBotToGroupState())
    val state: StateFlow<AddBotToGroupState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val name = conversationRepository.chatInfo(botChatId).getOrNull()?.title
            _state.update { it.copy(botName = name) }
        }
        viewModelScope.launch {
            val groups = chatRepository.groupsForBot(asAdmin)
            _state.update { it.copy(groups = groups, loading = false) }
        }
    }

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query) }
    }

    fun pick(chatId: Long) {
        val group = _state.value.groups.firstOrNull { it.id == chatId } ?: return
        _state.update { it.copy(confirming = group) }
    }

    fun dismissConfirm() {
        _state.update { it.copy(confirming = null) }
    }

    fun add(onAdded: (groupChatId: Long) -> Unit) {
        val group = _state.value.confirming ?: return
        if (_state.value.adding) return
        _state.update { it.copy(adding = true, confirming = null) }
        viewModelScope.launch {
            chatRepository.addBotToGroup(url, group.id)
                .onSuccess {
                    _state.update { it.copy(adding = false) }
                    onAdded(group.id)
                }
                .onFailure { failure ->
                    _state.update { it.copy(adding = false, error = failure.userMessage(Res.string.bot_add_group_failed)) }
                }
        }
    }

    fun consumeError() {
        _state.update { it.copy(error = null) }
    }
}

/**
 * Výběr skupiny pro bota — tentýž [ChatPicker] jako přeposílání a nová konverzace. Po přidání
 * se otevře skupina, kde bot právě odpověděl na `/start`.
 */
@Composable
fun AddBotToGroupScreen(
    viewModel: AddBotToGroupViewModel,
    onBack: () -> Unit,
    onAdded: (groupChatId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ChatPicker(
        title = stringResource(Res.string.bot_add_group_title),
        query = state.query,
        onQueryChange = viewModel::onQueryChange,
        searchPlaceholder = stringResource(Res.string.bot_add_group_search),
        loading = state.loading,
        sections = listOf(PickerSection(key = "groups", chats = state.shown)),
        // Dokud přidávání běží, další ťuknutí by přidávalo podruhé.
        enabled = !state.adding,
        onPick = viewModel::pick,
        onBack = onBack,
        emptyIcon = Res.drawable.group_24dp,
        emptyTitle = stringResource(Res.string.bot_add_group_empty_title).takeIf { state.query.isBlank() },
        emptyText = stringResource(
            if (viewModel.asAdmin) Res.string.bot_add_group_empty_text_admin else Res.string.bot_add_group_empty_text,
        ).takeIf { state.query.isBlank() },
        error = state.error,
        onErrorShown = viewModel::consumeError,
        modifier = modifier,
    )

    state.confirming?.let { group ->
        ConfirmDialog(
            title = stringResource(Res.string.bot_add_group_confirm_title, state.botName.orEmpty(), group.title),
            text = stringResource(
                if (viewModel.asAdmin) Res.string.bot_add_group_confirm_text_admin else Res.string.bot_add_group_confirm_text,
            ),
            confirmLabel = stringResource(Res.string.bot_add_group_confirm),
            onConfirm = { viewModel.add(onAdded) },
            onDismiss = viewModel::dismissConfirm,
            icon = Res.drawable.smart_toy_24dp,
        )
    }
}
