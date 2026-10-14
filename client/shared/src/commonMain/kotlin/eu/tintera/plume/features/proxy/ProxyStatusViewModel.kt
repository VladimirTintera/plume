package eu.tintera.plume.features.proxy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.ProxyConfig
import eu.tintera.plume.core.telegram.ProxyRepository
import eu.tintera.plume.core.telegram.SavedProxy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Co ukazuje řádek Proxy v Úložišti. */
sealed interface ProxyStatus {
    /** Žádná proxy není zapnutá. */
    data object Direct : ProxyStatus

    /** Aplikace se připojuje přes [config]. */
    data class Via(val config: ProxyConfig) : ProxyStatus

    /** Seznam se nepodařilo přečíst; řádek pak jen popíše, k čemu proxy je. */
    data object Unknown : ProxyStatus
}

/** Stav podle uložených proxy: zapnutá smí být nejvýš jedna (hlídá TDLib). */
fun proxyStatus(proxies: List<SavedProxy>): ProxyStatus =
    proxies.firstOrNull { it.enabled }?.let { ProxyStatus.Via(it.config) } ?: ProxyStatus.Direct

/**
 * Řádek Proxy v Úložišti. Odezvu NEMĚŘÍ — měření je dotaz přes každou uloženou proxy, a ten
 * patří až na obrazovku proxy, ne ke každému otevření Nastavení.
 *
 * TDLib o změně proxy update nepošle, takže se stav čte znovu při každém návratu na obrazovku
 * ([reload]) — po zapnutí proxy na její obrazovce by jinak řádek tvrdil starý stav.
 */
class ProxyStatusViewModel(private val repository: ProxyRepository) : ViewModel() {

    private val logger = Logger.withTag("Proxy")

    private val _status = MutableStateFlow<ProxyStatus?>(null)

    /** `null`, dokud se nepřečte — řádek do té doby nic netvrdí. */
    val status: StateFlow<ProxyStatus?> = _status.asStateFlow()

    fun reload() {
        viewModelScope.launch {
            _status.value = repository.proxies().fold(
                onSuccess = ::proxyStatus,
                onFailure = { failure ->
                    logger.w(failure) { "Stav proxy se nepřečetl." }
                    ProxyStatus.Unknown
                },
            )
        }
    }
}
