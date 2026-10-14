package eu.tintera.plume.features.profile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.ChatLogEvent
import eu.tintera.plume.core.telegram.ProfileRepository
import eu.tintera.plume.core.telegram.userMessage
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.event_log_failed
import kotlinx.coroutines.launch

/**
 * Poslední akce ve skupině nebo kanálu: první stránka při otevření, další při doscrollování.
 * Záznam je jen za 48 hodin, takže stránek bývá málo; živě se neobnovuje — TDLib na něj update
 * nemá a obrazovka je na chvíli.
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

    /** Jednorázová hláška pro snackbar; obrazovka ji po zobrazení vynuluje. */
    var notice by mutableStateOf<UiText?>(null)

    init {
        viewModelScope.launch {
            repository.eventLog(chatId)
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

    fun loadMore() {
        val from = nextFromEventId ?: return
        if (loadingMore) return
        loadingMore = true
        viewModelScope.launch {
            repository.eventLog(chatId, fromEventId = from)
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

    /** Surová hláška od TDLib patří do logu, ne na obrazovku — viz `userMessage`. */
    private fun Throwable.reported(): UiText {
        logger.w(this) { "Poslední akce chatu $chatId se nepodařilo načíst." }
        return userMessage(Res.string.event_log_failed)
    }
}
