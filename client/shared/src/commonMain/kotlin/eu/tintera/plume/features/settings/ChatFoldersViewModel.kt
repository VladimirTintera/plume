package eu.tintera.plume.features.settings

import eu.tintera.plume.core.telegram.CHAT_FOLDER_TITLE_MAX
import kotlinx.coroutines.CancellationException
import org.jetbrains.compose.resources.getString
import eu.tintera.plume.core.telegram.ChatListState
import eu.tintera.plume.core.telegram.ChatFolderDraft
import eu.tintera.plume.core.telegram.ChatFolder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.ChatFolderRepository
import eu.tintera.plume.core.telegram.ChatFolderTab
import eu.tintera.plume.core.telegram.ChatFolders
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.core.telegram.RecommendedChatFolder
import eu.tintera.plume.core.telegram.userMessage
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.chat_folders_failed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Položka seznamu složek: složka Telegramu, nebo „Všechny chaty" ([folder] `null`), které jdou
 * přesunout jen s Premium — bez něj ho server drží na začátku a přesun odmítne.
 */
data class FolderEntry(val folder: ChatFolderTab?) {
    val key: Any get() = folder?.id ?: MAIN_KEY
}

/** Klíč „Všech chatů" v seznamu; `Int` by se srazil s id složky. */
const val MAIN_KEY = "main"

/** Návrh složek modelem v zařízení ([FolderSuggestions]). */
sealed interface AiFolders {
    /** Ještě se nenavrhovalo — nabízí se „Navrhnout složky". */
    data object Idle : AiFolders
    data object Working : AiFolders
    /** Model v zařízení ještě není připravený (stahuje se v Nastavení). */
    data object ModelMissing : AiFolders
    data object Failed : AiFolders
    /** Hotovo; prázdný seznam = nic k navržení. Přidaná složka z něj zmizí. */
    data class Done(val folders: List<SuggestedFolder>) : AiFolders
}

data class ChatFoldersState(
    val entries: List<FolderEntry> = emptyList(),
    val recommended: List<RecommendedChatFolder> = emptyList(),
    /** Nabízet návrh složek modelem? Se zapnutou AI a modelem, který zařízení umí. */
    val aiAvailable: Boolean = false,
    val ai: AiFolders = AiFolders.Idle,
    /** Doporučená složka, která se právě zakládá — tlačítko u ní se točí a nejde ťuknout znovu. */
    val adding: String? = null,
    val error: UiText? = null,
)

/**
 * Seznam složek v Nastavení: pořadí tažením, doporučené složky k přidání jedním ťuknutím.
 *
 * - **Pořadí se drží lokálně jen po dobu tahu** ([dragOrder]) a odchází až po puštění, jedním
 *   voláním. Pak ho přepíše `UpdateChatFolders` od serveru; při selhání se vrátí původní.
 * - **Doporučené se čtou znovu po každé změně složek** — založená zmizí z doporučení, smazaná
 *   se může vrátit. Ptá se server, takže jen při změně, ne při každém složení.
 */
class ChatFoldersViewModel(
    private val chats: ChatRepository,
    private val folders: ChatFolderRepository,
    /** Návrh složek modelem v zařízení; bez něj se blok nenabízí. */
    private val suggestions: FolderSuggestions? = null,
) : ViewModel() {

    private val logger = Logger.withTag("ChatFolders")

    private val serverFolders: StateFlow<ChatFolders> = chats.chatFolders
    private val dragOrder = MutableStateFlow<List<FolderEntry>?>(null)
    private val local = MutableStateFlow(ChatFoldersState())

    val state: StateFlow<ChatFoldersState> = combine(serverFolders, dragOrder, local) { server, drag, base ->
        base.copy(entries = drag ?: server.entries())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatFoldersState())

    init {
        suggestions?.let { suggestions ->
            viewModelScope.launch { local.update { it.copy(aiAvailable = suggestions.available()) } }
        }
        viewModelScope.launch {
            serverFolders.map { it.folders.map { folder -> folder.id } }.distinctUntilChanged().collect {
                folders.recommended()
                    .onSuccess { recommended -> local.update { it.copy(recommended = recommended) } }
                    .onFailure { failure -> logger.w(failure) { "Doporučené složky se nepodařilo načíst." } }
            }
        }
    }

    fun move(from: Any, to: Any) {
        val current = dragOrder.value ?: serverFolders.value.entries()
        val fromIndex = current.indexOfFirst { it.key == from }
        val toIndex = current.indexOfFirst { it.key == to }
        if (fromIndex < 0 || toIndex < 0) return
        dragOrder.value = current.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
    }

    /** Puštěný tah: pořadí odejde serveru, a jen když se opravdu změnilo. */
    fun commitOrder() {
        val order = dragOrder.value ?: return
        if (order == serverFolders.value.entries()) {
            dragOrder.value = null
            return
        }
        viewModelScope.launch {
            folders.reorder(
                ids = order.mapNotNull { it.folder?.id },
                mainPosition = order.indexOfFirst { it.folder == null }.coerceAtLeast(0),
            ).onFailure { failure ->
                logger.w(failure) { "Pořadí složek se nepodařilo uložit." }
                local.update { it.copy(error = failure.userMessage(Res.string.chat_folders_failed)) }
            }
            dragOrder.value = null
        }
    }

    fun addRecommended(recommended: RecommendedChatFolder) {
        if (local.value.adding != null) return
        local.update { it.copy(adding = recommended.draft.title) }
        viewModelScope.launch {
            folders.create(recommended.draft).onFailure { failure ->
                logger.w(failure) { "Doporučenou složku se nepodařilo založit." }
                local.update { it.copy(error = failure.userMessage(Res.string.chat_folders_failed)) }
            }
            local.update { it.copy(adding = null) }
        }
    }

    /**
     * Rozřadí nejnovější chaty modelem. Chaty se berou z hlavního seznamu, jak je teď v paměti —
     * kvůli návrhu se nic nedotahuje; po startu jsou načtené první desítky, a to stačí.
     */
    fun suggestWithAi() {
        val suggestions = suggestions ?: return
        if (local.value.ai == AiFolders.Working) return
        local.update { it.copy(ai = AiFolders.Working) }
        viewModelScope.launch {
            val listed = (chats.chats(ChatFolder.Main).value as? ChatListState.Ready)?.chats.orEmpty()
            val result = try {
                suggestions.suggest(
                    chats = listed,
                    excludedChatIds = setOfNotNull(chats.savedMessagesChatId.value),
                    existingTitles = serverFolders.value.folders.map { it.title }.toSet(),
                    titleOf = { category -> category.title?.let { getString(it) }.orEmpty() },
                )?.let { AiFolders.Done(it) } ?: AiFolders.ModelMissing
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.w(e) { "Návrh složek modelem se nepovedl." }
                AiFolders.Failed
            }
            local.update { it.copy(ai = result) }
        }
    }

    /** Založí navrženou složku s chaty, které do ní model zařadil; z návrhu pak zmizí. */
    fun addSuggested(suggested: SuggestedFolder) {
        if (local.value.adding != null) return
        val key = suggested.addingKey
        local.update { it.copy(adding = key) }
        viewModelScope.launch {
            // Server bere nejvýš 12 znaků; komunitní překlad kategorie by mohl být delší.
            val title = suggested.category.title?.let { getString(it) }.orEmpty().trim().take(CHAT_FOLDER_TITLE_MAX)
            folders.create(ChatFolderDraft(title = title, includedChatIds = suggested.chatIds))
                .onSuccess {
                    local.update { state ->
                        val done = state.ai as? AiFolders.Done ?: return@update state
                        state.copy(ai = done.copy(folders = done.folders - suggested))
                    }
                }
                .onFailure { failure ->
                    logger.w(failure) { "Navrženou složku se nepodařilo založit." }
                    local.update { it.copy(error = failure.userMessage(Res.string.chat_folders_failed)) }
                }
            local.update { it.copy(adding = null) }
        }
    }

    fun consumeError() {
        local.update { it.copy(error = null) }
    }
}

/** Složky a „Všechny chaty" v pořadí, jak je drží server. */
internal fun ChatFolders.entries(): List<FolderEntry> {
    val list = folders.map { FolderEntry(it) }
    val at = mainPosition.coerceIn(0, list.size)
    return list.take(at) + FolderEntry(null) + list.drop(at)
}

/** Klíč navržené složky ve [ChatFoldersState.adding] — nesrazí se s názvem doporučené. */
val SuggestedFolder.addingKey: String get() = "ai-${category.name}"
