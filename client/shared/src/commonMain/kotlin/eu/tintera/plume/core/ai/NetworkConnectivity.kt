package eu.tintera.plume.core.ai

import eu.tintera.ai.core.Connectivity
import eu.tintera.plume.core.connectivity.NetworkKind
import eu.tintera.plume.core.connectivity.NetworkMonitor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Stav sítě pro stahování modelu (ai-engine :ai-core) z [NetworkMonitor], který už sleduje TDLib. */
class NetworkConnectivity(monitor: NetworkMonitor) : Connectivity {
    override val online: Flow<Boolean> = monitor.status.map { it.kind != NetworkKind.None }
}
