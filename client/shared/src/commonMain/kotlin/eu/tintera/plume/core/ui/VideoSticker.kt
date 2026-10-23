package eu.tintera.plume.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import eu.tintera.plume.core.telegram.FileState
import eu.tintera.plume.core.video.WebmSticker
import eu.tintera.plume.core.video.webmStickersSupported

/**
 * Videosamolepka (`.webm`) podle id souboru: stáhne ji a pustí přes [WebmSticker]. Do té doby,
 * a na platformě, která WebM neumí ([webmStickersSupported]), stojí [placeholder] — statický
 * náhled samolepky. Tam se soubor ani nestahuje.
 */
@Composable
fun VideoSticker(
    fileId: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    placeholder: @Composable () -> Unit,
) {
    if (!webmStickersSupported) {
        Box(modifier) { placeholder() }
        return
    }
    val path = (rememberFileState(fileId) as? FileState.Ready)?.path
    if (path == null) {
        Box(modifier) { placeholder() }
        return
    }
    WebmSticker(
        path = path,
        playing = true,
        contentDescription = contentDescription,
        modifier = modifier,
        placeholder = placeholder,
    )
}
