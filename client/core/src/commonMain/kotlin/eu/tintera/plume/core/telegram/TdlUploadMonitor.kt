package eu.tintera.plume.core.telegram

import dev.g000sha256.tdl.dto.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

/**
 * [UploadMonitor] nad `updateFile`: nahrávání se hlásí týmž updatem jako stahování
 * (`remote.isUploadingActive`, `remote.uploadedSize`), jako průběh u zprávy v
 * [TdlFileRepository.uploadProgress].
 *
 * Updaty i výměna klienta jdou JEDNÍM odběrem (`merge`), takže se do [UploadTracker] nesahá
 * ze dvou korutin naráz a vymazání po výměně klienta nepředběhne poslední update starého.
 */
class TdlUploadMonitor(
    holder: TelegramClientHolder,
    scope: CoroutineScope,
) : UploadMonitor {

    private val tracker = UploadTracker()
    private val _activity = MutableStateFlow(UploadActivity.Idle)
    override val activity: StateFlow<UploadActivity> = _activity.asStateFlow()

    init {
        scope.launch {
            merge(
                holder.updates { fileUpdates }.map { update -> update.file },
                // `fileId` platí jen v jedné databázi; po odhlášení by starý soubor visel navždy.
                holder.replaced.map { null },
            ).collect { file ->
                _activity.value = if (file == null) tracker.clear() else track(file)
            }
        }
    }

    private fun track(file: File): UploadActivity {
        val remote = file.remote
        return tracker.update(
            fileId = file.id,
            uploading = remote.isUploadingActive && !remote.isUploadingCompleted,
            uploadedBytes = remote.uploadedSize,
            totalBytes = file.size.takeIf { it > 0 } ?: file.expectedSize,
        )
    }
}
