package eu.tintera.plume.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.ChatFolder
import eu.tintera.plume.core.telegram.ChatFolderDraft
import eu.tintera.plume.core.telegram.ChatFolderProblem
import eu.tintera.plume.core.telegram.ChatFolderRepository
import eu.tintera.plume.core.telegram.ChatListState
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.core.telegram.ChatSummary
import eu.tintera.plume.core.telegram.problem
import eu.tintera.plume.core.telegram.userMessage
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.features.chats.ChatSearch
import eu.tintera.plume.features.chats.pickerChats
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.chat_folder_delete_failed
import eu.tintera.plume.resources.chat_folder_load_failed
import eu.tintera.plume.resources.chat_folder_save_failed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Výběr chatů v editoru: do složky ([Include]), nebo z ní vynechaných ([Exclude]). */
enum class FolderChatPick { Include, Exclude }

data class ChatFolderEditState(
    /** `0` = nová složka. */
    val folderId: Int = 0,
    val loading: Boolean = true,
    val draft: ChatFolderDraft = ChatFolderDraft(),
    /** Jak složka vypadala při otevření — „Uložit" svítí, jen když se něco změnilo. */
    val original: ChatFolderDraft = ChatFolderDraft(),
    /** Položky pro vybrané a vynechané chaty (jméno, fotka) — doplňují se, jak přibývají. */
    val chats: Map<Long, ChatSummary> = emptyMap(),
    val saving: Boolean = false,
    /** Ukázat chybu konceptu až po pokusu o uložení, ne hned u prázdného formuláře. */
    val showProblem: Boolean = false,
    val error: UiText? = null,
    /** Otevřený výběr chatů a co je v něm zaškrtnuté. */
    val pick: FolderChatPick? = null,
    val picked: Set<Long> = emptySet(),
    val pickQuery: String = "",
    /** Ikona, kterou by složce dal Telegram sám (podle obsahu) — ukazuje se, dokud žádná není vybraná. */
    val defaultIconName: String? = null,
    val iconPickerOpen: Boolean = false,
) {
    /** Co se kreslí: vybraná ikona, jinak ta, kterou vybere Telegram. */
    val shownIconName: String? get() = draft.iconName ?: defaultIconName

    val isNew: Boolean get() = folderId == 0
    val changed: Boolean get() = draft != original
    val problem: ChatFolderProblem? get() = draft.problem()
}

/**
 * Editor jedné složky: název, vybrané chaty a druhy chatů, vynechané chaty.
 *
 * - **Ukládá se tlačítkem, celým objektem** (`editChatFolder`): co editor nenabízí (barva,
 *   sdílení, připnuté chaty), se přenese z načtené složky.
 * - **Bez vybrané ikony vybírá Telegram podle obsahu** — a ukazuje se ta, kterou by vybral
 *   (`getChatFolderDefaultIconName`), přepočítaná s každou změnou konceptu. Zapnutí „Boti" tak
 *   ikonu samo změní na robota, jako to pak uvidí ostatní klienti.
 * - **Výběr chatů je součást obrazovky, ne další routa** — vybírá do konceptu, který žije
 *   tady; přes routu by se výběr musel předávat zpátky.
 */
class ChatFolderEditViewModel(
    folderId: Int,
    private val chats: ChatRepository,
    private val folders: ChatFolderRepository,
) : ViewModel() {

    private val logger = Logger.withTag("ChatFolderEdit")

    private val local = MutableStateFlow(ChatFolderEditState(folderId = folderId))
    private val search = ChatSearch(chats, viewModelScope)

    val state: StateFlow<ChatFolderEditState> = local

    /** Chaty k výběru: bez dotazu hlavní seznam, s dotazem výsledky hledání (i archiv). */
    val pickChats = combine(local, chats.chats(ChatFolder.Main), search.state) { state, list, found ->
        pickerChats(state.pickQuery, list, found)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), pickerChats("", ChatListState.Loading, search.state.value))

    val savedMessagesChatId: StateFlow<Long?> = chats.savedMessagesChatId

    /** Doplní se, když složku server uloží nebo smaže — obrazovka pak odchází. */
    val done = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            // Výchozí ikona závisí na obsahu, ne na vybrané ikoně ani na názvu.
            local.map { it.draft.copy(iconName = null, title = "", titleEntities = emptyList()) }
                .distinctUntilChanged()
                .collectLatest { draft ->
                    folders.defaultIconName(draft).onSuccess { name -> local.update { it.copy(defaultIconName = name) } }
                }
        }
        if (folderId == 0) {
            local.update { it.copy(loading = false) }
        } else {
            viewModelScope.launch {
                folders.folder(folderId)
                    .onSuccess { draft ->
                        local.update { it.copy(loading = false, draft = draft, original = draft) }
                        resolveChats(draft.chosenChatIds + draft.excludedChatIds)
                    }
                    .onFailure { failure ->
                        logger.w(failure) { "Složku $folderId se nepodařilo načíst." }
                        local.update { it.copy(loading = false, error = UiText(Res.string.chat_folder_load_failed)) }
                    }
            }
        }
    }

    private suspend fun resolveChats(ids: List<Long>) {
        val missing = ids.filterNot { it in local.value.chats }
        if (missing.isEmpty()) return
        val summaries = chats.summaries(missing)
        local.update { state -> state.copy(chats = state.chats + summaries.associateBy { it.id }) }
    }

    fun setTitle(text: String) = edit { it.withTitle(text) }

    fun openIconPicker() = local.update { it.copy(iconPickerOpen = true) }
    fun closeIconPicker() = local.update { it.copy(iconPickerOpen = false) }

    /** `null` = nechat na Telegramu. Výběr sheet zavře — jedna volba, nic dalšího se v něm nedělá. */
    fun setIcon(name: String?) {
        local.update { it.copy(draft = it.draft.copy(iconName = name), iconPickerOpen = false) }
    }

    fun setIncludeContacts(value: Boolean) = edit { it.copy(includeContacts = value) }
    fun setIncludeNonContacts(value: Boolean) = edit { it.copy(includeNonContacts = value) }
    fun setIncludeGroups(value: Boolean) = edit { it.copy(includeGroups = value) }
    fun setIncludeChannels(value: Boolean) = edit { it.copy(includeChannels = value) }
    fun setIncludeBots(value: Boolean) = edit { it.copy(includeBots = value) }
    fun setExcludeMuted(value: Boolean) = edit { it.copy(excludeMuted = value) }
    fun setExcludeRead(value: Boolean) = edit { it.copy(excludeRead = value) }
    fun setExcludeArchived(value: Boolean) = edit { it.copy(excludeArchived = value) }

    fun removeChosen(chatId: Long) = edit { draft -> draft.withChosen(draft.chosenChatIds - chatId) }
    fun removeExcluded(chatId: Long) = edit { draft -> draft.withExcluded(draft.excludedChatIds - chatId) }

    private fun edit(change: (ChatFolderDraft) -> ChatFolderDraft) {
        local.update { it.copy(draft = change(it.draft)) }
    }

    fun openPick(pick: FolderChatPick) {
        val draft = local.value.draft
        val picked = if (pick == FolderChatPick.Include) draft.chosenChatIds else draft.excludedChatIds
        local.update { it.copy(pick = pick, picked = picked.toCollection(LinkedHashSet()), pickQuery = "") }
        search.setQuery("")
    }

    fun setPickQuery(query: String) {
        local.update { it.copy(pickQuery = query) }
        search.setQuery(query)
    }

    fun togglePicked(chatId: Long) {
        local.update { state ->
            state.copy(picked = if (chatId in state.picked) state.picked - chatId else state.picked + chatId)
        }
    }

    /** Potvrzený výběr: zaškrtnuté chaty nahradí vybrané (nebo vynechané) a výběr se zavře. */
    fun confirmPick() {
        val state = local.value
        val ids = state.picked.toList()
        val draft = when (state.pick) {
            FolderChatPick.Include -> state.draft.withChosen(ids)
            FolderChatPick.Exclude -> state.draft.withExcluded(ids)
            null -> return
        }
        // Jména zaškrtnutých se berou z toho, co výběr ukazoval; chybějící se dočtou.
        val shown = pickChats.value.chats.filter { it.id in ids }.associateBy { it.id }
        local.update { it.copy(draft = draft, pick = null, chats = it.chats + shown) }
        viewModelScope.launch { resolveChats(ids) }
    }

    fun closePick() {
        local.update { it.copy(pick = null) }
    }

    fun save() {
        val state = local.value
        if (state.saving) return
        if (state.problem != null) {
            local.update { it.copy(showProblem = true) }
            return
        }
        local.update { it.copy(saving = true) }
        viewModelScope.launch {
            val result = if (state.isNew) folders.create(state.draft).map { } else folders.edit(state.folderId, state.draft)
            result
                .onSuccess { done.value = true }
                .onFailure { failure ->
                    logger.w(failure) { "Složku se nepodařilo uložit." }
                    local.update { it.copy(saving = false, error = failure.userMessage(Res.string.chat_folder_save_failed)) }
                }
        }
    }

    fun delete() {
        val id = local.value.folderId.takeIf { it != 0 } ?: return
        local.update { it.copy(saving = true) }
        viewModelScope.launch {
            folders.delete(id)
                .onSuccess { done.value = true }
                .onFailure { failure ->
                    logger.w(failure) { "Složku $id se nepodařilo smazat." }
                    local.update { it.copy(saving = false, error = failure.userMessage(Res.string.chat_folder_delete_failed)) }
                }
        }
    }

    fun consumeError() {
        local.update { it.copy(error = null) }
    }
}
