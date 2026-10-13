package eu.tintera.plume.core.files

import eu.tintera.plume.core.settings.AutoDownloadLevel
import eu.tintera.plume.core.settings.limits
import eu.tintera.plume.core.telegram.AutosaveCandidate
import eu.tintera.plume.core.telegram.AutosaveChatType
import eu.tintera.plume.core.telegram.AutosaveRepository
import eu.tintera.plume.core.telegram.AutosaveScope
import eu.tintera.plume.core.telegram.AutosaveScopeSettings
import eu.tintera.plume.core.telegram.AutosaveSettings
import eu.tintera.plume.core.telegram.FileRepository
import eu.tintera.plume.core.telegram.FileState
import eu.tintera.plume.core.telegram.MapRequest
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.autosave_load_failed
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Ukládání do galerie: stáhne a uloží, počká na cizí stažení, chybu nechá být. */
class AutosaveWorkerTest {

    private val photosInPrivate = AutosaveSettings(
        privateChats = AutosaveScopeSettings(photos = true, videos = false, maxVideoBytes = 0),
        groups = AutosaveScopeSettings(photos = false, videos = false, maxVideoBytes = 0),
        channels = AutosaveScopeSettings(photos = false, videos = false, maxVideoBytes = 0),
        exceptions = emptyList(),
    )

    @Test
    fun fotkaZeSoukromehoChatuSeStahneAUlozi() = runTest {
        val env = Env(this, level = AutoDownloadLevel.High)
        env.autosave.incoming.emit(photo(chatType = AutosaveChatType.Private))
        runCurrent()

        assertEquals(listOf(7), env.files.downloaded)
        assertEquals(listOf("/tdlib/photo.jpg" to "Plume_19700101_000000.jpg"), env.saved)
    }

    @Test
    fun fotkaZeSkupinySeNeukladaANicNestahuje() = runTest {
        val env = Env(this, level = AutoDownloadLevel.High)
        env.autosave.incoming.emit(photo(chatType = AutosaveChatType.Group))
        runCurrent()

        assertTrue(env.files.downloaded.isEmpty())
        assertTrue(env.saved.isEmpty())
    }

    @Test
    fun bezAutomatickehoStahovaniSeCekaNaCiziStazeni() = runTest {
        val env = Env(this, level = AutoDownloadLevel.Off)
        env.autosave.incoming.emit(photo(chatType = AutosaveChatType.Private))
        runCurrent()
        assertTrue(env.files.downloaded.isEmpty(), "Kvůli ukládání se nic stahovat nemá.")
        assertTrue(env.saved.isEmpty())

        // Uživatel chat otevřel a fotka se stáhla.
        env.files.observed.value = FileState.Ready("/tdlib/photo.jpg")
        runCurrent()
        assertEquals(1, env.saved.size)
    }

    @Test
    fun nestazenySouborSeNeulozi() = runTest {
        val env = Env(this, level = AutoDownloadLevel.High, downloadResult = FileState.Failed(UiText(Res.string.autosave_load_failed)))
        env.autosave.incoming.emit(photo(chatType = AutosaveChatType.Private))
        runCurrent()

        assertTrue(env.saved.isEmpty())
    }

    private fun photo(chatType: AutosaveChatType) =
        AutosaveCandidate(chatId = 1, chatType = chatType, fileId = 7, isVideo = false, size = 1000, date = 0)

    private inner class Env(test: TestScope, level: AutoDownloadLevel, downloadResult: FileState = FileState.Ready("/tdlib/photo.jpg")) {
        val autosave = WorkerAutosave(photosInPrivate)
        val files = WorkerFiles(downloadResult)
        val saved = mutableListOf<Pair<String, String>>()

        init {
            AutosaveWorker(
                autosave = autosave,
                files = files,
                save = { path, name, _ -> saved += path to name; true },
                limits = { level.limits },
                scope = test.backgroundScope,
                zone = { TimeZone.UTC },
            ).start()
            test.runCurrent()
        }
    }
}

private class WorkerAutosave(private val settings: AutosaveSettings) : AutosaveRepository {
    override val incoming = MutableSharedFlow<AutosaveCandidate>()
    override suspend fun settings(): Result<AutosaveSettings> = Result.success(settings)
    override suspend fun set(scope: AutosaveScope, settings: AutosaveScopeSettings?): Result<Unit> = Result.success(Unit)
}

private class WorkerFiles(private val downloadResult: FileState) : FileRepository {
    val downloaded = mutableListOf<Int>()
    val observed = MutableStateFlow<FileState>(FileState.Pending)

    override fun file(fileId: Int): Flow<FileState> {
        downloaded += fileId
        return flowOf(FileState.Downloading(0.5f), downloadResult)
    }
    override fun observe(fileId: Int): Flow<FileState> = observed
    override fun uploadProgress(fileId: Int): Flow<Float?> = flowOf(null)
    override suspend fun retryDownload(fileId: Int) = Unit
    override suspend fun cancelDownload(fileId: Int) = Unit
    override suspend fun downloadFrom(fileId: Int, offset: Long) = Unit
    override suspend fun mapThumbnail(map: MapRequest): Int? = null
}
