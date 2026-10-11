package eu.tintera.plume.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.ChatFolder
import eu.tintera.plume.core.telegram.ChatFolderDraft
import eu.tintera.plume.core.telegram.ChatFolderLink
import eu.tintera.plume.core.telegram.ChatFolderProblem
import eu.tintera.plume.core.telegram.ChatFolderRepository
import eu.tintera.plume.core.telegram.ChatListState
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.core.telegram.ChatSummary
import eu.tintera.plume.core.telegram.NothingToShareException
import eu.tintera.plume.core.telegram.problem
import eu.tintera.plume.core.telegram.userMessage
import eu.tintera.plume.core.files.MediaSharer
import eu.tintera.plume.core.ui.TextClipboard
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.features.chats.ChatSearch
import eu.tintera.plume.features.chats.pickerChats
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.chat_folder_delete_failed
import eu.tintera.plume.resources.chat_folder_load_failed
import eu.tintera.plume.resources.chat_folder_save_failed
import eu.tintera.plume.resources.chat_folder_share_delete_failed
import eu.tintera.plume.resources.chat_folder_share_failed
import eu.tintera.plume.resources.chat_folder_share_save_failed
import eu.tintera.plume.resources.chat_folder_share_nothing
import eu.tintera.plume.resources.invite_links_copied
import eu.tintera.plume.resources.invite_links_created
import eu.tintera.plume.resources.invite_links_share_failed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
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
    /** Vlastní odkazy na sdílenou složku; `null` = nová složka, nebo se ještě načítají. */
    val links: List<ChatFolderLink>? = null,
    /** Vytváří nebo maže se odkaz — tlačítka nejdou zmáčknout podruhé. */
    val linkBusy: Boolean = false,
    val notice: UiText? = null,
    /** Potvrzení smazání; `null` = zavřené. */
    val deleteRequest: FolderDeleteRequest? = null,
    /** Otevřený odkaz (úprava názvu a chatů); `null` = zavřený. */
    val linkEditor: FolderLinkEditor? = null,
) {
    /** Co se kreslí: vybraná ikona, jinak ta, kterou vybere Telegram. */
    val shownIconName: String? get() = draft.iconName ?: defaultIconName

    val isNew: Boolean get() = folderId == 0
    val changed: Boolean get() = draft != original
    val problem: ChatFolderProblem? get() = draft.problem()

    /**
     * Odkaz se dělá z ULOŽENÉ složky — server o rozepsaných změnách neví. S neuloženými změnami
     * se proto vytvořit nedá (obrazovka řekne proč) a sdílet jde jen složka, která sdílená být smí.
     */
    val canCreateLink: Boolean get() = !isNew && !changed && original.canBeShared && !linkBusy
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
    private val clipboard: TextClipboard,
    private val sharer: MediaSharer,
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
                        launch { reloadLinks() }
                        // Sdílenou se složka stane (a přestane být) až tehdy, kdy to potvrdí server;
                        // přijde `UpdateChatFolders`, ne odpověď na vytvoření či smazání odkazu.
                        launch {
                            chats.chatFolders
                                .map { list -> list.folders.firstOrNull { it.id == folderId }?.isShareable }
                                .distinctUntilChanged()
                                .drop(1)
                                .collect { refreshShareable() }
                        }
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

    /**
     * Otevře potvrzení smazání. U přidané sdílené složky se napřed zjistí, ke kterým chatům se
     * uživatel připojil přes její odkaz (`getChatFolderChatsToLeave`) — potvrzení je nabídne
     * k opuštění, jako oficiální klienti. Bez nich je to obyčejné „Smazat složku?".
     */
    fun requestDelete() {
        val id = local.value.folderId.takeIf { it != 0 } ?: return
        local.update { it.copy(deleteRequest = FolderDeleteRequest()) }
        viewModelScope.launch {
            val leave = folders.chatsToLeave(id)
                .onFailure { failure -> logger.w(failure) { "Chaty k opuštění se složkou $id se nepodařilo zjistit." } }
                .getOrDefault(emptyList())
            val summaries = if (leave.isEmpty()) emptyList() else chats.summaries(leave)
            local.update { state ->
                state.deleteRequest?.let {
                    state.copy(deleteRequest = it.copy(chats = summaries, leave = summaries.mapTo(LinkedHashSet()) { chat -> chat.id }))
                } ?: state
            }
        }
    }

    fun toggleLeave(chatId: Long) {
        local.update { state ->
            val request = state.deleteRequest ?: return@update state
            state.copy(deleteRequest = request.copy(leave = if (chatId in request.leave) request.leave - chatId else request.leave + chatId))
        }
    }

    fun cancelDelete() = local.update { it.copy(deleteRequest = null) }

    fun delete() {
        val id = local.value.folderId.takeIf { it != 0 } ?: return
        val leave = local.value.deleteRequest?.leave.orEmpty().toList()
        local.update { it.copy(saving = true, deleteRequest = null) }
        viewModelScope.launch {
            folders.delete(id, leave)
                .onSuccess { done.value = true }
                .onFailure { failure ->
                    logger.w(failure) { "Složku $id se nepodařilo smazat." }
                    local.update { it.copy(saving = false, error = failure.userMessage(Res.string.chat_folder_delete_failed)) }
                }
        }
    }

    private suspend fun reloadLinks() {
        val id = local.value.folderId
        folders.links(id)
            .onSuccess { links -> local.update { it.copy(links = links) } }
            .onFailure { failure ->
                // Bez odkazů jde složka upravovat dál; sekce sdílení se jen neukáže.
                logger.w(failure) { "Odkazy na složku $id se nepodařilo načíst." }
            }
    }

    /**
     * Nový odkaz se rovnou zkopíruje (vytváří se, aby se poslal dál). Složka se jím stane sdílenou,
     * a to editor musí vědět — sdílená složka druhy chatů ani vynechané nenese — proto se čte znovu.
     */
    fun createLink() {
        val state = local.value
        if (!state.canCreateLink) return
        local.update { it.copy(linkBusy = true) }
        viewModelScope.launch {
            folders.createLink(state.folderId)
                .onSuccess { link ->
                    clipboard.copy(link.url)
                    local.update { it.copy(notice = UiText(Res.string.invite_links_created)) }
                    refreshShareable()
                    reloadLinks()
                }
                .onFailure { failure ->
                    logger.w(failure) { "Odkaz na složku ${state.folderId} se nepodařilo vytvořit." }
                    val message = if (failure is NothingToShareException) {
                        UiText(Res.string.chat_folder_share_nothing)
                    } else {
                        failure.userMessage(Res.string.chat_folder_share_failed)
                    }
                    local.update { it.copy(error = message) }
                }
            local.update { it.copy(linkBusy = false) }
        }
    }

    /**
     * Prvním odkazem se složka stane sdílenou, smazáním posledního zase přestane (po smazání se
     * to projeví až s `UpdateChatFolders`, viz `init`) — a sdílená
     * složka nenese druhy chatů ani vynechané, takže to editor musí vědět. V konceptu se mění jen
     * tenhle příznak — rozepsané změny zůstávají.
     */
    private suspend fun refreshShareable() {
        folders.folder(local.value.folderId).onSuccess { draft ->
            local.update { it.copy(draft = it.draft.copy(isShareable = draft.isShareable), original = draft) }
        }
    }

    /** Otevře odkaz: název, chaty, které nabízí, kopírování, sdílení a smazání. */
    fun openLink(link: ChatFolderLink) {
        val id = local.value.folderId
        local.update { it.copy(linkEditor = FolderLinkEditor(link = link, name = link.name, selected = link.chatIds.toSet())) }
        viewModelScope.launch {
            val ids = folders.shareableChats(id)
                .onFailure { failure -> logger.w(failure) { "Chaty pro odkaz na složku $id se nepodařilo zjistit." } }
                .getOrDefault(emptyList())
            // Chat, který odkaz nese, se nabídne i tehdy, když už sdílet nejde — jinak by ho
            // nešlo z odkazu odebrat.
            val summaries = chats.summaries((link.chatIds + ids).distinct())
            local.update { state -> state.copy(linkEditor = state.linkEditor?.copy(chats = summaries)) }
        }
    }

    fun closeLink() = local.update { it.copy(linkEditor = null) }

    fun setLinkName(name: String) = local.update { state ->
        state.copy(linkEditor = state.linkEditor?.copy(name = name.take(LINK_NAME_MAX)))
    }

    fun toggleLinkChat(chatId: Long) = local.update { state ->
        val editor = state.linkEditor?.takeUnless { it.saving } ?: return@update state
        state.copy(linkEditor = editor.copy(selected = if (chatId in editor.selected) editor.selected - chatId else editor.selected + chatId))
    }

    fun saveLink() {
        val editor = local.value.linkEditor?.takeUnless { it.saving || !it.canSave } ?: return
        val id = local.value.folderId
        local.update { it.copy(linkEditor = editor.copy(saving = true, error = null)) }
        viewModelScope.launch {
            val order = editor.chats?.map { it.id }.orEmpty()
            folders.editLink(id, editor.link.url, editor.name, order.filter { it in editor.selected })
                .onSuccess {
                    local.update { state -> state.copy(linkEditor = null) }
                    reloadLinks()
                }
                .onFailure { failure ->
                    logger.w(failure) { "Odkaz na složku $id se nepodařilo upravit." }
                    local.update { state ->
                        state.copy(
                            linkEditor = state.linkEditor?.copy(
                                saving = false,
                                error = failure.userMessage(Res.string.chat_folder_share_save_failed),
                            ),
                        )
                    }
                }
        }
    }

    fun copyLink(link: ChatFolderLink) {
        viewModelScope.launch {
            clipboard.copy(link.url)
            local.update { state ->
                // V otevřeném odkazu potvrdí kopírování ikona — snackbar by zůstal pod sheetem.
                val editor = state.linkEditor?.takeIf { it.link.url == link.url }
                when {
                    editor != null -> state.copy(linkEditor = editor.copy(copied = true))
                    clipboard.confirmsCopy -> state
                    else -> state.copy(notice = UiText(Res.string.invite_links_copied))
                }
            }
        }
    }

    fun shareLink(link: ChatFolderLink) {
        if (!sharer.shareText(link.url)) local.update { it.copy(error = UiText(Res.string.invite_links_share_failed)) }
    }

    /** Smazaný odkaz přestane fungovat; kdo si složku už přidal, o ni nepřijde. */
    fun deleteLink(link: ChatFolderLink) {
        val id = local.value.folderId
        if (local.value.linkBusy) return
        local.update { it.copy(linkBusy = true) }
        viewModelScope.launch {
            folders.deleteLink(id, link.url)
                .onSuccess {
                    local.update { it.copy(linkEditor = null) }
                    // Seznam se čte znovu, ale po smazání POSLEDNÍHO odkazu server chvíli
                    // odpovídá `FILTER_ID_INVALID` — složka přestává být sdílená. Smazaný odkaz
                    // proto zmizí hned, ať čtení dopadne jakkoli.
                    local.update { state -> state.copy(links = state.links?.filterNot { it.url == link.url }) }
                    refreshShareable()
                    reloadLinks()
                }
                .onFailure { failure ->
                    logger.w(failure) { "Odkaz na složku $id se nepodařilo smazat." }
                    val message = failure.userMessage(Res.string.chat_folder_share_delete_failed)
                    local.update { state ->
                        val editor = state.linkEditor
                        if (editor != null) state.copy(linkEditor = editor.copy(error = message)) else state.copy(error = message)
                    }
                }
            local.update { it.copy(linkBusy = false) }
        }
    }

    fun consumeNotice() {
        local.update { it.copy(notice = null) }
    }

    fun consumeError() {
        local.update { it.copy(error = null) }
    }
}

/**
 * Potvrzení smazání složky. [chats] jsou chaty, ke kterým se uživatel připojil přes odkaz
 * složky (`null` = ještě se zjišťuje), [leave] ty z nich, které opustí.
 */
data class FolderDeleteRequest(
    val chats: List<ChatSummary>? = null,
    val leave: Set<Long> = emptySet(),
)

/** Otevřený odkaz na složku: název a chaty, které nabízí ([chats] `null` = načítají se). */
data class FolderLinkEditor(
    val link: ChatFolderLink,
    val name: String,
    val selected: Set<Long>,
    val chats: List<ChatSummary>? = null,
    val saving: Boolean = false,
    /** Adresa je ve schránce — ikona kopírování se změní na fajfku. */
    val copied: Boolean = false,
    /**
     * Chyba uložení. Hlásí ji sheet sám: snackbar obrazovky je pod modálním sheetem schovaný
     * a vypadalo by to, že Uložit nic neudělalo.
     */
    val error: UiText? = null,
) {
    val changed: Boolean get() = name.trim() != link.name || selected != link.chatIds.toSet()

    /** Odkaz bez chatů server odmítne („Folder must contain at least 1 chat"). */
    val canSave: Boolean get() = changed && selected.isNotEmpty() && chats != null
}

/** Nejdelší název odkazu, který server přijme. */
const val LINK_NAME_MAX = 32
