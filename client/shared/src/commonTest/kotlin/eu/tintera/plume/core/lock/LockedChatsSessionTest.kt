package eu.tintera.plume.core.lock

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LockedChatsSessionTest {

    @Test
    fun odemceniPlatiDoOdchoduDoPozadi() = runTest {
        val foreground = MutableStateFlow(true)
        val lock = MutableStateFlow<AppLockState>(AppLockState.Unlocked)
        val session = LockedChatsSession(foreground, lock, backgroundScope)
        runCurrent()
        assertFalse(session.unlocked.value, "po startu zamčeno")

        session.unlock()
        assertTrue(session.unlocked.value)

        foreground.value = false
        runCurrent()
        assertFalse(session.unlocked.value, "pozadí zamkne")

        foreground.value = true
        runCurrent()
        assertFalse(session.unlocked.value, "návrat do popředí neodemkne")
    }

    @Test
    fun zamceniAplikaceZamkneIZamceneChaty() = runTest {
        val lock = MutableStateFlow<AppLockState>(AppLockState.Unlocked)
        val session = LockedChatsSession(MutableStateFlow(true), lock, backgroundScope)
        runCurrent()
        session.unlock()
        lock.value = AppLockState.Locked(blockedUntil = 0, biometrics = false)
        runCurrent()
        assertFalse(session.unlocked.value)
    }

    @Test
    fun rucniZamceni() = runTest {
        val session = LockedChatsSession(MutableStateFlow(true), MutableStateFlow(AppLockState.Unlocked), backgroundScope)
        session.unlock()
        session.lock()
        assertFalse(session.unlocked.value)
    }
}
