package eu.tintera.plume.features.instantview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.tintera.plume.core.navigation.LinkOpenRequests
import eu.tintera.plume.core.navigation.isTelegramLink
import eu.tintera.plume.core.telegram.InstantViewPage
import eu.tintera.plume.core.telegram.InstantViewRepository
import eu.tintera.plume.core.ui.UrlOpener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * @property page načtená stránka; `null` = načítá se (nebo selhala, viz [failed]).
 * @property close stránka Instant View nemá — otevřela se v prohlížeči a čtečka se má zavřít.
 */
data class InstantViewState(
    val page: InstantViewPage? = null,
    val failed: Boolean = false,
    val close: Boolean = false,
)

/**
 * Čtečka Instant View. Stránku skládá server (`getWebPageInstantView`); kdyby ji přece neměl
 * (náhled to tvrdil, ale stránka se mezitím změnila), otevře se v prohlížeči a čtečka zmizí.
 */
class InstantViewViewModel(
    val url: String,
    private val repository: InstantViewRepository,
    private val urlOpener: UrlOpener,
    private val linkRequests: LinkOpenRequests,
) : ViewModel() {

    private val _state = MutableStateFlow(InstantViewState())
    val state: StateFlow<InstantViewState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(failed = false) }
        viewModelScope.launch {
            repository.page(url)
                .onSuccess { page ->
                    if (page == null) {
                        urlOpener.open(url)
                        _state.update { it.copy(close = true) }
                    } else {
                        _state.update { it.copy(page = page) }
                    }
                }
                .onFailure { _state.update { it.copy(failed = true) } }
        }
    }

    /** Odkaz ve stránce: na Telegram do Plume (jako odkaz zvenčí), jinam do prohlížeče. */
    fun openLink(link: String) {
        if (isTelegramLink(link)) linkRequests.request(link) else urlOpener.open(link)
    }

    fun openInBrowser() = urlOpener.open(url)
}
