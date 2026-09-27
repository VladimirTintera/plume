package eu.tintera.plume.features.settings

import eu.tintera.plume.core.telegram.DefaultAutoDeleteSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Výchozí doba automatického mazání: řádek nic netvrdí, dokud doba nedorazí, nezměněná volba
 * se nezapisuje a po neúspěšném zápisu se vrátí a řekne proč.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultAutoDeleteViewModelTest {

    private class FakeSettings(var value: Int, val readFails: Boolean = false, val writeFails: Boolean = false) :
        DefaultAutoDeleteSettings {
        val writes = mutableListOf<Int>()
        override suspend fun defaultAutoDeleteTime(): Result<Int> =
            if (readFails) Result.failure(IllegalStateException("x")) else Result.success(value)
        override suspend fun setDefaultAutoDeleteTime(seconds: Int): Result<Unit> {
            writes += seconds
            return if (writeFails) Result.failure(IllegalStateException("x")) else Result.success(Unit).also { value = seconds }
        }
    }

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun bezZjisteneDobyRadekNeniAVyberSeNeotevre() = runTest {
        val viewModel = DefaultAutoDeleteViewModel(FakeSettings(value = 0, readFails = true))
        advanceUntilIdle()
        viewModel.openChooser()
        assertNull(viewModel.state.value.seconds)
        assertFalse(viewModel.state.value.choosing)
    }

    @Test
    fun volbaSeZapiseAVyberZavre() = runTest {
        val settings = FakeSettings(value = 0)
        val viewModel = DefaultAutoDeleteViewModel(settings)
        advanceUntilIdle()

        viewModel.openChooser()
        viewModel.choose(DAY)
        advanceUntilIdle()

        assertEquals(listOf(DAY), settings.writes)
        assertEquals(DAY, viewModel.state.value.seconds)
        assertFalse(viewModel.state.value.choosing)
    }

    @Test
    fun stejnaVolbaSeNezapisuje() = runTest {
        val settings = FakeSettings(value = DAY)
        val viewModel = DefaultAutoDeleteViewModel(settings)
        advanceUntilIdle()

        viewModel.choose(DAY)
        advanceUntilIdle()

        assertTrue(settings.writes.isEmpty())
    }

    @Test
    fun selhaniVolbuVrati() = runTest {
        val viewModel = DefaultAutoDeleteViewModel(FakeSettings(value = 0, writeFails = true))
        advanceUntilIdle()

        viewModel.choose(DAY)
        advanceUntilIdle()

        assertEquals(0, viewModel.state.value.seconds)
        assertTrue(viewModel.state.value.failed)
    }

    private companion object {
        const val DAY = 24 * 60 * 60
    }
}
