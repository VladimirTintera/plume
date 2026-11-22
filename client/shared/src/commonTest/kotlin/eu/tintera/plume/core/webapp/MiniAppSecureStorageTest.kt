package eu.tintera.plume.core.webapp

import eu.tintera.plume.core.secrets.MiniAppSecretStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** Zabezpečené úložiště mini aplikací nad trezorem: oddělení po účtu a botovi a strop deseti položek. */
class MiniAppSecureStorageTest {

    /** Trezor v paměti; [stored] je to, co by platforma zašifrovala. */
    private class MemoryStore : MiniAppSecretStore {
        var stored: ByteArray? = null
        override fun generate(): ByteArray = ByteArray(32)
        override suspend fun load(): ByteArray? = stored
        override suspend fun store(key: ByteArray) {
            stored = key
        }
        override suspend fun clear() {
            stored = null
        }
    }

    private val store = MemoryStore()
    private val storage = SecretMiniAppSecureStorage(store)

    @Test
    fun zapisCteniASmazani() = runTest {
        storage.set(account = 1, bot = 7, key = "token", value = "abc")
        assertEquals("abc", storage.get(1, 7, "token"))

        storage.set(1, 7, "token", null)
        assertNull(storage.get(1, 7, "token"))
        assertNull(store.stored, "Prázdné úložiště nemá v trezoru nechat záznam")
    }

    @Test
    fun uctyABotiJsouOddeleni() = runTest {
        storage.set(1, 7, "token", "muj")
        storage.set(2, 7, "token", "cizi ucet")
        storage.set(1, 8, "token", "jiny bot")

        assertEquals("muj", storage.get(1, 7, "token"))
        storage.clear(1, 7)
        assertNull(storage.get(1, 7, "token"))
        assertEquals("cizi ucet", storage.get(2, 7, "token"))
        assertEquals("jiny bot", storage.get(1, 8, "token"))
    }

    @Test
    fun jedenactaPolozkaNeprojdePrepsaniAno() = runTest {
        repeat(SECURE_STORAGE_LIMIT) { storage.set(1, 7, "k$it", "v") }
        assertFailsWith<MiniAppStorageFull> { storage.set(1, 7, "navic", "v") }
        // Přepsání existující položky strop nepřekročí.
        storage.set(1, 7, "k0", "nove")
        assertEquals("nove", storage.get(1, 7, "k0"))
        // Jiný bot má vlastních deset.
        storage.set(1, 8, "k0", "v")
    }

    @Test
    fun prilisDlouhyKlicNeboHodnotaNeprojde() = runTest {
        storage.set(1, 7, "k".repeat(SECURE_KEY_LIMIT), "v".repeat(SECURE_VALUE_LIMIT))
        assertFailsWith<MiniAppStorageFull> { storage.set(1, 7, "k".repeat(SECURE_KEY_LIMIT + 1), "v") }
        assertFailsWith<MiniAppStorageFull> { storage.set(1, 7, "token", "v".repeat(SECURE_VALUE_LIMIT + 1)) }
        assertNull(storage.get(1, 7, "token"))
    }

    @Test
    fun necitelnyZaznamJePrazdny() = runTest {
        store.stored = "není JSON".encodeToByteArray()
        assertNull(storage.get(1, 7, "token"))
        storage.set(1, 7, "token", "abc")
        assertEquals("abc", storage.get(1, 7, "token"))
    }
}
