package eu.tintera.plume.features.settings

import eu.tintera.plume.core.lock.AppLock
import eu.tintera.plume.core.lock.LockClock
import eu.tintera.plume.core.secrets.AppLockStore
import eu.tintera.plume.core.telegram.AuthStage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppLockViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private class FakeStore(var bytes: ByteArray? = null) : AppLockStore {
        override fun generate() = ByteArray(32) { it.toByte() }
        override suspend fun load() = bytes
        override suspend fun store(key: ByteArray) { bytes = key }
        override suspend fun clear() { bytes = null }
    }

    private class FakeClock(var wall: Long = 1_000_000, var elapsed: Long = 0) : LockClock {
        override fun wallMillis() = wall
        override fun elapsedMillis() = elapsed
    }

    private inner class Setup(scope: TestScope, store: FakeStore = FakeStore()) {
        val store = store
        val clock = FakeClock()
        val foreground = MutableStateFlow(true)
        val auth = MutableStateFlow<AuthStage>(AuthStage.Ready)
        val lock = AppLock(store, scope.backgroundScope, clock, iterations = 1, hashDispatcher = dispatcher, foregroundContext = EmptyCoroutineContext)
            .also { it.start(foreground, auth) }
        val vm = AppLockViewModel(lock)
    }

    @Test
    fun vychoziStavJeVypnuty() = runTest {
        val s = Setup(this)
        advanceUntilIdle()
        assertNull(s.vm.settings.value)
        assertEquals(AppLockFlow.Idle, s.vm.uiState.value.flow)
        assertFalse(s.vm.uiState.value.isVerified)
    }

    @Test
    fun uspesneNastaveniNovehoPinu() = runTest {
        val s = Setup(this)
        advanceUntilIdle()

        s.vm.startSetup()
        assertEquals(AppLockFlow.EnterNewPin(isChanging = false), s.vm.uiState.value.flow)

        // Zadání 1, 2, 3, 4
        s.vm.onDigit(1)
        s.vm.onDigit(2)
        s.vm.onDigit(3)
        s.vm.onDigit(4)
        advanceUntilIdle()

        val confirm = assertIs<AppLockFlow.ConfirmNewPin>(s.vm.uiState.value.flow)
        assertEquals("1234", confirm.firstPin)
        assertEquals("", s.vm.uiState.value.enteredPin)

        // Potvrzení 1, 2, 3, 4
        s.vm.onDigit(1)
        s.vm.onDigit(2)
        s.vm.onDigit(3)
        s.vm.onDigit(4)
        advanceUntilIdle()

        assertEquals(AppLockFlow.Idle, s.vm.uiState.value.flow)
        assertTrue(s.vm.uiState.value.isVerified)
        assertNotNull(s.vm.settings.value)
    }

    @Test
    fun neshodnyPinPriNastaveniHlasiChybu() = runTest {
        val s = Setup(this)
        advanceUntilIdle()

        s.vm.startSetup()
        s.vm.onDigit(1); s.vm.onDigit(2); s.vm.onDigit(3); s.vm.onDigit(4)
        advanceUntilIdle()

        // Potvrzení jiného PINu
        s.vm.onDigit(1); s.vm.onDigit(2); s.vm.onDigit(3); s.vm.onDigit(5)
        advanceUntilIdle()

        assertEquals(AppLockFlow.EnterNewPin(isChanging = false), s.vm.uiState.value.flow)
        assertEquals("mismatch", s.vm.uiState.value.errorMessage)
        assertEquals(1, s.vm.uiState.value.errorTrigger)
        assertNull(s.vm.settings.value)
    }

    @Test
    fun overeniAktualnihoPinuPriVstupuDoNastaveni() = runTest {
        val store = FakeStore()
        Setup(this, store).vm.apply {
            startSetup()
            onDigit(1); onDigit(2); onDigit(3); onDigit(4)
            onDigit(1); onDigit(2); onDigit(3); onDigit(4)
        }
        advanceUntilIdle()

        // Nová instance ViewModelu simuluje otevření obrazovky Nastavení
        val s = Setup(this, store)
        runCurrent()

        assertNotNull(s.vm.settings.value)
        assertFalse(s.vm.uiState.value.isVerified)

        // Chybný PIN
        s.vm.onDigit(9); s.vm.onDigit(9); s.vm.onDigit(9); s.vm.onDigit(9)
        advanceUntilIdle()
        assertFalse(s.vm.uiState.value.isVerified)
        assertEquals("wrong", s.vm.uiState.value.errorMessage)

        // Správný PIN
        s.vm.onDigit(1); s.vm.onDigit(2); s.vm.onDigit(3); s.vm.onDigit(4)
        advanceUntilIdle()
        assertTrue(s.vm.uiState.value.isVerified)
        assertNull(s.vm.uiState.value.errorMessage)
    }

    @Test
    fun zmenaDobyNeaktivityABiometrie() = runTest {
        val s = Setup(this)
        advanceUntilIdle()
        s.vm.startSetup()
        s.vm.onDigit(1); s.vm.onDigit(2); s.vm.onDigit(3); s.vm.onDigit(4)
        s.vm.onDigit(1); s.vm.onDigit(2); s.vm.onDigit(3); s.vm.onDigit(4)
        advanceUntilIdle()

        s.vm.setTimeout(300)
        advanceUntilIdle()
        assertEquals(300, s.vm.settings.value?.timeoutSeconds)

        s.vm.setBiometrics(true)
        advanceUntilIdle()
        assertTrue(s.vm.settings.value?.biometrics == true)

        s.vm.disableLock()
        advanceUntilIdle()
        assertNull(s.vm.settings.value)
    }
}
