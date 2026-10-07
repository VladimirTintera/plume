package eu.tintera.plume.core.telegram

import co.touchlab.kermit.Logger
import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.dto.File
import dev.g000sha256.tdl.dto.Location
import dev.g000sha256.tdl.toResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
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

    /**
     * Mapy, o které už se žádalo. `getMapThumbnailFile` je lokální, ale mapa se skládá při každém
     * návratu položky do seznamu; `fileId` je pro tentýž výřez pořád stejné. Čistí se
     * s výměnou klienta — `fileId` platí jen v jedné databázi.
     */
    private val maps = mutableMapOf<MapRequest, Int>()

    /** Soubory, na které už request odešel — aby se stahování nespouštělo znovu při každém odběru. */
    private val requested = mutableSetOf<Int>()

    /** Soubory, jejichž dokončení se právě ověřuje dotazem ([confirmFinished]) — nejvýš jedna kontrola na soubor. */
    private val confirming = mutableSetOf<Int>()

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
                    confirming.clear()
                    maps.clear()
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

    override suspend fun retryDownload(fileId: Int) {
        mutex.withLock { requested.remove(fileId) }
        request(fileId)
    }

    override suspend fun cancelDownload(fileId: Int) {
        mutex.withLock { requested.remove(fileId) }
        logger.d { "Ruším stahování souboru $fileId" }
        client.cancelDownloadFile(fileId = fileId, onlyIfPending = false)
            .toResult()
            .onFailure { failure -> logger.w { "cancelDownloadFile($fileId) selhalo: ${failure.message}" } }
    }

    /**
     * Přesune stahování na [offset]. Používá to streamování videa, když si přehrávač řekne
     * o data mimo souvisle stažený úsek — typicky o `moov` atom na konci souboru.
     *
     * Volá se z vlákna přehrávače, takže nic nečeká na výsledek: nový stav dorazí updatem
     * jako u běžného stahování. Opakované volání se stejným offsetem TDLib nevadí.
     */
    override suspend fun downloadFrom(fileId: Int, offset: Long) {
        logger.d { "Stahování souboru $fileId se přesouvá na $offset" }
        client.downloadFile(
            fileId = fileId,
            priority = DEFAULT_PRIORITY,
            offset = offset,
            // 0 = zbytek souboru od offsetu.
            limit = 0,
            synchronous = false,
        )
            .toResult()
            .onFailure { failure -> logger.w { "downloadFile($fileId, offset=$offset) selhalo: ${failure.message}" } }
    }

    override suspend fun mapThumbnail(map: MapRequest): Int? {
        mutex.withLock { maps[map] }?.let { return it }
        return client.getMapThumbnailFile(
            location = Location(latitude = map.latitude, longitude = map.longitude, horizontalAccuracy = 0.0),
            zoom = map.zoom,
            width = map.width,
            height = map.height,
            scale = map.scale,
            chatId = map.chatId,
        )
            .toResult()
            .onFailure { failure -> logger.w { "getMapThumbnailFile($map) selhalo: ${failure.message}" } }
            .getOrNull()
            ?.also { file -> publish(file) }
            ?.id
            ?.also { id -> mutex.withLock { maps[map] = id } }
    }

    private suspend fun request(fileId: Int) {
        val alreadyRequested = mutex.withLock { !requested.add(fileId) }
        if (alreadyRequested) return

        logger.d { "Žádám o soubor $fileId" }
        // Aktuální stav — soubor už může být na disku z dřívějška, pak se nestahuje nic.
        client.getFile(fileId)
            .toResult()
            .onSuccess { current ->
                logger.d {
                    "getFile($fileId): staženo=${current.local.isDownloadingCompleted} " +
                        "stahuje=${current.local.isDownloadingActive} lzeStáhnout=${current.local.canBeDownloaded} " +
                        "cesta='${current.local.path}' ${current.local.downloadedSize}/${current.expectedSize}"
                }
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
                FileState.Downloading(
                    progress = progress,
                    // Rozestavěný soubor už na disku je; streamování z něj čte souvislý úsek.
                    path = local.path.takeIf { it.isNotEmpty() },
                    downloadOffset = local.downloadOffset,
                    downloadedPrefixSize = local.downloadedPrefixSize,
                    totalSize = total,
                )
            }

            else -> FileState.Pending
        }

        stateOf(file.id).value = state

        val fullSize = file.size.takeIf { it > 0 } ?: file.expectedSize
        if (looksFinished(local.isDownloadingActive, local.isDownloadingCompleted, local.downloadedSize, fullSize)) {
            confirmFinished(file.id)
        }

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

    /**
     * Ověří dotazem, že stažený soubor je opravdu hotový — update, který by to řekl, nemusí přijít.
     *
     * TDLib po stažení pozná, že tentýž soubor už má pod jinými id (bot poslal skladbu, která
     * v chatu už byla), a soubory SLOUČÍ. Updaty „hotovo" pak pošle jen pro ta starší id;
     * stahované id dostane jako poslední update ještě „stahuje, 73531 ze 73531" a dál nic.
     * `getFile` o chvíli později přitom hlásí hotovo. Bez téhle kontroly čekal přehrávač
     * skladby na stav, který nikdy nepřišel, a točil se navždy.
     *
     * Ptá se několikrát s rostoucí prodlevou, dokud soubor tak vypadá; dokončení, které dorazí
     * updatem, kontrolu ukončí samo (další dotaz už „vypadá hotově" nesplní).
     */
    private fun confirmFinished(fileId: Int) {
        scope.launch {
            if (!mutex.withLock { confirming.add(fileId) }) return@launch
            val askedClient = client
            try {
                for (wait in FINISHED_RECHECK_DELAYS_MS) {
                    delay(wait)
                    // Po odhlášení patří id jiné databázi.
                    if (client !== askedClient) return@launch
                    val current = askedClient.getFile(fileId).toResult()
                        .onFailure { failure -> logger.w { "getFile($fileId) po stažení selhalo: ${failure.message}" } }
                        .getOrNull() ?: return@launch
                    val local = current.local
                    val total = current.size.takeIf { it > 0 } ?: current.expectedSize
                    val stillPending = looksFinished(local.isDownloadingActive, local.isDownloadingCompleted, local.downloadedSize, total)
                    if (!stillPending) {
                        logger.d { "Soubor $fileId po stažení: hotovo=${local.isDownloadingCompleted}" }
                        publish(current)
                        return@launch
                    }
                }
                logger.w { "Soubor $fileId je stažený celý, ale TDLib ho pořád nehlásí jako hotový." }
            } finally {
                mutex.withLock { confirming.remove(fileId) }
            }
        }
    }

    /** Jeden stav na soubor. Vždy pod zámkem — mapa je sdílená mezi odběry a update vláknem. */
    private suspend fun stateOf(fileId: Int): MutableStateFlow<FileState> =
        mutex.withLock { states.getOrPut(fileId) { MutableStateFlow(FileState.Pending) } }

    private companion object {
        const val DEFAULT_PRIORITY = 1

        /** Prodlevy před dotazy na soubor, který je stažený celý, a přesto se ještě „stahuje". */
        val FINISHED_RECHECK_DELAYS_MS = listOf(250L, 1_000L, 3_000L, 10_000L)
    }
}

/**
 * Soubor je stažený celý, a přesto ho TDLib hlásí jako rozestavěný — viz `confirmFinished`.
 * Neznámá velikost (`total` 0) se za hotovou nepovažuje.
 */
internal fun looksFinished(active: Boolean, completed: Boolean, downloadedSize: Long, total: Long): Boolean =
    active && !completed && total > 0 && downloadedSize >= total
