package eu.tintera.plume.core.telegram

import co.touchlab.kermit.Logger
import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.dto.FileDownload
import dev.g000sha256.tdl.dto.Message
import dev.g000sha256.tdl.dto.MessageAnimation
import dev.g000sha256.tdl.dto.MessageAudio
import dev.g000sha256.tdl.dto.MessageDocument
import dev.g000sha256.tdl.dto.MessageVideo
import dev.g000sha256.tdl.toResult
import eu.tintera.plume.core.ui.readString
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.content_file
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

/** Seznam stahování nad TDLib — viz [DownloadsRepository]. */
class TdlDownloadsRepository(private val holder: TelegramClientHolder) : DownloadsRepository {

    private val logger = Logger.withTag("Downloads")
    private val client: TdlClient get() = holder.client

    override val changes: Flow<Unit> = merge(
        holder.updates { fileAddedToDownloadsUpdates }.map { },
        holder.updates { fileDownloadUpdates }.map { },
        holder.updates { fileRemovedFromDownloadsUpdates }.map { },
    )

    override suspend fun add(fileId: Int, chatId: Long, messageId: Long): Result<Unit> =
        client.addFileToDownloads(fileId = fileId, chatId = chatId, messageId = messageId, priority = PRIORITY)
            .toResult()
            .onFailure { logger.w(it) { "addFileToDownloads($fileId) selhalo." } }
            .map { }

    override suspend fun list(offset: String): Result<DownloadsPage> = runCatching {
        val found = client.searchFileDownloads(
            query = "",
            onlyActive = false,
            onlyCompleted = false,
            offset = offset,
            limit = PAGE_SIZE,
        ).toResult().getOrThrow()
        val fallbackName = readString(Res.string.content_file)
        val titles = mutableMapOf<Long, String>()
        DownloadsPage(
            items = found.files.filterNotNull().map { download ->
                val chatId = download.message.chatId
                val title = titles.getOrPut(chatId) { client.getChat(chatId = chatId).toResult().getOrNull()?.title.orEmpty() }
                download.toItem(title, fallbackName)
            },
            activeCount = found.totalCounts.activeCount,
            pausedCount = found.totalCounts.pausedCount,
            completedCount = found.totalCounts.completedCount,
            nextOffset = found.nextOffset.takeIf { it.isNotEmpty() },
        )
    }

    override suspend fun setPaused(fileId: Int, paused: Boolean): Result<Unit> =
        client.toggleDownloadIsPaused(fileId = fileId, isPaused = paused).toResult().map { }

    override suspend fun setAllPaused(paused: Boolean): Result<Unit> =
        client.toggleAllDownloadsArePaused(arePaused = paused).toResult().map { }

    override suspend fun remove(fileId: Int, deleteFile: Boolean): Result<Unit> =
        client.removeFileFromDownloads(fileId = fileId, deleteFromCache = deleteFile).toResult().map { }

    override suspend fun clearCompleted(): Result<Unit> =
        client.removeAllFilesFromDownloads(onlyActive = false, onlyCompleted = true, deleteFromCache = false)
            .toResult().map { }

    private fun FileDownload.toItem(chatTitle: String, fallbackName: String): FileDownloadItem {
        val (name, size) = message.fileNameAndSize(fileId)
        return FileDownloadItem(
            fileId = fileId,
            chatId = message.chatId,
            messageId = message.id,
            chatTitle = chatTitle,
            fileName = name.ifBlank { fallbackName },
            size = size,
            addDate = addDate,
            completeDate = completeDate,
            isPaused = isPaused,
        )
    }

    /** Jméno a velikost souboru ze zprávy; do seznamu se přidávají dokumenty, skladby a videa. */
    private fun Message.fileNameAndSize(fileId: Int): Pair<String, Long> = when (val content = content) {
        is MessageDocument -> content.document.fileName to content.document.document.size()
        is MessageAudio -> content.audio.fileName.ifBlank { content.audio.title } to content.audio.audio.size()
        is MessageVideo -> content.video.fileName to content.video.video.size()
        is MessageAnimation -> content.animation.fileName to content.animation.animation.size()
        else -> "" to 0L
    }.also { if (it.first.isBlank()) logger.d { "Stahování $fileId: zpráva bez jména souboru (${content::class.simpleName})." } }

    private fun dev.g000sha256.tdl.dto.File.size(): Long = if (size > 0) size else expectedSize
}

/** Stejná priorita jako běžné stahování v aplikaci (1 = nejvyšší). */
private const val PRIORITY = 1

private const val PAGE_SIZE = 100
