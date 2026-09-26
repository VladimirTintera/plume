package eu.tintera.plume.features.settings

import eu.tintera.plume.core.telegram.SensitiveContentSettings
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
 * Přepínač „Zobrazovat citlivý obsah": nabízí se jen tam, kde volbu server změnit dovolí,
 * a po neúspěšném zápisu se vrátí a řekne proč.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SensitiveContentViewModelTest {

    private class FakeSettings(var value: Boolean?, val fails: Boolean = false) : SensitiveContentSettings {
        val writes = mutableListOf<Boolean>()
        override suspend fun showsSensitiveContent(): Result<Boolean?> = Result.success(value)
        override suspend fun setShowsSensitiveContent(show: Boolean): Result<Unit> {
            writes += show
            return if (fails) Result.failure(IllegalStateException("x")) else Result.success(Unit).also { value = show }
        }
    }

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun kdeZmenitNejdeRadekNeni() = runTest {
        val viewModel = SensitiveContentViewModel(FakeSettings(value = null))
        advanceUntilIdle()
        assertNull(viewModel.state.value.show)
    }

    @Test
    fun zapnutiSeZapise() = runTest {
        val settings = FakeSettings(value = false)
        val viewModel = SensitiveContentViewModel(settings)
        advanceUntilIdle()

        viewModel.setShow(true)
        advanceUntilIdle()

        assertEquals(listOf(true), settings.writes)
        assertEquals(true, viewModel.state.value.show)
        assertFalse(viewModel.state.value.failed)
    }

    @Test
    fun selhaniPrepinacVrati() = runTest {
        val viewModel = SensitiveContentViewModel(FakeSettings(value = false, fails = true))
        advanceUntilIdle()

        viewModel.setShow(true)
        advanceUntilIdle()

        assertEquals(false, viewModel.state.value.show)
        assertTrue(viewModel.state.value.failed)
    }
}
