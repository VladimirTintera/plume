package eu.tintera.plume.core.telegram

import kotlinx.coroutines.flow.Flow

/**
 * Seznam stahovaných souborů, jak ho vede TDLib (`addFileToDownloads`, `searchFileDownloads`).
 * Patří do něj soubory, které si uživatel z chatu VÝSLOVNĚ stáhl (dokument, skladba) —
 * náhledy, fotky v konverzaci ani streamované video ne, ty TDLib stahuje mimo něj.
 */
interface DownloadsRepository {
    /** Ozve se při každé změně seznamu: přidání, dokončení, pozastavení, odebrání. */
    val changes: Flow<Unit>

    /** Přidá soubor zprávy do seznamu a spustí stahování. */
    suspend fun add(fileId: Int, chatId: Long, messageId: Long): Result<Unit>

    /** Stránka seznamu od [offset] (prázdný = první), nejnovější nahoře. */
    suspend fun list(offset: String = ""): Result<DownloadsPage>

    suspend fun setPaused(fileId: Int, paused: Boolean): Result<Unit>

    suspend fun setAllPaused(paused: Boolean): Result<Unit>

    /** Odebere soubor ze seznamu; s [deleteFile] smaže i stažený soubor z úložiště aplikace. */
    suspend fun remove(fileId: Int, deleteFile: Boolean): Result<Unit>

    /** Vyčistí dokončená stahování ze seznamu; soubory zůstanou. */
    suspend fun clearCompleted(): Result<Unit>
}

/** Soubor v seznamu stahování: odkud je (chat a zpráva), jak se jmenuje a kde se stahování nachází. */
data class FileDownloadItem(
    val fileId: Int,
    val chatId: Long,
    val messageId: Long,
    val chatTitle: String,
    val fileName: String,
    /** Velikost v bajtech; 0 = neznámá. */
    val size: Long,
    val addDate: Int,
    /** Kdy se dostáhlo; 0 = ještě se stahuje (nebo je pozastavené). */
    val completeDate: Int,
    val isPaused: Boolean,
) {
    val isCompleted: Boolean get() = completeDate > 0
}

data class DownloadsPage(
    val items: List<FileDownloadItem>,
    /** Rozestavěná stahování včetně pozastavených. */
    val activeCount: Int,
    val pausedCount: Int,
    val completedCount: Int,
    val nextOffset: String?,
)
