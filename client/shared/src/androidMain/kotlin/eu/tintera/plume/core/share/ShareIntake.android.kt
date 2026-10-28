package eu.tintera.plume.core.share

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat
import androidx.core.content.pm.ShortcutManagerCompat
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.navigation.ChatOpenRequests
import eu.tintera.plume.core.ui.AttachmentPrepareDispatcher
import eu.tintera.plume.core.ui.PICK_LIMIT
import eu.tintera.plume.core.ui.prepareFile
import eu.tintera.plume.core.ui.displayName
import eu.tintera.plume.core.ui.prepareVisual
import kotlinx.coroutines.CoroutineScope
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
    /** Sdílení přes zkratku chatu (Direct Share) výběr přeskočí a otevře rovnou ten chat. */
    private val chatOpenRequests: ChatOpenRequests,
    private val scope: CoroutineScope,
) {

    private val logger = Logger.withTag("Share")

    /** Zpracuje intent, pokud je to sdílení; vrací `true`, když byl. */
    fun handle(intent: Intent): Boolean {
        if (intent.action != Intent.ACTION_SEND && intent.action != Intent.ACTION_SEND_MULTIPLE) return false

        val uris = streams(intent).distinct()
        /*
         * Předmět (`EXTRA_SUBJECT`) je náhrada textu jen bez souborů. U souboru je to jméno pro
         * e-mail, ne zpráva: snímek obrazovky ho nese jako „Screenshot (6. 10. 2026 18:18:38)"
         * a ten skončil v popisku každého sdíleného screenshotu.
         */
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            ?: intent.getStringExtra(Intent.EXTRA_SUBJECT)?.takeIf { uris.isEmpty() }
        if (text.isNullOrBlank() && uris.isEmpty()) return true

        /*
         * Export chatu z WhatsAppu přichází jako `.txt` se všemi médii najednou (desítky souborů).
         * Strop výběru příloh by import ořízl o přílohy, proto se u sdílení s `.txt` bere víc —
         * jestli to export opravdu je, pozná až konverzace (`ConversationImport`).
         */
        val exportLike = uris.size > 1 && uris.any { uri ->
            runCatching { displayName(context, uri) }.getOrNull()?.endsWith(".txt", ignoreCase = true) == true
        }
        val limited = uris.take(if (exportLike) EXPORT_FILE_LIMIT else PICK_LIMIT)
        val declared = declaredTypes(intent)
        val visual = limited.isNotEmpty() && limited.all { isVisual(typeOf(it) ?: declared) }
        val shortcutChat = chatIdFromShortcut(intent.getStringExtra(ShortcutManagerCompat.EXTRA_SHORTCUT_ID))
        // Chat se zakládá rovnou s obsahem, ne dalším voláním — viz `ShareRequests.start`.
        val id = requests.start(
            text = text,
            hasFiles = limited.isNotEmpty(),
            target = shortcutChat?.let { ShareTarget(it, confirm = visual) },
        )

        /*
         * Direct Share: uživatel ťukl rovnou na chat v nabídce „Sdílet", takže je chat vybraný
         * a výběr se neukáže (shell ho otevírá jen pro sdílení bez chatu).
         *
         * **Fotky a videa jdou na potvrzovací obrazovku** („Komu: …" s náhledem, popiskem
         * a volbou spoileru či mizení) — sdílený snímek je to jediné, co odejde, a než se pošle,
         * má být vidět velký. **Ostatní obsah** (soubory, text, odkaz) rovnou otevře konverzaci
         * a čeká nad vstupním polem jako dřív: u odkazu není co ukazovat a pole na popisek
         * k němu je krok navíc.
         */
        if (shortcutChat != null && !visual) chatOpenRequests.request(shortcutChat)

        if (limited.isEmpty()) return true

        scope.launch {
            val results = limited
                .map { uri ->
                    async(AttachmentPrepareDispatcher) {
                        runCatching { if (visual) prepareVisual(context, uri) else prepareFile(context, uri) }
                    }
                }
                .awaitAll()
            results.forEach { result ->
                result.exceptionOrNull()?.let { logger.w(it) { "Sdílený soubor se nepodařilo připravit." } }
            }
            requests.prepared(
                id = id,
                denied = results.count { it.exceptionOrNull() is SecurityException },
                attachments = results.mapNotNull { it.getOrNull() },
                sources = results.withIndex()
                    .mapNotNull { (index, result) -> result.getOrNull()?.let { it.path to limited[index].toString() } }
                    .toMap(),
                // Co se nevešlo pod strop alba, se taky neposlalo — uživatel to má vědět.
                failed = results.count { it.isFailure } + (uris.size - limited.size),
            )
        }
        return true
    }

    /**
     * URI sdílených souborů: `EXTRA_STREAM` (jeden i víc), záložně `ClipData`.
     *
     * **U sdílení textu se `ClipData` nečte.** Chrome ke sdílenému odkazu přikládá do `ClipData`
     * obrázek náhledu stránky (favicon nebo `og:image`) — jen pro náhled v systémové nabídce
     * „Sdílet", ne jako obsah. Brát ho za soubor znamenalo, že z odkazu byla fotka s odkazem jako
     * popiskem: výběr hlásil „1 soubor", Direct Share otevřel obrazovku „Komu: …" s logem webu
     * a text čekal, až se obrázek zkopíruje. Skutečný soubor nese `EXTRA_STREAM` (systém ho do
     * `ClipData` jen zrcadlí) a typ intentu pak textový není.
     */
    private fun streams(intent: Intent): List<Uri> {
        val extra = if (intent.action == Intent.ACTION_SEND_MULTIPLE) {
            IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
        } else {
            listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
        }
        if (extra.isNotEmpty()) return extra
        if (intent.type?.startsWith("text/") == true) return emptyList()
        val clip = intent.clipData ?: return emptyList()
        return (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri }
    }

    /**
     * Typ souboru od jeho poskytovatele; `null`, když ho bez přístupu nevydá.
     *
     * **Bez oprávnění `MediaProvider` typ neprozradí**, a právě tak chodí snímek obrazovky
     * sdílený z náhledu screenshotu přes Android System Intelligence (viz `ShareRequests.prepared`).
     */
    private fun typeOf(uri: Uri): String? = runCatching { context.contentResolver.getType(uri) }.getOrNull()

    /**
     * Typ, který tvrdí sdílející aplikace: typ intentu, a když je obecný (`*` / `*`), jediný typ
     * z popisu `ClipData`. Náhrada za [typeOf], když poskytovatel mlčí — screenshot přichází
     * s typem intentu `*` / `*` a `image/png` jen v popisu `ClipData`.
     */
    private fun declaredTypes(intent: Intent): String? {
        intent.type?.takeIf { !it.startsWith("*") }?.let { return it }
        val description = intent.clipData?.description ?: return null
        val types = (0 until description.mimeTypeCount).map(description::getMimeType).distinct()
        return types.singleOrNull()
    }

    /** Obrázek, který jde zmenšit a poslat jako fotku, nebo video. SVG je obrázek jen podle jména. */
    private fun isVisual(mimeType: String?): Boolean = when {
        mimeType == null -> false
        mimeType == "image/svg+xml" -> false
        else -> mimeType.startsWith("image/") || mimeType.startsWith("video/")
    }
}

/** Kolik souborů se vezme ze sdílení, které vypadá jako export chatu (`.txt` s přílohami). */
private const val EXPORT_FILE_LIMIT = 300
