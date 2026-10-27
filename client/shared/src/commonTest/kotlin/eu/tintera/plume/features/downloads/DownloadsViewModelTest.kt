package eu.tintera.plume.features.downloads

import eu.tintera.plume.core.telegram.FakeDownloads
import eu.tintera.plume.core.telegram.FileState
import eu.tintera.plume.core.telegram.UnimplementedFileRepository
import eu.tintera.plume.core.telegram.testDownload
import eu.tintera.plume.core.ui.UNDO_WINDOW_MS
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.document_open_failed
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
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
        val viewModel = viewModel(downloads)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(listOf(1, 3), state.active.map { it.fileId })
        assertEquals(listOf(2), state.completed.map { it.fileId })
        assertTrue(state.anyRunning)
    }

    @Test
    fun vsePozastaveneNabidnePokracovat() = runTest {
        val viewModel = viewModel(FakeDownloads(listOf(testDownload(1, paused = true))))
        advanceUntilIdle()
        assertFalse(viewModel.state.value.anyRunning)
    }

    @Test
    fun zmenaSeznamuHoNacteZnovu() = runTest {
        val downloads = FakeDownloads(listOf(testDownload(1)))
        val viewModel = viewModel(downloads)
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
    fun pozastaveniJdeDoRepository() = runTest {
        val downloads = FakeDownloads(listOf(testDownload(1)))
        val viewModel = viewModel(downloads)
        advanceUntilIdle()

        viewModel.setPaused(testDownload(1), paused = true)
        viewModel.setAllPaused(false)
        advanceUntilIdle()

        assertEquals(listOf(1 to true), downloads.paused)
        assertEquals(listOf(false), downloads.allPaused)
    }

    @Test
    fun stazenySouborSeOtevreVSystemu() = runTest {
        val opened = mutableListOf<String>()
        val viewModel = viewModel(
            FakeDownloads(listOf(testDownload(1, completed = true))),
            files = { FileState.Ready("/data/soubor1.pdf") },
            openInSystem = { path, _ -> opened += path; true },
        )
        advanceUntilIdle()

        var missing = false
        viewModel.openFile(testDownload(1, completed = true)) { missing = true }
        advanceUntilIdle()

        assertEquals(listOf("/data/soubor1.pdf"), opened)
        assertFalse(missing)
    }

    @Test
    fun souborBezAplikaceOhlasiChybu() = runTest {
        val viewModel = viewModel(
            FakeDownloads(listOf(testDownload(1, completed = true))),
            files = { FileState.Ready("/data/soubor1.xyz") },
            openInSystem = { _, _ -> false },
        )
        advanceUntilIdle()

        viewModel.openFile(testDownload(1, completed = true)) {}
        advanceUntilIdle()

        assertEquals(UiText(Res.string.document_open_failed), viewModel.state.value.error)
    }

    @Test
    fun souborKteryUzNaDiskuNeniOtevreZpravu() = runTest {
        val opened = mutableListOf<String>()
        val viewModel = viewModel(
            FakeDownloads(listOf(testDownload(1, completed = true))),
            files = { FileState.Pending },
            openInSystem = { path, _ -> opened += path; true },
        )
        advanceUntilIdle()

        var missing = false
        viewModel.openFile(testDownload(1, completed = true)) { missing = true }
        advanceUntilIdle()

        assertTrue(missing)
        assertTrue(opened.isEmpty())
    }

    @Test
    fun odebraniSeOdloziASouborHnedZmizi() = runTest {
        val downloads = FakeDownloads(listOf(testDownload(1, completed = true), testDownload(2, completed = true)))
        val viewModel = viewModel(downloads)
        advanceUntilIdle()

        viewModel.remove(testDownload(1, completed = true), deleteFile = true)
        runCurrent()

        assertEquals(listOf(2), viewModel.state.value.completed.map { it.fileId })
        assertEquals(DownloadRemoval.Kind.Deleted, viewModel.state.value.removal?.kind)
        assertTrue(downloads.removed.isEmpty())

        advanceTimeBy(UNDO_WINDOW_MS + 1)
        assertEquals(listOf(1 to true), downloads.removed)
    }

    @Test
    fun vratitOdebraniZrusi() = runTest {
        val downloads = FakeDownloads(listOf(testDownload(1, completed = true)))
        val viewModel = viewModel(downloads)
        advanceUntilIdle()

        viewModel.remove(testDownload(1, completed = true), deleteFile = false)
        runCurrent()
        val removal = viewModel.state.value.removal!!
        viewModel.consumeRemoval()
        assertNull(viewModel.state.value.removal)

        advanceTimeBy(UNDO_WINDOW_MS - 1_000)
        viewModel.undoRemoval(removal)
        advanceUntilIdle()

        assertTrue(downloads.removed.isEmpty())
        assertEquals(listOf(1), viewModel.state.value.completed.map { it.fileId })
    }

    @Test
    fun neuspesneOdebraniSouborVratiAOhlasi() = runTest {
        val downloads = FakeDownloads(listOf(testDownload(1, completed = true)))
        downloads.removeResult = Result.failure(IllegalStateException("Bad request"))
        val viewModel = viewModel(downloads)
        advanceUntilIdle()

        viewModel.remove(testDownload(1, completed = true), deleteFile = false)
        // Lhůta běží v `backgroundScope`, kterou `advanceUntilIdle` nepohání.
        advanceTimeBy(UNDO_WINDOW_MS + 1)
        runCurrent()

        assertEquals(listOf(1), viewModel.state.value.completed.map { it.fileId })
        assertTrue(viewModel.state.value.error != null)
    }

    @Test
    fun vymazaniStazenychOdebereJenViditelnaDokoncena() = runTest {
        val downloads = FakeDownloads(listOf(testDownload(1), testDownload(2, completed = true), testDownload(3, completed = true)))
        val viewModel = viewModel(downloads)
        advanceUntilIdle()

        viewModel.clearCompleted()
        runCurrent()
        assertTrue(viewModel.state.value.completed.isEmpty())
        assertEquals(listOf(1), viewModel.state.value.active.map { it.fileId })
        assertEquals(DownloadRemoval.Kind.Cleared, viewModel.state.value.removal?.kind)

        advanceTimeBy(UNDO_WINDOW_MS + 1)
        assertEquals(listOf(2 to false, 3 to false), downloads.removed)
    }

    /** ViewModel s odkladačem v [backgroundScope], aby jeho lhůta běžela na virtuálním čase testu. */
    private fun TestScope.viewModel(
        downloads: FakeDownloads,
        files: (Int) -> FileState = { FileState.Pending },
        openInSystem: (String, String) -> Boolean = { _, _ -> true },
    ) = DownloadsViewModel(
        downloads = downloads,
        files = object : UnimplementedFileRepository() {
            override fun observe(fileId: Int): Flow<FileState> = flowOf(files(fileId))
        },
        removals = PendingDownloadRemovals(backgroundScope),
        openInSystem = openInSystem,
    )
}
