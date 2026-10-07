package eu.tintera.plume.core.lock

import eu.tintera.plume.core.secrets.AppLockStore
import eu.tintera.plume.core.telegram.AuthStage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppLockTest {

    private class FakeStore(var bytes: ByteArray? = null) : AppLockStore {
        override fun generate() = ByteArray(32) { it.toByte() }
        override suspend fun load() = bytes
        override suspend fun store(key: ByteArray) { bytes = key }
        override suspend fun clear() { bytes = null }
    }

    private class FakeClock(var wall: Long = 1_000_000, var elapsed: Long = 0) : LockClock {
        override fun wallMillis() = wall
        override fun elapsedMillis() = elapsed
        fun advance(ms: Long) { wall += ms; elapsed += ms }
    }

    private class Setup(scope: TestScope, store: FakeStore = FakeStore()) {
        val store = store
        val clock = FakeClock()
        val foreground = MutableStateFlow(true)
        val auth = MutableStateFlow<AuthStage>(AuthStage.Ready)
        val lock = AppLock(store, scope.backgroundScope, clock, iterations = 1)
            .also { it.start(foreground, auth) }
    }

    @Test
    fun bezZaznamuJeZamekVypnuty() = runTest {
        val s = Setup(this)
        runCurrent()
        assertEquals(AppLockState.Disabled, s.lock.state.value)
        assertNull(s.lock.settings.value)
    }

    @Test
    fun zapnutyZamekZacinaZamceny() = runTest {
        val store = FakeStore()
        Setup(this, store).lock.enable("1234")
        val s = Setup(this, store)
        runCurrent()
        assertIs<AppLockState.Locked>(s.lock.state.value)
        assertEquals(PinResult.Wrong(0), s.lock.unlock("0000"))
        assertEquals(PinResult.Success, s.lock.unlock("1234"))
        assertEquals(AppLockState.Unlocked, s.lock.state.value)
    }

    @Test
    fun vPozadiSeZamkneAzPoDobe() = runTest {
        val s = Setup(this)
        runCurrent()
        s.lock.enable("1234")
        s.foreground.value = false; runCurrent()
        s.clock.advance(59_000)
        s.foreground.value = true; runCurrent()
        assertEquals(AppLockState.Unlocked, s.lock.state.value)

        s.foreground.value = false; runCurrent()
        s.clock.advance(60_000)
        s.foreground.value = true; runCurrent()
        assertIs<AppLockState.Locked>(s.lock.state.value)
    }

    @Test
    fun hodinyPrestaveneDozaduZamekNeobejdou() = runTest {
        val s = Setup(this)
        runCurrent()
        s.lock.enable("1234")
        s.foreground.value = false; runCurrent()
        s.clock.elapsed += 120_000
        s.clock.wall -= 3_600_000
        s.foreground.value = true; runCurrent()
        assertIs<AppLockState.Locked>(s.lock.state.value)
    }

    @Test
    fun hlubokySpanekZamekNeobejde() = runTest {
        val s = Setup(this)
        runCurrent()
        s.lock.enable("1234")
        s.foreground.value = false; runCurrent()
        s.clock.wall += 120_000 // monotónní čas ve spánku stál
        s.foreground.value = true; runCurrent()
        assertIs<AppLockState.Locked>(s.lock.state.value)
    }

    @Test
    fun poPetiChybachPrijdeBrzdaKteraPrezijeRestart() = runTest {
        val store = FakeStore()
        val s = Setup(this, store)
        runCurrent()
        s.lock.enable("1234")
        repeat(FREE_ATTEMPTS - 1) { assertEquals(PinResult.Wrong(0), s.lock.verify("0000")) }
        val wrong = s.lock.verify("0000")
        assertEquals(PinResult.Wrong(s.clock.wall + 30_000), wrong)
        assertIs<PinResult.Blocked>(s.lock.verify("1234"))

        // Nový proces: brzda platí dál.
        val again = Setup(this, store)
        again.clock.wall = s.clock.wall
        runCurrent()
        assertIs<PinResult.Blocked>(again.lock.unlock("1234"))
        again.clock.advance(30_000)
        assertEquals(PinResult.Success, again.lock.unlock("1234"))
    }

    @Test
    fun brzdaSeZdvojujeAZastaviNaHodine() {
        assertEquals(0, blockFor(FREE_ATTEMPTS - 1))
        assertEquals(30_000, blockFor(FREE_ATTEMPTS))
        assertEquals(60_000, blockFor(FREE_ATTEMPTS + 1))
        assertEquals(3_600_000, blockFor(FREE_ATTEMPTS + 10))
        assertEquals(3_600_000, blockFor(1_000))
    }

    @Test
    fun brzdaSeHodinamiNaZdiNeprotahne() {
        val config = LockConfig(1, byteArrayOf(1), byteArrayOf(1), 60, false, FREE_ATTEMPTS, blockedUntil = 10_000_000)
        // Hodiny se vrátily o den: brzda smí trvat nejvýš 30 s od teď.
        assertEquals(1_030_000, effectiveBlockedUntil(config, now = 1_000_000))
        assertEquals(0, effectiveBlockedUntil(config, now = 20_000_000))
    }

    @Test
    fun biometrieOdemkneIBehemBrzdy() = runTest {
        val s = Setup(this)
        runCurrent()
        s.lock.enable("1234")
        s.lock.setBiometrics(true)
        s.foreground.value = false; runCurrent()
        s.clock.advance(60_000)
        s.foreground.value = true; runCurrent()
        repeat(FREE_ATTEMPTS) { s.lock.unlock("0000") }
        val locked = assertIs<AppLockState.Locked>(s.lock.state.value)
        assertTrue(locked.biometrics)
        assertTrue(locked.blockedUntil > s.clock.wall)
        s.lock.unlockWithBiometrics()
        assertEquals(AppLockState.Unlocked, s.lock.state.value)
        assertEquals(PinResult.Success, s.lock.verify("1234"))
    }

    @Test
    fun odhlaseniZamekZahodi() = runTest {
        val store = FakeStore()
        val s = Setup(this, store)
        runCurrent()
        s.lock.enable("1234")
        s.auth.value = AuthStage.LoggingOut; runCurrent()
        assertEquals(AppLockState.Disabled, s.lock.state.value)
        assertNull(store.bytes)
    }

    @Test
    fun zmenaPinuANastaveni() = runTest {
        val s = Setup(this)
        runCurrent()
        s.lock.enable("1234")
        s.lock.changePin("9876")
        s.lock.setTimeout(300)
        assertEquals(AppLockSettings(300, false), s.lock.settings.value)
        assertEquals(PinResult.Wrong(0), s.lock.verify("1234"))
        assertEquals(PinResult.Success, s.lock.verify("9876"))
        s.lock.disable()
        assertEquals(AppLockState.Disabled, s.lock.state.value)
    }

    @Test
    fun poskozenyZaznamSeZahodi() = runTest {
        val store = FakeStore("nesmysl".encodeToByteArray())
        val s = Setup(this, store)
        runCurrent()
        assertEquals(AppLockState.Disabled, s.lock.state.value)
        assertNull(store.bytes)
    }
}
