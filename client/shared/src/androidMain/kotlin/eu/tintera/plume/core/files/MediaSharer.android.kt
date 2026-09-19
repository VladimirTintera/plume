package eu.tintera.plume.core.files

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val logger = Logger.withTag("MediaSharer")

/** Typ podle přípony; bez něj by příjemci nabízeli „otevřít jako text". */
internal fun mimeTypeOf(fileName: String): String =
    MimeTypeMap.getSingleton().getMimeTypeFromExtension(File(fileName).extension.lowercase())
        ?: "application/octet-stream"

/**
 * Android: `ACTION_SEND` přes výběr aplikací, soubor přes `FileProvider` (`…documents`,
 * adresář `cache/shared`).
 *
 * Oprávnění ke čtení nese intent dvakrát: v `ClipData` (výběr aplikací ho předá dál, příznak
 * v extras by nepředal) a výslovným `grantUriPermission` každému, kdo sdílení přijme — tatáž
 * past jako u otevírání souborů: část aplikací čte soubor z jiné své části a na grant
 * z intentu nedosáhne.
 */
actual class MediaSharer(private val context: Context) {

    actual suspend fun share(path: String, fileName: String): Boolean {
        val copy = withContext(Dispatchers.IO) {
            runCatching {
                val directory = File(context.cacheDir, SHARED_DIRECTORY)
                // Předchozí sdílení už příjemce dočetl; hromadit kopie by jen plnilo cache.
                directory.deleteRecursively()
                directory.mkdirs()
                File(path).copyTo(File(directory, fileName), overwrite = true)
            }.onFailure { logger.w(it) { "Soubor $path se nepodařilo připravit ke sdílení." } }.getOrNull()
        } ?: return false

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.documents", copy)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mimeTypeOf(fileName)
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(fileName, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.packageManager.queryIntentActivities(send, 0).forEach { target ->
            context.grantUriPermission(target.activityInfo.packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return try {
            context.startActivity(
                Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            true
        } catch (failure: ActivityNotFoundException) {
            logger.w(failure) { "Sdílení nemá kdo přijmout." }
            false
        }
    }

    private companion object {
        const val SHARED_DIRECTORY = "shared"
    }
}

/** `ACTION_CREATE_DOCUMENT` s typem podle souboru — vestavěný `CreateDocument` má typ napevno. */
private class CreateDocument : ActivityResultContract<String, Uri?>() {
    override fun createIntent(context: Context, input: String): Intent =
        Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(mimeTypeOf(input))
            .putExtra(Intent.EXTRA_TITLE, input)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        intent?.data?.takeIf { resultCode == Activity.RESULT_OK }
}

@Composable
actual fun rememberSaveAs(): SaveAsLauncher? {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Co se má po výběru umístění zkopírovat; launcher vrací jen URI.
    val pending = remember { arrayOfNulls<Pair<String, (Boolean?) -> Unit>>(1) }
    val launcher = rememberLauncherForActivityResult(CreateDocument()) { uri ->
        val (path, onResult) = pending[0] ?: return@rememberLauncherForActivityResult
        pending[0] = null
        if (uri == null) {
            onResult(null)
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                runCatching {
                    checkNotNull(context.contentResolver.openOutputStream(uri)) { "Umístění nejde otevřít." }.use { output ->
                        File(path).inputStream().use { it.copyTo(output) }
                    }
                }.onFailure { logger.w(it) { "Soubor $path se nepodařilo uložit do $uri." } }.isSuccess
            }
            onResult(saved)
        }
    }
    return remember(launcher) {
        object : SaveAsLauncher {
            override fun launch(path: String, fileName: String, onResult: (saved: Boolean?) -> Unit) {
                pending[0] = path to onResult
                launcher.launch(fileName)
            }
        }
    }
}

actual val saveAsSupported: Boolean = true
