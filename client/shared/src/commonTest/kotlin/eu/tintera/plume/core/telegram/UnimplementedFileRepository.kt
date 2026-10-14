package eu.tintera.plume.core.telegram

import kotlinx.coroutines.flow.Flow
import eu.tintera.plume.core.ui.UiText

/**
 * FileRepository pro testy: každé volání, které test nepřepsal, vyhodí chybu se jménem metody — test tak
 * hned řekne, na co ještě sahá. Vygenerované z rozhraní; po změně rozhraní stačí doplnit chybějící
 * člen (překladač ho vyjmenuje).
 */
open class UnimplementedFileRepository : FileRepository {
    override fun file(fileId: Int) : Flow<FileState> = error("file")
    override fun observe(fileId: Int) : Flow<FileState> = error("observe")
    override fun uploadProgress(fileId: Int) : Flow<Float?> = error("uploadProgress")
    override suspend fun retryDownload(fileId: Int): Unit = error("retryDownload")
    override suspend fun cancelDownload(fileId: Int): Unit = error("cancelDownload")
    override suspend fun downloadFrom(fileId: Int, offset: Long): Unit = error("downloadFrom")
    override suspend fun mapThumbnail(map: MapRequest) : Int? = error("mapThumbnail")
}
