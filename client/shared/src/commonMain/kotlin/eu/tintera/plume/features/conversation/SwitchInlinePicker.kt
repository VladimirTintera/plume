package eu.tintera.plume.features.conversation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.core.telegram.ChatSummary
import eu.tintera.plume.core.telegram.SwitchInlineTarget
import eu.tintera.plume.core.ui.ChatPicker
import eu.tintera.plume.core.ui.PickerSearch
import eu.tintera.plume.core.ui.PickerEmpty
import eu.tintera.plume.core.ui.PickerSection
import eu.tintera.plume.core.ui.foldDiacritics
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.bot_switch_inline_empty_text
import eu.tintera.plume.resources.bot_switch_inline_empty_title
import eu.tintera.plume.resources.bot_switch_inline_search
import eu.tintera.plume.resources.bot_switch_inline_title
import eu.tintera.plume.resources.forum_24dp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

data class SwitchInlinePickerState(
    val query: String = "",
    val loading: Boolean = true,
    val chats: List<ChatSummary> = emptyList(),
) {
    /** Hledá se lokálně, bez ohledu na velikost písmen a diakritiku — jako TDLib. */
    val shown: List<ChatSummary>
        get() {
            val needle = foldDiacritics(query.trim()).lowercase()
            if (needle.isEmpty()) return chats
            return chats.filter { needle in foldDiacritics(it.title).lowercase() }
        }
}

/**
 * Výběr chatu pro tlačítko bota s inline dotazem (`switchInline` s `TargetChatChosen`).
 *
 * **Nabízí se jen druh chatu, který bot dovolil, a jen chat, kam se smí psát**
 * ([ChatRepository.inlineQueryTargets]) — dotaz, který by v chatu nešel odeslat, by byl slepá
 * ulička. Uložené zprávy jsou kartou nahoře, když bot dovoluje chaty s lidmi.
 */
class SwitchInlinePickerViewModel(
    val target: SwitchInlineTarget.Chosen,
    chatRepository: ChatRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SwitchInlinePickerState())
    val state: StateFlow<SwitchInlinePickerState> = _state.asStateFlow()

    val savedMessagesChatId: StateFlow<Long?> = chatRepository.savedMessagesChatId

    init {
        viewModelScope.launch {
            val chats = chatRepository.inlineQueryTargets(target)
            _state.update { it.copy(chats = chats, loading = false) }
        }
    }

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query) }
    }
}

/**
 * Tentýž [ChatPicker] jako přeposílání a přidání bota do skupiny. Ťuknutí na chat rovnou
 * otevře konverzaci s `@bot dotaz` v poli — nic se neposílá, výsledek si uživatel vybere sám.
 */
@Composable
fun SwitchInlinePickerScreen(
    viewModel: SwitchInlinePickerViewModel,
    bot: String,
    onBack: () -> Unit,
    onPick: (chatId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val savedMessagesChatId by viewModel.savedMessagesChatId.collectAsStateWithLifecycle()

    ChatPicker(
        title = stringResource(Res.string.bot_switch_inline_title, bot),
        search = PickerSearch(
            query = state.query,
            onQueryChange = viewModel::onQueryChange,
            placeholder = stringResource(Res.string.bot_switch_inline_search),
        ),
        loading = state.loading,
        sections = listOf(PickerSection(key = "chats", chats = state.shown)),
        enabled = true,
        onPick = onPick,
        onBack = onBack,
        savedMessagesChatId = savedMessagesChatId.takeIf { viewModel.target.users },
        empty = PickerEmpty(
            icon = Res.drawable.forum_24dp,
            title = stringResource(Res.string.bot_switch_inline_empty_title).takeIf { state.query.isBlank() },
            text = stringResource(Res.string.bot_switch_inline_empty_text).takeIf { state.query.isBlank() },
        ),
        modifier = modifier,
    )
}
