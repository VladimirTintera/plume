package eu.tintera.plume.core.connectivity

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import platform.Network.nw_interface_type_cellular
import platform.Network.nw_interface_type_wifi
import platform.Network.nw_path_get_status
import platform.Network.nw_path_is_constrained
import platform.Network.nw_path_monitor_cancel
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_status_satisfied
import platform.Network.nw_path_t
import platform.Network.nw_path_uses_interface_type
import platform.darwin.dispatch_get_main_queue

/**
 * Stav sítě z `NWPathMonitor`.
 *
 * Na rozdíl od Androidu sem chodí jen skutečné změny cesty, ne průběžný šum o kvalitě signálu,
 * takže se pořadové číslo zvedá při každém oznámení.
 */
@OptIn(ExperimentalForeignApi::class)
actual class NetworkMonitor {

    actual val status: Flow<NetworkStatus> = callbackFlow {
        var generation = 0L

        val monitor = nw_path_monitor_create()

        // Fronta se musí nastavit dřív, než monitor odstartuje — jinak si ho systém nemá kde zavolat.
        nw_path_monitor_set_queue(monitor, dispatch_get_main_queue())

        nw_path_monitor_set_update_handler(monitor) { path ->
            generation++
            trySend(NetworkStatus(kindOf(path), generation))
        }

        // Handler se ozve hned po startu s aktuální cestou, takže se počáteční stav nečte zvlášť.
        nw_path_monitor_start(monitor)
        awaitClose { nw_path_monitor_cancel(monitor) }
    }
        // Zajímá nás poslední stav, ne fronta minulých; handler přitom nesmí čekat na odběratele.
        .buffer(Channel.CONFLATED)

    /** Režim nízkých dat (`NWPath.isConstrained`) — obdoba spořiče dat na Androidu. */
    actual val saveData: Flow<Boolean> = callbackFlow {
        val monitor = nw_path_monitor_create()
        nw_path_monitor_set_queue(monitor, dispatch_get_main_queue())
        nw_path_monitor_set_update_handler(monitor) { path -> trySend(nw_path_is_constrained(path)) }
        nw_path_monitor_start(monitor)
        awaitClose { nw_path_monitor_cancel(monitor) }
    }.buffer(Channel.CONFLATED)
}

actual val detectsRoaming: Boolean = false

/**
 * Roaming `NWPath` nehlásí, takže mobilní data jsou vždycky [NetworkKind.Mobile]. Je to údaj pro
 * statistiku TDLib; na to, jestli se spojení obnoví, vliv nemá.
 */
@OptIn(ExperimentalForeignApi::class)
private fun kindOf(path: nw_path_t): NetworkKind = when {
    nw_path_get_status(path) != nw_path_status_satisfied -> NetworkKind.None
    nw_path_uses_interface_type(path, nw_interface_type_wifi) -> NetworkKind.WiFi
    nw_path_uses_interface_type(path, nw_interface_type_cellular) -> NetworkKind.Mobile
    else -> NetworkKind.Other
}
