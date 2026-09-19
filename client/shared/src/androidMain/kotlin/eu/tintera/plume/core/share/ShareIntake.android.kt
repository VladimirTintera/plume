package eu.tintera.plume.core.share

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.ui.PICK_LIMIT
import eu.tintera.plume.core.ui.prepareDocument
import eu.tintera.plume.core.ui.prepareVisual
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch

/**
 * Příjem obsahu sdíleného ze systému (`ACTION_SEND` / `ACTION_SEND_MULTIPLE`).
 *
 * **Soubory se kopírují HNED** do cache aplikace, stejnou přípravou jako u výběru příloh (fotka
 * se zmenší, z videa se přečtou rozměry a náhled). Oprávnění ke čtení `content://` URI je
 * dočasné a patří přijímající aktivitě; až uživatel vybere chat, mohlo by už neplatit. Kopíruje
 * se v rozsahu aplikace ([scope]), ne aktivity — zavření aktivity nesmí kopii přerušit.
 *
 * **Fotky a videa jdou jako média, jen když jsou médii všechny.** Telegram fotky se soubory do
 * jednoho alba nesloží; smíšené sdílení proto odejde celé jako soubory, ať se nic neztratí.
 */
class ShareIntake(
    private val context: Context,
    private val requests: ShareRequests,
    private val scope: CoroutineScope,
) {

    private val logger = Logger.withTag("Share")

    /** Zpracuje intent, pokud je to sdílení; vrací `true`, když byl. */
    fun handle(intent: Intent): Boolean {
        if (intent.action != Intent.ACTION_SEND && intent.action != Intent.ACTION_SEND_MULTIPLE) return false

        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: intent.getStringExtra(Intent.EXTRA_SUBJECT)
        val uris = streams(intent).distinct()
        if (text.isNullOrBlank() && uris.isEmpty()) return true

        val limited = uris.take(PICK_LIMIT)
        val id = requests.start(text = text, hasFiles = limited.isNotEmpty())
        if (limited.isEmpty()) return true

        val visual = limited.all { isVisual(context.contentResolver.getType(it)) }
        scope.launch {
            val results = limited
                .map { uri ->
                    async(Dispatchers.IO) {
                        runCatching { if (visual) prepareVisual(context, uri) else prepareDocument(context, uri) }
                    }
                }
                .awaitAll()
            results.forEach { result ->
                result.exceptionOrNull()?.let { logger.w(it) { "Sdílený soubor se nepodařilo připravit." } }
            }
            requests.prepared(
                id = id,
                attachments = results.mapNotNull { it.getOrNull() },
                // Co se nevešlo pod strop alba, se taky neposlalo — uživatel to má vědět.
                failed = results.count { it.isFailure } + (uris.size - limited.size),
            )
        }
        return true
    }

    /** URI sdílených souborů: `EXTRA_STREAM` (jeden i víc), záložně `ClipData`. */
    private fun streams(intent: Intent): List<Uri> {
        val extra = if (intent.action == Intent.ACTION_SEND_MULTIPLE) {
            IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
        } else {
            listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
        }
        if (extra.isNotEmpty()) return extra
        val clip = intent.clipData ?: return emptyList()
        return (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri }
    }

    /** Obrázek, který jde zmenšit a poslat jako fotku, nebo video. SVG je obrázek jen podle jména. */
    private fun isVisual(mimeType: String?): Boolean = when {
        mimeType == null -> false
        mimeType == "image/svg+xml" -> false
        else -> mimeType.startsWith("image/") || mimeType.startsWith("video/")
    }
}
