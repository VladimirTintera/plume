package eu.tintera.plume.core.files

import eu.tintera.plume.core.accounts.Accounts
import eu.tintera.plume.core.telegram.TransferActivity
import eu.tintera.plume.core.telegram.TransferMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/** Nahrávání a stahování naráz — jedna notifikace, jedna úloha na pozadí. */
data class Transfers(val uploads: TransferActivity, val downloads: TransferActivity) {
    val isIdle: Boolean get() = uploads.isIdle && downloads.isIdle

    /** Roste s každým novým přenosem, ať nahrávaným, nebo stahovaným. */
    val started: Long get() = uploads.started + downloads.started

    companion object {
        val Idle = Transfers(TransferActivity.Idle, TransferActivity.Idle)
    }
}

/**
 * Přenosy přes všechny účty ([TransferMonitor] každého z nich sečtené). Proces drží naživu
 * služba v popředí (Android) nebo úloha na pozadí (iOS), a ta je v procesu jedna — nepatří
 * žádnému účtu, jako služba videochatu (`VideoChats`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ActiveTransfers(
    /** Až za běhu: v globálním Koinu se na účty při vzniku definice sahat nesmí. */
    accounts: () -> Accounts,
    scope: CoroutineScope,
) {
    val activity: StateFlow<Transfers> = flow { emitAll(accounts().all) }
        .flatMapLatest { all ->
            if (all.isEmpty()) {
                flowOf(Transfers.Idle)
            } else {
                val monitors = all.map { account -> account.get<TransferMonitor>() }
                combine(sum(monitors.map { it.uploads }), sum(monitors.map { it.downloads }), ::Transfers)
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, Transfers.Idle)

    private fun sum(flows: List<Flow<TransferActivity>>): Flow<TransferActivity> =
        combine(flows) { activities -> activities.fold(TransferActivity.Idle, TransferActivity::plus) }
}
