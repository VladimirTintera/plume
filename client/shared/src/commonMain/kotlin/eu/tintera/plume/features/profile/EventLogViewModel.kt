package eu.tintera.plume.features.profile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.ChatLogCategory
import eu.tintera.plume.core.telegram.ChatLogEvent
import eu.tintera.plume.core.telegram.ChatLogFilter
import eu.tintera.plume.core.telegram.ProfileRepository
import eu.tintera.plume.core.telegram.SenderInfo
import eu.tintera.plume.core.telegram.userMessage
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.event_log_failed
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Poslední akce ve skupině nebo kanálu: první stránka při otevření, další při doscrollování.
 * Záznam je jen za 48 hodin, takže stránek bývá málo; živě se neobnovuje — TDLib na něj update
 * nemá a obrazovka je na chvíli.
 *
 * Hledání a filtry (druh akce, kdo) jdou na server (`getChatEventLog` s `query`, `filters`
 * a `userIds`), ne přes načtené stránky: záznam se stránkuje a filtr nad první stránkou by
 * tvrdil „nic", i když starší akce odpovídá.
 */
class EventLogViewModel(
    private val chatId: Long,
    private val repository: ProfileRepository,
) : ViewModel() {

    private val logger = Logger.withTag("EventLog")

    /** `null` = první stránka se načítá. */
    var events by mutableStateOf<List<ChatLogEvent>?>(null)
        private set
    var loadFailed by mutableStateOf(false)
        private set
    var loadingMore by mutableStateOf(false)
        private set
    private var nextFromEventId: Long? = null

    /** Co ze záznamu ukázat; změna načte záznam znovu od nejnovější akce. */
    var filter by mutableStateOf(ChatLogFilter())
        private set

    /** Vlastník a správci k filtru podle autora; prázdné, dokud se nenačtou (nebo když selžou). */
    var admins by mutableStateOf<List<SenderInfo>>(emptyList())
        private set

    /** Jednorázová hláška pro snackbar; obrazovka ji po zobrazení vynuluje. */
    var notice by mutableStateOf<UiText?>(null)

    private var loadJob: Job? = null

    init {
        reload()
        viewModelScope.launch {
            repository.administrators(chatId)
                .onSuccess { admins = it }
                // Filtr podle autora pak jen nebude co nabídnout; záznam sám funguje dál.
                .onFailure { logger.w(it) { "Správce chatu $chatId se nepodařilo načíst." } }
        }
    }

    /** Hledání: počká, až uživatel dopíše, ať se server neptá na každé písmeno. */
    fun setQuery(query: String) {
        if (query == filter.query) return
        val searchChanged = query.trim() != filter.query.trim()
        filter = filter.copy(query = query)
        if (searchChanged) reload(debounce = true)
    }

    fun toggleCategory(category: ChatLogCategory) {
        filter = filter.copy(categories = filter.categories.toggled(category))
        reload()
    }

    fun toggleAuthor(userId: Long) {
        filter = filter.copy(userIds = filter.userIds.toggled(userId))
        reload()
    }

    fun clearAuthors() {
        if (filter.userIds.isEmpty()) return
        filter = filter.copy(userIds = emptySet())
        reload()
    }

    /**
     * Hledání, druh i autor pryč. Text v poli drží pole samo, takže ho obrazovka založí znovu
     * (viz `EventLogScreen`).
     */
    fun clearFilters() {
        if (!filter.isActive) return
        filter = ChatLogFilter()
        reload()
    }

    fun loadMore() {
        val from = nextFromEventId ?: return
        if (loadingMore || loadJob?.isActive == true) return
        loadingMore = true
        val requested = filter
        loadJob = viewModelScope.launch {
            repository.eventLog(chatId, fromEventId = from, filter = requested)
                .onSuccess { page ->
                    // Událost na hranici stránek by se zdvojila a dva stejné klíče shodí seznam.
                    events = (events.orEmpty() + page.events).distinctBy { it.id }
                    nextFromEventId = page.nextFromEventId
                }
                .onFailure { failure ->
                    nextFromEventId = null
                    notice = failure.reported()
                }
            loadingMore = false
        }
    }

    /**
     * Záznam znovu od začátku. Rozběhnuté načítání (i další stránka) se zruší — jeho výsledek
     * patří k filtru, který už neplatí, a vložil by do seznamu akce, které mu neodpovídají.
     */
    private fun reload(debounce: Boolean = false) {
        loadJob?.cancel()
        loadingMore = false
        nextFromEventId = null
        val requested = filter
        loadJob = viewModelScope.launch {
            if (debounce) delay(SEARCH_DEBOUNCE_MS)
            events = null
            loadFailed = false
            repository.eventLog(chatId, filter = requested)
                .onSuccess { page ->
                    events = page.events
                    nextFromEventId = page.nextFromEventId
                }
                .onFailure { failure ->
                    loadFailed = true
                    notice = failure.reported()
                }
        }
    }

    /** Surová hláška od TDLib patří do logu, ne na obrazovku — viz `userMessage`. */
    private fun Throwable.reported(): UiText {
        logger.w(this) { "Poslední akce chatu $chatId se nepodařilo načíst." }
        return userMessage(Res.string.event_log_failed)
    }
}

/** Přidá, nebo odebere — čip filtru jde zapnout i vypnout týmž ťuknutím. */
internal fun <T> Set<T>.toggled(item: T): Set<T> = if (item in this) this - item else this + item

/** Jak dlouho po posledním písmenu se teprve hledá. */
private const val SEARCH_DEBOUNCE_MS = 400L
