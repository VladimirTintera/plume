package eu.tintera.plume.core.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Žádost o čtečku Instant View. Přes sdílený objekt, ne přes parametr konverzace: obrazovku
 * konverzace drží na hraně počet registrů v dexu (viz CLAUDE.md), takže otevření čtečky
 * vyřídí shell, jako odkazy zvenčí ([LinkOpenRequests]).
 */
class InstantViewRequests {

    private val pending = MutableStateFlow<InstantViewRequest?>(null)

    val request: StateFlow<InstantViewRequest?> = pending.asStateFlow()

    fun request(url: String) {
        pending.value = InstantViewRequest(url, serial++)
    }

    fun consume(request: InstantViewRequest) {
        pending.compareAndSet(request, null)
    }

    private var serial = 0L
}

/** [serial] odliší dvakrát po sobě otevřenou tutéž stránku (zavřít a otevřít znovu). */
data class InstantViewRequest(val url: String, val serial: Long)
