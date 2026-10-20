package eu.tintera.plume.features.downloads

import eu.tintera.plume.core.telegram.FakeDownloads
import eu.tintera.plume.core.telegram.testDownload
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadsViewModelTest {

    @BeforeTest
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun seznamSeRozdeliNaStahovaneAStazene() = runTest {
        val downloads = FakeDownloads(listOf(testDownload(1), testDownload(2, completed = true), testDownload(3, paused = true)))
        val viewModel = DownloadsViewModel(downloads)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(listOf(1, 3), state.active.map { it.fileId })
        assertEquals(listOf(2), state.completed.map { it.fileId })
        assertTrue(state.anyRunning)
    }

    @Test
    fun vsePozastaveneNabidnePokracovat() = runTest {
        val viewModel = DownloadsViewModel(FakeDownloads(listOf(testDownload(1, paused = true))))
        advanceUntilIdle()
        assertFalse(viewModel.state.value.anyRunning)
    }

    @Test
    fun zmenaSeznamuHoNacteZnovu() = runTest {
        val downloads = FakeDownloads(listOf(testDownload(1)))
        val viewModel = DownloadsViewModel(downloads)
        advanceUntilIdle()

        downloads.items = listOf(testDownload(1, completed = true))
        downloads.changes.tryEmit(Unit)
        downloads.changes.tryEmit(Unit)
        advanceUntilIdle()

        assertEquals(listOf(1), viewModel.state.value.completed.map { it.fileId })
        // Dávka změn = jedno čtení navíc.
        assertEquals(2, downloads.lists)
    }

    @Test
    fun pozastaveniAOdebraniJdouDoRepository() = runTest {
        val downloads = FakeDownloads(listOf(testDownload(1)))
        val viewModel = DownloadsViewModel(downloads)
        advanceUntilIdle()

        viewModel.setPaused(testDownload(1), paused = true)
        viewModel.setAllPaused(false)
        viewModel.remove(testDownload(1), deleteFile = true)
        advanceUntilIdle()

        assertEquals(listOf(1 to true), downloads.paused)
        assertEquals(listOf(false), downloads.allPaused)
        assertEquals(listOf(1 to true), downloads.removed)
    }
}
