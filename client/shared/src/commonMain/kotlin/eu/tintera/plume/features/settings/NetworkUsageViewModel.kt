package eu.tintera.plume.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.tintera.plume.core.telegram.NetworkUsage
import eu.tintera.plume.core.telegram.StorageRepository
import eu.tintera.plume.core.telegram.UsageNetwork
import eu.tintera.plume.core.telegram.userMessage
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.network_usage_reset_failed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * @property usage statistika; `null` = načítá se (nebo selhala, viz [loadFailed]).
 * @property selected síť, jejíž rozpis se ukazuje; `null` = ta s nejvíc daty.
 * @property error chyba vynulování — na snackbar, pak [consumeError].
 */
data class NetworkUsageState(
    val usage: NetworkUsage? = null,
    val loadFailed: Boolean = false,
    val selected: UsageNetwork? = null,
    val confirmingReset: Boolean = false,
    val error: UiText? = null,
) {
    /**
     * Síť, která se ukazuje: vybraná, když v ní něco je, jinak ta s nejvíc daty — obvykle ta,
     * na které uživatel je (na emulátoru Wi-Fi s 24 MB proti 193 kB mobilních dat).
     */
    val shown: UsageNetwork?
        get() = usage?.networks?.let { networks ->
            networks.firstOrNull { it.network == selected }?.network
                ?: networks.maxByOrNull { it.sentBytes + it.receivedBytes }?.network
        }
}

/** Přenesená data. Čte se jednou při otevření — statistika se nemění tak rychle, aby stála za živý odběr. */
class NetworkUsageViewModel(private val storage: StorageRepository) : ViewModel() {

    private val _state = MutableStateFlow(NetworkUsageState())
    val state: StateFlow<NetworkUsageState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loadFailed = false) }
        viewModelScope.launch {
            storage.networkUsage()
                .onSuccess { usage -> _state.update { it.copy(usage = usage) } }
                .onFailure { _state.update { it.copy(loadFailed = true) } }
        }
    }

    fun select(network: UsageNetwork) {
        _state.update { it.copy(selected = network) }
    }

    fun requestReset() {
        _state.update { it.copy(confirmingReset = true) }
    }

    fun dismissReset() {
        _state.update { it.copy(confirmingReset = false) }
    }

    /** Po vynulování se statistika čte znovu — začátek počítání je teď, ne odhad. */
    fun confirmReset() {
        _state.update { it.copy(confirmingReset = false) }
        viewModelScope.launch {
            storage.resetNetworkUsage()
                .onSuccess { load() }
                .onFailure { failure -> _state.update { it.copy(error = failure.userMessage(Res.string.network_usage_reset_failed)) } }
        }
    }

    fun consumeError() {
        _state.update { it.copy(error = null) }
    }
}
