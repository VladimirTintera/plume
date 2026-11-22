package eu.tintera.plume.core.webapp

import eu.tintera.plume.core.secrets.MiniAppSecretStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

/**
 * Zabezpečené úložiště mini aplikací (`SecureStorage` z Bot API 9.0): klíč → text jako
 * [MiniAppStorage], jen šifrovaně v trezoru platformy — na tokeny a hesla, která stránka nechce
 * nechat v obyčejném úložišti.
 *
 * - **Odděleně po účtu a botovi**, ze stejného důvodu jako `DeviceStorage`.
 * - **Nejvýš [SECURE_STORAGE_LIMIT] položek na bota**, jako u oficiálních klientů; další klíč
 *   selže ([MiniAppStorageFull]), přepsání existujícího projde.
 * - **Klíč nejvýš [SECURE_KEY_LIMIT] a hodnota [SECURE_VALUE_LIMIT] znaků** (tokeny a hesla, ne
 *   soubory). Hlídal se jen počet položek: deset hodnot po desítkách MB by šlo zapsat, a protože
 *   je všechno jeden šifrovaný záznam pro všechny boty, každé další čtení kterékoli mini
 *   aplikace by ho celý dešifrovalo do paměti a aplikaci shodilo.
 * - **Obnova z jiného zařízení není** (`restoreItem`): oficiální klienti obnovují ze zálohy
 *   Klíčenky, Plume žádnou zálohu nevede — `canRestore` je vždycky `false`.
 */
interface MiniAppSecureStorage {
    suspend fun get(account: Long, bot: Long, key: String): String?

    /** `null` klíč smaže. */
    suspend fun set(account: Long, bot: Long, key: String, value: String?)

    suspend fun clear(account: Long, bot: Long)
}

/**
 * [MiniAppSecureStorage] nad trezorem platformy ([MiniAppSecretStore]: Android Keystore, iOS
 * Keychain, desktop Klíčenka nebo soubor).
 *
 * **Všechno je jeden záznam** (JSON `"účet:bot:klíč" → hodnota`), ne záznam na klíč: trezor drží
 * jedno tajemství na instanci a položek je málo (deset na bota). Každá změna záznam přečte,
 * upraví a celý zapíše pod [mutex] — souběžné požadavky stránky se jinak přepíšou. Nečitelný
 * záznam (ztracený klíč Keystore po obnově zařízení) se bere jako prázdný; data mini aplikací
 * nejsou nenahraditelná.
 */
class SecretMiniAppSecureStorage(private val store: MiniAppSecretStore) : MiniAppSecureStorage {

    private val mutex = Mutex()

    private fun prefix(account: Long, bot: Long) = "$account:$bot:"

    override suspend fun get(account: Long, bot: Long, key: String): String? =
        mutex.withLock { read()[prefix(account, bot) + key] }

    override suspend fun set(account: Long, bot: Long, key: String, value: String?) {
        mutex.withLock {
            val items = read()
            val itemKey = prefix(account, bot) + key
            val updated = if (value == null) {
                items - itemKey
            } else {
                if (key.length > SECURE_KEY_LIMIT || value.length > SECURE_VALUE_LIMIT) throw MiniAppStorageFull()
                val prefix = prefix(account, bot)
                val others = items.keys.count { it.startsWith(prefix) && it != itemKey }
                if (others >= SECURE_STORAGE_LIMIT) throw MiniAppStorageFull()
                items + (itemKey to value)
            }
            if (updated != items) write(updated)
        }
    }

    override suspend fun clear(account: Long, bot: Long) {
        mutex.withLock {
            val items = read()
            val prefix = prefix(account, bot)
            val updated = items.filterKeys { !it.startsWith(prefix) }
            if (updated.size != items.size) write(updated)
        }
    }

    private suspend fun read(): Map<String, String> {
        val bytes = store.load() ?: return emptyMap()
        return runCatching { Json.decodeFromString<Map<String, String>>(bytes.decodeToString()) }.getOrElse { emptyMap() }
    }

    private suspend fun write(items: Map<String, String>) {
        if (items.isEmpty()) store.clear() else store.store(Json.encodeToString(items).encodeToByteArray())
    }
}

/** Položek `SecureStorage` na bota — jako u oficiálních klientů. */
const val SECURE_STORAGE_LIMIT = 10

/** Nejdelší klíč položky SecureStorage ve znacích. */
const val SECURE_KEY_LIMIT = 128

/** Nejdelší hodnota položky SecureStorage ve znacích (32 Ki — i dlouhý JWT se vejde). */
const val SECURE_VALUE_LIMIT = 32 * 1024
