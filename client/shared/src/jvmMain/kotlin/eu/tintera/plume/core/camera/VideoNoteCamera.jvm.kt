package eu.tintera.plume.core.camera

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import eu.tintera.plume.core.audio.RecordPermission
import eu.tintera.plume.core.telegram.OutgoingVideoNote

/** Kameru desktop zatím nemá; `null` = video kolečko se v konverzaci nenabídne. */
@Composable
actual fun rememberVideoNoteCamera(): VideoNoteCamera? = null

@Composable
actual fun rememberVideoNotePermission(): RecordPermission = remember {
    object : RecordPermission {
        override fun request(onResult: (granted: Boolean) -> Unit) = onResult(false)
    }
}

actual suspend fun prepareVideoNote(path: String): OutgoingVideoNote =
    throw UnsupportedOperationException("Video kolečka na desktopu zatím nejsou.")
