package eu.tintera.plume.core.accounts

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import eu.tintera.plume.core.telegram.CallRepository
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.core.ui.LocalSavedMessagesChatId
import eu.tintera.plume.core.ui.LocalSharedElementGeneration
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import org.koin.compose.KoinIsolatedContext
import org.koin.compose.koinInject

/**
 * Účet, kterému patří tahle část obrazovky. Čte se, když composable staví routu nebo se ptá na
 * slot; repository si bere dál přes `koinInject` a `koinViewModel`, ty jdou do kontejneru
 * z [AccountScope]. Bez [AccountScope] je to chyba, ne tichý návrat k výchozímu účtu.
 */
val LocalAccount = staticCompositionLocalOf<AccountSession> {
    error("Composable pracuje s účtem mimo AccountScope.")
}

/**
 * [content] pracuje s účtem [account]: `koinInject` a `koinViewModel` míří do jeho kontejneru.
 *
 * Podává i id Uložených zpráv toho účtu (`LocalSavedMessagesChatId`): je to vlastní user id,
 * a chat jednoho účtu s druhým má právě tohle id. Dokud ho podával jen shell, ukázal společný
 * seznam chat VT s VT2 jako „Uložené zprávy" (ověřeno na zařízení).
 */
@Composable
fun AccountScope(account: AccountSession, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalAccount provides account) {
        KoinIsolatedContext(account.application) {
            val savedMessages = remember(account) { account.koin.getOrNull<ChatRepository>()?.savedMessagesChatId }
                ?.collectAsStateWithLifecycle()?.value
            CompositionLocalProvider(LocalSavedMessagesChatId provides savedMessages, content = content)
        }
    }
}

/**
 * [content] pracuje s výchozím účtem (`Accounts.default`): kořeny, které slot odnikud nemají
 * (shell, bublina). Se změnou výchozího účtu se [content] složí znovu od začátku, i se svými
 * ViewModely ([AccountViewModels]): stav jednoho účtu nesmí přejít do druhého.
 */
@Composable
fun DefaultAccountScope(content: @Composable () -> Unit) {
    val account by koinInject<Accounts>().default.collectAsStateWithLifecycle()
    key(account.slot) {
        // Nová instance shellu = nové klíče sdílených prvků (viz LocalSharedElementGeneration).
        val generation = remember { Any() }
        CompositionLocalProvider(LocalSharedElementGeneration provides generation) {
            AccountScope(account) {
                AccountViewModels(account.slot, content)
            }
        }
    }
}

/**
 * [content] pracuje s účtem, který má hovor; bez hovoru s výchozím. Pro okna a pruhy hovoru,
 * které stojí mimo routy: hovor je přes všechny účty jeden a může patřit kterémukoli.
 */
@Composable
fun CallAccountScope(content: @Composable () -> Unit) {
    val accounts = koinInject<Accounts>()
    val all by accounts.all.collectAsStateWithLifecycle()
    val default by accounts.default.collectAsStateWithLifecycle()
    val calling = remember(all) {
        if (all.size == 1) flowOf(null) else combine(all.map { it.get<CallRepository>().active }) { calls ->
            all.getOrNull(calls.indexOfFirst { it != null })
        }
    }.collectAsStateWithLifecycle(null).value
    val account = calling ?: default
    key(account.slot) { AccountScope(account, content) }
}

/**
 * Úložiště ViewModelů po účtech pod úložištěm Activity.
 *
 * `koinViewModel()` bez klíče hledá ViewModel podle třídy, a kořenové obrazovky (přihlášení,
 * seznam chatů nad zásobníkem) by po přepnutí účtu dostaly ViewModel předchozího účtu, s jeho
 * repository — Koin u nalezené instance kontejner nemění (CLAUDE.md, „ViewModel na detailu MUSÍ
 * mít klíč"). Každý účet proto má vlastní úložiště; přepnutí zpátky najde jeho ViewModely,
 * odebraný účet je zahodí ([AccountViewModelStores.retain]).
 */
@Composable
fun AccountViewModels(slot: AccountSlot, content: @Composable () -> Unit) {
    val parent = checkNotNull(LocalViewModelStoreOwner.current) { "Úložiště ViewModelů chybí." }
    val stores = viewModel(parent, key = ACCOUNT_STORES_KEY) { AccountViewModelStores() }
    val accounts = koinInject<Accounts>()
    val all by accounts.all.collectAsStateWithLifecycle()
    val adding by accounts.adding.collectAsStateWithLifecycle()
    LaunchedEffect(all, adding) { stores.retain((all + listOfNotNull(adding)).map { it.slot }.toSet()) }
    val owner = remember(stores, slot) {
        object : ViewModelStoreOwner {
            override val viewModelStore: ViewModelStore = stores.store(slot)
        }
    }
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner, content = content)
}

private class AccountViewModelStores : ViewModel() {

    private val stores = mutableMapOf<AccountSlot, ViewModelStore>()

    fun store(slot: AccountSlot): ViewModelStore = stores.getOrPut(slot) { ViewModelStore() }

    /** Zahodí úložiště účtů, které už nejsou. */
    fun retain(slots: Set<AccountSlot>) {
        val gone = stores.keys - slots
        gone.forEach { stores.remove(it)?.clear() }
    }

    override fun onCleared() {
        stores.values.forEach { it.clear() }
        stores.clear()
    }
}

private const val ACCOUNT_STORES_KEY = "account-view-model-stores"
