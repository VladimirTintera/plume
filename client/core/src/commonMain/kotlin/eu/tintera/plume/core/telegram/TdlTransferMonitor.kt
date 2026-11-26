package eu.tintera.plume.core.telegram

import co.touchlab.kermit.Logger
import dev.g000sha256.tdl.dto.File
import dev.g000sha256.tdl.dto.Message
import dev.g000sha256.tdl.dto.MessageAnimation
import dev.g000sha256.tdl.dto.MessageAudio
import dev.g000sha256.tdl.dto.MessageContent
import dev.g000sha256.tdl.dto.MessageDocument
import dev.g000sha256.tdl.dto.MessagePhoto
import dev.g000sha256.tdl.dto.MessageSendingStatePending
import dev.g000sha256.tdl.dto.MessageSticker
import dev.g000sha256.tdl.dto.MessageVideo
import dev.g000sha256.tdl.dto.MessageVideoNote
import dev.g000sha256.tdl.dto.MessageVoiceNote
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * [TransferMonitor] nad `updateFile`: nahrávání i stahování se hlásí týmž updatem
 * (`remote.isUploadingActive` / `local.isDownloadingActive`), jako průběh u zprávy
 * v [TdlFileRepository]. Které stahování se počítá, říká seznam Stahování
 * (`updateFileAddedToDownloads`, `updateFileDownload`, `updateFileRemovedFromDownloads`).
 *
 * Ke zrušení nahrávání ([cancelUploads]) se vedou i rozesílané zprávy s jejich soubory
 * (`updateNewMessage` se stavem odesílání, konec `updateMessageSendSucceeded` / `…Failed`
 * / `updateDeleteMessages`): TDLib nahrávání přílohy zastaví jen smazáním zprávy.
 *
 * Všechny zdroje jdou JEDNÍM odběrem (`merge`), takže vymazání po výměně klienta nepředběhne
 * poslední update starého. Zámek je kvůli [cancelUploads], které čte stav z jiné korutiny.
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

    private val mutex = Mutex()

    /** Rozesílané zprávy (chat, id) a soubory, které nahrávají. */
    private val sending = mutableMapOf<Pair<Long, Long>, List<Int>>()

    private sealed interface Event {
        class FileChanged(val file: File) : Event
        class Listed(val fileId: Int, val listed: Boolean) : Event
        class Sending(val message: Message) : Event
        class Sent(val chatId: Long, val messageIds: List<Long>) : Event
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
                holder.updates { newMessageUpdates }.map { Event.Sending(it.message) },
                holder.updates { messageSendSucceededUpdates }.map { Event.Sent(it.message.chatId, listOf(it.oldMessageId)) },
                holder.updates { messageSendFailedUpdates }.map { Event.Sent(it.message.chatId, listOf(it.oldMessageId)) },
                holder.updates { deleteMessagesUpdates }.map { Event.Sent(it.chatId, it.messageIds.toList()) },
                // `fileId` platí jen v jedné databázi; po odhlášení by starý soubor visel navždy.
                holder.replaced.map { Event.Cleared },
                session.authStage.filter { it == AuthStage.Ready }.transform { emitActiveDownloads(this) },
            ).collect { event -> mutex.withLock { handle(event) } }
        }
    }

    override suspend fun cancelUploads() {
        val targets = mutex.withLock {
            val active = uploadTracker.activeFileIds
            sending.filterValues { files -> files.any { it in active } }.keys
        }
        logger.i { "Ruším odesílání, počet zpráv: ${targets.size}." }
        // Smazání zastaví nahrávání; zpráva zmizí z konverzace updatem jako po křížku u ní.
        targets.groupBy({ it.first }, { it.second }).forEach { (chatId, messageIds) ->
            holder.client.deleteMessages(chatId = chatId, messageIds = messageIds.toLongArray(), revoke = true)
                .toResult()
                .onFailure { logger.w(it) { "Odesílání v chatu $chatId nejde zrušit." } }
        }
    }

    private fun handle(event: Event) {
        when (event) {
            is Event.FileChanged -> {
                val file = event.file
                val remote = file.remote
                val local = file.local
                val total = file.size.takeIf { it > 0 } ?: file.expectedSize
                uploadTracker.update(
                    fileId = file.id,
                    transferring = remote.isUploadingActive && !remote.isUploadingCompleted,
                    transferredBytes = remote.uploadedSize,
                    totalBytes = total,
                )
                publishUploads()
                _downloads.value = downloadTracker.update(
                    fileId = file.id,
                    transferring = local.isDownloadingActive && !local.isDownloadingCompleted,
                    transferredBytes = local.downloadedSize,
                    totalBytes = total,
                )
            }

            is Event.Listed -> _downloads.value = downloadTracker.listed(event.fileId, event.listed)

            is Event.Sending -> {
                val message = event.message
                if (!message.isOutgoing || message.sendingState !is MessageSendingStatePending) return
                val files = message.content.uploadedFileIds()
                if (files.isEmpty()) return
                sending[message.chatId to message.id] = files
                publishUploads()
            }

            is Event.Sent -> {
                event.messageIds.forEach { sending.remove(event.chatId to it) }
                publishUploads()
            }

            Event.Cleared -> {
                uploadTracker.clear()
                sending.clear()
                publishUploads()
                _downloads.value = downloadTracker.clear()
            }
        }
    }

    /** Souhrn nahrávání s počtem souborů, jejichž zprávu známe — jen ty jde zrušit. */
    private fun publishUploads() {
        val summary = uploadTracker.summary()
        val active = uploadTracker.activeFileIds
        val ofMessages = sending.values.flatten().toSet()
        _uploads.value = summary.copy(cancellable = active.count { it in ofMessages })
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

/**
 * Soubory, které rozesílaná zpráva nahrává. Fotka nese všechny velikosti, nahrává se z nich jedna;
 * typ, který tu chybí, se jen nedá zrušit z notifikace (křížek u zprávy funguje dál).
 */
private fun MessageContent.uploadedFileIds(): List<Int> = when (this) {
    is MessagePhoto -> photo.sizes.filterNotNull().map { it.photo.id }
    is MessageVideo -> listOf(video.video.id)
    is MessageDocument -> listOf(document.document.id)
    is MessageAudio -> listOf(audio.audio.id)
    is MessageAnimation -> listOf(animation.animation.id)
    is MessageVoiceNote -> listOf(voiceNote.voice.id)
    is MessageVideoNote -> listOf(videoNote.video.id)
    is MessageSticker -> listOf(sticker.sticker.id)
    else -> emptyList()
}
