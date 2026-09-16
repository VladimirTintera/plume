package eu.tintera.plume.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.telegram.FileRepository
import org.koin.compose.koinInject

/** Průběh nahrávání souboru 0f–1f, nebo `null`, když se nenahrává. */
@Composable
fun rememberUploadProgress(fileId: Int): Float? {
    val repository = koinInject<FileRepository>()
    val flow = remember(repository, fileId) { repository.uploadProgress(fileId) }
    val progress by flow.collectAsStateWithLifecycle(initialValue = null)
    return progress
}
