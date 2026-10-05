package eu.tintera.plume.features.proxy

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.ProxyConfig
import eu.tintera.plume.core.telegram.ProxyRepository
import eu.tintera.plume.core.telegram.userMessage
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.core.ui.resolveNow
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.proxy_connect_failed
import eu.tintera.plume.resources.proxy_connected
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Proxy z odkazu, která čeká na potvrzení. */
data class ProxyLinkState(
    val config: ProxyConfig,
    val ping: ProxyPing = ProxyPing.Measuring,
    /** Proxy se přidává; tlačítko je neaktivní, aby nešlo ťuknout dvakrát. */
    val connecting: Boolean = false,
    val error: UiText? = null,
)

/**
 * Odkaz `t.me/proxy` / `t.me/socks`: ukáže, kam proxy vede a jestli přes ni Telegram odpovídá,
 * a teprve po potvrzení ji přidá a zapne. Obyčejná třída nad [CoroutineScope] jako
 * [eu.tintera.plume.features.report.ReportFlow] — odkaz může přijít z kterékoli obrazovky
 * s textem a druhý ViewModel kvůli jednomu dialogu nestojí za to.
 */
class ProxyLinkFlow(
    private val scope: CoroutineScope,
    private val repository: ProxyRepository,
) {
    private val logger = Logger.withTag("Proxy")

    private val _state = MutableStateFlow<ProxyLinkState?>(null)
    val state: StateFlow<ProxyLinkState?> = _state.asStateFlow()

    /** Jednorázová hláška pro snackbar; nuluje ji obrazovka ([consumeOutcome]). */
    private val _outcome = MutableStateFlow<UiText?>(null)
    val outcome: StateFlow<UiText?> = _outcome.asStateFlow()

    fun open(config: ProxyConfig) {
        _state.value = ProxyLinkState(config)
        scope.launch {
            val ping = repository.ping(config).fold({ ProxyPing.Measured(it) }, { ProxyPing.Failed })
            // Mezitím zavřený (nebo jiný) odkaz se nepřepisuje.
            _state.update { if (it?.config == config) it.copy(ping = ping) else it }
        }
    }

    fun connect() {
        val current = _state.value ?: return
        if (current.connecting) return
        _state.value = current.copy(connecting = true, error = null)
        scope.launch {
            repository.add(current.config, enable = true)
                .onSuccess {
                    _state.value = null
                    _outcome.value = UiText(Res.string.proxy_connected)
                }
                .onFailure { failure ->
                    logger.w(failure) { "Proxy z odkazu se nepřidala." }
                    _state.update {
                        it?.copy(connecting = false, error = failure.userMessage(Res.string.proxy_connect_failed))
                    }
                }
        }
    }

    fun dismiss() {
        _state.value = null
    }

    fun consumeOutcome() {
        _outcome.value = null
    }
}

@Composable
fun ProxyLinkHost(flow: ProxyLinkFlow, snackbarHostState: SnackbarHostState) {
    val state by flow.state.collectAsStateWithLifecycle()
    val outcome by flow.outcome.collectAsStateWithLifecycle()
    // Snackbar mimo efekt: vynulování mění klíč efektu a zrušilo by ho (past „Vrátit").
    val snackbarScope = rememberCoroutineScope()
    LaunchedEffect(outcome) {
        val text = outcome ?: return@LaunchedEffect
        flow.consumeOutcome()
        val message = text.resolveNow()
        snackbarScope.launch { snackbarHostState.showSnackbar(message) }
    }
    state?.let {
        ProxyLinkDialog(
            config = it.config,
            ping = it.ping,
            connecting = it.connecting,
            error = it.error,
            onConnect = flow::connect,
            onDismiss = flow::dismiss,
        )
    }
}
