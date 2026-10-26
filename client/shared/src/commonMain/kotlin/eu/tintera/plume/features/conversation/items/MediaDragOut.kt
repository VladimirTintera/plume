package eu.tintera.plume.features.conversation.items

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import eu.tintera.plume.core.files.savedFileName
import eu.tintera.plume.core.telegram.FileRepository
import eu.tintera.plume.core.telegram.FileState
import eu.tintera.plume.core.ui.DraggedFile
import eu.tintera.plume.core.ui.fileDragSource
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.TimeZone
import org.koin.compose.koinInject

/**
 * Přílohy v konverzaci jdou vytáhnout myší z okna ven (`fileDragSource`). Poskytuje ji
 * konverzace jen tam, kde to platforma umí a chat obsah nechrání; `null` = tah ven není
 * (telefon, chráněný chat, náhledy mimo konverzaci). [sentAt] dá čas zprávy — fotka a video
 * podle něj dostanou jméno ([savedFileName]).
 */
internal class MediaDragOut(val sentAt: (messageId: Long) -> Int?)

internal val LocalMediaDragOut = compositionLocalOf<MediaDragOut?> { null }

/**
 * Tah přílohy zprávy [messageId] ven. Táhne se soubor [fileId], a když ještě není stažený,
 * [fallbackFileId] (fotka: ostrá verze, jinak ta z bubliny). Nestažený soubor se netáhne —
 * stav se jen sleduje (`observe`), tah ven kvůli bublině nic stahovat nesmí. [originalName]
 * je jméno souboru od odesílatele; fotka a video ho nemají.
 */
@Composable
internal fun Modifier.mediaDragOut(
    messageId: Long,
    fileId: Int,
    fallbackFileId: Int? = null,
    originalName: String? = null,
    enabled: Boolean = true,
): Modifier {
    val drag = LocalMediaDragOut.current
    if (drag == null || !enabled) return this
    val files = koinInject<FileRepository>()
    val primary by remember(fileId) { files.observe(fileId) }.collectAsState(null)
    val fallback by remember(fallbackFileId) { fallbackFileId?.let(files::observe) ?: flowOf(null) }.collectAsState(null)
    return fileDragSource {
        val ready = primary as? FileState.Ready ?: fallback as? FileState.Ready ?: return@fileDragSource null
        val sentAt = drag.sentAt(messageId) ?: return@fileDragSource null
        DraggedFile(ready.path, savedFileName(originalName, ready.path, sentAt, TimeZone.currentSystemDefault()))
    }
}
