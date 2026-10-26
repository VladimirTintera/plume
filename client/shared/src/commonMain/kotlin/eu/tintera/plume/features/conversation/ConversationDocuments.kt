package eu.tintera.plume.features.conversation

import androidx.lifecycle.ViewModel
import co.touchlab.kermit.Logger
import kotlin.time.Duration.Companion.seconds
import eu.tintera.plume.core.files.SaveAsLauncher
import eu.tintera.plume.core.files.savedFileName
import eu.tintera.plume.core.telegram.DownloadsRepository
import eu.tintera.plume.core.telegram.FileRepository
import eu.tintera.plume.core.telegram.FileState
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.document_download_failed
import eu.tintera.plume.resources.document_open_failed
import eu.tintera.plume.resources.media_copied
import eu.tintera.plume.resources.media_copy_failed
import eu.tintera.plume.resources.media_save_failed
import eu.tintera.plume.resources.media_saved_as
import eu.tintera.plume.resources.media_share_failed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.TimeZone

/**
 * Soubory ve zprávách a v článcích: stažení na pokyn s průběhem ve stavu, otevření v systému,
 * sdílení a „Uložit jako…". Druhé ťuknutí během stahování ho zruší.
 *
 * Část [ConversationViewModel] — ten na ni deleguje.
 */
internal class ConversationDocuments(
    private val state: MutableStateFlow<ConversationState>,
    private val scope: CoroutineScope,
    private val fileRepository: FileRepository,
    /** Předá stažený soubor systému (`FileOpener.open`); `false` = nic ho neotevře. */
    private val openFile: (path: String, mimeType: String) -> Boolean,
    /** Systémový dialog sdílení (`MediaSharer.share`); `false` = nepovedlo se. */
    private val share: suspend (path: String, fileName: String) -> Boolean,
    /** Soubor do schránky (`MediaSharer.copy`); `false` = nepovedlo se. */
    private val copy: suspend (path: String, fileName: String, isImage: Boolean) -> Boolean = { _, _, _ -> false },
    /** Zkopírování potvrdí systém sám (`TextClipboard.confirmsCopy`) — pak bez hlášky. */
    private val copyConfirmedBySystem: Boolean = false,
    /** Položka nabídky nad zprávou zavírá nabídku sama, než začne stahovat. */
    private val closeMenus: () -> Unit,
    private val logger: Logger,
    /**
     * Seznam stahování TDLib: soubor stažený ze zprávy se do něj přidá, takže ho uživatel najde
     * v Stahování (menu účtu). `null` = jen obyčejné stažení (testy).
     */
    private val downloads: DownloadsRepository? = null,
    private val chatId: Long = 0,
) {
    /** Běžící otevírání souborů podle `fileId`; druhé ťuknutí to čekání zruší. */
    private val documentJobs = mutableMapOf<Int, Job>()

    /** Stáhne soubor a předá ho systému. */
    fun openDocument(messageId: Long) = withDocument(messageId) { path, document ->
        if (!openFile(path, document.mimeType)) {
            state.update { it.copy(notice = UiText(Res.string.document_open_failed)) }
        }
    }

    /** Soubor v článku: stejně jako soubor ve zprávě, jen se hledá v médiích článku. */
    fun openArticleDocument(messageId: Long, index: Int) {
        val document = state.value.articleMedia(messageId, index) as? MessageMedia.Document ?: return
        withDocument(document, messageId = null) { path, _ ->
            if (!openFile(path, document.mimeType)) {
                state.update { it.copy(notice = UiText(Res.string.document_open_failed)) }
            }
        }
    }

    /**
     * Stáhne soubor a otevře systémový dialog sdílení.
     *
     * Položka nabídky nabídku zavírá sama: stahování může chvíli trvat a jeho průběh je vidět
     * v řádku souboru, ne v nabídce — a hláška o výsledku by pod závojem zapadla.
     */
    fun shareDocument(messageId: Long) {
        closeMenus()
        shareDownloaded(messageId)
    }

    private fun shareDownloaded(messageId: Long) = withExported(messageId) { path, _, fileName ->
        if (!share(path, fileName)) {
            state.update { it.copy(notice = UiText(Res.string.media_share_failed)) }
        }
    }

    /**
     * Stáhne soubor, fotku nebo video zprávy a zkopíruje ho do schránky. Fotka jde i jako
     * obrázek, ostatní jen jako soubor (viz `MediaSharer.copy`).
     */
    fun copyToClipboard(messageId: Long) {
        closeMenus()
        withExported(messageId, media = true) { path, document, fileName ->
            val isImage = document.mimeType.startsWith("image/")
            val notice = when {
                !copy(path, fileName, isImage) -> Res.string.media_copy_failed
                copyConfirmedBySystem -> null
                else -> Res.string.media_copied
            }
            if (notice != null) state.update { it.copy(notice = UiText(notice)) }
        }
    }

    /**
     * Stáhne soubor a nabídne systémový výběr umístění („Uložit jako…").
     *
     * Výběr je aktivita systému, takže [launcher] dodá obrazovka; ViewModel ho jen zavolá, až je
     * soubor na disku. Zavřený výběr (`null`) není chyba a hlášku nedostane.
     */
    fun saveDocumentAs(messageId: Long, launcher: SaveAsLauncher) {
        closeMenus()
        saveDownloadedAs(messageId, launcher)
    }

    private fun saveDownloadedAs(messageId: Long, launcher: SaveAsLauncher) = withExported(messageId, media = true) { path, _, fileName ->
        launcher.launch(path, fileName) { saved ->
            when (saved) {
                true -> state.update { it.copy(notice = UiText(Res.string.media_saved_as)) }
                false -> state.update { it.copy(notice = UiText(Res.string.media_save_failed)) }
                null -> Unit
            }
        }
    }

    /**
     * Stáhne soubor zprávy a pak s ním udělá [use] — otevření, sdílení i „Uložit jako…" jdou
     * touž cestou, takže mají stejný průběh v řádku souboru i stejné hlášky při selhání.
     *
     * Stahuje se až na pokyn — soubory v chatu bývají velké a stahovat je dopředu by znamenalo
     * data za všechno, co kdo poslal. Průběh se drží ve stavu podle `fileId`, takže ho ukáže
     * i bublina alba.
     */
    /**
     * Totéž co [withDocument] pro cestu ven (sdílení, schránka, „Uložit jako…"), s jménem, pod
     * kterým soubor odejde ([savedFileName]: soubor svoje, fotka a video podle času zprávy).
     *
     * S [media] i fotka (ostrá verze, ne zmenšenina z bubliny) a video — v nabídce nad zprávou na
     * desktopu, kde prohlížeč není jediná cesta ven. Průběh stahování jim bublina neukazuje;
     * stažení je vidět v seznamu Stahování.
     */
    private fun withExported(
        messageId: Long,
        media: Boolean = false,
        use: suspend (path: String, document: MessageMedia.Document, fileName: String) -> Unit,
    ) {
        val message = state.value.messages.firstOrNull { it.id == messageId } ?: return
        val document = when (val content = message.media) {
            is MessageMedia.Document -> content
            is MessageMedia.Audio -> content.asDocument()
            // Jméno fotka ani video nemají; dostanou ho až podle přípony staženého souboru.
            is MessageMedia.Photo -> if (media) exportStandIn(content.fullFileId, "image/jpeg") else return
            is MessageMedia.Video -> if (media) exportStandIn(content.fileId, "video/mp4") else return
            else -> return
        }
        withDocument(document, messageId) { path, file ->
            use(path, file, savedFileName(file.fileName, path, message.timestamp, TimeZone.currentSystemDefault()))
        }
    }

    private fun withDocument(
        messageId: Long,
        use: suspend (path: String, document: MessageMedia.Document) -> Unit,
    ) {
        val document = when (val media = state.value.messages.firstOrNull { it.id == messageId }?.media) {
            is MessageMedia.Document -> media
            is MessageMedia.Audio -> media.asDocument()
            else -> return
        }
        withDocument(document, messageId, use)
    }

    private fun withDocument(
        document: MessageMedia.Document,
        /** Zpráva se souborem — kvůli seznamu stahování; soubor v článku ji nemá. */
        messageId: Long?,
        use: suspend (path: String, document: MessageMedia.Document) -> Unit,
    ) {

        // Druhé ťuknutí během čekání ho zruší. Bez toho by se z rozbitého stahování (mrtvé spojení,
        // odpověď, která nedorazí) nedalo dostat ven — kolečko by se točilo napořád.
        documentJobs.remove(document.fileId)?.let { running ->
            running.cancel()
            // Zrušit i stahování v TDLib — jinak by soubor tekl dál a potichu se dostáhl.
            scope.launch { fileRepository.cancelDownload(document.fileId) }
            state.update { it.copy(documentDownloads = it.documentDownloads - document.fileId) }
            return
        }

        state.update { it.copy(documentDownloads = it.documentDownloads + (document.fileId to 0f)) }

        documentJobs[document.fileId] = scope.launch {
            try {
                logger.d { "Otevírám soubor ${document.fileId} (${document.fileName})" }
                // Ťuknutí je výslovný pokyn: stahování se spustí znovu, i kdyby předchozí zůstalo viset.
                // Soubor ze zprávy jde přes seznam stahování (ten stahování spustí sám); když se
                // přidat nepovede, stáhne se postaru.
                val listed = messageId != null && downloads != null && chatId != 0L &&
                    downloads.add(document.fileId, chatId, messageId).isSuccess
                if (!listed) fileRepository.retryDownload(document.fileId)

                val states = fileRepository.file(document.fileId)
                    .onEach { file ->
                        if (file is FileState.Downloading) {
                            state.update { it.copy(documentDownloads = it.documentDownloads + (document.fileId to file.progress)) }
                        }
                    }

                /*
                 * Na ROZBĚH stahování se čeká jen chvíli, na dokončení pak jak dlouho je potřeba.
                 * Když TDLib neodpoví (mrtvé spojení), zůstal by stav `Pending` napořád; jakmile
                 * ale stahování běží, průběh je vidět a velký soubor smí trvat.
                 */
                val started = withTimeoutOrNull(DOWNLOAD_START_TIMEOUT) { states.first { it !is FileState.Pending } }
                val file = started?.let { first ->
                    if (first is FileState.Ready || first is FileState.Failed) first else states.first { it is FileState.Ready || it is FileState.Failed }
                }
                logger.d { "Soubor ${document.fileId} skončil jako $file" }

                when (file) {
                    is FileState.Ready -> use(file.path, document)
                    else -> state.update { it.copy(error = UiText(Res.string.document_download_failed)) }
                }
            } finally {
                // I při zrušení (odchod z chatu, druhé ťuknutí) musí průběh ze stavu zmizet.
                documentJobs.remove(document.fileId)
                state.update { it.copy(documentDownloads = it.documentDownloads - document.fileId) }
            }
        }
    }
}

/** Fotka nebo video jako soubor bez jména — jen aby šly cestou souborů ([ConversationDocuments.withExported]). */
private fun exportStandIn(fileId: Int, mimeType: String) =
    MessageMedia.Document(fileId = fileId, fileName = "", mimeType = mimeType, sizeBytes = 0, thumbnailFileId = null)

/** Jak dlouho se čeká, než se stahování souboru vůbec rozběhne. */
private val DOWNLOAD_START_TIMEOUT = 30.seconds
