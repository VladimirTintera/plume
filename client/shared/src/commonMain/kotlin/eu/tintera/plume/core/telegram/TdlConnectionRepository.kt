package eu.tintera.plume.core.telegram

import co.touchlab.kermit.Logger
import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.dto.ConnectionStateConnecting
import dev.g000sha256.tdl.dto.ConnectionStateConnectingToProxy
import dev.g000sha256.tdl.dto.ConnectionStateReady
import dev.g000sha256.tdl.dto.ConnectionStateUpdating
import dev.g000sha256.tdl.dto.ConnectionStateWaitingForNetwork
import dev.g000sha256.tdl.dto.NetworkTypeMobile
import dev.g000sha256.tdl.dto.NetworkTypeMobileRoaming
import dev.g000sha256.tdl.dto.NetworkTypeNone
import dev.g000sha256.tdl.dto.NetworkTypeOther
import dev.g000sha256.tdl.dto.NetworkTypeWiFi
import dev.g000sha256.tdl.dto.OptionValueBoolean
import dev.g000sha256.tdl.toResult
import eu.tintera.plume.core.connectivity.NetworkKind
import eu.tintera.plume.core.connectivity.NetworkStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import dev.g000sha256.tdl.dto.ConnectionState as TdlConnectionState

/** Implementace [ConnectionRepository] nad TDLib. */
class TdlConnectionRepository(
    private val holder: TelegramClientHolder,
    scope: CoroutineScope,
) : ConnectionRepository {

    private val logger = Logger.withTag("Telegram")

    private val client: TdlClient get() = holder.client

    /*
     * Výchozí stav je „připojuje se", ne „připraveno". Než dorazí první update, spojení opravdu
     * nestojí — a kdyby se začínalo na Ready, problikla by při startu hlavička s falešnou
     * jistotou.
     */
    private val _state = MutableStateFlow(ConnectionState.Connecting)

    override val state: StateFlow<ConnectionState> = _state.asStateFlow()

    init {
        // Odběr přes holder, ne přes zachyceného klienta: po odhlášení vzniká nová instance
        // a `flatMapLatest` se na ni přepne sám.
        scope.launch {
            holder.updates { connectionStateUpdates }.collect { update ->
                val state = update.state.toDomain()
                logger.d { "ConnectionState -> $state" }
                _state.value = state
            }
        }
    }

    override suspend fun networkChanged(network: NetworkStatus) {
        client.setNetworkType(type = network.kind.toTdl())
            .toResult()
            .onSuccess { logger.i { "Síť ohlášena: ${network.kind}, spojení se otevírají znovu." } }
            .onFailure { logger.w(it) { "setNetworkType selhalo." } }
    }

    override suspend fun setOnline(online: Boolean) {
        client.setOption(name = "online", value = OptionValueBoolean(value = online))
            .toResult()
            .onFailure { logger.w(it) { "Nepodařilo se nastavit volbu online." } }
    }
}

/**
 * `when` bez `else` schválně: novější TDLib s dalším stavem tím rozbije překlad, místo aby se
 * tiše hlásilo něco nepravdivého.
 */
private fun TdlConnectionState.toDomain(): ConnectionState = when (this) {
    is ConnectionStateWaitingForNetwork -> ConnectionState.WaitingForNetwork
    is ConnectionStateConnectingToProxy -> ConnectionState.ConnectingToProxy
    is ConnectionStateConnecting -> ConnectionState.Connecting
    is ConnectionStateUpdating -> ConnectionState.Updating
    is ConnectionStateReady -> ConnectionState.Ready
}

private fun NetworkKind.toTdl() = when (this) {
    NetworkKind.None -> NetworkTypeNone()
    NetworkKind.WiFi -> NetworkTypeWiFi()
    NetworkKind.Mobile -> NetworkTypeMobile()
    NetworkKind.MobileRoaming -> NetworkTypeMobileRoaming()
    NetworkKind.Other -> NetworkTypeOther()
}
