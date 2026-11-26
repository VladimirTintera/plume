package eu.tintera.plume.core.files

import eu.tintera.plume.core.accounts.Accounts
import eu.tintera.plume.core.telegram.UploadActivity
import eu.tintera.plume.core.telegram.UploadMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/**
 * Nahrávání přes všechny účty ([UploadMonitor] každého z nich sečtené). Proces drží naživu
 * služba v popředí (Android) nebo úloha na pozadí (iOS), a ta je v procesu jedna — nepatří
 * žádnému účtu, jako služba videochatu (`VideoChats`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ActiveUploads(
    /** Až za běhu: v globálním Koinu se na účty při vzniku definice sahat nesmí. */
    accounts: () -> Accounts,
    scope: CoroutineScope,
) {
    val activity: StateFlow<UploadActivity> = flow { emitAll(accounts().all) }
        .flatMapLatest { all ->
            if (all.isEmpty()) {
                flowOf(UploadActivity.Idle)
            } else {
                combine(all.map { account -> account.get<UploadMonitor>().activity }) { activities ->
                    activities.fold(UploadActivity.Idle, UploadActivity::plus)
                }
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, UploadActivity.Idle)
}
