package eu.tintera.plume.core.webapp

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first

/**
 * Úložiště mini aplikací v zařízení (`DeviceStorage` z Bot API 9.0): klíč → text, zvlášť pro
 * každý účet a bota. Nesynchronizuje se (od toho je `CloudStorage` na serveru) a nešifruje —
 * zabezpečené úložiště (`SecureStorage`) Plume nemá.
 *
 * - **Odděleně po účtu**: po odhlášení a přihlášení jiným účtem by jinak táž mini aplikace
 *   viděla data předchozího uživatele.
 * - **Strop 5 MB na bota**, jako u oficiálních klientů; zápis přes strop selže
 *   ([MiniAppStorageFull]) a stará data zůstanou.
 */
interface MiniAppStorage {
    suspend fun get(account: Long, bot: Long, key: String): String?

    /** `null` klíč smaže. */
    suspend fun set(account: Long, bot: Long, key: String, value: String?)

    suspend fun clear(account: Long, bot: Long)

    /** Smí bot znát polohu zařízení? `null` = ještě se neptal. */
    suspend fun locationAllowed(account: Long, bot: Long): Boolean?

    suspend fun setLocationAllowed(account: Long, bot: Long, allowed: Boolean)
}

class MiniAppStorageFull : IllegalStateException("Úložiště mini aplikace je plné")

class DataStoreMiniAppStorage(private val dataStore: DataStore<Preferences>) : MiniAppStorage {

    private fun prefix(account: Long, bot: Long) = "d:$account:$bot:"

    override suspend fun get(account: Long, bot: Long, key: String): String? =
        dataStore.data.first()[stringPreferencesKey(prefix(account, bot) + key)]

    override suspend fun set(account: Long, bot: Long, key: String, value: String?) {
        val prefKey = stringPreferencesKey(prefix(account, bot) + key)
        dataStore.edit { prefs ->
            if (value == null) {
                prefs.remove(prefKey)
                return@edit
            }
            val prefix = prefix(account, bot)
            val others = prefs.asMap().entries.sumOf { (k, v) ->
                if (k.name.startsWith(prefix) && k != prefKey) (k.name.length - prefix.length) + v.toString().length else 0
            }
            if (others + key.length + value.length > STORAGE_LIMIT_CHARS) throw MiniAppStorageFull()
            prefs[prefKey] = value
        }
    }

    override suspend fun clear(account: Long, bot: Long) {
        val prefix = prefix(account, bot)
        dataStore.edit { prefs ->
            prefs.asMap().keys.filter { it.name.startsWith(prefix) }.forEach { prefs.remove(it) }
        }
    }

    override suspend fun locationAllowed(account: Long, bot: Long): Boolean? =
        dataStore.data.first()[stringPreferencesKey("l:$account:$bot")]?.toBooleanStrictOrNull()

    override suspend fun setLocationAllowed(account: Long, bot: Long, allowed: Boolean) {
        dataStore.edit { it[stringPreferencesKey("l:$account:$bot")] = allowed.toString() }
    }
}

/** 5 MB jako u oficiálních klientů; počítá se v znacích (UTF-16 jednotkách), ne bajtech. */
private const val STORAGE_LIMIT_CHARS = 5 * 1024 * 1024

/** Soubor úložiště; adresář dodává platforma jako u nastavení. */
const val MINI_APP_STORAGE_FILE_NAME = "mini_app_storage.preferences_pb"
