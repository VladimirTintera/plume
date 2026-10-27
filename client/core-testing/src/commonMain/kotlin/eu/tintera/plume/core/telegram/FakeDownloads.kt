package eu.tintera.plume.core.telegram

import kotlinx.coroutines.flow.MutableSharedFlow

/** Seznam stahování v paměti pro testy; [changes] posílá test sám. */
class FakeDownloads(
    var items: List<FileDownloadItem> = emptyList(),
    var addResult: Result<Unit> = Result.success(Unit),
) : DownloadsRepository {
    override val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    val added = mutableListOf<Triple<Int, Long, Long>>()
    val paused = mutableListOf<Pair<Int, Boolean>>()
    val allPaused = mutableListOf<Boolean>()
    val removed = mutableListOf<Pair<Int, Boolean>>()
    var lists = 0
    var removeResult: Result<Unit> = Result.success(Unit)

    override suspend fun add(fileId: Int, chatId: Long, messageId: Long): Result<Unit> {
        added += Triple(fileId, chatId, messageId)
        return addResult
    }

    override suspend fun list(offset: String): Result<DownloadsPage> {
        lists++
        return Result.success(
            DownloadsPage(
                items = items,
                activeCount = items.count { !it.isCompleted },
                pausedCount = items.count { it.isPaused },
                completedCount = items.count { it.isCompleted },
                nextOffset = null,
            ),
        )
    }

    override suspend fun setPaused(fileId: Int, paused: Boolean): Result<Unit> {
        this.paused += fileId to paused
        return Result.success(Unit)
    }

    override suspend fun setAllPaused(paused: Boolean): Result<Unit> {
        allPaused += paused
        return Result.success(Unit)
    }

    override suspend fun remove(fileId: Int, deleteFile: Boolean): Result<Unit> {
        removed += fileId to deleteFile
        return removeResult
    }
}

fun testDownload(
    fileId: Int,
    completed: Boolean = false,
    paused: Boolean = false,
    fileName: String = "soubor$fileId.pdf",
    kind: DownloadKind = DownloadKind.Document,
) = FileDownloadItem(
    fileId = fileId,
    chatId = 1,
    messageId = fileId.toLong(),
    chatTitle = "Chat",
    fileName = fileName,
    mimeType = "",
    kind = kind,
    size = 1000,
    addDate = 0,
    completeDate = if (completed) 100 else 0,
    isPaused = paused,
)
