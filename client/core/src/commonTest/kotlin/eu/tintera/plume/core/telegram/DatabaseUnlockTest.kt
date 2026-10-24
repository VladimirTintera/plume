package eu.tintera.plume.core.telegram

import eu.tintera.plume.core.secrets.DatabaseKeyStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Pravidlo kolem šifrované databáze TDLib. Testuje se proto, že se na jednom z jeho rozhodnutí
 * veze **smazání lokálních dat** — a to se na zařízení pozná až tím, že uživatel přišel
 * o přihlášení.
 */
class DatabaseUnlockTest {

    private val wrongKey = IllegalStateException("Wrong database encryption key")

    /** Klíč z toho, čím se databáze otevírala, ať jde poznat, kterým klíčem se kdy šlo. */
    private class FakeKeys(private var stored: ByteArray? = null) : DatabaseKeyStore {
        var generated = 0
        val storedKey: ByteArray? get() = stored
        var cleared = false

        override fun generate(): ByteArray {
            generated++
            return byteArrayOf(generated.toByte())
        }
        override suspend fun load(): ByteArray? = stored
        override suspend fun store(key: ByteArray) { stored = key }
        override suspend fun clear() { cleared = true; stored = null }
    }

    private class Recorder {
        val opened = mutableListOf<ByteArray>()
        var encryptedWith: ByteArray? = null
        var wiped = false
    }

    private suspend fun unlock(
        keys: FakeKeys,
        recorder: Recorder,
        open: (key: ByteArray, attempt: Int) -> Throwable?,
    ): Throwable? = unlockDatabase(
        keys = keys,
        isWrongKey = { it.message == wrongKey.message },
        open = { key ->
            recorder.opened += key
            open(key, recorder.opened.size)
        },
        encrypt = { key -> recorder.encryptedWith = key },
        wipe = { recorder.wiped = true },
    )

    @Test
    fun `bezny start otevira ulozenym klicem a nic dalsiho nedela`() = runTest {
        val keys = FakeKeys(stored = byteArrayOf(9))
        val recorder = Recorder()

        val failure = unlock(keys, recorder) { _, _ -> null }

        assertNull(failure)
        assertEquals(1, recorder.opened.size)
        assertContentEquals(byteArrayOf(9), recorder.opened.single())
        assertNull(recorder.encryptedWith)
        assertFalse(recorder.wiped)
        assertEquals(0, keys.generated)
    }

    @Test
    fun `prvni spusteni vyrobi klic a ulozi ho jeste pred otevrenim`() = runTest {
        val keys = FakeKeys(stored = null)
        val recorder = Recorder()

        // Databáze ještě není, takže ji TDLib založí rovnou zašifrovanou a otevření projde.
        val failure = unlock(keys, recorder) { _, _ -> null }

        assertNull(failure)
        assertEquals(1, keys.generated)
        assertContentEquals(recorder.opened.single(), keys.storedKey)
        assertNull(recorder.encryptedWith)
        assertFalse(recorder.wiped)
    }

    @Test
    fun `instalace z verze bez sifrovani se otevre prazdnym klicem a zasifruje`() = runTest {
        val keys = FakeKeys(stored = null)
        val recorder = Recorder()

        val failure = unlock(keys, recorder) { key, _ ->
            if (key.isEmpty()) null else wrongKey
        }

        assertNull(failure)
        assertEquals(2, recorder.opened.size)
        assertContentEquals(EMPTY_DATABASE_KEY, recorder.opened[1])
        assertContentEquals(keys.storedKey, recorder.encryptedWith)
        assertFalse(recorder.wiped)
    }

    @Test
    fun `migrace prerusena pred zasifrovanim se dozene ulozenym klicem`() = runTest {
        // Klíč uložený je, databáze ho ještě nedostala — proces umřel mezi `store` a `setDatabaseEncryptionKey`.
        val keys = FakeKeys(stored = byteArrayOf(7))
        val recorder = Recorder()

        val failure = unlock(keys, recorder) { key, _ -> if (key.isEmpty()) null else wrongKey }

        assertNull(failure)
        assertContentEquals(byteArrayOf(7), recorder.encryptedWith)
        assertContentEquals(byteArrayOf(7), keys.storedKey)
        assertFalse(recorder.wiped)
        assertEquals(0, keys.generated)
    }

    @Test
    fun `databaze zasifrovana cizim klicem se zahodi a zaklada se nova`() = runTest {
        val keys = FakeKeys(stored = byteArrayOf(3))
        val recorder = Recorder()

        val failure = unlock(keys, recorder) { _, attempt -> if (attempt < 3) wrongKey else null }

        assertNull(failure)
        assertTrue(recorder.wiped)
        assertEquals(3, recorder.opened.size)
        assertContentEquals(byteArrayOf(3), recorder.opened[2])
    }

    /**
     * Nejdůležitější test celého souboru: chyba, která o klíči nic neříká (plný disk, špatné
     * `api_id`), NESMÍ vést ke smazání lokálních dat. S mazáním „při každém selhání" by se
     * uživatel odhlásil kvůli něčemu, co s klíčem nemá nic společného.
     */
    @Test
    fun `chyba mimo klic nemaze nic a vraci se volajicimu`() = runTest {
        val keys = FakeKeys(stored = byteArrayOf(5))
        val recorder = Recorder()
        val other = IllegalStateException("Can't create directory")

        val failure = unlock(keys, recorder) { _, _ -> other }

        assertSame(other, failure)
        assertFalse(recorder.wiped)
        assertFalse(keys.cleared)
        assertEquals(1, recorder.opened.size)
    }

    @Test
    fun `chyba mimo klic pri druhem pokusu taky nemaze`() = runTest {
        val keys = FakeKeys(stored = byteArrayOf(5))
        val recorder = Recorder()
        val other = IllegalStateException("Can't create directory")

        val failure = unlock(keys, recorder) { key, _ -> if (key.isEmpty()) other else wrongKey }

        assertSame(other, failure)
        assertFalse(recorder.wiped)
        assertEquals(2, recorder.opened.size)
    }
}
