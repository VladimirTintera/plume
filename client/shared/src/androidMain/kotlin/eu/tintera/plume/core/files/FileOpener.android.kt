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

    /** Naposledy vydané URI; při dalším otevření se mu oprávnění odebere. */
    private var granted: android.net.Uri? = null

    actual fun open(path: String, mimeType: String): Boolean {
        val uri = try {
            FileProvider.getUriForFile(context, "${context.packageName}.documents", File(path))
        } catch (failure: IllegalArgumentException) {
            // Soubor mimo adresáře, které provider vydává — chyba konfigurace, ne uživatele.
            Logger.withTag("Files").e(failure) { "Soubor je mimo sdílené adresáře: $path" }
            return false
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType.ifBlank { "*/*" })
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            // Otevírá se mimo naši úlohu: aplikace se otevře vedle nás, ne uvnitř historie chatu.
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        /*
         * Oprávnění se uděluje i výslovně, ne jen příznakem v intentu.
         *
         * Příznak platí pro spuštěnou aktivitu; některé aplikace (Fotky) ale soubor otevírají
         * z jiné části sebe sama a ta na grant z intentu nedosáhne — v logu je pak
         * „Permission Denial: opening provider … that is not exported" a uživatel vidí jen
         * „media not found". Grant se ruší při dalším otevření, ať nezůstane viset napořád.
         */
        context.packageManager.queryIntentActivities(intent, 0).forEach { target ->
            context.grantUriPermission(target.activityInfo.packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        return try {
            context.startActivity(intent)
            // Předchozí grant se ruší, JEN když šlo o jiný soubor. Odebrat ho u téhož URI by
            // znamenalo vzít si zpátky oprávnění, které jsme právě vydali — druhé otevření téhož
            // souboru pak skončilo na „media not found".
            granted?.takeIf { it != uri }?.let { previous ->
                context.revokeUriPermission(previous, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            granted = uri
            true
        } catch (failure: ActivityNotFoundException) {
            Logger.withTag("Files").w(failure) { "Typ $mimeType nemá čím otevřít" }
            false
        }
    }
}
