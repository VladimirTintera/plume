package eu.tintera.plume.core.files

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Android: kopie do `MediaStore`, do `Pictures/Plume` nebo `Movies/Plume`.
 *
 * Od Androidu 10 se zapisuje přes `RELATIVE_PATH` bez oprávnění; položka je během kopírování
 * `IS_PENDING`, aby ji galerie neukázala napůl zapsanou, a při selhání se smaže. Starší systémy
 * zapisují do veřejného adresáře (s `WRITE_EXTERNAL_STORAGE`) a soubor ohlásí skeneru médií,
 * jinak by se v galerii objevil až po restartu.
 */
actual class MediaSaver(private val context: Context) {

    private val logger = Logger.withTag("MediaSaver")

    actual suspend fun save(path: String, fileName: String, isVideo: Boolean): SavedMedia? =
        if (saveToMediaStore(path, fileName, isVideo)) SavedMedia() else null

    private suspend fun saveToMediaStore(path: String, fileName: String, isVideo: Boolean): Boolean = withContext(Dispatchers.IO) {
        val source = File(path)
        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(File(fileName).extension.lowercase())
            ?: if (isVideo) "video/mp4" else "image/jpeg"
        val directory = if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveToMediaStore(source, fileName, mimeType, directory, isVideo)
            } else {
                saveToPublicDirectory(source, fileName, mimeType, directory)
            }
        }
            .onFailure { logger.w(it) { "Soubor se nepodařilo uložit do galerie: $path" } }
            .isSuccess
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private fun saveToMediaStore(source: File, fileName: String, mimeType: String, directory: String, isVideo: Boolean) {
        val resolver = context.contentResolver
        val collection = if (isVideo) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val values = ContentValues().apply {
            // Stejné jméno podruhé systém sám očísluje, nic se nepřepíše.
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "$directory/$ALBUM")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = checkNotNull(resolver.insert(collection, values)) { "MediaStore položku nezaložil." }
        try {
            checkNotNull(resolver.openOutputStream(uri)) { "Položku nejde otevřít pro zápis." }.use { output ->
                source.inputStream().use { it.copyTo(output) }
            }
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (failure: Exception) {
            resolver.delete(uri, null, null)
            throw failure
        }
    }

    @Suppress("DEPRECATION") // Veřejný adresář je pro Android 9 a starší jediná cesta.
    private fun saveToPublicDirectory(source: File, fileName: String, mimeType: String, directory: String) {
        val target = File(Environment.getExternalStoragePublicDirectory(directory), ALBUM)
            .apply { mkdirs() }
            .let { uniqueFile(it, fileName) }
        source.copyTo(target)
        MediaScannerConnection.scanFile(context, arrayOf(target.path), arrayOf(mimeType), null)
    }

    /** `foto.jpg`, `foto (1).jpg`, … — to, co `MediaStore` od Androidu 10 dělá sám. */
    private fun uniqueFile(directory: File, name: String): File {
        val base = name.substringBeforeLast('.')
        val extension = name.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
        return generateSequence(0) { it + 1 }
            .map { index -> File(directory, if (index == 0) name else "$base ($index)$extension") }
            .first { !it.exists() }
    }

    private companion object {
        const val ALBUM = "Plume"
    }
}

@Composable
actual fun rememberSavePermission(): SavePermission {
    val context = LocalContext.current
    val pending = remember { arrayOfNulls<(Boolean) -> Unit>(1) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pending[0]?.invoke(granted)
        pending[0] = null
    }
    return remember(launcher) {
        object : SavePermission {
            override fun request(onResult: (granted: Boolean) -> Unit) {
                val needed = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                    PackageManager.PERMISSION_GRANTED
                if (!needed) {
                    onResult(true)
                } else {
                    pending[0] = onResult
                    launcher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }
        }
    }
}

actual val autosavesToGallery: Boolean = true

actual fun revealInFolder(path: String): Boolean = false
