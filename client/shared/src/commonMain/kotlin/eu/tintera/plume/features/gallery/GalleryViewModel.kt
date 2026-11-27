package eu.tintera.plume.features.gallery

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.ConversationRepository
import eu.tintera.plume.core.telegram.GalleryItem
import eu.tintera.plume.core.telegram.userMessage
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.gallery_failed
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Načtené fotky a videa galerie a stav stránkování. */
@Immutable
data class GalleryLoad(
    val items: List<GalleryItem> = emptyList(),
    val loading: Boolean = true,
    val reachedEnd: Boolean = false,
    /** První stránka selhala — obrazovka ukáže chybu se „Zkusit znovu". */
    val failure: UiText? = null,
    /** Další stránka selhala; zkusí se znovu, až seznam znovu dojede ke konci. */
    val moreFailed: Boolean = false,
)

/**
 * **Galerie přes všechny chaty**: fotky a videa od nejnovějších, ze všech chatů najednou
 * ([ConversationRepository.galleryMedia]). Stránky se dotahují, jak mřížka dojíždí ke konci.
 *
 * - **Zamčené chaty se nevrací** ([lockedChats]) — jako hledání fotek podle obsahu. Filtruje se
 *   při zobrazení, ne při načtení: zamknutí chatu se tak projeví hned, bez nového načtení.
 * - **Stránka smí po filtru vyjít prázdná** (samá mizející média); konec je jen prázdný
 *   `nextOffset`. Prázdná stránka proto rovnou dotáhne další.
 */
class GalleryViewModel(
    private val repository: ConversationRepository,
    /** Zamčené chaty; `null` = čtou se (galerie pak nic neukáže, aby zamčené neprobleskly). */
    lockedChats: Flow<Set<Long>?> = flowOf(emptySet()),
) : ViewModel() {

    private val logger = Logger.withTag("Gallery")

    private val load = MutableStateFlow(GalleryLoad())

    val state: StateFlow<GalleryLoad> = combine(load, lockedChats) { current, locked ->
        if (locked == null) {
            current.copy(items = emptyList(), loading = true)
        } else {
            current.copy(items = current.items.filterNot { it.chatId in locked })
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, GalleryLoad())

    private var nextOffset: String = ""
    private var job: Job? = null

    init {
        loadMore()
    }

    /** Další stránka; volá ji mřížka, když dojíždí ke konci. */
    fun loadMore() {
        val current = load.value
        if (job?.isActive == true || current.reachedEnd || current.failure != null) return
        job = viewModelScope.launch {
            load.update { it.copy(loading = true, moreFailed = false) }
            // Prázdná stránka po filtru (mizející média) neznamená konec — zkusí se další.
            repeat(MAX_EMPTY_PAGES) {
                val page = repository.galleryMedia(nextOffset).getOrElse { error ->
                    logger.w(error) { "Galerii se nepodařilo načíst." }
                    load.update {
                        if (it.items.isEmpty()) {
                            it.copy(loading = false, failure = error.userMessage(Res.string.gallery_failed))
                        } else {
                            it.copy(loading = false, moreFailed = true)
                        }
                    }
                    return@launch
                }
                val next = page.nextOffset
                nextOffset = next.orEmpty()
                load.update { current ->
                    // Táž zpráva na hranici stránek by se ukázala dvakrát.
                    val known = current.items.mapTo(HashSet()) { it.chatId to it.message.id }
                    current.copy(
                        items = current.items + page.items.filter { (it.chatId to it.message.id) !in known },
                        reachedEnd = next == null,
                    )
                }
                if (next == null || page.items.isNotEmpty()) {
                    load.update { it.copy(loading = false) }
                    return@launch
                }
            }
            load.update { it.copy(loading = false) }
        }
    }

    /** „Zkusit znovu" po chybě první stránky. */
    fun retry() {
        job?.cancel()
        nextOffset = ""
        load.value = GalleryLoad()
        loadMore()
    }
}

/** Kolik prázdných stránek po sobě se zkusí, než se čeká na další dojetí mřížky. */
private const val MAX_EMPTY_PAGES = 3
