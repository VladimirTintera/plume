package eu.tintera.plume.core.telegram

import co.touchlab.kermit.Logger
import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.dto.File
import dev.g000sha256.tdl.toResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.file_download_failed
import eu.tintera.plume.resources.file_unavailable

/**
 * Stahování souborů nad TDLib.
 *
 * Drží mapu `fileId → StateFlow<FileState>`. Jeden stav na soubor znamená, že když tentýž obrázek
 * visí na dvou místech (náhled v seznamu i v konverzaci), sdílí jedno stahování a jeden průběh.
 */
class TdlFileRepository(
    private val holder: TelegramClientHolder,
    private val scope: CoroutineScope,
) : FileRepository {

    private val logger = Logger.withTag("Files")

    private val client: TdlClient get() = holder.client

    private val states = mutableMapOf<Int, MutableStateFlow<FileState>>()
    private val uploads = mutableMapOf<Int, MutableStateFlow<Float?>>()
    private val mutex = Mutex()

    /** Soubory, na které už request odešel — aby se stahování nespouštělo znovu při každém odběru. */
    private val requested = mutableSetOf<Int>()

    init {
        // Průběh chodí updaty pro VŠECHNY soubory, ne jen ty, o které jsme požádali.
        scope.launch {
            holder.updates { fileUpdates }.collect { update -> publish(update.file) }
        }

        // `fileId` platí jen v rámci jedné databáze TDLib. Po odhlášení dostane nový účet čísla
        // znovu od začátku, takže by stará mapa vydávala cizí soubory pod novými id.
        scope.launch {
            holder.replaced.collect {
                mutex.withLock {
                    states.clear()
                    uploads.clear()
                    requested.clear()
                }
            }
        }
    }

    /**
     * Flow se staví líně (`flow { }`), aby se k mapě stavů sahalo **jen uvnitř korutiny**, kde
     * se dá vzít zámek. Kdyby se mapa četla přímo v těle funkce, sahalo by se na ni z kompozice
     * (hlavní vlákno) i z odběru `fileUpdates` (Dispatchers.Default) najednou.
     */
    override fun file(fileId: Int): Flow<FileState> = flow {
        // Stahování se spouští jako vedlejší efekt odběru — UI tak nemusí volat nic navíc
        // a nemůže se stát, že se obrázek nikdy nezačne stahovat, protože to někdo zapomněl.
        scope.launch { request(fileId) }
        emitAll(stateOf(fileId).asStateFlow())
    }

    override fun uploadProgress(fileId: Int): Flow<Float?> = flow {
        emitAll(uploadStateOf(fileId).asStateFlow())
    }

    private suspend fun uploadStateOf(fileId: Int): MutableStateFlow<Float?> =
        mutex.withLock { uploads.getOrPut(fileId) { MutableStateFlow(null) } }

    private suspend fun request(fileId: Int) {
        val alreadyRequested = mutex.withLock { !requested.add(fileId) }
        if (alreadyRequested) return

        // Aktuální stav — soubor už může být na disku z dřívějška, pak se nestahuje nic.
        client.getFile(fileId)
            .toResult()
            .onSuccess { current ->
                publish(current)
                if (current.local.isDownloadingCompleted) return@onSuccess

                if (!current.local.canBeDownloaded) {
                    stateOf(fileId).value = FileState.Failed(UiText(Res.string.file_download_failed))
                    return@onSuccess
                }

                client.downloadFile(
                    fileId = fileId,
                    // 1 = nejvyšší priorita. Řadí fronty, když je ke stažení víc věcí najednou.
                    priority = DEFAULT_PRIORITY,
                    offset = 0,
                    // 0 = celý soubor.
                    limit = 0,
                    // `false` = vrať se hned, průběh přijde updaty. S `true` by volání čekalo
                    // na dokončení celého stahování a UI by mezitím nemělo co ukázat.
                    synchronous = false,
                )
                    .toResult()
                    .onFailure { failure ->
                        logger.w { "downloadFile($fileId) selhalo: ${failure.message}" }
                        stateOf(fileId).value =
                            FileState.Failed(failure.userMessage(Res.string.file_download_failed))
                    }
            }
            .onFailure { failure ->
                logger.w { "getFile($fileId) selhalo: ${failure.message}" }
                stateOf(fileId).value = FileState.Failed(failure.userMessage(Res.string.file_unavailable))
            }
    }

    private suspend fun publish(file: File) {
        val local = file.local

        val state = when {
            // Cesta je vyplněná i u rozestavěného souboru, takže samotné `path.isNotEmpty()`
            // jako podmínka hotovosti nestačí — musí se ptát na `isDownloadingCompleted`.
            local.isDownloadingCompleted && local.path.isNotEmpty() -> FileState.Ready(local.path)

            local.isDownloadingActive -> {
                val total = file.size.takeIf { it > 0 } ?: file.expectedSize
                val progress = if (total > 0) {
                    (local.downloadedSize.toDouble() / total.toDouble()).toFloat().coerceIn(0f, 1f)
                } else {
                    0f
                }
                FileState.Downloading(progress)
            }

            else -> FileState.Pending
        }

        stateOf(file.id).value = state

        // Nahrávání se hlásí stejným updatem. Mapa se plní jen u souborů, které se nahrávají nebo
        // o které si někdo řekl — ne u každého staženého obrázku.
        val remote = file.remote
        val uploading = remote.isUploadingActive && !remote.isUploadingCompleted
        val tracked = mutex.withLock { uploads[file.id] }
        if (uploading || tracked != null) {
            val total = file.size.takeIf { it > 0 } ?: file.expectedSize
            uploadStateOf(file.id).value = if (uploading && total > 0) {
                (remote.uploadedSize.toDouble() / total.toDouble()).toFloat().coerceIn(0f, 1f)
            } else if (uploading) {
                0f
            } else {
                null
            }
        }
    }

    /** Jeden stav na soubor. Vždy pod zámkem — mapa je sdílená mezi odběry a update vláknem. */
    private suspend fun stateOf(fileId: Int): MutableStateFlow<FileState> =
        mutex.withLock { states.getOrPut(fileId) { MutableStateFlow(FileState.Pending) } }

    private companion object {
        const val DEFAULT_PRIORITY = 1
    }
}
