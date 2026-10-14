package eu.tintera.plume.features.proxy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.ProxyConfig
import eu.tintera.plume.core.telegram.ProxyRepository
import eu.tintera.plume.core.telegram.SavedProxy
import eu.tintera.plume.core.telegram.shareLink
import eu.tintera.plume.core.telegram.userMessage
import eu.tintera.plume.core.ui.TextClipboard
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.proxy_action_failed
import eu.tintera.plume.resources.proxy_link_copied
import eu.tintera.plume.resources.proxy_load_failed
import eu.tintera.plume.resources.proxy_save_failed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Co je v editoru otevřené: nová proxy, nebo úprava uložené. */
sealed interface ProxyEditTarget {
    data object New : ProxyEditTarget
    data class Existing(val proxy: SavedProxy) : ProxyEditTarget
}

data class ProxySettingsState(
    /** Uložené proxy v pořadí od TDLib; `null`, dokud se nenačtou. */
    val proxies: List<SavedProxy>? = null,
    /** Odezva podle id proxy; chybějící = ještě se neměřilo. */
    val pings: Map<Int, ProxyPing> = emptyMap(),
    /** Probíhá zapnutí, vypnutí nebo smazání — řádky nejdou ťuknout znovu. */
    val busy: Boolean = false,
    val editing: ProxyEditTarget? = null,
    val saving: Boolean = false,
    /** Proč se proxy z editoru neuložila; ukazuje ji editor, snackbar by byl pod sheetem. */
    val editorError: UiText? = null,
    val pendingRemove: SavedProxy? = null,
    /** Jednorázová hláška pro snackbar. */
    val notice: UiText? = null,
)

/**
 * Nastavení → Úložiště → Proxy. Seznam se po každé akci ČTE ZNOVU: TDLib o změně seznamu update
 * nepošle a co zapnutí jedné proxy udělalo s ostatními, ví jen on.
 */
class ProxySettingsViewModel(
    private val repository: ProxyRepository,
    private val clipboard: TextClipboard,
) : ViewModel() {

    private val logger = Logger.withTag("Proxy")

    private val _state = MutableStateFlow(ProxySettingsState())
    val state: StateFlow<ProxySettingsState> = _state.asStateFlow()

    init {
        reload(measure = true)
    }

    /**
     * Ťuknutí na řádek: zapnutou proxy vypne (připojí se napřímo), jinou zapne. Jen jedna smí
     * být zapnutá — zapnutí vypne tu dosavadní, to řeší TDLib.
     */
    fun toggle(proxy: SavedProxy) = act {
        if (proxy.enabled) repository.disable() else repository.enable(proxy.id)
    }

    fun disable() = act { repository.disable() }

    fun add() = _state.update { it.copy(editing = ProxyEditTarget.New, editorError = null) }

    fun edit(proxy: SavedProxy) = _state.update { it.copy(editing = ProxyEditTarget.Existing(proxy), editorError = null) }

    fun closeEditor() = _state.update { if (it.saving) it else it.copy(editing = null, editorError = null) }

    /**
     * Uloží proxy z editoru. Nová se rovnou zapne — kdo ji přidává, chce se přes ni připojit;
     * upravená si zapnutí nechává, jak bylo.
     */
    fun save(config: ProxyConfig) {
        val target = _state.value.editing ?: return
        if (_state.value.saving) return
        _state.update { it.copy(saving = true, editorError = null) }
        viewModelScope.launch {
            val result = when (target) {
                ProxyEditTarget.New -> repository.add(config, enable = true)
                is ProxyEditTarget.Existing -> repository.edit(target.proxy.id, config, enable = target.proxy.enabled)
            }
            result
                .onSuccess {
                    _state.update { it.copy(saving = false, editing = null) }
                    reload(measure = true)
                }
                .onFailure { failure ->
                    logger.w(failure) { "Proxy se neuložila." }
                    _state.update {
                        it.copy(saving = false, editorError = failure.userMessage(Res.string.proxy_save_failed))
                    }
                }
        }
    }

    fun askRemove(proxy: SavedProxy) = _state.update { it.copy(pendingRemove = proxy) }

    fun dismissRemove() = _state.update { it.copy(pendingRemove = null) }

    fun confirmRemove() {
        val proxy = _state.value.pendingRemove ?: return
        _state.update { it.copy(pendingRemove = null) }
        act { repository.remove(proxy.id) }
    }

    fun copyLink(proxy: SavedProxy) {
        val link = proxy.config.shareLink() ?: return
        viewModelScope.launch {
            clipboard.copy(link)
            if (!clipboard.confirmsCopy) _state.update { it.copy(notice = UiText(Res.string.proxy_link_copied)) }
        }
    }

    /** Odezvu všech proxy změří znovu (tlačítko v liště). */
    fun measure() = reload(measure = true)

    fun noticeShown() = _state.update { it.copy(notice = null) }

    private fun act(action: suspend () -> Result<Unit>) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            action().onFailure { failure ->
                logger.w(failure) { "Akce nad proxy selhala." }
                _state.update { it.copy(notice = failure.userMessage(Res.string.proxy_action_failed)) }
            }
            reload(measure = false)
            _state.update { it.copy(busy = false) }
        }
    }

    /**
     * Načte seznam; s [measure] změří odezvu všech proxy souběžně. Bez něj si změřené hodnoty
     * nechá — zapnutí ani vypnutí na odezvě proxy nic nemění.
     */
    private fun reload(measure: Boolean) {
        viewModelScope.launch {
            repository.proxies()
                .onSuccess { proxies ->
                    _state.update { state ->
                        val pings = if (measure) {
                            proxies.associate { it.id to ProxyPing.Measuring }
                        } else {
                            state.pings.filterKeys { id -> proxies.any { it.id == id } }
                        }
                        state.copy(proxies = proxies, pings = pings)
                    }
                    if (measure) proxies.forEach { proxy -> launch { ping(proxy) } }
                }
                .onFailure { failure ->
                    logger.w(failure) { "Seznam proxy se nenačetl." }
                    _state.update { it.copy(proxies = it.proxies ?: emptyList(), notice = UiText(Res.string.proxy_load_failed)) }
                }
        }
    }

    private suspend fun ping(proxy: SavedProxy) {
        val ping = repository.ping(proxy.config).fold({ ProxyPing.Measured(it) }, { ProxyPing.Failed })
        _state.update { it.copy(pings = it.pings + (proxy.id to ping)) }
    }
}
