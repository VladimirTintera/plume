package eu.tintera.plume.core.webapp

import eu.tintera.plume.core.settings.createSettingsDataStore
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * Úložiště mini aplikací nad skutečným souborem (`runBlocking`, jako `DataStoreAppSettingsTest`:
 * DataStore pracuje na vlastních vláknech).
 */
class MiniAppStorageTest {

    private fun storage() = DataStoreMiniAppStorage(
        createSettingsDataStore((FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "plume-miniapp-${Random.nextLong()}.preferences_pb").toString()),
    )

    @Test
    fun hodnotyJsouZvlastPoUctuABotovi() = runBlocking {
        val storage = storage()
        storage.set(account = 1, bot = 10, key = "skore", value = "42")
        storage.set(account = 1, bot = 11, key = "skore", value = "7")
        storage.set(account = 2, bot = 10, key = "skore", value = "99")

        assertEquals("42", storage.get(1, 10, "skore"))
        assertEquals("7", storage.get(1, 11, "skore"))
        assertEquals("99", storage.get(2, 10, "skore"))
        assertNull(storage.get(1, 10, "jiny"))
    }

    @Test
    fun smazaniKliceAVymazaniBota() = runBlocking {
        val storage = storage()
        storage.set(1, 10, "a", "1")
        storage.set(1, 10, "b", "2")
        storage.set(1, 11, "a", "3")

        storage.set(1, 10, "a", null)
        assertNull(storage.get(1, 10, "a"))

        storage.clear(1, 10)
        assertNull(storage.get(1, 10, "b"))
        assertEquals("3", storage.get(1, 11, "a"), "jiného bota vymazání nesmí")
    }

    @Test
    fun stropPetMegabajtuNaBota() = runBlocking {
        val storage = storage()
        val big = "x".repeat(3 * 1024 * 1024)
        storage.set(1, 10, "a", big)
        assertFailsWith<MiniAppStorageFull> { storage.set(1, 10, "b", big) }
        assertNull(storage.get(1, 10, "b"))
        // Přepsat týž klíč jde — počítá se bez jeho staré hodnoty.
        storage.set(1, 10, "a", big)
        // Jiný bot má strop svůj.
        storage.set(1, 11, "a", big)
    }

    @Test
    fun souhlasSPolohou() = runBlocking {
        val storage = storage()
        assertNull(storage.locationAllowed(1, 10))
        storage.setLocationAllowed(1, 10, false)
        assertEquals(false, storage.locationAllowed(1, 10))
        storage.setLocationAllowed(1, 10, true)
        assertEquals(true, storage.locationAllowed(1, 10))
        assertNull(storage.locationAllowed(2, 10))
    }
}
