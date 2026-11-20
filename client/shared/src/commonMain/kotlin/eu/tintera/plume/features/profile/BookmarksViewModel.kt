package eu.tintera.plume.features.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.tintera.plume.core.bookmarks.MessageBookmark
import eu.tintera.plume.core.bookmarks.MessageBookmarks
import eu.tintera.plume.core.telegram.ChatRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn

/**
 * Záložky zpráv ([eu.tintera.plume.core.navigation.BookmarksRoute]): ze všech chatů ([chatId]
 * `null`, menu účtu), nebo jednoho chatu (profil). Vlastní ViewModel, ne ten seznamu nebo
 * profilu: sheet je navigační destinace a má vlastní úložiště ViewModelů.
 */
class BookmarksViewModel(
    private val chatId: Long?,
    private val store: MessageBookmarks,
    private val repository: ChatRepository,
) : ViewModel() {

    /** Nejnověji přidané první; `null`, dokud se nenačtou (prázdný seznam = sheet se zavře). */
    val bookmarks: StateFlow<List<MessageBookmark>?> = store.all
        .map { list -> if (chatId == null) list else list.filter { it.chatId == chatId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Názvy chatů u záložek, jen v přehledu přes všechny chaty (`null` = záložky jednoho chatu).
     * Dotahují se zvlášť (`summaries`, lokální): chat se záložkou nemusí být v načteném seznamu.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val chatTitles: StateFlow<Map<Long, String>?> = if (chatId != null) {
        MutableStateFlow(null)
    } else {
        store.all
            .map { list -> list.map { it.chatId }.distinct() }
            .distinctUntilChanged()
            .mapLatest { ids -> repository.summaries(ids).associate { it.id to it.title } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
    }

    fun remove(bookmark: MessageBookmark) = store.remove(bookmark.chatId, bookmark.messageId)
}
