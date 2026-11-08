package eu.tintera.plume.core.telegram

import dev.g000sha256.tdl.TdlClient
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Mezipaměť odpovědí klienta TDLib, která platí jen pro klienta, od kterého je má.
 *
 * Holder po odhlášení vymění klienta a další účet má vlastní databázi: tatáž id uživatelů
 * a souborů tam znamenají něco jiného (jiné jméno z kontaktů, jiné `fileId` profilovky). Keš si
 * proto pamatuje klienta a s jiným začne od prázdna. Odpověď, která dorazí od klienta, mezitím
 * vyměněného, se nezapíše.
 */
internal class ClientCache<K, V : Any>(private val holder: TelegramClientHolder) {

    private val mutex = Mutex()
    private val entries = mutableMapOf<K, V>()
    private var owner: TdlClient? = null

    /** Hodnota pro [key]; když chybí, načte ji [load]. `null` z [load] se nepamatuje. */
    suspend fun getOrLoad(key: K, load: suspend (TdlClient) -> V?): V? {
        val client = holder.client
        mutex.withLock {
            if (owner !== client) {
                entries.clear()
                owner = client
            }
            entries[key]
        }?.let { return it }

        val loaded = load(client) ?: return null
        mutex.withLock { if (owner === client) entries[key] = loaded }
        return loaded
    }
}
