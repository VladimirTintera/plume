package eu.tintera.plume.features.settings

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

data class ChatFoldersState(
    val entries: List<FolderEntry> = emptyList(),
    val recommended: List<RecommendedChatFolder> = emptyList(),
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
    chats: ChatRepository,
    private val folders: ChatFolderRepository,
) : ViewModel() {

    private val logger = Logger.withTag("ChatFolders")

    private val serverFolders: StateFlow<ChatFolders> = chats.chatFolders
    private val dragOrder = MutableStateFlow<List<FolderEntry>?>(null)
    private val local = MutableStateFlow(ChatFoldersState())

    val state: StateFlow<ChatFoldersState> = combine(serverFolders, dragOrder, local) { server, drag, base ->
        base.copy(entries = drag ?: server.entries())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatFoldersState())

    init {
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
