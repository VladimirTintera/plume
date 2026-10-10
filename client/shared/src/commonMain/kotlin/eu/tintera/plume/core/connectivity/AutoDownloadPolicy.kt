package eu.tintera.plume.core.connectivity

import co.touchlab.kermit.Logger
import eu.tintera.plume.core.settings.AppSettings
import eu.tintera.plume.core.settings.AutoDownloadLimits
import eu.tintera.plume.core.settings.autoDownloadLevel
import eu.tintera.plume.core.settings.limits
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

/**
 * Co se smí stáhnout samo PRÁVĚ TEĎ: úroveň z Nastavení pro síť, na které telefon je,
 * snížená spořičem dat ([autoDownloadLevel]). Přepne se sama s výměnou sítě — kdo vyjde
 * z Wi-Fi, nemá na mobilních datech dostahovat to, co by doma stáhl.
 *
 * Jedna instance na proces (Koin `single`), aby se monitor sítě neodebíral za každou bublinu.
 */
class AutoDownloadPolicy(settings: AppSettings, network: NetworkMonitor, scope: CoroutineScope) {

    private val logger = Logger.withTag("AutoDownload")

    val limits: StateFlow<AutoDownloadLimits> = combine(
        settings.autoDownload,
        // Do první zprávy od monitoru platí mobilní data — opatrnější z obou možností.
        network.status.onStart { emit(NetworkStatus(NetworkKind.Mobile, generation = -1)) },
        network.saveData.onStart { emit(false) },
    ) { levels, status, saveData ->
        Triple(autoDownloadLevel(levels, status.kind, saveData), status.kind, saveData)
    }
        // Android hlásí síť i při změně kvality signálu; zajímá nás jen jiná úroveň.
        .distinctUntilChanged { old, new -> old.first == new.first }
        .onEach { (level, kind, saveData) -> logger.i { "Automatické stahování: $level (síť $kind, spořič dat $saveData)" } }
        .map { (level) -> level.limits }
        .stateIn(
            scope,
            SharingStarted.Eagerly,
            autoDownloadLevel(settings.autoDownload.value, NetworkKind.Mobile, saveData = false).limits,
        )
}
