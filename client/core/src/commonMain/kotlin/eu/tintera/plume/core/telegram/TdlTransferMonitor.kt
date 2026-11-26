package eu.tintera.plume.core.telegram

import co.touchlab.kermit.Logger
import dev.g000sha256.tdl.dto.File
import dev.g000sha256.tdl.toResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch

/**
 * [TransferMonitor] nad `updateFile`: nahrávání i stahování se hlásí týmž updatem
 * (`remote.isUploadingActive` / `local.isDownloadingActive`), jako průběh u zprávy
 * v [TdlFileRepository]. Které stahování se počítá, říká seznam Stahování
 * (`updateFileAddedToDownloads`, `updateFileDownload`, `updateFileRemovedFromDownloads`).
 *
 * Všechny zdroje jdou JEDNÍM odběrem (`merge`), takže se do trackerů nesahá ze dvou korutin
 * naráz a vymazání po výměně klienta nepředběhne poslední update starého.
 */
class TdlTransferMonitor(
    private val holder: TelegramClientHolder,
    session: TelegramSession,
    scope: CoroutineScope,
) : TransferMonitor {

    private val logger = Logger.withTag("Transfers")

    private val uploadTracker = TransferTracker()
    private val downloadTracker = TransferTracker(onlyListed = true)
    private val _uploads = MutableStateFlow(TransferActivity.Idle)
    private val _downloads = MutableStateFlow(TransferActivity.Idle)
    override val uploads: StateFlow<TransferActivity> = _uploads.asStateFlow()
    override val downloads: StateFlow<TransferActivity> = _downloads.asStateFlow()

    private sealed interface Event {
        class FileChanged(val file: File) : Event
        class Listed(val fileId: Int, val listed: Boolean) : Event
        data object Cleared : Event
    }

    init {
        scope.launch {
            merge(
                holder.updates { fileUpdates }.map { Event.FileChanged(it.file) },
                // Dokončený soubor v seznamu zůstává („Nedávno staženo"), ale už se nepřenáší.
                holder.updates { fileAddedToDownloadsUpdates }
                    .map { Event.Listed(it.fileDownload.fileId, listed = it.fileDownload.completeDate == 0) },
                holder.updates { fileDownloadUpdates }.map { Event.Listed(it.fileId, listed = it.completeDate == 0) },
                holder.updates { fileRemovedFromDownloadsUpdates }.map { Event.Listed(it.fileId, listed = false) },
                // `fileId` platí jen v jedné databázi; po odhlášení by starý soubor visel navždy.
                holder.replaced.map { Event.Cleared },
                session.authStage.filter { it == AuthStage.Ready }.transform { emitActiveDownloads(this) },
            ).collect(::handle)
        }
    }

    private fun handle(event: Event) {
        when (event) {
            is Event.FileChanged -> {
                val file = event.file
                val remote = file.remote
                val local = file.local
                val total = file.size.takeIf { it > 0 } ?: file.expectedSize
                _uploads.value = uploadTracker.update(
                    fileId = file.id,
                    transferring = remote.isUploadingActive && !remote.isUploadingCompleted,
                    transferredBytes = remote.uploadedSize,
                    totalBytes = total,
                )
                _downloads.value = downloadTracker.update(
                    fileId = file.id,
                    transferring = local.isDownloadingActive && !local.isDownloadingCompleted,
                    transferredBytes = local.downloadedSize,
                    totalBytes = total,
                )
            }

            is Event.Listed -> _downloads.value = downloadTracker.listed(event.fileId, event.listed)

            Event.Cleared -> {
                _uploads.value = uploadTracker.clear()
                _downloads.value = downloadTracker.clear()
            }
        }
    }

    /**
     * Rozestahované soubory ze seznamu, který TDLib vede v databázi. Updaty seznamu chodí až po
     * jeho prvním načtení, a stahování, které TDLib po startu sám obnoví, by jinak nikdo neohlásil.
     */
    private suspend fun emitActiveDownloads(collector: FlowCollector<Event>) {
        val client = holder.client
        var offset = ""
        do {
            val page = client.searchFileDownloads(
                query = "",
                onlyActive = true,
                onlyCompleted = false,
                offset = offset,
                limit = PAGE_SIZE,
            ).toResult().onFailure { logger.w(it) { "Rozestahované soubory nejde načíst." } }.getOrNull() ?: return
            for (download in page.files.filterNotNull()) {
                collector.emit(Event.Listed(download.fileId, listed = true))
                client.getFile(fileId = download.fileId).toResult().getOrNull()?.let { collector.emit(Event.FileChanged(it)) }
            }
            offset = page.nextOffset
        } while (offset.isNotEmpty())
    }

    private companion object {
        const val PAGE_SIZE = 100
    }
}
