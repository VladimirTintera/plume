package eu.tintera.plume.features.calls

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.key
import eu.tintera.plume.core.accounts.AccountSession
import eu.tintera.plume.core.accounts.LocalAccount
import eu.tintera.plume.core.telegram.AccountInfo
import eu.tintera.plume.features.chats.rememberAllAccountsEntries
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinInternalApi

/**
 * Účet v historii hovorů: jeho kontejner, ViewModel (akce nad řádkem jdou do něj) a fotka se
 * jménem pro odznak u řádku — ten jen ve společné historii víc účtů.
 */
@Immutable
internal data class CallSource(val account: AccountSession, val viewModel: CallsViewModel, val info: AccountInfo?)

/**
 * Zdroje historie hovorů: v režimu Všechny účty každý účet s ViewModelem ze svého kontejneru
 * (klíč se slotem, jako `rememberChatSources`), jinak jen účet obrazovky.
 */
@OptIn(KoinInternalApi::class)
@Composable
internal fun rememberCallSources(): List<CallSource> {
    val all = rememberAllAccountsEntries()
    if (all.isEmpty()) return listOf(CallSource(LocalAccount.current, koinViewModel(), info = null))
    return all.map { entry ->
        key(entry.account.slot) {
            CallSource(
                account = entry.account,
                viewModel = koinViewModel(key = "calls-all-${entry.account.slot.id}", scope = entry.account.koin.scopeRegistry.rootScope),
                info = entry.info,
            )
        }
    }
}

/** Historie hovorů jednoho nebo víc účtů tak, jak ji kreslí obrazovka ([mergeCallStates]). */
@Immutable
internal data class MergedCallsState(
    /** Hovory od nejnovějšího; `null`, dokud nemá první stránku každý účet. */
    val calls: List<SourcedCall>?,
    val onlyMissed: Boolean,
    val loadingMore: Boolean,
    val deleting: Boolean,
    /** Účty, jejichž další stránka může přinést hovor mezi ukázané; čtou se při dojetí ke konci. */
    val needMore: List<Int>,
)

/**
 * Sloučí historii hovorů účtů [states] (pořadí = pořadí zdrojů) podle data.
 *
 * Každý účet stránkuje sám, takže se ukazuje jen to, co je novější než nejstarší načtený hovor
 * účtu, který má další stránku: starší hovor jiného účtu by se jinak zařadil před hovory, které
 * ještě nepřišly, a po dočtení stránky by se pod ním objevily novější. Další stránka se čte jen
 * účtu, který tu hranici drží ([MergedCallsState.needMore]). S jedním účtem se ukáže všechno
 * načtené, jako dřív.
 */
internal fun mergeCallStates(states: List<CallsState>): MergedCallsState {
    val onlyMissed = states.firstOrNull()?.onlyMissed ?: false
    val deleting = states.any { it.deleting }
    val loaded = states.map { it.entries ?: return MergedCallsState(null, onlyMissed, false, deleting, emptyList()) }
    // Prázdná stránka bez konce je nezdar čtení: hranici nedrží, jinak by nebylo vidět nic.
    val open = states.indices.filter { !states[it].endReached && loaded[it].isNotEmpty() }
    val cutoff = open.maxOfOrNull { loaded[it].last().date }
    val calls = loaded
        .flatMapIndexed { source, entries -> entries.map { SourcedCall(source, it) } }
        .filter { cutoff == null || it.entry.date >= cutoff }
        .sortedByDescending { it.entry.date }
    return MergedCallsState(
        calls = calls,
        onlyMissed = onlyMissed,
        loadingMore = states.any { it.loadingMore },
        deleting = deleting,
        needMore = open.filter { loaded[it].last().date == cutoff },
    )
}
