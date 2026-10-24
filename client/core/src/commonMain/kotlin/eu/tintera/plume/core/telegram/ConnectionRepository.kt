package eu.tintera.plume.core.telegram

import eu.tintera.plume.core.connectivity.NetworkStatus
import kotlinx.coroutines.flow.StateFlow

/**
 * Stav spojení s Telegramem — a jediné dvě páky, kterými se dá ovlivnit.
 *
 * Je to něco jiného než [TelegramSession] a jeho [AuthStage]: přihlášený klient může být bez
 * spojení a naopak. Dokud se [state] nikde nesledoval, nedalo se to poznat — aplikace vypadala
 * normálně, jen nic nechodilo.
 */
interface ConnectionRepository {

    val state: StateFlow<ConnectionState>

    /**
     * Ohlásí TDLib, že se změnila síť. **Vynutí tím znovuotevření všech spojení**, což je jediný
     * způsob, jak ho dostat z mrtvého socketu; hlásí se proto i tehdy, když je typ sítě pořád
     * stejný (návrat z pozadí).
     */
    suspend fun networkChanged(network: NetworkStatus)

    /**
     * Volba `online`. Rozhoduje o tom, jestli nás ostatní vidí jako online, a zrychluje doručování
     * updatů, dokud je aplikace na obrazovce.
     */
    suspend fun setOnline(online: Boolean)
}

/**
 * Stav spojení, jak ho hlásí TDLib. Doslova jeho `ConnectionState` — dokumentace u něj říká, že
 * se smí použít **jen na popisek pro člověka**, takže se z něj nic neodvozuje.
 */
enum class ConnectionState {

    /** Není síť. TDLib čeká, až mu ji ohlásíme přes [ConnectionRepository.networkChanged]. */
    WaitingForNetwork,

    Connecting,

    ConnectingToProxy,

    /** Připojeno, dotahuje se, co uteklo, když byla aplikace offline. */
    Updating,

    Ready,
}
