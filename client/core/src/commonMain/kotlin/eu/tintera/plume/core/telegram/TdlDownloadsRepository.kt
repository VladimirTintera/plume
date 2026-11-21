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
import org.jetbrains.compose.resources.getString
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
        val fallbackName = getString(Res.string.content_file)
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

    private fun FileDownload.toItem(chatTitle: String, fallbackName: String): FileDownloadItem {
        val file = message.fileInfo(fileId)
        return FileDownloadItem(
            fileId = fileId,
            chatId = message.chatId,
            messageId = message.id,
            chatTitle = chatTitle,
            fileName = file.name.ifBlank { fallbackName },
            mimeType = file.mimeType,
            kind = file.kind,
            size = file.size,
            addDate = addDate,
            completeDate = completeDate,
            isPaused = isPaused,
        )
    }

    /** Jméno, typ a velikost souboru ze zprávy; do seznamu se přidávají dokumenty, skladby a videa. */
    private fun Message.fileInfo(fileId: Int): DownloadedFile = when (val content = content) {
        is MessageDocument -> content.document.let {
            DownloadedFile(it.fileName, it.mimeType, DownloadKind.Document, it.document.size())
        }
        is MessageAudio -> content.audio.let {
            DownloadedFile(it.fileName.ifBlank { it.title }, it.mimeType, DownloadKind.Audio, it.audio.size())
        }
        is MessageVideo -> content.video.let {
            DownloadedFile(it.fileName, it.mimeType, DownloadKind.Video, it.video.size())
        }
        is MessageAnimation -> content.animation.let {
            DownloadedFile(it.fileName, it.mimeType, DownloadKind.Video, it.animation.size())
        }
        else -> DownloadedFile("", "", DownloadKind.Document, 0L)
    }.also { if (it.name.isBlank()) logger.d { "Stahování $fileId: zpráva bez jména souboru (${content::class.simpleName})." } }

    private class DownloadedFile(val name: String, val mimeType: String, val kind: DownloadKind, val size: Long)

    private fun dev.g000sha256.tdl.dto.File.size(): Long = if (size > 0) size else expectedSize
}

/** Stejná priorita jako běžné stahování v aplikaci (1 = nejvyšší). */
private const val PRIORITY = 1

private const val PAGE_SIZE = 100
