package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.files.SaveAsLauncher
import eu.tintera.plume.core.telegram.FakeDownloads
import eu.tintera.plume.core.telegram.FileState
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.telegram.UnimplementedFileRepository
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.document_download_failed
import eu.tintera.plume.resources.document_open_failed
import eu.tintera.plume.resources.media_save_failed
import eu.tintera.plume.resources.media_saved_as
import eu.tintera.plume.resources.media_share_failed
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationDocumentsTest {

    private class FakeFiles : UnimplementedFileRepository() {
        /** Stav souboru, jak ho hlásí TDLib; test ho posouvá. */
        val file = MutableStateFlow<FileState>(FileState.Pending)
        val retried = mutableListOf<Int>()
        val cancelled = mutableListOf<Int>()

        override fun file(fileId: Int): Flow<FileState> = file

        override suspend fun retryDownload(fileId: Int) {
            retried += fileId
        }

        override suspend fun cancelDownload(fileId: Int) {
            cancelled += fileId
        }
    }

    private val files = FakeFiles()
    private val opened = mutableListOf<Pair<String, String>>()
    private val shared = mutableListOf<Pair<String, String>>()
    private var opens = true
    private var shares = true
    private var menusClosed = 0

    private fun TestScope.documents(state: MutableStateFlow<ConversationState>, downloads: FakeDownloads? = null) = ConversationDocuments(
        state = state,
        scope = this,
        fileRepository = files,
        openFile = { path, mime -> opened += path to mime; opens },
        share = { path, name -> shared += path to name; shares },
        closeMenus = { menusClosed++ },
        logger = testLogger,
        downloads = downloads,
        chatId = if (downloads != null) TEST_CHAT else 0,
    )

    private fun stateWithDocument() = testState(
        testMessage(1).copy(
            media = MessageMedia.Document(
                fileId = FILE,
                fileName = "smlouva.pdf",
                mimeType = "application/pdf",
                sizeBytes = 1000,
                thumbnailFileId = null,
            ),
        ),
    )

    private class FakeSaveAs(private val result: Boolean?) : SaveAsLauncher {
        val launched = mutableListOf<Pair<String, String>>()
        override fun launch(path: String, fileName: String, onResult: (saved: Boolean?) -> Unit) {
            launched += path to fileName
            onResult(result)
        }
    }

    @Test
    fun otevreniStahneSouborSPrubehemAPredaHoSystemu() = runTest {
        val state = stateWithDocument()
        documents(state).openDocument(1)
        runCurrent()
        assertEquals(listOf(FILE), files.retried)
        assertEquals(mapOf(FILE to 0f), state.value.documentDownloads)

        files.file.value = FileState.Downloading(progress = 0.5f)
        runCurrent()
        assertEquals(mapOf(FILE to 0.5f), state.value.documentDownloads)

        files.file.value = FileState.Ready("/data/smlouva.pdf")
        advanceUntilIdle()
        assertEquals(listOf("/data/smlouva.pdf" to "application/pdf"), opened)
        assertTrue(state.value.documentDownloads.isEmpty())
        assertNull(state.value.notice)
    }

    @Test
    fun souborZeZpravyJdeDoSeznamuStahovani() = runTest {
        val downloads = FakeDownloads()
        val state = stateWithDocument()
        documents(state, downloads).openDocument(1)
        runCurrent()

        assertEquals(listOf(Triple(FILE, TEST_CHAT, 1L)), downloads.added)
        // Stahování spustil seznam; postaru se nestahuje.
        assertEquals(emptyList(), files.retried)
    }

    @Test
    fun kdyzSeNepridaDoSeznamuStahujeSePostaru() = runTest {
        val downloads = FakeDownloads(addResult = Result.failure(IllegalStateException("FILE_ID_INVALID")))
        documents(stateWithDocument(), downloads).openDocument(1)
        runCurrent()

        assertEquals(listOf(FILE), files.retried)
    }

    @Test
    fun souborKteryNicNeoteviraDostaneHlasku() = runTest {
        val state = stateWithDocument()
        opens = false
        files.file.value = FileState.Ready("/data/smlouva.pdf")
        documents(state).openDocument(1)
        advanceUntilIdle()
        assertEquals(UiText(Res.string.document_open_failed), state.value.notice)
    }

    @Test
    fun selhaneStahovaniHlasiChybuANicNeotevre() = runTest {
        val state = stateWithDocument()
        files.file.value = FileState.Failed(UiText(Res.string.document_download_failed))
        documents(state).openDocument(1)
        advanceUntilIdle()
        assertEquals(UiText(Res.string.document_download_failed), state.value.error)
        assertTrue(opened.isEmpty())
        assertTrue(state.value.documentDownloads.isEmpty())
    }

    @Test
    fun stahovaniKtereSeNerozbehneSeVzdaPoTricetiSekundach() = runTest {
        val state = stateWithDocument()
        documents(state).openDocument(1)
        advanceTimeBy(29.seconds)
        assertNull(state.value.error)
        assertEquals(mapOf(FILE to 0f), state.value.documentDownloads)

        advanceTimeBy(2.seconds)
        assertEquals(UiText(Res.string.document_download_failed), state.value.error)
        assertTrue(state.value.documentDownloads.isEmpty())
    }

    @Test
    fun beziciStahovaniSmiTrvatDelNezLimitNaRozbeh() = runTest {
        val state = stateWithDocument()
        documents(state).openDocument(1)
        runCurrent()
        files.file.value = FileState.Downloading(progress = 0.1f)
        advanceTimeBy(60.seconds)
        assertNull(state.value.error)

        files.file.value = FileState.Ready("/data/smlouva.pdf")
        advanceUntilIdle()
        assertEquals(1, opened.size)
    }

    @Test
    fun druheTuknutiStahovaniZrusiIVTdlib() = runTest {
        val state = stateWithDocument()
        val documents = documents(state)
        documents.openDocument(1)
        runCurrent()

        documents.openDocument(1)
        runCurrent()
        assertEquals(listOf(FILE), files.cancelled)
        assertTrue(state.value.documentDownloads.isEmpty())

        files.file.value = FileState.Ready("/data/smlouva.pdf")
        advanceUntilIdle()
        assertTrue(opened.isEmpty(), "zrušené stahování už nic neotevře")
    }

    @Test
    fun sdileniZavreNabidkuAPredaSouborSeJmenem() = runTest {
        val state = stateWithDocument()
        files.file.value = FileState.Ready("/data/smlouva.pdf")
        documents(state).shareDocument(1)
        advanceUntilIdle()
        assertEquals(1, menusClosed)
        assertEquals(listOf("/data/smlouva.pdf" to "smlouva.pdf"), shared)
        assertNull(state.value.notice)
    }

    @Test
    fun nepovedeneSdileniDostaneHlasku() = runTest {
        val state = stateWithDocument()
        shares = false
        files.file.value = FileState.Ready("/data/smlouva.pdf")
        documents(state).shareDocument(1)
        advanceUntilIdle()
        assertEquals(UiText(Res.string.media_share_failed), state.value.notice)
    }

    @Test
    fun ulozitJakoHlasiVysledekAZavrenyVyberMlci() = runTest {
        files.file.value = FileState.Ready("/data/smlouva.pdf")
        val expected = mapOf(true to UiText(Res.string.media_saved_as), false to UiText(Res.string.media_save_failed), null to null)
        for ((result, notice) in expected) {
            val state = stateWithDocument()
            val launcher = FakeSaveAs(result)
            documents(state).saveDocumentAs(1, launcher)
            advanceUntilIdle()
            assertEquals(listOf("/data/smlouva.pdf" to "smlouva.pdf"), launcher.launched)
            assertEquals(notice, state.value.notice, "výsledek $result")
        }
        assertEquals(3, menusClosed)
    }

    @Test
    fun zpravaBezSouboruNicNestahuje() = runTest {
        val state = testState(testMessage(1))
        documents(state).openDocument(1)
        documents(state).openDocument(99)
        advanceUntilIdle()
        assertTrue(files.retried.isEmpty())
        assertTrue(state.value.documentDownloads.isEmpty())
    }

    private companion object {
        const val FILE = 5
    }
}
