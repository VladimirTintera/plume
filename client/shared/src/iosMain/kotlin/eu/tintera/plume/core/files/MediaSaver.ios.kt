package eu.tintera.plume.core.files

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import co.touchlab.kermit.Logger
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSURL
import platform.Photos.PHAccessLevelAddOnly
import platform.Photos.PHAssetChangeRequest
import platform.Photos.PHAuthorizationStatusAuthorized
import platform.Photos.PHAuthorizationStatusLimited
import platform.Photos.PHPhotoLibrary
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.coroutines.resume

/**
 * iOS: nový záznam v knihovně fotek (`PHAssetChangeRequest`). Knihovna si soubor zkopíruje sama,
 * takže nevadí, že soubor TDLib později zmizí. Stačí oprávnění jen k přidávání
 * (`NSPhotoLibraryAddUsageDescription`) — číst knihovnu nepotřebujeme. Jméno souboru si knihovna
 * fotek nevede, takže `fileName` se tu nepoužije.
 */
actual class MediaSaver {

    private val logger = Logger.withTag("MediaSaver")

    actual suspend fun save(path: String, fileName: String, isVideo: Boolean): SavedMedia? =
        if (saveToLibrary(path, isVideo)) SavedMedia() else null

    private suspend fun saveToLibrary(path: String, isVideo: Boolean): Boolean = suspendCancellableCoroutine { continuation ->
        val url = NSURL.fileURLWithPath(path)
        PHPhotoLibrary.sharedPhotoLibrary().performChanges(
            changeBlock = {
                if (isVideo) {
                    PHAssetChangeRequest.creationRequestForAssetFromVideoAtFileURL(url)
                } else {
                    PHAssetChangeRequest.creationRequestForAssetFromImageAtFileURL(url)
                }
            },
            completionHandler = { success, error ->
                if (!success) logger.w { "Soubor se nepodařilo uložit do knihovny fotek: $path (${error?.localizedDescription})" }
                continuation.resume(success)
            },
        )
    }
}

@Composable
actual fun rememberSavePermission(): SavePermission = remember {
    object : SavePermission {
        override fun request(onResult: (granted: Boolean) -> Unit) {
            // Odpověď chodí na libovolné frontě; výsledek se předává zpátky na hlavní.
            PHPhotoLibrary.requestAuthorizationForAccessLevel(PHAccessLevelAddOnly) { status ->
                val granted = status == PHAuthorizationStatusAuthorized || status == PHAuthorizationStatusLimited
                dispatch_async(dispatch_get_main_queue()) { onResult(granted) }
            }
        }
    }
}

actual val autosavesToGallery: Boolean = true

actual fun revealInFolder(path: String): Boolean = false
