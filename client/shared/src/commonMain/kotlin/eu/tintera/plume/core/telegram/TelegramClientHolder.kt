package eu.tintera.plume.core.telegram

import dev.g000sha256.tdl.TdlClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flatMapLatest

/**
 * Drží aktuální instanci [TdlClient] pro celý proces.
 *
 * TDLib klient je jeden na aplikaci — má vlastní spojení, lokální databázi a vlákno na updaty.
 * Víc instancí by si šláplo na stejný databázový adresář. Holder existuje proto, aby ho mohly
 * sdílet všechny implementace nad TDLib a přitom zůstal privátní vůči zbytku aplikace.
 *
 * **Instance se po odhlášení mění.** Odhlášením TDLib klienta zavře (`AuthorizationStateClosed`)
 * a wrapper ukončí všechny jeho update flows — dalšího přihlášení se zavřený klient nedočká.
 * Proto se nesmí zachytit do `val`: požadavky jdou přes [client] (vždy aktuální) a odběry přes
 * [updates], které se na nového klienta přepnou samy.
 */
class TelegramClientHolder {

    private val _clients = MutableStateFlow(TdlClient.create())

    val client: TdlClient get() = _clients.value

    /** Nová instance po odhlášení. Starou už TDLib zavřel, takže nemá co uklízet. */
    fun recreate(): TdlClient = TdlClient.create().also { _clients.value = it }

    /** Odběr updatů, který přežije výměnu klienta. */
    fun <T> updates(select: TdlClient.() -> Flow<T>): Flow<T> =
        _clients.flatMapLatest { it.select() }

    /** Ozve se při každé výměně klienta — tedy po dokončeném odhlášení. Na úklid kešek. */
    val replaced: Flow<TdlClient> = _clients.drop(1)
}
