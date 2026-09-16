package eu.tintera.plume.features.media

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.ConversationRepository
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.telegram.PhotoPosition
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Fotky chatu přes celou obrazovku, s listováním.
 *
 * Otevírá se na jedné zprávě ([messageId]) a kolem ní se dotahují další fotky chatu — nejdřív
 * kus na obě strany, pak po stránkách, jak se uživatel blíží k okraji načteného výřezu.
 *
 * Zprávy se **načítají znovu podle id**, nepředávají se z konverzace. Routa musí být
 * serializovatelná (jinak by nepřežila zabití procesu) a celé zprávy do navigačního zásobníku
 * nepatří. Proti stahování fotek je dotaz navíc zanedbatelný.
 */
class MediaViewerViewModel(
    private val chatId: Long,
    private val messageId: Long,
    private val repository: ConversationRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(MediaViewerState(initialMessageId = messageId))
    val state: StateFlow<MediaViewerState> = _state.asStateFlow()

    /** Probíhající dotahování. Stránkuje se z `onPageChanged`, které chodí při každém posunu. */
    private var loadingOlder = false
    private var loadingNewer = false

    init {
        viewModelScope.launch { loadInitial() }
        viewModelScope.launch {
            repository.photoPosition(chatId, messageId)
                .onSuccess { position -> _state.update { it.copy(initialPosition = position) } }
        }
    }

    private suspend fun loadInitial() {
        repository.photos(chatId, messageId, older = PAGE_SIZE, newer = PAGE_SIZE)
            .onSuccess { messages ->
                val photos = messages.mapNotNull { it.toViewerPhoto() }
                if (photos.none { it.messageId == messageId }) {
                    // Otevřená zpráva mezi fotkami není — jiný typ média, nebo ji vyhledávání
                    // ještě nezná. Pak se aspoň zkusí ukázat samotná.
                    loadSingle()
                    return
                }
                _state.update { it.copy(photos = photos, loading = false) }
            }
            .onFailure { loadSingle() }
    }

    /** Záloha bez listování: jen otevřená zpráva, stejně jako před zavedením listování. */
    private suspend fun loadSingle() {
        repository.message(chatId, messageId)
            .onSuccess { message ->
                val photo = message.toViewerPhoto()
                _state.update {
                    it.copy(
                        photos = listOfNotNull(photo),
                        reachedOldest = true,
                        reachedNewest = true,
                        loading = false,
                        // Jiný typ média zatím neumíme; prázdný stav by vypadal jako chyba
                        // načítání, tak se to řekne rovnou.
                        error = if (photo == null) "Tenhle typ přílohy zatím neumíme otevřít." else null,
                    )
                }
            }
            .onFailure { failure ->
                _state.update {
                    it.copy(loading = false, error = failure.message ?: "Fotku se nepodařilo načíst.")
                }
            }
    }

    /** Pager se posunul. U okraje načteného výřezu se dotáhne další stránka. */
    fun onPageChanged(index: Int) {
        val photos = _state.value.photos
        if (photos.isEmpty()) return
        if (index <= PRELOAD_DISTANCE) loadOlder()
        if (index >= photos.lastIndex - PRELOAD_DISTANCE) loadNewer()
    }

    private fun loadOlder() {
        val current = _state.value
        if (loadingOlder || current.reachedOldest) return
        val oldest = current.photos.firstOrNull() ?: return
        loadingOlder = true

        viewModelScope.launch {
            repository.photos(chatId, oldest.messageId, older = PAGE_SIZE, newer = 0)
                .onSuccess { messages ->
                    // Výsledek obsahuje i výchozí zprávu, ta se zahodí.
                    val older = messages.filter { it.id < oldest.messageId }.mapNotNull { it.toViewerPhoto() }
                    _state.update {
                        it.copy(
                            photos = older + it.photos,
                            // Konec se pozná jen podle prázdné odpovědi, příznak TDLib nedává.
                            reachedOldest = older.isEmpty(),
                        )
                    }
                }
            loadingOlder = false
        }
    }

    private fun loadNewer() {
        val current = _state.value
        if (loadingNewer || current.reachedNewest) return
        val newest = current.photos.lastOrNull() ?: return
        loadingNewer = true

        viewModelScope.launch {
            repository.photos(chatId, newest.messageId, older = 0, newer = PAGE_SIZE)
                .onSuccess { messages ->
                    val newer = messages.filter { it.id > newest.messageId }.mapNotNull { it.toViewerPhoto() }
                    _state.update {
                        it.copy(
                            photos = it.photos + newer,
                            reachedNewest = newer.isEmpty(),
                        )
                    }
                }
            loadingNewer = false
        }
    }

    private fun ChatMessage.toViewerPhoto(): ViewerPhoto? {
        val photo = media as? MessageMedia.Photo ?: return null
        return ViewerPhoto(messageId = id, photo = photo, caption = text)
    }

    private companion object {
        /** Kolik fotek se dotahuje najednou na každou stranu. */
        const val PAGE_SIZE = 20

        /** Jak blízko okraje načteného výřezu se začne dotahovat další stránka. */
        const val PRELOAD_DISTANCE = 3
    }
}

data class ViewerPhoto(
    val messageId: Long,
    val photo: MessageMedia.Photo,
    /** Popisek pod fotkou. Prázdný u fotky bez textu. */
    val caption: String,
)

data class MediaViewerState(
    /** Zpráva, na které se prohlížeč otevřel. Pager na ní startuje. */
    val initialMessageId: Long,
    /** Načtený výřez fotek chatu, od nejstarší. Pager jde zleva doprava k novějším. */
    val photos: List<ViewerPhoto> = emptyList(),
    /**
     * Pořadí otevřené fotky v chatu. Počítadlo u ostatních stránek se od něj odvozuje posunem
     * indexu — výřez je souvislý, takže to sedí.
     */
    val initialPosition: PhotoPosition? = null,
    val reachedOldest: Boolean = false,
    val reachedNewest: Boolean = false,
    val loading: Boolean = true,
    val error: String? = null,
)
