package eu.tintera.plume.features.downloads

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.key
import eu.tintera.plume.core.accounts.AccountSession
import eu.tintera.plume.core.accounts.LocalAccount
import eu.tintera.plume.core.telegram.AccountInfo
import eu.tintera.plume.core.telegram.FileDownloadItem
import eu.tintera.plume.features.chats.rememberAllAccountsEntries
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinInternalApi

/**
 * Účet ve Stahování: jeho kontejner, ViewModel (akce nad souborem jdou do něj) a fotka se jménem
 * pro odznak u řádku — ten jen ve společném seznamu víc účtů.
 */
@Immutable
internal data class DownloadSource(val account: AccountSession, val viewModel: DownloadsViewModel, val info: AccountInfo?)

/**
 * Zdroje Stahování: v režimu Všechny účty každý účet s ViewModelem ze svého kontejneru (klíč se
 * slotem, jako `rememberChatSources`), jinak jen účet obrazovky. Seznam stahování vede TDLib
 * každého účtu zvlášť, soubory i jejich id patří účtu.
 */
@OptIn(KoinInternalApi::class)
@Composable
internal fun rememberDownloadSources(): List<DownloadSource> {
    val all = rememberAllAccountsEntries()
    if (all.isEmpty()) return listOf(DownloadSource(LocalAccount.current, koinViewModel(), info = null))
    return all.map { entry ->
        key(entry.account.slot) {
            DownloadSource(
                account = entry.account,
                viewModel = koinViewModel(key = "downloads-all-${entry.account.slot.id}", scope = entry.account.koin.scopeRegistry.rootScope),
                info = entry.info,
            )
        }
    }
}

/** Soubor ve Stahování víc účtů: [source] je pořadí účtu ve zdrojích. */
internal data class SourcedDownload(val source: Int, val item: FileDownloadItem) {
    /** Klíč seznamu: id souboru platí jen v účtu. */
    val key: String get() = "$source:${item.fileId}"
}

/** Stahování jednoho nebo víc účtů tak, jak ho kreslí obrazovka ([mergeDownloads]). */
@Immutable
internal data class MergedDownloads(val active: List<SourcedDownload>, val completed: List<SourcedDownload>) {
    val isEmpty: Boolean get() = active.isEmpty() && completed.isEmpty()

    /** Běží aspoň jedno stahování — „Pozastavit vše" má smysl. */
    val anyRunning: Boolean get() = active.any { !it.item.isPaused }
}

/**
 * Sloučí Stahování účtů [states] (pořadí = pořadí zdrojů); `null`, dokud se nenačte každý.
 * Víc účtů se řadí podle data: stahované podle přidání, stažené podle dokončení, nejnovější
 * nahoře. Jeden účet zůstává v pořadí od TDLib, jako dřív.
 */
internal fun mergeDownloads(states: List<DownloadsState>): MergedDownloads? {
    if (states.any { it.items == null }) return null
    val active = states.flatMapIndexed { source, state -> state.active.map { SourcedDownload(source, it) } }
    val completed = states.flatMapIndexed { source, state -> state.completed.map { SourcedDownload(source, it) } }
    if (states.size == 1) return MergedDownloads(active, completed)
    return MergedDownloads(
        active = active.sortedByDescending { it.item.addDate },
        completed = completed.sortedByDescending { it.item.completeDate },
    )
}
