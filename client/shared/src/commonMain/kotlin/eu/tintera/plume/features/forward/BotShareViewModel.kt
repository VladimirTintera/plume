package eu.tintera.plume.features.forward

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.tintera.plume.core.telegram.BotChatRequest
import eu.tintera.plume.core.telegram.BotUsersRequest
import eu.tintera.plume.core.telegram.ChatKind
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.core.telegram.ChatSummary
import eu.tintera.plume.core.telegram.ConversationRepository
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.features.chats.ChatSearch
import eu.tintera.plume.features.chats.pickerChats
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.bot_share_chat_not_allowed
import eu.tintera.plume.resources.bot_share_failed
import eu.tintera.plume.resources.bot_share_user_not_allowed
import eu.tintera.plume.resources.bot_share_users_limit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Koho výběr nabídne. Lidé = soukromé chaty bez Uložených zpráv; chat = skupiny, nebo kanály,
 * podle toho, co bot chce, a s tématy / bez nich, když na tom trvá.
 *
 * **Předem se filtruje jen podle toho, co seznam ví.** Jestli je účet bot nebo prémiový, jestli
 * je uživatel vlastník chatu, jestli má chat veřejné jméno, ví až server — to se ověří při výběru
 * (`onlyCheck`) a nevyhovující výběr se ohlásí. Filtrovat dopředu dotazem na každý chat by byl
 * dotaz za řádek.
 */
internal fun botShareCandidates(
    chats: List<ChatSummary>,
    users: BotUsersRequest?,
    chat: BotChatRequest?,
    savedMessagesChatId: Long?,
): List<ChatSummary> = chats.filter { summary ->
    when {
        users != null -> summary.kind == ChatKind.Private && summary.id != savedMessagesChatId
        chat != null -> summary.kind == (if (chat.channel) ChatKind.Channel else ChatKind.Group) &&
            (chat.forum == null || summary.isForum == chat.forum)
        else -> false
    }
}

/**
 * Výběr pro tlačítko bota „vyber lidi" ([users]) nebo „vyber chat" ([chat]) ve zprávě
 * [messageId] chatu [chatId].
 *
 * - **Lidí víc, chat jeden.** Lidé se zaškrtávají (nejvýš [BotUsersRequest.maxQuantity])
 *   a sdílí tlačítkem pod kartou, která říká, co bot dostane. Chat se po ťuknutí potvrzuje
 *   dialogem — sdílení ho může botovi otevřít (přidat ho, dát mu práva správce).
 * - **Každý výběr se napřed ověří serverem** (`onlyCheck`), ať se nevyhovující účet nebo chat
 *   pozná hned u řádku, ne až po odeslání.
 */
class BotShareViewModel(
    private val chatId: Long,
    private val messageId: Long,
    private val users: BotUsersRequest?,
    private val chat: BotChatRequest?,
    private val repository: ConversationRepository,
    chatRepository: ChatRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val _selected = MutableStateFlow<List<ChatSummary>>(emptyList())
    private val _pendingChat = MutableStateFlow<ChatSummary?>(null)
    private val _busy = MutableStateFlow(false)
    private val _error = MutableStateFlow<UiText?>(null)
    private val search = ChatSearch(chatRepository, viewModelScope)

    val state: StateFlow<BotShareState> = combine(
        combine(chatRepository.chats(), query, search.state) { list, text, found -> pickerChats(text, list, found) },
        chatRepository.savedMessagesChatId,
        query,
        combine(_selected, _pendingChat, _busy, ::Triple),
        _error,
    ) { picker, saved, text, (selected, pending, busy), error ->
        BotShareState(
            query = text,
            loading = picker.loading,
            chats = botShareCandidates(picker.chats, users, chat, saved),
            selected = selected.mapTo(LinkedHashSet()) { it.id },
            pendingChat = pending,
            busy = busy,
            error = error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BotShareState())

    fun onQueryChange(text: String) {
        query.value = text
        search.setQuery(text)
    }

    fun pick(id: Long) {
        if (_busy.value) return
        val summary = state.value.chats.firstOrNull { it.id == id } ?: return
        when {
            users != null -> toggleUser(users, summary)
            chat != null -> check(chat, summary)
        }
    }

    private fun toggleUser(request: BotUsersRequest, summary: ChatSummary) {
        if (_selected.value.any { it.id == summary.id }) {
            _selected.update { list -> list.filterNot { it.id == summary.id } }
            return
        }
        if (_selected.value.size >= request.maxQuantity) {
            _error.value = UiText(Res.plurals.bot_share_users_limit, request.maxQuantity, request.maxQuantity)
            return
        }
        _busy.value = true
        viewModelScope.launch {
            // Soukromý chat má id uživatele.
            repository.shareUsersWithBot(chatId, messageId, request.buttonId, listOf(summary.id), onlyCheck = true)
                .onSuccess { _selected.update { it + summary } }
                .onFailure { _error.value = UiText(Res.string.bot_share_user_not_allowed) }
            _busy.value = false
        }
    }

    private fun check(request: BotChatRequest, summary: ChatSummary) {
        _busy.value = true
        viewModelScope.launch {
            repository.shareChatWithBot(chatId, messageId, request.buttonId, summary.id, onlyCheck = true)
                .onSuccess { _pendingChat.value = summary }
                .onFailure { _error.value = UiText(Res.string.bot_share_chat_not_allowed) }
            _busy.value = false
        }
    }

    fun dismissChat() {
        _pendingChat.value = null
    }

    /** Lidé: sdílí vybrané naráz, chat: potvrzený z dialogu. [onDone] = obrazovka odchází. */
    fun share(onDone: () -> Unit) {
        if (_busy.value) return
        val result: suspend () -> Result<Unit> = when {
            users != null -> {
                val ids = _selected.value.map { it.id }.takeIf { it.isNotEmpty() } ?: return
                suspend { repository.shareUsersWithBot(chatId, messageId, users.buttonId, ids, onlyCheck = false) }
            }
            chat != null -> {
                val target = _pendingChat.value ?: return
                _pendingChat.value = null
                suspend { repository.shareChatWithBot(chatId, messageId, chat.buttonId, target.id, onlyCheck = false) }
            }
            else -> return
        }
        _busy.value = true
        viewModelScope.launch {
            result()
                .onSuccess { onDone() }
                .onFailure {
                    _error.value = UiText(Res.string.bot_share_failed)
                    _busy.value = false
                }
        }
    }

    fun consumeError() {
        _error.value = null
    }
}

data class BotShareState(
    val query: String = "",
    val loading: Boolean = true,
    val chats: List<ChatSummary> = emptyList(),
    /** Vybraní lidé (u chatu se nevybírá, potvrzuje se dialogem). */
    val selected: Set<Long> = emptySet(),
    /** Chat čekající na potvrzení; `null` = dialog zavřený. */
    val pendingChat: ChatSummary? = null,
    /** Ověřuje se nebo sdílí; výběr mezitím nereaguje. */
    val busy: Boolean = false,
    val error: UiText? = null,
)
