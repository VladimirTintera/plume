package eu.tintera.plume.core.files

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import co.touchlab.kermit.Logger
import java.io.File

/**
 * Android: `ACTION_VIEW` přes `FileProvider`.
 *
 * **Cesta k souboru se předat nedá** — od Androidu 7 je `file://` URI mimo aplikaci zakázané
 * (`FileUriExposedException`). Soubor se proto vydává přes providera a oprávnění se uděluje
 * **jen na to jedno URI a jen na jedno otevření** (`FLAG_GRANT_READ_URI_PERMISSION`); adresář
 * TDLib tím veřejný není.
 *
 * Bez aplikace, která by typ otevřela, se vrací `false` a zavolá se snackbar — systém by sám
 * jen vyhodil výjimku.
 */
actual class FileOpener(private val context: Context) {

    actual fun open(path: String, mimeType: String): Boolean {
        val uri = try {
            FileProvider.getUriForFile(context, "${context.packageName}.documents", File(path))
        } catch (failure: IllegalArgumentException) {
            // Soubor mimo adresáře, které provider vydává — chyba konfigurace, ne uživatele.
            Logger.withTag("Files").e(failure) { "File is outside the shared directories: $path" }
            return false
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType.ifBlank { "*/*" })
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            // Otevírá se mimo naši úlohu: aplikace se otevře vedle nás, ne uvnitř historie chatu.
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return try {
            context.startActivity(intent)
            true
        } catch (failure: ActivityNotFoundException) {
            Logger.withTag("Files").w(failure) { "No application can open $mimeType" }
            false
        }
    }
}
