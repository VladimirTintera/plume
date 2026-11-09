package eu.tintera.plume.core.accounts

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import org.koin.compose.KoinIsolatedContext
import org.koin.compose.koinInject

/**
 * Účet, kterému patří tahle část obrazovky. Čte se, když composable staví routu nebo se ptá na
 * slot; repository si bere dál přes `koinInject` a `koinViewModel`, ty jdou do kontejneru
 * z [AccountScope]. Bez [AccountScope] je to chyba, ne tichý návrat k primárnímu účtu.
 */
val LocalAccount = staticCompositionLocalOf<AccountSession> {
    error("Composable pracuje s účtem mimo AccountScope.")
}

/** [content] pracuje s účtem [account]: `koinInject` a `koinViewModel` míří do jeho kontejneru. */
@Composable
fun AccountScope(account: AccountSession, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalAccount provides account) {
        KoinIsolatedContext(account.application, content)
    }
}

/**
 * [content] pracuje s primárním účtem. Pro kořeny, které slot odnikud nemají (shell, okna na
 * desktopu), dokud nepřijde výchozí účet a slot ve vstupních bodech.
 */
@Composable
fun PrimaryAccountScope(content: @Composable () -> Unit) {
    AccountScope(koinInject<Accounts>().primary, content)
}
