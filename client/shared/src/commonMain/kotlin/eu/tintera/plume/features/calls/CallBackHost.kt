package eu.tintera.plume.features.calls

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.accounts.AccountScope
import eu.tintera.plume.core.accounts.Accounts
import eu.tintera.plume.core.calls.CallBackRequest
import eu.tintera.plume.core.calls.CallRequests
import org.koin.compose.koinInject

/**
 * Vyřizuje „Zavolat zpět" z notifikace o zmeškaném hovoru ([CallRequests.callBack]): vytočí
 * hovor z účtu, kterému notifikace patřila, stejnou cestou jako sluchátko v konverzaci
 * ([rememberStartCall] — dotaz na mikrofon, u videohovoru i na kameru, hovor v jiném účtu).
 *
 * Přes kompozici, ne z notifikace rovnou: o oprávnění se umí zeptat jen obrazovka.
 */
@Composable
fun CallBackHost() {
    val requests = koinInject<CallRequests>()
    val accounts = koinInject<Accounts>()
    val request by requests.callBack.collectAsStateWithLifecycle()
    // Poslední požadavek zůstává ve složení i po vyřízení: dotaz na oprávnění vrátí výsledek až
    // po chvíli a jeho launcher (uvnitř [rememberStartCall]) musí do té doby v kompozici přežít.
    var last by remember { mutableStateOf<CallBackRequest?>(null) }
    LaunchedEffect(request) { request?.let { last = it } }
    val target = last ?: return
    val account = accounts.find(target.slot) ?: return
    key(target.slot) {
        AccountScope(account) {
            val start = rememberStartCall(target.userId)
            LaunchedEffect(target) {
                requests.consumeCallBack(target)
                start?.invoke(target.video)
            }
        }
    }
}
